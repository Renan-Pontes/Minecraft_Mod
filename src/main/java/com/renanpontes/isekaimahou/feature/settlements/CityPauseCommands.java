package com.renanpontes.isekaimahou.feature.settlements;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de teste da onda 7 (city_pause), pendurados no {@code /mahou city} pelo {@link CityDebugCommands}:
 * <pre>
 * /mahou city pause            pausa a primeira obra que está andando (e lista a fila numerada)
 * /mahou city pause &lt;n&gt;        pausa a obra nº n da fila          /mahou city pause all    pausa todas
 * /mahou city resume           retoma a primeira obra pausada
 * /mahou city resume &lt;n&gt;       retoma a obra nº n                /mahou city resume all   retoma todas
 * /mahou city cancel           só lista a fila: o que dá para cancelar, quanto volta, ou o motivo de só pausar
 * /mahou city cancel &lt;n&gt;       desiste da obra nº n em qualquer etapa (sem a confirmação da tela; mesmas regras)
 * </pre>
 * Valem para a cidade fundada onde você está (ou a mais perto). "n" começa em 1, na ordem da aba Obras.
 */
public final class CityPauseCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> pause() {
        return toggle("pause", true);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> resume() {
        return toggle("resume", false);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggle(String name, boolean pause) {
        return Commands.literal(name).executes(c -> run(c, pause, 0))
                .then(Commands.literal("all").executes(c -> run(c, pause, -1)))
                .then(Commands.argument("n", IntegerArgumentType.integer(1, SettlementService.MAX_QUEUE))
                        .executes(c -> run(c, pause, IntegerArgumentType.getInteger(c, "n"))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> cancel() {
        return Commands.literal("cancel").executes(c -> {
            Settlement s = city(c);
            return s == null ? 0 : list(c, s);
        }).then(Commands.argument("n", IntegerArgumentType.integer(1, SettlementService.MAX_QUEUE)).executes(c -> {
            Settlement s = city(c);
            if (s == null) return 0;
            MinecraftServer server = c.getSource().getServer();
            int index = IntegerArgumentType.getInteger(c, "n") - 1;
            if (index >= s.projects.size()) {
                c.getSource().sendFailure(SettlementsLang.tr("pause.no.index"));
                return 0;
            }
            Project p = s.projects.get(index);
            Component name = CityPlanning.projectName(s, p);
            int before = s.treasury;
            Component problem = CityPause.cancel(server, s, index);
            if (problem != null) {
                c.getSource().sendFailure(problem);
                return 0;
            }
            int back = s.treasury - before;
            c.getSource().sendSuccess(() -> SettlementsLang.tr(p.undo ? "cmd.pause.cancel.undo" : "cmd.pause.cancel", name, back), true);
            SettlementService.refreshViewers(server, s);
            return list(c, s);
        }));
    }

    /** {@code n}: 0 = a primeira que couber, −1 = todas, senão o número da obra na fila (a partir de 1). */
    private static int run(CommandContext<CommandSourceStack> c, boolean pause, int n) throws CommandSyntaxException {
        Settlement s = city(c);
        if (s == null) return 0;
        MinecraftServer server = c.getSource().getServer();
        int changed;
        if (n < 0) {
            changed = pause ? CityPause.pauseAll(server, s) : CityPause.resumeAll(server, s);
        } else {
            int index = n - 1;
            if (n == 0) {
                for (int i = 0; i < s.projects.size() && index < 0; i++) {
                    Project p = s.projects.get(i);
                    if (pause ? !p.paused && !p.undo : p.paused) index = i;
                }
            }
            changed = index >= 0 && (pause ? CityPause.pause(server, s, index) : CityPause.resume(server, s, index)) ? 1 : 0;
        }
        int total = changed;
        c.getSource().sendSuccess(() -> SettlementsLang.tr(pause ? "cmd.pause.paused" : "cmd.pause.resumed", total), true);
        SettlementService.refreshViewers(server, s);
        list(c, s);
        return changed;
    }

    private static int list(CommandContext<CommandSourceStack> c, Settlement s) {
        for (Component line : CityPause.debug(c.getSource().getServer(), s)) c.getSource().sendSuccess(() -> line, false);
        return 1;
    }

    /** A cidade FUNDADA onde o jogador está (ou a mais perto na dimensão). */
    @Nullable
    private static Settlement city(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Places.Place at = SettlementService.cityAt(p.serverLevel(), p.blockPosition());
        Settlement s = at == null ? null : SettlementService.city(p.server, at.id());
        if (s == null || !s.own()) {
            s = null;
            double best = Double.MAX_VALUE;
            for (Settlement o : SettlementData.get(p.server).all()) {
                if (!o.own() || o.dim != p.level().dimension()) continue;
                double d = o.heart.distSqr(p.blockPosition());
                if (d < best) {
                    best = d;
                    s = o;
                }
            }
        }
        if (s == null) c.getSource().sendFailure(SettlementsLang.tr("cmd.founded_only"));
        return s;
    }

    private CityPauseCommands() {}
}
