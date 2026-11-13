package com.renanpontes.isekaimahou.feature.travel.guide;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * <pre>
 * /mahou portals                     – o Guia de Portais em texto (cada dimensão: requisito e se você já cumpre)
 * /mahou portals track &lt;dimensão&gt;    – o mesmo que o botão "Rastrear o mais próximo" do guia
 * /mahou portals untrack             – para de rastrear
 * /mahou portals locate &lt;dimensão&gt;   – (op) acha o portal mais próximo e mostra as coordenadas (teste de raridade)
 * /mahou portals markers             – (op) lista os pontos do minimapa que seriam mandados a você agora
 * /mahou portals markers resync      – (op) manda a lista de novo ao cliente
 * /mahou portals markers forget      – (op) apaga os pontos de estrutura guardados em você
 * </pre>
 */
public final class GuideCommands {

    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("portals")
                .executes(c -> list(c.getSource()))
                .then(Commands.literal("track").then(Commands.argument("dimension", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                PortalGuide.entries().stream().filter(PortalGuide.Entry::trackable).map(PortalGuide.Entry::id), b))
                        .executes(c -> {
                            GuideService.track(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "dimension"));
                            return 1;
                        })))
                .then(Commands.literal("untrack").executes(c -> {
                    GuideService.track(c.getSource().getPlayerOrException(), "");
                    return 1;
                }))
                .then(MahouCommands.op("locate").then(Commands.argument("dimension", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                PortalGuide.entries().stream().filter(PortalGuide.Entry::trackable).map(PortalGuide.Entry::id), b))
                        .executes(c -> locate(c.getSource(), StringArgumentType.getString(c, "dimension")))))
                .then(MahouCommands.op("markers")
                        .executes(c -> markers(c.getSource()))
                        .then(Commands.literal("resync").executes(c -> {
                            MapMarkers.sync(c.getSource().getPlayerOrException(), "");
                            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.portals.resynced"), false);
                            return 1;
                        }))
                        .then(Commands.literal("forget").executes(c -> {
                            MapMarkers.forget(c.getSource().getPlayerOrException());
                            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.portals.forgot"), false);
                            return 1;
                        })))));
    }

    private static int list(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MagicData d = MagicApi.get(p);
        src.sendSuccess(() -> Component.translatable("guide.isekai_mahou.portals.title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (PortalGuide.Entry e : PortalGuide.entries()) {
            MutableComponent line = Component.literal("• ").append(e.name());
            if (e.trackable()) line.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY)).append(e.site());
            if (e.minCircle() > 0) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable("guide.isekai_mahou.portals.req_circle", e.minCircle())
                                .withStyle(e.circleOk(d) ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            for (String boss : e.bosses()) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable("guide.isekai_mahou.portals.req_boss", PortalGuide.bossName(boss))
                                .withStyle(e.bossDone(d, boss) ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            if (e.visited(d)) {
                line.append(Component.literal(" ✔").withStyle(ChatFormatting.GREEN));
            }
            src.sendSuccess(() -> line, false);
            src.sendSuccess(() -> Component.literal("   ").append(e.how()).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int locate(CommandSourceStack src, String id) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        PortalGuide.Entry e = PortalGuide.get(id);
        if (e == null || !e.trackable()) {
            src.sendFailure(Component.translatable("command.isekai_mahou.portals.unknown", id));
            return 0;
        }
        ServerLevel level = p.getServer().getLevel(e.siteDim());
        if (level == null) {
            src.sendFailure(Component.translatable("command.isekai_mahou.portals.unknown", id));
            return 0;
        }
        BlockPos from = p.blockPosition();
        long t0 = System.nanoTime();
        GuideService.Found f = GuideService.find(level, e, from);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        if (f.pos() == null) {
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.portals.locate_none", e.site(), f.reach(), ms), false);
            return 0;
        }
        int dist = (int) Math.sqrt(f.pos().distSqr(new BlockPos(from.getX(), f.pos().getY(), from.getZ())));
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.portals.locate_found", e.site(),
                f.pos().getX(), f.pos().getY(), f.pos().getZ(), dist, ms), false);
        return 1;
    }

    private static int markers(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        List<GuideNet.Marker> list = MapMarkers.collect(p);
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.portals.markers", list.size()), false);
        for (GuideNet.Marker m : list) {
            src.sendSuccess(() -> Component.literal(" [" + m.category() + "] ").withStyle(ChatFormatting.GRAY)
                    .append(m.name()).append(Component.literal(" " + m.pos().toShortString()).withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return 1;
    }

    private GuideCommands() {}
}
