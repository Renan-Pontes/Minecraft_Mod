package com.renanpontes.isekaimahou.feature.class_ranger;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de teste (cheats): {@code /mahou ranger rank <n>} conclui a Trilha do Ranger até o rank n (com as
 * recompensas), {@code /mahou ranger call} chama os companheiros e {@code /mahou ranger steed} o Corcel.
 */
public final class RangerCommands {
    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("ranger")
                .then(Commands.literal("rank").then(Commands.argument("rank", IntegerArgumentType.integer(1, RangerProgress.MAX_RANK))
                        .executes(c -> rank(c, IntegerArgumentType.getInteger(c, "rank")))))
                .then(Commands.literal("call").executes(RangerCommands::call))
                .then(Commands.literal("steed").executes(RangerCommands::steed))));
    }

    private static int rank(CommandContext<CommandSourceStack> c, int rank) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        if (MagicApi.get(sp).path() != MagicPath.RANGER) {
            c.getSource().sendFailure(Component.translatable("trail.isekai_mahou.class_ranger.need_class"));
            return 0;
        }
        for (int i = 1; i <= rank; i++) RangerService.grant(sp, i);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.class_ranger.rank", rank), true);
        return 1;
    }

    private static int call(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        return RangerCompanions.call(sp, null, 1.0F) ? 1 : 0;
    }

    private static int steed(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        return RangerCompanions.callSteed(sp) != null ? 1 : 0;
    }

    private RangerCommands() {}
}
