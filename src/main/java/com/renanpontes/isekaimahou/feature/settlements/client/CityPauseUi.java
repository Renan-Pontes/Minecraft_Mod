package com.renanpontes.isekaimahou.feature.settlements.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * Onda 7 (city_pause — "quero poder parar as construções no meio...") — as peças da aba Obras do {@link SettlementPanel}:
 * o botão pequeno de pausar/retomar de cada obra, o "Desistir" com confirmação (dois cliques; apagado, com o motivo na
 * dica, quando a obra só pode ser pausada) e o "Pausar tudo / Retomar tudo" no cabeçalho. Sem HUD novo: a obra pausada
 * só ganha um "⏸ pausada" azulado no lugar do "em obra".
 */
final class CityPauseUi {
    /** Tom discreto da obra pausada (azul acinzentado) e da obra sendo desmontada. */
    static final int PAUSED = 0xFF8FA8C8, UNDO = 0xFFD8A070;
    /** Largura do botão de pausar de uma linha e do "Desistir". */
    static final int PAUSE_W = 16, CANCEL_W = 46;

    /** Confirmação do "Desistir": chave da obra armada por 4 s (o segundo clique manda). */
    private static String armed = "";
    private static long armedUntil;

    // =====================================================================================
    //  Cabeçalho: Pausar tudo / Retomar tudo
    // =====================================================================================
    /**
     * Desenha o botão à direita do cabeçalho e devolve a largura que ele ocupou (o "ritmo" fica à esquerda dele).
     * "Pausar tudo" enquanto houver alguma obra andando; com todas pausadas vira "Retomar tudo".
     */
    static int header(SettlementPanel pn, GuiGraphics g, int mx, int my, ListTag list, int cy) {
        int pausable = 0, paused = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag p = list.getCompound(i);
            if (p.getBoolean("Paused")) paused++;
            else if (p.getBoolean("CanPause")) pausable++;
        }
        if (pausable == 0 && paused == 0) return 0;
        boolean resume = pausable == 0;
        Component label = SettlementsLang.tr(resume ? "ui.pause.all.resume" : "ui.pause.all");
        int bw = pn.font().width(label) + 10;
        int bx = pn.x + pn.w - 8 - bw;
        List<Component> tip = List.of(SettlementsLang.tr(resume ? "ui.pause.all.resume.tip" : "ui.pause.all.tip").withColor(SettlementPanel.TEXT),
                SettlementsLang.tr("ui.pause.tip2").withColor(SettlementPanel.MUTED));
        pn.button(g, mx, my, bx, cy - 1, bw, 12, label, true, false, () -> pn.act(resume ? "work_resume" : "work_pause", "all"), tip);
        return bw + 6;
    }

    // =====================================================================================
    //  Linha de uma obra
    // =====================================================================================
    /** Texto de estado ao lado do nome: "⏸ pausada", "desmontando" ou null (o painel usa o "em obra / na fila" dele). */
    static Component state(CompoundTag p) {
        if (p.getBoolean("Undo")) return SettlementsLang.tr("ui.pause.state.undo");
        if (p.getBoolean("Paused")) return SettlementsLang.tr("ui.pause.state");
        return null;
    }

    static int stateColor(CompoundTag p) {
        return p.getBoolean("Undo") ? UNDO : PAUSED;
    }

    /** A linha mostra o botão de pausar/retomar? (obra sendo desmontada não pausa) */
    static boolean showPause(CompoundTag p) {
        return p.getBoolean("CanPause") || p.getBoolean("Paused");
    }

    /**
     * A linha mostra o "Desistir"? Sim quando dá para cancelar; e também, apagado e com o motivo, quando só dá para
     * pausar — menos no conserto, que mantém o botão de terminar na hora no lugar.
     */
    static boolean showCancel(CompoundTag p) {
        return p.getBoolean("Cancel") || p.contains("NoCancel") && !p.getBoolean("Rush") && !p.getBoolean("Undo");
    }

    static void pauseButton(SettlementPanel pn, GuiGraphics g, int mx, int my, int bx, int by, CompoundTag p, int idx) {
        boolean paused = p.getBoolean("Paused");
        List<Component> tip = List.of(SettlementsLang.tr(paused ? "ui.pause.resume.tip" : "ui.pause.tip").withColor(SettlementPanel.TEXT),
                SettlementsLang.tr(paused ? "ui.pause.resume.tip2" : "ui.pause.tip2").withColor(SettlementPanel.MUTED));
        pn.button(g, mx, my, bx, by, PAUSE_W, 14, Component.literal(paused ? "▶" : "⏸"), true, paused,
                () -> pn.act(paused ? "work_resume" : "work_pause", Integer.toString(idx)), tip);
    }

    static void cancelButton(SettlementPanel pn, GuiGraphics g, int mx, int my, int bx, int by, CompoundTag p, int idx) {
        Font f = pn.font();
        if (!p.getBoolean("Cancel")) {
            // só dá para pausar: botão apagado, o motivo na dica
            List<Component> tip = new ArrayList<>();
            tip.add(SettlementsLang.tr("ui.pause.cancel.no").withColor(SettlementPanel.RED));
            for (var line : f.split(ClientSettlements.text(p, "NoCancel"), 210)) tip.add(Component.literal(SettlementPanel.toString(line)).withColor(SettlementPanel.MUTED));
            pn.button(g, mx, my, bx, by, CANCEL_W, 14, SettlementsLang.tr("ui.pause.cancel"), false, false, () -> { }, tip);
            return;
        }
        // a chave leva o nome da obra: se a fila andar entre os dois cliques, a confirmação não vale para outra obra
        String key = idx + "#" + p.getString("Name").hashCode();
        boolean on = armed.equals(key) && Util.getMillis() < armedUntil;
        List<Component> tip = new ArrayList<>();
        tip.add(SettlementsLang.tr(on ? "ui.pause.cancel.confirm.tip" : "ui.pause.cancel.tip").withColor(on ? SettlementPanel.RED : SettlementPanel.TEXT));
        if (p.contains("CancelTip")) {
            for (var line : f.split(ClientSettlements.text(p, "CancelTip"), 210)) tip.add(Component.literal(SettlementPanel.toString(line)).withColor(SettlementPanel.MUTED));
        }
        if (p.getInt("Refund") > 0) tip.add(SettlementsLang.tr("ui.pause.cancel.refund", p.getInt("Refund")).withColor(SettlementPanel.GOLD));
        pn.button(g, mx, my, bx, by, CANCEL_W, 14, SettlementsLang.tr(on ? "ui.pause.cancel.confirm" : "ui.pause.cancel"), true, on, () -> {
            if (armed.equals(key) && Util.getMillis() < armedUntil) {
                pn.act("cancel", Integer.toString(idx));
                armed = "";
            } else {
                armed = key;
                armedUntil = Util.getMillis() + 4000;
            }
        }, tip);
    }

    private CityPauseUi() {}
}
