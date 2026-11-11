package com.renanpontes.isekaimahou.feature.afterlife.client;

import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeNet;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Tela da alma durante o desafio: vinheta escura que pulsa com o coração, barra de tempo no topo, seta e distância até
 * o corpo, a faixa do eletrocardiograma (minijogo) quando está no corpo, as batidas que faltam, a ajuda de um aliado e
 * a dica de desistir. Sem chat: tudo aqui.
 */
final class AfterlifeHud {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int CYAN = 0x7FF5F0;
    private static final int RED = 0xFF4A5A;

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientAfterlife.active()) return;
        AfterlifeNet.State s = ClientAfterlife.state;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        double phase = ClientAfterlife.inRange ? ClientAfterlife.phase() : (Util.getMillis() % 1600L) / 1600.0;
        float pulse = (float) Math.max(0, 1 - Math.abs(phase - 0.5) * 6);

        vignette(g, w, h, 0.55F + 0.25F * pulse);

        // tempo
        float secs = s.ticksLeft() / 20F;
        float frac = Mth.clamp(s.ticksLeft() / (float) Math.max(1, s.totalTicks()), 0F, 1F);
        int barW = 182, bx = (w - barW) / 2, by = 22;
        Component head = Component.translatable("hud.isekai_mahou.afterlife.title", String.format("%.0f", Math.ceil(secs)));
        g.drawCenteredString(font, head, w / 2, 10, frac < 0.3F ? RED : CYAN);
        g.fill(bx - 1, by - 1, bx + barW + 1, by + 4, 0xAA000000);
        int col = frac < 0.3F ? 0xFFFF4A5A : 0xFF7FF5F0;
        g.fill(bx, by, bx + (int) (barW * frac), by + 3, col);

        // direção até o corpo
        Vec3 body = new Vec3(s.x(), s.y(), s.z());
        double dist = mc.player.position().distanceTo(body);
        if (!ClientAfterlife.inRange) {
            double dx = body.x - mc.player.getX(), dz = body.z - mc.player.getZ();
            float target = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
            float rel = Mth.wrapDegrees(target - mc.player.getYRot());
            String arrow = ARROWS[Math.floorMod(Math.round(rel / 45F), 8)];
            Component hint = Component.translatable("hud.isekai_mahou.afterlife.go_back", arrow, (int) Math.ceil(dist));
            g.drawCenteredString(font, hint, w / 2, by + 9, 0xFFFFFF);
        }

        int cy = h - 92;
        if (ClientAfterlife.inRange) ecg(g, font, w, cy, phase);

        // batidas que faltam
        int n = s.required();
        int spacing = 12;
        int hx = w / 2 - (n * spacing) / 2 + 2;
        int hy = cy + 40;
        for (int i = 0; i < n; i++) {
            boolean on = i < s.beats();
            g.drawString(font, "❤", hx + i * spacing, hy, on ? 0xFFFF5566 : 0xFF553340, true);
        }

        // ajuda do grupo
        if (s.helpPercent() > 0 && !s.helper().isEmpty()) {
            Component help = Component.translatable("hud.isekai_mahou.afterlife.helping", s.helper(), s.helpPercent());
            g.drawCenteredString(font, help, w / 2, hy + 12, 0x9FFFB0);
        }

        // desistir
        Component key = ClientAfterlife.GIVE_UP.getTranslatedKeyMessage();
        int held = ClientAfterlife.giveUpHeld;
        Component give = Component.translatable("hud.isekai_mahou.afterlife.give_up", key);
        g.drawCenteredString(font, give, w / 2, h - 30, 0x8899AA);
        if (held > 0) {
            int gw = 80, gx = (w - gw) / 2;
            g.fill(gx, h - 20, gx + gw, h - 18, 0x66000000);
            g.fill(gx, h - 20, gx + gw * held / ClientAfterlife.GIVE_UP_TICKS, h - 18, 0xFFFF4A5A);
        }
    }

    /** Faixa do eletrocardiograma: o pulso corre da direita para a esquerda; acerte quando ele estiver na faixa clara. */
    private static void ecg(GuiGraphics g, Font font, int w, int cy, double phase) {
        int ew = 170, eh = 30, ex = (w - ew) / 2, ey = cy;
        g.fill(ex - 1, ey - 1, ex + ew + 1, ey + eh + 1, 0x99102028);
        g.fill(ex, ey, ex + ew, ey + eh, 0xCC050A10);
        int zone = (int) (ew * ClientAfterlife.WINDOW);
        int mid = ex + ew / 2;
        long since = Util.getMillis() - ClientAfterlife.flashAt;
        int zoneCol = 0x3355FFEE;
        if (since < 250) zoneCol = ClientAfterlife.flashHit ? 0x8844FF77 : 0x88FF3344;
        g.fill(mid - zone, ey + 1, mid + zone, ey + eh - 1, zoneCol);
        int base = ey + eh / 2;
        g.fill(ex + 2, base, ex + ew - 2, base + 1, 0x5555FFEE);
        // pulso: um "QRS" pequeno que anda; em phase 0,5 está no meio
        int px = ex + (int) (ew * (1.0 - phase));
        int[] shape = {0, -2, 0, 3, -12, 9, -3, 0};
        for (int i = 0; i < shape.length - 1; i++) {
            int x0 = px + i * 2 - shape.length, x1 = x0 + 2;
            if (x0 < ex + 1 || x1 > ex + ew - 1) continue;
            int y0 = base + shape[i], y1 = base + shape[i + 1];
            g.fill(x0, Math.min(y0, y1), x1, Math.max(y0, y1) + 1, 0xFF9FFFF5);
        }
        g.drawCenteredString(font, Component.translatable("hud.isekai_mahou.afterlife.beat_hint",
                Minecraft.getInstance().options.keyJump.getTranslatedKeyMessage()), w / 2, ey - 11, 0xFFFFFF);
    }

    /** Bordas escuras azuladas (degraus de transparência), mais fortes no pulso. */
    private static void vignette(GuiGraphics g, int w, int h, float strength) {
        int steps = 12, step = 5;
        for (int k = 0; k < steps; k++) {
            int a = (int) (110 * strength * (1F - k / (float) steps));
            if (a <= 0) continue;
            int col = (a << 24) | 0x05101A;
            int i = k * step;
            g.fill(i, i, w - i, i + step, col);
            g.fill(i, h - i - step, w - i, h - i, col);
            g.fill(i, i + step, i + step, h - i - step, col);
            g.fill(w - i - step, i + step, w - i, h - i - step, col);
        }
    }

    private AfterlifeHud() {}
}
