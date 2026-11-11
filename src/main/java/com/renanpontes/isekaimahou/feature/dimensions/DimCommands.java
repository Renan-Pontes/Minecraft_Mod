package com.renanpontes.isekaimahou.feature.dimensions;

import java.util.Arrays;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.dimensions.block.CelestialBeaconBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;

/**
 * /mahou dimension info                  – cada dimensão: Círculo, visitas, se o portal (estrutura) já foi encontrado e onde,
 *                                          como despertá-lo e, se ainda não foi achado, a dica de onde procurar
 * /mahou dimension go <dimensão>         – (op) vai para o hub da dimensão, guardando o ponto de retorno
 * /mahou dimension back                  – (op) volta da dimensão atual para o ponto salvo
 * /mahou dimension rebuild               – (op) reconstrói o hub da dimensão atual
 */
public final class DimCommands {

    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("dimension")
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(MahouCommands.op("go").then(Commands.argument("dimension", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(DimKind.values()).map(k -> k.id), b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            DimKind kind = DimKind.byId(StringArgumentType.getString(c, "dimension"));
                            if (kind == null) {
                                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.dimensions.unknown"));
                                return 0;
                            }
                            ServerLevel from = p.serverLevel();
                            DimensionTransition t = DimTravel.enter(from, p, kind);
                            if (t == null) {
                                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.dimensions.missing", kind.displayName()));
                                return 0;
                            }
                            p.changeDimension(t);
                            return 1;
                        })))
                .then(MahouCommands.op("back").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    DimKind kind = DimKind.of(p.level().dimension());
                    DimensionTransition t = kind == null ? null : DimTravel.leave(p.serverLevel(), p, kind);
                    if (t == null) {
                        c.getSource().sendFailure(Component.translatable("command.isekai_mahou.dimensions.not_inside"));
                        return 0;
                    }
                    p.changeDimension(t);
                    return 1;
                }))
                .then(MahouCommands.op("rebuild").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    DimKind kind = DimKind.of(p.level().dimension());
                    if (kind == null) {
                        c.getSource().sendFailure(Component.translatable("command.isekai_mahou.dimensions.not_inside"));
                        return 0;
                    }
                    boolean ok = DimHubs.build(p.serverLevel(), kind, false);
                    DimHubs.ensureSafeArrival(p.serverLevel(), kind);
                    c.getSource().sendSuccess(() -> Component.translatable(ok ? "command.isekai_mahou.dimensions.rebuilt"
                            : "command.isekai_mahou.dimensions.rebuild_failed", kind.displayName()), true);
                    return ok ? 1 : 0;
                }))));
    }

    private static int info(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MagicData d = MagicApi.get(p);
        boolean op = src.hasPermission(2);
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.dimensions.header")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        for (DimKind k : DimKind.values()) {
            int circle = minCircle(k);
            boolean ok = d.circle() >= circle && (k != DimKind.TWILIGHT || DimTravel.missingTwilight(d).isEmpty());
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.dimensions.line",
                            k.displayName(), circle, d.counter(k.visitKey()))
                    .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);

            // Onde fica o portal: as estruturas que o jogador já visitou (a mais perto) ou a dica de onde procurar.
            List<Places.Place> known = DimSites.known(p, k);
            if (!known.isEmpty()) {
                Places.Place site = known.get(0);
                src.sendSuccess(() -> siteLine(p, k, site, op), false);
                if (known.size() > 1) {
                    src.sendSuccess(() -> Component.translatable("command.isekai_mahou.dimensions.site_more", known.size() - 1)
                            .withStyle(ChatFormatting.DARK_GRAY), false);
                }
            } else if (DimSites.discovered(p, k)) {
                src.sendSuccess(() -> Component.translatable("command.isekai_mahou.dimensions.site_seen", k.siteName().withColor(k.color))
                        .withStyle(ChatFormatting.GRAY), false);
            } else {
                src.sendSuccess(() -> Component.translatable("command.isekai_mahou.dimensions.site_unknown",
                        k.siteName().withColor(k.color), k.siteWhere()).withStyle(ChatFormatting.GRAY), false);
            }
            if (DimSites.pathOpened(p, k)) {
                src.sendSuccess(() -> Component.translatable("command.isekai_mahou.dimensions.path_opened")
                        .withStyle(ChatFormatting.DARK_AQUA), false);
            }
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.dimensions.awaken", awaken(k))
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            if (k == DimKind.TWILIGHT) {
                for (Component miss : DimTravel.missingTwilight(d)) {
                    src.sendSuccess(() -> Component.translatable("command.isekai_mahou.dimensions.missing_req", miss)
                            .withStyle(ChatFormatting.RED), false);
                }
            }
        }
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.dimensions.route_hint")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        return 1;
    }

    static int minCircle(DimKind k) {
        return switch (k) {
            case ELEMENTAL -> PortalKind.ELEMENTAL.minCircle;
            case ABYSS -> PortalKind.ABYSS.minCircle;
            case CELESTIAL -> CelestialBeaconBlock.MIN_CIRCLE;
            case TWILIGHT -> DimTravel.TWILIGHT_MIN_CIRCLE;
        };
    }

    /** Como despertar o portal desta dimensão (mesmo texto da dica do item). */
    private static Component awaken(DimKind k) {
        return switch (k) {
            case ELEMENTAL, ABYSS -> {
                PortalKind pk = k == DimKind.ELEMENTAL ? PortalKind.ELEMENTAL : PortalKind.ABYSS;
                yield pk.manaCost > 0
                        ? Component.translatable("tooltip.isekai_mahou.dimensions.frame_mana", pk.key().getDescription(), pk.minCircle, pk.manaCost)
                        : Component.translatable("tooltip.isekai_mahou.dimensions.frame_offer", pk.key().getDescription(), pk.minCircle);
            }
            case CELESTIAL -> Component.translatable("tooltip.isekai_mahou.dimensions.beacon_noon",
                    Convergence.essence(Element.LIGHT).getDescription(), CelestialBeaconBlock.MIN_CIRCLE);
            case TWILIGHT -> Component.translatable("tooltip.isekai_mahou.dimensions.rift_awaken", DimTravel.TWILIGHT_MIN_CIRCLE,
                    DimTravel.bossName(DimTravel.BOSS_ABYSS), DimTravel.bossName(DimTravel.BOSS_SERAPH));
        };
    }

    /** "Portão Elemental — 1204 71 -388 (a 640 blocos)"; para op, clicar nas coordenadas sugere o /tp. */
    private static Component siteLine(ServerPlayer p, DimKind k, Places.Place site, boolean op) {
        BlockPos c = site.center();
        MutableComponent coords = Component.translatable("command.isekai_mahou.dimensions.coords", c.getX(), c.getY(), c.getZ());
        if (op) {
            coords = coords.withStyle(s -> s.withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + c.getX() + " " + c.getY() + " " + c.getZ()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("command.isekai_mahou.dimensions.tp_hover"))));
        }
        Component where;
        if (site.dim().equals(p.level().dimension())) {
            double dx = c.getX() + 0.5 - p.getX(), dz = c.getZ() + 0.5 - p.getZ();
            where = Component.translatable("command.isekai_mahou.dimensions.distance", (int) Math.round(Math.sqrt(dx * dx + dz * dz)));
        } else {
            where = Component.translatable("command.isekai_mahou.dimensions.other_world");
        }
        return Component.translatable("command.isekai_mahou.dimensions.site_found", site.name().copy().withColor(k.color), coords, where)
                .withStyle(ChatFormatting.WHITE);
    }

    private DimCommands() {}
}
