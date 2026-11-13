package com.renanpontes.isekaimahou.core.onboard;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Dialogue;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Travel;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos (op):
 * <pre>
 * /mahou onboarding reset [jogadores]   – recomeça a entrada (sem classe: tela de classe; com classe: parabéns + tutorial)
 * /mahou onboarding skip [jogadores]    – pula o tutorial (TutorialDone) e fecha os diálogos
 * /mahou travel list                    – destinos conhecidos, custo pelo menu e situação
 * /mahou onboarding status [jogadores]  – classe, etapas de criação pendentes e se a proteção da entrada está ativa
 * /mahou travel menu                    – abre a lista de viagem (como a aba Viagem)
 * /mahou travel go &lt;lugar&gt;              – viaja como pela pedra (sem recarga)
 * /mahou travel discover &lt;lugar|all&gt;     – marca lugar(es) como descoberto(s)
 * /mahou travel cooldown                – zera a recarga da viagem pelo menu
 * </pre>
 */
final class OnboardCommands {

    static void register() {
        MahouCommands.extend((root, ctx) -> {
            root.then(MahouCommands.op("onboarding")
                    .then(Commands.literal("reset")
                            .executes(c -> reset(c, List.of(c.getSource().getPlayerOrException())))
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .executes(c -> reset(c, EntityArgument.getPlayers(c, "targets")))))
                    .then(Commands.literal("skip")
                            .executes(c -> skip(c, List.of(c.getSource().getPlayerOrException())))
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .executes(c -> skip(c, EntityArgument.getPlayers(c, "targets")))))
                    // v1.0: em que ponto da criação do personagem cada um está (e se está protegido)
                    .then(Commands.literal("status")
                            .executes(c -> status(c, List.of(c.getSource().getPlayerOrException())))
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .executes(c -> status(c, EntityArgument.getPlayers(c, "targets"))))));

            root.then(MahouCommands.op("travel")
                    .then(Commands.literal("list").executes(OnboardCommands::list))
                    .then(Commands.literal("menu").executes(c -> {
                        Travel.openMenu(c.getSource().getPlayerOrException(), null);
                        return 1;
                    }))
                    .then(Commands.literal("go").then(Commands.argument("place", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(placeIds(c.getSource()), b))
                            .executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                Travel.TravelResult r = Travel.travel(p, StringArgumentType.getString(c, "place"), true);
                                c.getSource().sendSuccess(() -> r.message(), false);
                                return r.success() ? 1 : 0;
                            })))
                    .then(Commands.literal("discover").then(Commands.argument("place", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(withAll(placeIds(c.getSource())), b))
                            .executes(OnboardCommands::discover)))
                    .then(Commands.literal("cooldown").executes(c -> {
                        Travel.resetCooldown(c.getSource().getPlayerOrException());
                        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.travel.cooldown_reset"), false);
                        return 1;
                    })));
        });
    }

    private static int reset(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            Dialogue.close(p);
            OnboardService.reset(p);
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.onboarding.reset", players.size()), true);
        return players.size();
    }

    private static int skip(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            Dialogue.close(p);
            Onboarding.skipTutorial(p);
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.onboarding.skip", players.size()), true);
        return players.size();
    }

    private static int status(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            List<String> pending = OnboardService.pendingCreationSteps(p);
            Component line = Component.translatable("command.isekai_mahou.onboarding.status", p.getDisplayName(),
                    com.renanpontes.isekaimahou.core.MagicApi.get(p).path().displayName(),
                    pending.isEmpty() ? "-" : String.join(", ", pending),
                    Component.translatable(EntryGuard.isProtected(p) ? "gui.yes" : "gui.no"));
            c.getSource().sendSuccess(() -> line, false);
        }
        return players.size();
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<Places.Place> dests = Travel.destinations(p);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.travel.header", dests.size(),
                TravelService.formatTime(Travel.menuCooldown(p))).withStyle(ChatFormatting.GOLD), false);
        for (Places.Place place : dests) {
            Travel.TravelResult r = Travel.check(p, place, false);
            Component line = Component.literal(" • ").append(place.name().copy().withColor(place.color() != 0 ? place.color() : 0xFFFFFF))
                    .append(Component.literal(" [" + place.id() + "] " + place.dim().location().getPath() + " ")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("command.isekai_mahou.travel.line", Travel.cost(p, place, true),
                            Travel.cost(p, place, false)).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" "))
                    .append(r.message().copy().withStyle(r.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
            c.getSource().sendSuccess(() -> line, false);
        }
        return dests.size();
    }

    private static int discover(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "place");
        int n = 0;
        if (id.equals("all")) {
            for (String pid : placeIds(c.getSource())) {
                if (TravelService.discover(p, pid, false)) n++;
            }
        } else if (TravelService.discover(p, id, true)) {
            n = 1;
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.travel.discovered", count), false);
        return n;
    }

    /** Ids de todos os lugares que aceitam viagem + os hubs das dimensões existentes. */
    private static List<String> placeIds(CommandSourceStack src) {
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        for (ServerLevel level : src.getServer().getAllLevels()) {
            for (Places.Place p : Places.all(level)) if (TravelService.travelable(p)) ids.add(p.id());
        }
        for (TravelService.Hub hub : TravelService.Hub.values()) {
            if (src.getServer().getLevel(hub.dim) != null && !ids.contains(hub.placeId())) ids.add(hub.placeId());
        }
        return ids;
    }

    private static List<String> withAll(List<String> ids) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(ids);
        out.add("all");
        return out;
    }

    private OnboardCommands() {}
}
