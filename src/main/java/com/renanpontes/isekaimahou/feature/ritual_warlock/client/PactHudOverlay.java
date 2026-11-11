package com.renanpontes.isekaimahou.feature.ritual_warlock.client;

import java.util.List;

import com.renanpontes.isekaimahou.feature.ritual_warlock.network.WarlockPayloads;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactRitual;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PatronKind;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Painel do pacto pendente (canto superior direito, abaixo dos ícones de efeito):
 * Patrono, tributo, progresso e o tempo que resta (contagem regressiva no cliente).
 */
public final class PactHudOverlay {
    private static final int WIDTH = 150;
    private static WarlockPayloads.PactHud data = WarlockPayloads.PactHud.empty();
    private static long receivedAt;
    /** Altura desenhada no último quadro (0 = painel escondido); o rastreador de objetivos desce para não sobrepor. */
    private static int lastHeight;

    /** Espaço que o painel ocupa no canto superior direito (para ObjectiveTracker.reserveTop). */
    public static int reservedHeight() {
        return data.active() && lastHeight > 0 ? lastHeight + 4 : 0;
    }

    public static void update(WarlockPayloads.PactHud p) {
        data = p;
        Minecraft mc = Minecraft.getInstance();
        receivedAt = mc.level != null ? mc.level.getGameTime() : 0;
    }

    public static void clear() {
        data = WarlockPayloads.PactHud.empty();
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!data.active() || mc.options.hideGui || mc.level == null || mc.getDebugOverlay().showDebugScreen()) {
            lastHeight = 0;
            return;
        }
        Font font = mc.font;
        PatronKind kind = PatronKind.byId(data.kind());
        long left = Math.max(0, data.remaining() - (mc.level.getGameTime() - receivedAt));

        Component title = Component.translatable("gui.isekai_mahou.warlock.hud_title", kind.displayName());
        List<FormattedCharSequence> demand = font.split(data.demand(), WIDTH - 12);
        if (demand.size() > 2) demand = demand.subList(0, 2);
        Component status;
        int statusColor;
        if (data.fulfilled()) {
            status = Component.translatable("gui.isekai_mahou.warlock.hud_done");
            statusColor = 0xFFE07A;
        } else if (data.tracked() && left <= 0) {
            status = Component.translatable("gui.isekai_mahou.warlock.hud_failed");
            statusColor = 0xFF5555;
        } else {
            status = Component.translatable("gui.isekai_mahou.warlock.hud_status", data.progress(), data.amount(), PactRitual.formatTicks(left));
            statusColor = left < 1200 ? 0xFF8080 : 0xCFC8E0;
        }

        int h = 8 + 10 + demand.size() * 10 + 12;
        lastHeight = h;
        int x = g.guiWidth() - WIDTH - 4;
        int y = 56;
        g.fill(x, y, x + WIDTH, y + h, 0xA0100508);
        g.fill(x, y, x + 2, y + h, 0xFF000000 | kind.color);
        g.fill(x + 2, y, x + WIDTH, y + 1, 0x60000000 | (kind.color & 0xFFFFFF));
        int ty = y + 4;
        g.drawString(font, title, x + 6, ty, 0xFFFFFF, true);
        ty += 11;
        for (FormattedCharSequence line : demand) {
            g.drawString(font, line, x + 6, ty, 0xE0E0E0, false);
            ty += 10;
        }
        g.drawString(font, status, x + 6, ty + 1, statusColor, false);
    }

    private PactHudOverlay() {}
}
