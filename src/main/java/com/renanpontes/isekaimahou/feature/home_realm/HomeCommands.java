package com.renanpontes.isekaimahou.feature.home_realm;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Rewards;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Comandos da Dimensão Casa.
 * <ul>
 *   <li>Para todos (mesmas regras da tecla/aba): {@code /mahou home enter | leave | visit <jogador> | invite <jogador> |
 *       uninvite <jogador>}.</li>
 *   <li>Teste (op): {@code /mahou home key} (dá a chave), {@code unlock [jogador]} (libera sem chave), {@code go} (entra
 *       ignorando o combate; libera se preciso), {@code goto <jogador>} (entra no lar de alguém sem convite),
 *       {@code combat} / {@code combat clear} (marca/limpa o "em combate"), {@code status} (lista os lares).</li>
 * </ul>
 */
final class HomeCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("home")
                .then(Commands.literal("enter").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return HomeManager.enter(p, p.getUUID(), false) ? 1 : 0;
                }))
                .then(Commands.literal("leave").executes(c -> HomeManager.leave(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("visit").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return HomeManager.enter(p, EntityArgument.getPlayer(c, "player").getUUID(), false) ? 1 : 0;
                })))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    HomeManager.invite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player").getUUID());
                    return 1;
                })))
                .then(Commands.literal("uninvite").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    HomeManager.uninvite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player").getUUID());
                    return 1;
                })))
                .then(MahouCommands.op("key").executes(c -> {
                    Rewards.give(c.getSource().getPlayerOrException(), new ItemStack(HomeContent.HOME_KEY.get()));
                    return 1;
                }))
                .then(MahouCommands.op("unlock")
                        .executes(c -> HomeManager.unlock(c.getSource().getPlayerOrException()) ? 1 : 0)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> HomeManager.unlock(EntityArgument.getPlayer(c, "player")) ? 1 : 0)))
                .then(MahouCommands.op("go").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!HomeManager.owns(p)) HomeManager.unlock(p);
                    return HomeManager.enter(p, p.getUUID(), true) ? 1 : 0;
                }))
                .then(MahouCommands.op("goto").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return HomeManager.enter(p, EntityArgument.getPlayer(c, "player").getUUID(), true) ? 1 : 0;
                })))
                .then(MahouCommands.op("combat")
                        .executes(c -> combat(c, true))
                        .then(Commands.literal("clear").executes(c -> combat(c, false))))
                .then(MahouCommands.op("status").executes(HomeCommands::status))));
    }

    private static int combat(CommandContext<CommandSourceStack> c, boolean mark) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (mark) HomeManager.markCombat(p);
        else HomeManager.clearCombat(p);
        HomeManager.sendInfo(p);
        int seconds = HomeManager.combatLeft(p) / 20;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.home_realm.combat", seconds), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        var homes = HomeManager.data().all();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.home_realm.status", homes.size()), false);
        for (HomeData.Home h : homes) {
            var at = HomeDimension.center(h.index);
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.home_realm.status_row", h.index, h.ownerName,
                    at.getX(), at.getY() + 1, at.getZ(), h.invited.size(), h.built ? "ok" : "...").withStyle(ChatFormatting.GRAY), false);
        }
        return homes.size();
    }

    private HomeCommands() {}
}
