package com.renanpontes.isekaimahou.feature.class_hollow.client;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_hollow.Hollow;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowSouls;
import com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowTech;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * SÓ CLIENTE. Camadas de tela do Hollow:
 * <ul>
 *   <li>a CENA da hollowificação (por cima de tudo): as bordas embranquecem enquanto os ossos sobem; a máscara se fecha;
 *   pulsos rubros quando o buraco abre; a tela fica BRANCA e depois NEGRA com a máscara e rachaduras rubras; o rugido
 *   rasga o negro e a tela treme;</li>
 *   <li>o contador de Almas e a fome, pequeno, ao lado da hotbar (só para Hollow).</li>
 * </ul>
 */
public final class HollowOverlay {
    private static long sceneStart = -1;
    private static int sceneTicks;

    static void accept(HollowNetwork.Cinematic p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (p.stage() == 0) {
            sceneStart = mc.level.getGameTime();
            sceneTicks = p.ticks();
        } else if (p.stage() == 4) {
            HollowFxClient.addShake(1.4F);
        } else if (p.stage() >= 5) {
            sceneStart = -1;
        }
    }

    static void clear() {
        sceneStart = -1;
    }

    // =====================================================================================
    //  A cena
    // =====================================================================================
    static void renderScene(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (sceneStart < 0 || mc.level == null) return;
        float t = mc.level.getGameTime() - sceneStart + delta.getGameTimeDeltaPartialTick(false);
        if (t > sceneTicks + 10) {
            sceneStart = -1;
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        if (t < 20) {
            // ossos subindo: bordas brancas
            vignette(g, w, h, 0xF2EEE6, t / 20F * 0.55F);
            if (mc.level.getGameTime() % 10 < 2) HollowFxClient.addShake(0.2F);
        } else if (t < 40) {
            vignette(g, w, h, 0xF2EEE6, 0.55F);
            mask(g, w, h, Mth.clamp((t - 20) / 8F, 0F, 1F) * 0.35F, false);
        } else if (t < 55) {
            float pulse = 0.5F + 0.5F * Mth.sin((t - 40) * 1.3F);
            vignette(g, w, h, 0xC0102A, 0.35F + 0.35F * pulse);
            mask(g, w, h, 0.35F, false);
        } else if (t < 75) {
            // branco → negro, a máscara e as rachaduras
            float u = (t - 55) / 20F;
            if (u < 0.3F) {
                g.fill(0, 0, w, h, MahouUi.alpha(0xFFFFFFFF, (int) (255 * Mth.clamp(u / 0.15F, 0F, 1F))));
            } else {
                float toBlack = Mth.clamp((u - 0.3F) / 0.2F, 0F, 1F);
                int c = HollowRenderTypes.mix(0xFFFFFF, 0x000000, toBlack);
                g.fill(0, 0, w, h, 0xFF000000 | c);
                mask(g, w, h, toBlack, true);
                cracks(g, w, h, toBlack, t);
            }
        } else {
            // o rugido: o negro se rasga e some
            float u = Mth.clamp((t - 75) / 14F, 0F, 1F);
            int a = (int) (255 * (1F - u));
            if (a > 0) {
                g.fill(0, 0, w, h, MahouUi.alpha(0xFF000000, a));
                cracks(g, w, h, 1F - u, t);
            }
            vignette(g, w, h, 0xC0102A, 0.6F * (1F - u * 0.6F));
        }
    }

    /** Bordas coloridas (escurecendo para dentro). */
    private static void vignette(GuiGraphics g, int w, int h, int rgb, float strength) {
        if (strength <= 0) return;
        int steps = 10;
        int band = Math.max(4, Math.min(w, h) / 6);
        for (int i = 0; i < steps; i++) {
            float k = (1F - i / (float) steps);
            int a = (int) (255 * strength * k * k);
            if (a <= 0) continue;
            int c = (a << 24) | (rgb & 0xFFFFFF);
            int d = i * band / steps;
            int s = band / steps + 1;
            g.fill(0, d, w, d + s, c);
            g.fill(0, h - d - s, w, h - d, c);
            g.fill(d, 0, d + s, h, c);
            g.fill(w - d - s, 0, w - d, h, c);
        }
    }

    /** A máscara vista de dentro: placa branca com as fendas dos olhos e listras rubras. */
    private static void mask(GuiGraphics g, int w, int h, float alpha, boolean full) {
        if (alpha <= 0) return;
        int cx = w / 2, cy = h / 2;
        int mw = full ? Math.min(w, h) / 2 : w, mh = full ? (int) (Math.min(w, h) * 0.62F) : h;
        int bone = MahouUi.alpha(0xFFF2EEE6, (int) (255 * alpha));
        int red = MahouUi.alpha(0xFFC0102A, (int) (255 * alpha));
        int black = MahouUi.alpha(0xFF050005, (int) (255 * alpha));
        if (full) {
            // o rosto: placas que estreitam no queixo
            for (int i = 0; i < 12; i++) {
                float v = i / 12F;
                int rw = (int) (mw * 0.5F * (1F - 0.45F * v * v));
                int y0 = cy - mh / 2 + (int) (v * mh), y1 = cy - mh / 2 + (int) ((i + 1) / 12F * mh);
                g.fill(cx - rw, y0, cx + rw, y1, bone);
            }
            int ey = cy - mh / 8;
            int ew = mw / 5, eh = Math.max(3, mh / 14);
            g.fill(cx - mw / 4 - ew / 2, ey, cx - mw / 4 + ew / 2, ey + eh, black);
            g.fill(cx + mw / 4 - ew / 2, ey, cx + mw / 4 + ew / 2, ey + eh, black);
            // listras rubras saindo dos olhos
            int sw = Math.max(2, mw / 40);
            g.fill(cx - mw / 4 - sw / 2, ey + eh, cx - mw / 4 + sw / 2, cy + mh / 3, red);
            g.fill(cx + mw / 4 - sw / 2, ey + eh, cx + mw / 4 + sw / 2, cy + mh / 3, red);
            // dentes
            int ty = cy + mh / 4;
            int tw = mw / 14;
            for (int i = -3; i <= 3; i++) g.fill(cx + i * tw - tw / 3, ty, cx + i * tw + tw / 3, ty + mh / 12, black);
        } else {
            // de dentro: as bordas ficam brancas, as fendas dos olhos deixam ver
            int ey = cy - h / 10;
            g.fill(0, 0, w, ey, bone);
            g.fill(0, cy + h / 10, w, h, bone);
            g.fill(0, ey, w / 8, cy + h / 10, bone);
            g.fill(w - w / 8, ey, w, cy + h / 10, bone);
            g.fill(cx - w / 16, ey, cx + w / 16, cy + h / 10, bone);
            g.fill(cx - w / 4 - 2, cy + h / 10, cx - w / 4 + 2, h, red);
            g.fill(cx + w / 4 - 2, cy + h / 10, cx + w / 4 + 2, h, red);
        }
    }

    /** Rachaduras rubras que se espalham do centro. */
    private static void cracks(GuiGraphics g, int w, int h, float alpha, float t) {
        if (alpha <= 0) return;
        int c = MahouUi.alpha(0xFFE01030, (int) (255 * alpha));
        int cx = w / 2, cy = h / 2;
        float reach = Mth.clamp((t - 60) / 15F, 0.2F, 1F);
        for (int k = 0; k < 9; k++) {
            double a = k * Math.PI * 2 / 9 + 0.3;
            float x = cx, y = cy;
            int segs = 8;
            for (int s = 0; s < segs * reach; s++) {
                double jitter = Math.sin(k * 13.1 + s * 7.7) * 0.6;
                float nx = x + (float) (Math.cos(a + jitter) * Math.max(w, h) / 16F);
                float ny = y + (float) (Math.sin(a + jitter) * Math.max(w, h) / 16F);
                line(g, (int) x, (int) y, (int) nx, (int) ny, c);
                x = nx;
                y = ny;
            }
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i += 2) {
            int x = x0 + (x1 - x0) * i / steps, y = y0 + (y1 - y0) * i / steps;
            g.fill(x - 1, y - 1, x + 1, y + 1, c);
        }
    }

    // =====================================================================================
    //  Almas e fome (ao lado da hotbar)
    // =====================================================================================
    static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || sceneStart >= 0) return;
        MagicData d = ClientMagic.data();
        if (d == null || d.path() != MagicPath.HOLLOW || mc.level == null) return;
        Font font = mc.font;
        int souls = Hollow.souls(d);
        int now = (int) (mc.level.getGameTime() / 20L);
        int hunger = HollowSouls.hunger(d, now);
        int color = hunger >= 2 ? 0xFFFF3040 : hunger == 1 ? 0xFFE0A040 : 0xFFE6DFCF;
        Component text = Component.translatable("hud.isekai_mahou.hollow.souls", souls);
        // à esquerda da hotbar, depois do espaço da mão secundária
        int right = g.guiWidth() / 2 - 91 - 36;
        int x = right - font.width(text);
        int y = g.guiHeight() - 12;
        if (hunger > 0 && (mc.level.getGameTime() / 10) % 2 == 0) color = MahouUi.alpha(color, 160);
        g.drawString(font, text, x, y, color, true);
        if (hunger > 0) {
            Component hk = Component.translatable("hud.isekai_mahou.hollow.hunger." + hunger);
            g.drawString(font, hk, right - font.width(hk), y - 10, color, true);
        }
        renderTechHud(g, mc, d, font, right, y - (hunger > 0 ? 30 : 20));
        renderCharge(g, mc, d, font, delta.getGameTimeDeltaPartialTick(false));
    }

    // =====================================================================================
    //  v1.4 — técnicas inatas: a fileira da tecla "Instinto Hollow" e a barra de carga
    // =====================================================================================
    private static boolean releasedSelf(Minecraft mc) {
        HollowLooks.Look l = HollowLooks.of(mc.player);
        return l != null && l.released();
    }

    /** [R] Cero · Gran Rey · Sonído (com recarga), à esquerda da hotbar, acima das Almas. */
    private static void renderTechHud(GuiGraphics g, Minecraft mc, MagicData d, Font font, int right, int y) {
        boolean rel = releasedSelf(mc);
        java.util.List<HollowTech> row = new java.util.ArrayList<>();
        row.add(HollowTech.CERO);
        if (HollowTech.GRAN_REY_CERO.unlocked(d, rel)) row.add(HollowTech.GRAN_REY_CERO);
        if (HollowTech.CERO_OSCURAS.unlocked(d, rel)) row.add(HollowTech.CERO_OSCURAS);
        if (HollowTech.SONIDO.unlocked(d, rel)) row.add(HollowTech.SONIDO);
        int size = 14, gap = 2;
        int x = right - row.size() * (size + gap) + gap;
        Component key = Component.literal("[").append(HollowInput.TECH.getTranslatedKeyMessage()).append("]");
        g.drawString(font, key, x - font.width(key) - 3, y + 3, 0xFFE6DFCF, true);
        for (HollowTech t : row) {
            MahouUi.roundRect(g, x - 1, y - 1, size + 2, size + 2, 0xC0200608, 0xC00A0204);
            g.blit(t.icon(), x, y, size, size, 0, 0, 32, 32, 32, 32);
            float cd = HollowInput.cooldown(t);
            if (cd > 0) {
                int h = Math.round(size * cd);
                g.fill(x, y + size - h, x + size, y + size, 0xA0000000);
            }
            if (t == HollowTech.SONIDO && HollowInput.sonidoChain() > 0 && cd <= 0) {
                for (int i = 0; i < HollowTech.SONIDO_CHAIN - HollowInput.sonidoChain(); i++) g.fill(x + 1 + i * 4, y + size - 2, x + 3 + i * 4, y + size, 0xFFFFFFFF);
            }
            x += size + gap;
        }
    }

    /** A barra de carga sob a mira enquanto segura a tecla (Bala → Cero → Cero Oscuras; Gran Rey Cero agachado). */
    private static void renderCharge(GuiGraphics g, Minecraft mc, MagicData d, Font font, float pt) {
        int held = HollowInput.heldTicks(pt);
        int mode = HollowInput.mode();
        if (held <= HollowTech.TAP_MAX || mode == 2) return;
        boolean rel = releasedSelf(mc);
        int rgb = 0xFF000000 | Hollow.ceroRgb(d);
        float frac;
        Component label;
        int[] marks;
        if (mode == 1) {
            if (!HollowTech.GRAN_REY_CERO.unlocked(d, rel) || HollowInput.cooldown(HollowTech.GRAN_REY_CERO) > 0) return;
            int min = rel ? HollowTech.GRAN_REY_MIN_RELEASED : HollowTech.GRAN_REY_MIN;
            frac = Math.min(1F, held / (float) (min * 2));
            marks = new int[] {50};
            label = held >= min ? HollowTech.GRAN_REY_CERO.displayName() : Component.translatable("hud.isekai_mahou.hollow.charging",
                    HollowTech.GRAN_REY_CERO.displayName());
            if (held < min) rgb = 0xFF8A7070;
        } else {
            boolean osc = HollowTech.CERO_OSCURAS.unlocked(d, rel) && HollowInput.cooldown(HollowTech.CERO_OSCURAS) <= 0;
            int top = osc ? HollowTech.OSCURAS_FULL : HollowTech.CERO_FULL;
            frac = Math.min(1F, held / (float) top);
            marks = osc ? new int[] {HollowTech.CERO_MIN * 100 / top, HollowTech.CERO_FULL * 100 / top}
                    : new int[] {HollowTech.CERO_MIN * 100 / top};
            if (held < HollowTech.CERO_MIN) {
                label = HollowTech.BALA.displayName();
            } else if (osc && held >= HollowTech.OSCURAS_FULL) {
                label = HollowTech.CERO_OSCURAS.displayName();
                rgb = 0xFF300818;
            } else {
                int pct = Math.round(Math.min(1F, (held - HollowTech.CERO_MIN) / (float) (HollowTech.CERO_FULL - HollowTech.CERO_MIN)) * 100);
                label = HollowTech.CERO.displayName().append(" " + pct + "%");
            }
            if (HollowInput.cooldown(HollowTech.CERO) > 0) {
                label = HollowTech.BALA.displayName();
                frac = 0F;
            }
        }
        int w = 72, h = 4;
        int x = g.guiWidth() / 2 - w / 2, y = g.guiHeight() / 2 + 16;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xA0000000);
        int fw = Math.round(w * frac);
        boolean pulse = frac >= 1F && (mc.level.getGameTime() / 3) % 2 == 0;
        g.fill(x, y, x + fw, y + h, pulse ? 0xFFFFFFFF : rgb);
        for (int mk : marks) {
            int mx = x + w * mk / 100;
            g.fill(mx, y - 1, mx + 1, y + h + 1, 0xC0FFFFFF);
        }
        MahouUi.smallText(g, font, label, x + w / 2F - font.width(label) * 0.4F, y + h + 3, 0.8F, 0xFFE6DFCF, true);
    }

    private HollowOverlay() {}
}
