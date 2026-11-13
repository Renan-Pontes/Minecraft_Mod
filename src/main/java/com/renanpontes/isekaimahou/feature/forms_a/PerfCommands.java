package com.renanpontes.isekaimahou.feature.forms_a;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.ElementReactions.Reaction;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.fx.FxBatch;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Comandos de teste do trabalho de desempenho (op): {@code /mahou fx ...} (o {@code /mahou perf} já é o medidor de tick do núcleo)
 * <ul>
 *   <li>{@code stats} — quantos itens de efeito saíram em quantos pacotes desde a última consulta (e quantos pacotes
 *   seriam no jeito antigo, um por partícula);</li>
 *   <li>{@code stress [efeitos] [segundos]} — solta explosões, anéis e raios em volta do jogador a cada 2 ticks e mostra
 *   a estatística no fim;</li>
 *   <li>{@code reactions [quantas]} — dispara várias explosões de reação no MESMO tick para ver o orçamento
 *   (completas → enxutas → nada).</li>
 * </ul>
 */
final class PerfCommands {
    static void register() {
        MahouCommands.extend((root, buildCtx) -> root.then(MahouCommands.op("fx")
                .then(Commands.literal("stats").executes(c -> stats(c.getSource())))
                .then(Commands.literal("stress")
                        .executes(c -> stress(c.getSource(), 40, 5))
                        .then(Commands.argument("effects", IntegerArgumentType.integer(1, 400))
                                .executes(c -> stress(c.getSource(), IntegerArgumentType.getInteger(c, "effects"), 5))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 30))
                                        .executes(c -> stress(c.getSource(), IntegerArgumentType.getInteger(c, "effects"),
                                                IntegerArgumentType.getInteger(c, "seconds"))))))
                .then(Commands.literal("reactions")
                        .executes(c -> reactions(c.getSource(), 20))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 60))
                                .executes(c -> reactions(c.getSource(), IntegerArgumentType.getInteger(c, "count")))))));
    }

    private static int stats(CommandSourceStack src) {
        long[] s = FxBatch.stats();
        FxBatch.resetStats();
        String ratio = s[1] == 0 ? "-" : String.format(java.util.Locale.ROOT, "%.1f", s[2] / (double) s[1]);
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.fx.stats", s[0], s[1], s[2], ratio, s[3]), false);
        return (int) Math.min(Integer.MAX_VALUE, s[1]);
    }

    private static int stress(CommandSourceStack src, int effects, int seconds) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ServerLevel level = sp.serverLevel();
        FxBatch.resetStats();
        Element[] els = Element.values();
        int ticks = seconds * 20;
        for (int t = 0; t < ticks; t += 2) {
            BossKit.schedule(level, t, () -> {
                if (!sp.isAlive() || sp.level() != level) return;
                RandomSource r = level.random;
                for (int i = 0; i < effects; i++) {
                    Vec3 p = sp.position().add((r.nextDouble() - 0.5) * 24, 0.2 + r.nextDouble() * 3, (r.nextDouble() - 0.5) * 24);
                    Element e = els[r.nextInt(els.length)];
                    switch (i % 3) {
                        case 0 -> ElementFx.burst(level, p, e, 12, 0.4);
                        case 1 -> ElementFx.ring(level, p, e, 1.5 + r.nextDouble() * 2, 24);
                        default -> ElementFx.line(level, p, p.add((r.nextDouble() - 0.5) * 10, r.nextDouble() * 3, (r.nextDouble() - 0.5) * 10), e);
                    }
                }
            });
        }
        // 2 ticks depois do último disparo o lote já saiu: mostra o resultado
        BossKit.schedule(level, ticks + 2, () -> stats(src));
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.fx.stress", effects, seconds), false);
        return effects;
    }

    private static int reactions(CommandSourceStack src, int count) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ServerLevel level = sp.serverLevel();
        Reaction[] all = Reaction.values();
        int full = 0, lite = 0;
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            double d = 4 + (i % 3) * 2.5;
            Vec3 c = sp.position().add(Math.cos(a) * d, 1.0, Math.sin(a) * d);
            // mesmo orçamento do ElementReactions.trigger
            if (i < ElementReactions.FX_FULL_PER_TICK) {
                ElementFx.reactionAt(level, c, 1.0, 1.1F, all[i % all.length], false);
                full++;
            } else if (i < ElementReactions.FX_MAX_PER_TICK) {
                ElementFx.reactionAt(level, c, 1.0, 1.1F, all[i % all.length], true);
                lite++;
            }
        }
        int f = full, l = lite;
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.fx.reactions", count, f, l, count - f - l), false);
        return count;
    }

    private PerfCommands() {}
}
