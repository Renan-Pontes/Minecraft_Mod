package com.renanpontes.isekaimahou.feature.class_gambler;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.JackpotService;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseChallenge;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseRanks;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /mahou gambler ... (cheats): {@code jackpot} liga o Jackpot, {@code fever <0-100>} ajusta a Tensão,
 * {@code rank <0-5>} sobe a Casa até o rank (com as recompensas), {@code luck} mostra Sorte/esquiva/valor esperado.
 */
public final class GamblerCommands {
    public static void register() {
        MahouCommands.extend((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> g = MahouCommands.op("gambler");
            g.then(Commands.literal("jackpot").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                if (!JackpotService.active(sp)) JackpotService.start(sp, 0);
                return 1;
            }));
            g.then(Commands.literal("fever").then(Commands.argument("value", IntegerArgumentType.integer(0, LuckService.FEVER_MAX))
                    .executes(c -> {
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        int v = IntegerArgumentType.getInteger(c, "value");
                        LuckService.setFever(MagicApi.get(sp), v);
                        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.gambler.fever", v), false);
                        return 1;
                    })));
            g.then(Commands.literal("rank").then(Commands.argument("rank", IntegerArgumentType.integer(0, HouseRanks.MAX_RANK))
                    .executes(c -> {
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        int target = IntegerArgumentType.getInteger(c, "rank");
                        for (int n = HouseRanks.rank(MagicApi.get(sp)) + 1; n <= target; n++) {
                            HouseRanks.Rank r = HouseRanks.rank(n);
                            if (r != null) HouseChallenge.rankUp(sp, r, false);
                        }
                        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.gambler.rank",
                                HouseRanks.rank(MagicApi.get(sp))), false);
                        return 1;
                    })));
            g.then(Commands.literal("luck").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                int luck = LuckService.luck(sp);
                float ev = LuckService.expected(luck, LuckService.Options.NONE);
                c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.gambler.luck", luck,
                        LuckService.fever(MagicApi.get(sp)), String.format(java.util.Locale.ROOT, "%.2f", ev),
                        Math.round(com.renanpontes.isekaimahou.feature.class_gambler.luck.GamblerPassives.dodgeChance(sp) * 100)), false);
                return 1;
            }));
            root.then(g);
        });
    }

    private GamblerCommands() {}
}
