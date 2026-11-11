package com.renanpontes.isekaimahou.core.reputation;

import java.util.Collection;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Reputation.Faction;
import com.renanpontes.isekaimahou.core.api.Reputation.Standing;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /mahou rep [jogador]} (qualquer um: lista) · op: {@code /mahou rep set|add <jogadores> <facção> <n>},
 * {@code /mahou rep reset <jogadores>}.
 */
final class ReputationCommands {

    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("rep")
                .executes(c -> list(c.getSource(), c.getSource().getPlayerOrException()))
                .then(MahouCommands.op("set").then(Commands.argument("targets", EntityArgument.players())
                        .then(factionArg().then(Commands.argument("value", IntegerArgumentType.integer(Reputation.MIN, Reputation.MAX))
                                .executes(c -> change(c, true))))))
                .then(MahouCommands.op("add").then(Commands.argument("targets", EntityArgument.players())
                        .then(factionArg().then(Commands.argument("value", IntegerArgumentType.integer(-2000, 2000))
                                .executes(c -> change(c, false))))))
                .then(MahouCommands.op("reset").then(Commands.argument("targets", EntityArgument.players())
                        .executes(ReputationCommands::reset)))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> list(c.getSource(), EntityArgument.getPlayer(c, "player"))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> factionArg() {
        return Commands.argument("faction", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(FactionRegistry.server().sorted().stream().map(Faction::id), b));
    }

    private static int list(CommandSourceStack src, ServerPlayer player) {
        Collection<Faction> all = FactionRegistry.server().sorted();
        if (all.isEmpty()) {
            src.sendFailure(Component.translatable("command.isekai_mahou.rep.empty"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.rep.header", player.getDisplayName())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (Faction f : all) {
            int value = Reputation.get(player, f.id());
            Standing s = Standing.of(value);
            Standing next = s.next();
            MutableComponent nextText = next == null
                    ? Component.translatable("command.isekai_mahou.rep.max").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("command.isekai_mahou.rep.next", next.displayName(), next.min - value)
                            .withStyle(ChatFormatting.DARK_GRAY);
            Component line = Component.translatable("command.isekai_mahou.rep.entry", f.coloredName(), s.displayName(),
                    Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE), nextText).withStyle(ChatFormatting.GRAY);
            src.sendSuccess(() -> line, false);
        }
        MutableComponent title = Reputation.selectedTitle(player);
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.rep.title",
                title != null ? title : Component.translatable("screen.isekai_mahou.reputation.title_none").withStyle(ChatFormatting.DARK_GRAY))
                .withStyle(ChatFormatting.GRAY), false);
        return all.size();
    }

    private static int change(CommandContext<CommandSourceStack> c, boolean set) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "faction");
        Faction f = FactionRegistry.server().get(id);
        if (f == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.rep.unknown", id));
            return 0;
        }
        int n = IntegerArgumentType.getInteger(c, "value");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        Component reason = Component.translatable("reputation.isekai_mahou.reason.command");
        for (ServerPlayer p : targets) {
            if (set) ReputationService.set(p, f.id(), n, reason);
            else ReputationService.add(p, f.id(), n, reason);
            int now = Reputation.get(p, f.id());
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.rep.changed", p.getDisplayName(), f.coloredName(),
                    Standing.of(now).displayName(), now), true);
        }
        return targets.size();
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : targets) {
            ReputationService.reset(p);
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.rep.reset", p.getDisplayName()), true);
        }
        return targets.size();
    }

    private ReputationCommands() {}
}
