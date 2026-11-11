package com.renanpontes.isekaimahou.core.ux;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Entrada no jogo da v1.0 (A3): <b>classe → raça → "Parabéns!" → título do capítulo → Espírito Guia</b>, sem atributos.
 * As etapas de criação continuam vindo de {@code Onboarding.addCreationStep} (módulos); aqui fica só a ORDEM fixa
 * (a etapa {@value #STEP_RACE} do módulo {@code character} sempre logo depois da classe, venha o registro na ordem que
 * vier) e as linhas extras do letreiro "Parabéns!" (ex.: "Raça: Elfo").
 */
public final class EntryFlow {
    /** Etapa de criação da raça (registrada pelo módulo {@code character}). */
    public static final String STEP_RACE = "race";
    /** Etapas com lugar fixo, nesta ordem, antes de todas as outras (as outras seguem a ordem de registro). */
    public static final List<String> STEP_ORDER = List.of(STEP_RACE);

    private record Line(int order, Function<ServerPlayer, Component> supplier) {}

    private static final Map<String, Line> LINES = new LinkedHashMap<>();

    /**
     * Linha extra no letreiro "Parabéns!" (abaixo de categoria/recurso). Ex. do módulo character:
     * {@code EntryFlow.addCongratsLine("race", 0, sp -> Component.translatable("x.race_line", raceName(sp)))}.
     * Devolver null/vazio = sem linha para esse jogador. Máximo de 3 linhas mostradas (menor {@code order} primeiro).
     * Registro (chame no init do módulo, lado comum); o mesmo id substitui.
     */
    public static synchronized void addCongratsLine(String id, int order, Function<ServerPlayer, Component> line) {
        if (id == null || line == null) return;
        LINES.put(id, new Line(order, line));
    }

    /** Linhas extras do letreiro para este jogador (no máximo 3). */
    public static List<Component> congratsLines(ServerPlayer sp) {
        List<Line> lines;
        synchronized (EntryFlow.class) {
            lines = new ArrayList<>(LINES.values());
        }
        lines.sort(Comparator.comparingInt(Line::order));
        List<Component> out = new ArrayList<>();
        for (Line l : lines) {
            if (out.size() >= 3) break;
            @Nullable Component c;
            try {
                c = l.supplier().apply(sp);
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.error("Linha do letreiro de parabéns falhou", e);
                continue;
            }
            if (c != null && !c.getString().isBlank()) out.add(c);
        }
        return out;
    }

    /** Ordena as etapas: as de {@link #STEP_ORDER} primeiro (nessa ordem), depois as outras na ordem de registro. */
    public static List<String> ordered(List<String> registered) {
        List<String> out = new ArrayList<>(registered.size());
        for (String fixed : STEP_ORDER) if (registered.contains(fixed)) out.add(fixed);
        for (String s : registered) if (!out.contains(s)) out.add(s);
        return out;
    }

    private EntryFlow() {}
}
