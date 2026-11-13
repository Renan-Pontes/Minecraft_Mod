package com.renanpontes.isekaimahou.core.balance;

import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.Progression;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /mahou balance} (op) — imprime a tabela da curva de XP para conferir o balanço sem jogar horas:
 * XP para o próximo nível e o total acumulado desde o nível 1.
 * <ul>
 *   <li>{@code /mahou balance} — resumo (de 10 em 10 níveis, mais os marcos 50/100/150/200);</li>
 *   <li>{@code /mahou balance <de> <até>} — nível por nível no intervalo (no máx. 60 linhas).</li>
 * </ul>
 */
public final class BalanceCommands {
    private static final int MAX_ROWS = 60;

    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("balance")
                .executes(BalanceCommands::summary)
                .then(Commands.argument("from", IntegerArgumentType.integer(1, Progression.HIDDEN_MAX_LEVEL))
                        .then(Commands.argument("to", IntegerArgumentType.integer(1, Progression.HIDDEN_MAX_LEVEL))
                                .executes(c -> range(c, IntegerArgumentType.getInteger(c, "from"),
                                        IntegerArgumentType.getInteger(c, "to")))))));
    }

    private static int summary(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        header(src);
        for (int l = 1; l < Progression.HIDDEN_MAX_LEVEL; l = l == 1 ? 10 : l + 10) row(src, l);
        row(src, Progression.HIDDEN_MAX_LEVEL - 1);
        // Totais por trecho: é aqui que se vê o "mais difícil depois do 50 e do 100"
        int[] marks = {1, Progression.STEEP_LEVEL, Progression.CIRCLE_TEN_LEVEL, Progression.MAX_LEVEL, Progression.HIDDEN_MAX_LEVEL};
        for (int i = 0; i + 1 < marks.length; i++) {
            int a = marks[i], b = marks[i + 1];
            long span = Progression.totalXpTo(b) - Progression.totalXpTo(a);
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.balance.span", a, b, fmt(span))
                    .withStyle(ChatFormatting.AQUA), false);
        }
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.balance.hint").withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int range(CommandContext<CommandSourceStack> c, int from, int to) {
        CommandSourceStack src = c.getSource();
        int a = Math.min(from, to), b = Math.min(Math.max(from, to), a + MAX_ROWS - 1);
        header(src);
        for (int l = a; l <= b && l < Progression.HIDDEN_MAX_LEVEL; l++) row(src, l);
        return 1;
    }

    private static void header(CommandSourceStack src) {
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.balance.header").withStyle(ChatFormatting.GOLD), false);
    }

    /** Uma linha: nível L → L+1, XP desse nível e total acumulado até chegar em L+1. */
    private static void row(CommandSourceStack src, int level) {
        int need = Progression.xpToNext(level);
        long total = Progression.totalXpTo(level + 1);
        ChatFormatting color = level >= Progression.CIRCLE_TEN_LEVEL ? ChatFormatting.LIGHT_PURPLE
                : level >= Progression.STEEP_LEVEL ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.balance.row", level, level + 1, fmt(need), fmt(total))
                .withStyle(color), false);
    }

    /** Número com separador de milhar (ponto, como no PT-BR). */
    private static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', '.');
    }

    private BalanceCommands() {}
}
