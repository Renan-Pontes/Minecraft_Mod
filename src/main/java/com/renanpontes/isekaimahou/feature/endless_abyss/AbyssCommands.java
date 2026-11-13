package com.renanpontes.isekaimahou.feature.endless_abyss;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /mahou abyss ...}
 * <ul>
 *   <li>qualquer jogador: {@code leave} (sair da descida), {@code record} (seu recorde e o ranking);</li>
 *   <li>op (teste): {@code enter [andar]} (sem exigir nível 100 nem ponto de partida), {@code shadow <chefe>} (qualquer
 *       chefe, mesmo sem ter vencido), {@code floor <n>} (pula a descida atual para o andar), {@code skip} (vence a
 *       onda/chefe em campo), {@code setbest <n>}, {@code status}.</li>
 * </ul>
 */
final class AbyssCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("abyss")
                .then(Commands.literal("leave").executes(c -> {
                    AbyssManager.leave(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("record").executes(AbyssCommands::record))
                .then(MahouCommands.op("enter")
                        .executes(c -> AbyssManager.enterEndless(c.getSource().getPlayerOrException(), 1, true) ? 1 : 0)
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 9999))
                                .executes(c -> AbyssManager.enterEndless(c.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(c, "floor"), true) ? 1 : 0)))
                .then(MahouCommands.op("shadow").then(Commands.argument("boss", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(AbyssShadows.all(), b))
                        .executes(c -> AbyssManager.enterShadow(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "boss"), true) ? 1 : 0)))
                .then(MahouCommands.op("floor").then(Commands.argument("n", IntegerArgumentType.integer(1, 9999)).executes(c -> {
                    AbyssRun run = AbyssManager.of(c.getSource().getPlayerOrException().getUUID());
                    if (run == null) return notIn(c);
                    run.debugFloor(IntegerArgumentType.getInteger(c, "n"));
                    return 1;
                })))
                .then(MahouCommands.op("skip").executes(c -> {
                    AbyssRun run = AbyssManager.of(c.getSource().getPlayerOrException().getUUID());
                    if (run == null) return notIn(c);
                    run.debugSkip();
                    return 1;
                }))
                .then(MahouCommands.op("setbest").then(Commands.argument("n", IntegerArgumentType.integer(0, 9999)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    AbyssManager.data().setBest(p.getUUID(), p.getGameProfile().getName(), IntegerArgumentType.getInteger(c, "n"),
                            p.serverLevel().getGameTime());
                    AbyssNetwork.sendInfo(p);
                    return record(c);
                })))
                .then(MahouCommands.op("status").executes(c -> {
                    List<AbyssRun> runs = AbyssManager.all();
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.endless_abyss.status", runs.size()), false);
                    for (AbyssRun r : runs) c.getSource().sendSuccess(r::describe, false);
                    return runs.size();
                }))));
    }

    private static int notIn(CommandContext<CommandSourceStack> c) {
        c.getSource().sendFailure(Component.translatable("message.isekai_mahou.endless_abyss.deny.not_in"));
        return 0;
    }

    private static int record(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int best = AbyssRecords.bestFloor(p.server, p.getUUID());
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.endless_abyss.record", best,
                AbyssRecords.shadowWins(p.server, p.getUUID())).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        int pos = 1;
        for (AbyssRecords.Entry e : AbyssRecords.top(p.server, 10)) {
            int rank = pos++;
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.endless_abyss.rank_row", rank, e.name(),
                    e.bestFloor()).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private AbyssCommands() {}
}
