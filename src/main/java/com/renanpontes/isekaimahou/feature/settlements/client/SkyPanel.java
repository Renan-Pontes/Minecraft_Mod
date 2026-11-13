package com.renanpontes.isekaimahou.feature.settlements.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;
import com.renanpontes.isekaimahou.feature.settlements.SkyCity;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Onda 7 (sky_ui) — o que o painel da cidade mostra de uma ILHA CELESTE, sem poluir:
 * <ul>
 *   <li><b>Geral</b> ({@link #overview}): duas linhas no pé da coluna da esquerda — "Ilha Celeste · altitude N" e,
 *       embaixo, o traço da cidade do céu ou, enquanto a terra se forma, "A terra está se formando: N%" com uma barra
 *       fina. O resto fica na dica ao passar o mouse.</li>
 *   <li><b>Planejar</b> ({@link #plotTint}, {@link #plotTip}): terreno cuja terra ainda não se formou ganha um véu
 *       azulado e o motivo "Aguardando a terra se formar" na dica. A conta é a mesma do servidor
 *       ({@link SkyCity#inside}, pura) com os números da geração que vêm no estado da cidade.</li>
 * </ul>
 * Cidade no chão: o estado não traz {@code Sky} e nada aqui desenha.
 */
final class SkyPanel {
    private static final int SKY = 0xFF000000 | SkyPreview.SKY;

    /** Linhas da Ilha Celeste no Geral, ancoradas no pé da coluna (nunca por cima do que já está lá). */
    static void overview(SettlementPanel panel, GuiGraphics g, CompoundTag t, int mx, int my, int lx, int minY, int col, int bottom) {
        if (!t.getBoolean("Sky")) return;
        Font f = panel.font();
        int y0 = Math.max(minY, bottom - 24);
        int pct = t.getInt("SkyPct");
        // onda 9 (isles): no arquipélago "se formando" = alguma ilha ainda não terminou
        boolean isles = t.getBoolean("Isles");
        boolean forming = isles ? pct < 100 : t.getInt("SkyGoal") > 0;
        g.drawString(f, SettlementsLang.tr("sky.ui.line", t.getInt("SkyY")), lx, y0, SKY, false);
        if (forming) {
            g.drawString(f, SettlementsLang.tr("sky.ui.forming", pct), lx, y0 + 10, SettlementPanel.GOLD, false);
            int by = y0 + 20;
            g.fill(lx, by, lx + col, by + 2, 0x60000000);
            g.fill(lx, by, lx + Math.round(col * Math.max(0, Math.min(100, pct)) / 100F), by + 2, SKY);
        } else {
            g.drawString(f, SettlementsLang.tr("sky.ui.trait"), lx, y0 + 10, SettlementPanel.MUTED, false);
        }
        if (mx >= lx && mx < lx + col && my >= y0 && my < y0 + 22) {
            List<Component> tip = new ArrayList<>();
            tip.add(SettlementsLang.tr("sky.terrain.skyland").withColor(SKY));
            tip.add(SettlementsLang.tr(isles ? "isle.ui.tip.land" : "sky.ui.tip.land").withColor(SettlementPanel.MUTED));
            tip.add(SettlementsLang.tr("sky.ui.tip.trait").withColor(SettlementPanel.MUTED));
            tip.add(SettlementsLang.tr(isles ? "isle.ui.tip.grow" : "sky.ui.tip.grow").withColor(SettlementPanel.MUTED));
            if (forming) tip.add(SettlementsLang.tr("sky.ui.tip.forming").withColor(SettlementPanel.GOLD));
            panel.tooltip = tip;
        }
    }

    // ---- Planejar: "a terra deste terreno já existe?"
    private static int cacheVersion = -1;
    private static final Map<Integer, Boolean> NO_LAND = new HashMap<>();

    /** O terreno (grade ou livre) ainda espera a terra se formar? Só há o que conferir enquanto a ilha cresce. */
    static boolean noLand(int cell) {
        CompoundTag t = ClientSettlements.state();
        if (!t.getBoolean("Sky") || t.getInt("SkyGoal") <= 0 || cell < 0 || !t.contains("HeartX")) return false;
        if (cacheVersion != ClientSettlements.version()) {
            cacheVersion = ClientSettlements.version();
            NO_LAND.clear();
        }
        return NO_LAND.computeIfAbsent(cell, c -> !landReady(t, c));
    }

    /** Repete {@code SkyCity.landReady(s, cell)} com os números do estado: cantos, lados e meio do lote + rua. */
    private static boolean landReady(CompoundTag t, int cell) {
        BlockPos heart = new BlockPos(t.getInt("HeartX"), t.getInt("SkyY"), t.getInt("HeartZ"));
        int land = t.getInt("SkyLand"), goal = t.getInt("SkyGoal"), ring = t.getInt("SkyRing");
        int dx = CityLayout.offsetX(cell), dz = CityLayout.offsetZ(cell), h = CityTerrain.LEVEL_HALF;
        for (int ox = -h; ox <= h; ox += h) {
            for (int oz = -h; oz <= h; oz += h) {
                int x = dx + ox, z = dz + oz;
                if (land > 0 && SkyCity.inside(heart, land, x, z)) continue;
                if (goal > 0 && Math.max(Math.abs(x), Math.abs(z)) < ring && SkyCity.inside(heart, goal, x, z)) continue;
                return false;
            }
        }
        return true;
    }

    /** Véu azulado por cima do terreno que espera a terra (planta da aba Planejar). */
    static void plotTint(GuiGraphics g, int cell, int bx, int by, int size) {
        if (noLand(cell)) g.fill(bx + 1, by + 1, bx + size - 1, by + size - 1, 0x508FD3FF);
    }

    /** Acrescenta o motivo "Aguardando a terra se formar" à dica do terreno. */
    static void plotTip(@Nullable List<Component> tip, int cell) {
        if (tip != null && noLand(cell)) tip.add(SettlementsLang.tr("sky.wait.land").withColor(SKY));
    }

    private SkyPanel() {}
}
