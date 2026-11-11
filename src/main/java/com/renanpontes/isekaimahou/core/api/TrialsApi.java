package com.renanpontes.isekaimahou.core.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Câmaras de Provação (ranks elementais I–VII). O NÚCLEO só guarda o progresso e encaminha pedidos;
 * o módulo {@code trials} instala o {@link Handler} que cria a instância, as ondas e o chefe.
 *
 * <ul>
 *   <li>Flag de conclusão: {@code trial.<elemento>.<rank>} ({@link #flag}).</li>
 *   <li>Contadores somados por {@link #markCleared}: {@code trial.<elemento>.<rank>}, {@code trial.clear},
 *       {@code trial.clear.<elemento>} (cada um dispara {@link MagicProgressEvent.Counter}).</li>
 *   <li>Pedido do cliente: pacote {@code CorePayloads.RequestTrial(elemento, rank)} → {@link #request}.</li>
 * </ul>
 */
public final class TrialsApi {
    /** Implementado pelo módulo trials (registre com {@link #set} no init do módulo). */
    public interface Handler {
        /** Pode começar? Se não, adicione os motivos (texto para o jogador) em {@code reasons}. */
        boolean canStart(ServerPlayer player, Element element, int rank, List<Component> reasons);

        /** Começa a Provação (leva o jogador para a recepção). Só é chamado depois de {@link #canStart} == true. */
        void start(ServerPlayer player, Element element, int rank);
    }

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    /** Intervalo mínimo entre pedidos do mesmo jogador (evita spam de pacotes/cliques). */
    private static final long REQUEST_COOLDOWN_MS = 750;

    private static volatile Handler handler;
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    /** Instala (ou remove, com null) o handler das Provações. */
    public static void set(@Nullable Handler h) {
        handler = h;
    }

    /** Handler atual, ou null se nenhum módulo implementa as Provações. */
    @Nullable
    public static Handler handler() {
        return handler;
    }

    // ---------------------------------------------------------------- Progresso (lado comum: serve no cliente também)
    /** Flag gravada ao concluir: {@code trial.<elemento>.<rank>}. */
    public static String flag(Element element, int rank) {
        return "trial." + element.id + "." + rank;
    }

    public static boolean isCleared(MagicData data, Element element, int rank) {
        return data.hasFlag(flag(element, rank));
    }

    /** Maior rank N (0..7) com a flag {@code trial.<elemento>.<N>}; 0 = nenhuma Provação concluída. */
    public static int clearedRank(MagicData data, Element element) {
        for (int r = Progression.MAX_TRIAL_RANK; r >= 1; r--) {
            if (data.hasFlag(flag(element, r))) return r;
        }
        return 0;
    }

    /**
     * Requisitos padrão do §3 para ENTRAR no rank: elemento despertado (não Arcano), rank anterior concluído,
     * nível do elemento ({@link Progression#trialElementLevel}) e Círculo ({@link Progression#trialCircle}).
     * Adiciona em {@code missing} uma linha traduzida por requisito que falta. Funciona no cliente
     * (com {@code ClientMagic.data()}) para telas mostrarem o que falta.
     */
    public static boolean meetsRequirements(MagicData data, Element element, int rank, List<Component> missing) {
        int before = missing.size();
        if (rank < 1 || rank > Progression.MAX_TRIAL_RANK) {
            missing.add(Component.translatable("core.isekai_mahou.trial.req.rank", rank));
            return false;
        }
        if (element == Element.ARCANE) {
            missing.add(Component.translatable("core.isekai_mahou.trial.req.arcane"));
            return false;
        }
        if (!data.isAwakened(element)) {
            missing.add(Component.translatable("core.isekai_mahou.trial.req.awakened", element.displayName()));
        }
        if (rank > 1 && !isCleared(data, element, rank - 1)) {
            missing.add(Component.translatable("core.isekai_mahou.trial.req.previous", title(element, rank - 1)));
        }
        int needLevel = Progression.trialElementLevel(rank);
        int level = Math.max(0, data.elementLevel(element));
        if (level < needLevel) {
            missing.add(Component.translatable("core.isekai_mahou.trial.req.element_level", element.displayName(), level, needLevel));
        }
        int needCircle = Progression.trialCircle(rank);
        if (data.circle() < needCircle) {
            missing.add(Component.translatable("core.isekai_mahou.trial.req.circle", needCircle, data.circle()));
        }
        return missing.size() == before;
    }

    /** Próximo rank a fazer (1..7), ou 0 se todos foram concluídos. */
    public static int nextRank(MagicData data, Element element) {
        int cleared = clearedRank(data, element);
        return cleared >= Progression.MAX_TRIAL_RANK ? 0 : cleared + 1;
    }

    /** "I".."VII" (até X). */
    public static String roman(int rank) {
        return rank >= 1 && rank <= ROMAN.length ? ROMAN[rank - 1] : String.valueOf(rank);
    }

    /** "Provação de Fogo III" na cor do elemento. */
    public static MutableComponent title(Element element, int rank) {
        return Component.translatable("core.isekai_mahou.trial.title", element.displayName(), roman(rank)).withColor(element.color);
    }

    // ---------------------------------------------------------------- Servidor
    /**
     * Registra a conclusão: grava a flag e soma os contadores {@code trial.<el>.<rank>}, {@code trial.clear} e
     * {@code trial.clear.<el>} (cada soma dispara {@link MagicProgressEvent.Counter}; a flag já está gravada
     * quando os ouvintes recebem o evento — o módulo paths libera o nó do caminho aqui).
     */
    public static void markCleared(ServerPlayer player, Element element, int rank) {
        MagicData d = MagicApi.get(player);
        d.setFlag(flag(element, rank), true);
        MagicApi.addCounter(player, "trial." + element.id + "." + rank, 1);
        MagicApi.addCounter(player, "trial.clear", 1);
        MagicApi.addCounter(player, "trial.clear." + element.id, 1);
    }

    /**
     * Pedido para começar uma Provação (pacote {@code RequestTrial}, botões, comandos): valida, pergunta ao
     * handler e começa — ou explica no chat por que não pode.
     */
    public static void request(ServerPlayer player, Element element, int rank) {
        if (element == null || rank < 1 || rank > Progression.MAX_TRIAL_RANK) return;
        long now = System.currentTimeMillis();
        synchronized (LAST_REQUEST) {
            // descarta registros vencidos (jogadores que já saíram) para o mapa não crescer para sempre
            if (LAST_REQUEST.size() > 32) LAST_REQUEST.values().removeIf(t -> now - t >= REQUEST_COOLDOWN_MS);
            Long last = LAST_REQUEST.get(player.getUUID());
            if (last != null && now - last < REQUEST_COOLDOWN_MS) return;
            LAST_REQUEST.put(player.getUUID(), now);
        }
        Handler h = handler;
        if (h == null) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.trial_unavailable").withStyle(ChatFormatting.RED));
            return;
        }
        List<Component> reasons = new ArrayList<>();
        if (h.canStart(player, element, rank, reasons)) {
            h.start(player, element, rank);
            return;
        }
        if (reasons.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.trial_cannot_start").withStyle(ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.trial_blocked", title(element, rank)).withStyle(ChatFormatting.GOLD));
        for (Component r : reasons) {
            player.sendSystemMessage(Component.literal("  ✘ ").withStyle(ChatFormatting.RED).append(r.copy().withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Versão por id de elemento (como chega no pacote). Ids inválidos são ignorados. */
    public static void request(ServerPlayer player, String elementId, int rank) {
        request(player, Element.byId(elementId), rank);
    }

    private TrialsApi() {}
}
