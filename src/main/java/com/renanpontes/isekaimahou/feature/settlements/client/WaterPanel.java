package com.renanpontes.isekaimahou.feature.settlements.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Onda 8 (water_city) — o que o painel da cidade mostra de uma CIDADE DAS ÁGUAS, sem poluir: duas linhas no pé da
 * coluna da esquerda do Geral (o mesmo lugar das linhas da Ilha Celeste — uma cidade é de um tipo só): o nome do tipo
 * e o traço "Porto"; o resto fica na dica ao passar o mouse. Outras cidades: o estado não traz {@code Water} e nada
 * aqui desenha.
 */
final class WaterPanel {
    private static final int AQUA = 0xFF000000 | CityPlacer.AQUA;

    static void overview(SettlementPanel panel, GuiGraphics g, CompoundTag t, int mx, int my, int lx, int minY, int col, int bottom) {
        if (!t.getBoolean("Water")) return;
        Font f = panel.font();
        int y0 = Math.max(minY, bottom - 24);
        g.drawString(f, SettlementsLang.tr("water.ui.line"), lx, y0, AQUA, false);
        g.drawString(f, SettlementsLang.tr("water.ui.trait", t.getInt("WaterFish")), lx, y0 + 10, SettlementPanel.MUTED, false);
        if (mx >= lx && mx < lx + col && my >= y0 && my < y0 + 22) {
            List<Component> tip = new ArrayList<>();
            tip.add(SettlementsLang.tr("water.ui.line").withColor(AQUA));
            // onda 9 (isles): o arquipélago tem o texto dele (plataformas separadas, pontes, porto no meio)
            boolean isles = t.getBoolean("Isles");
            tip.add(SettlementsLang.tr(isles ? "isle.water.tip.deck" : "water.ui.tip.deck").withColor(SettlementPanel.MUTED));
            tip.add(SettlementsLang.tr(isles ? "isle.water.tip.canal" : "water.ui.tip.canal").withColor(SettlementPanel.MUTED));
            tip.add(SettlementsLang.tr("water.ui.tip.trait", t.getInt("WaterFish")).withColor(SettlementPanel.MUTED));
            panel.tooltip = tip;
        }
    }

    private WaterPanel() {}
}
