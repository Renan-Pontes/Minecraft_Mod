package com.renanpontes.isekaimahou.feature.legends_d.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.feature.legends_d.LdNet;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Letreiros e clarões das lendas (HUD):
 * <ul>
 *   <li>"Eu..." e "...sou..." surgindo LETRA A LETRA (cada letra cai e acende, com um tique) durante a carga do
 *       Atômico, e "ATÔMICO!" gigante com eco colorido na detonação; quem conjura vê a borda da tela pulsar violeta;</li>
 *   <li>"⚠ ABRIGUEM-SE! ⚠" com a contagem para quem está perto da carga — aliados sabem que vão para a bolha, os
 *       outros veem quantos blocos faltam para sair do raio;</li>
 *   <li>moldura azul "Bolha protetora" enquanto o jogador está na bolha;</li>
 *   <li>clarão branco (com rabo violeta) para quem VIU a explosão — mais forte olhando para ela e de perto;</li>
 *   <li>clarão dourado curto do pilar da Desintegração.</li>
 * </ul>
 */
public final class AtomicOverlay {
    private static final int VIOLET = 0x8A5CFF;
    private static final int BLUE = 0x4A7BFF;
    private static final int BUBBLE = 0x5AB8FF;
    private static int ticks;
    private static int textStart = -1;
    private static int textLen = 60;
    private static boolean textMine;
    private static int lettersShown;
    private static int wordStart = -1;
    private static int flashStart = -1;
    private static float flashPower;
    private static int holyStart = -1;
    private static float holyPower;
    private static int warnStart = -1;
    private static int warnEnd = -1;
    private static boolean warnSafe;
    private static Vec3 warnCenter = Vec3.ZERO;
    private static float warnRadius;

    public static void onFx(LdNet.Fx p) {
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        switch (p.kind()) {
            case LdNet.ATOMIC_TEXT -> {
                textStart = ticks;
                textLen = Math.max(20, p.ticks());
                textMine = mc.player != null && mc.player.getId() == p.entityId();
                lettersShown = 0;
                wordStart = -1;
            }
            case LdNet.ATOMIC_FLASH -> {
                wordStart = ticks;
                flashStart = ticks;
                flashPower = cam == null ? 1F : seen(cam, p.pos(), p.size());
                warnEnd = -1;
            }
            case LdNet.ATOMIC_ABORT -> {
                textStart = -1;
                wordStart = -1;
                warnEnd = -1;
            }
            case LdNet.ATOMIC_WARN_SAFE, LdNet.ATOMIC_WARN_DANGER -> {
                if (warnEnd < ticks) warnStart = ticks;
                warnEnd = ticks + Math.max(1, p.ticks());
                warnSafe = p.kind() == LdNet.ATOMIC_WARN_SAFE;
                warnCenter = p.pos();
                warnRadius = p.size();
            }
            case LdNet.ATOMIC_BUBBLE, LdNet.ATOMIC_BUBBLE_END -> AtomicBubbleClient.onFx(p);
            case LdNet.HOLY_FLASH -> {
                holyStart = ticks;
                double d = cam == null ? 0 : cam.position().distanceTo(p.pos());
                holyPower = (float) Mth.clamp(1.0 - d / 40.0, 0.15, 0.8);
            }
            default -> {}
        }
    }

    /** Quanto do clarão chega a quem está em {@code cam}: dentro da esfera = tudo; olhando para ela = muito. */
    private static float seen(Entity cam, Vec3 center, float radius) {
        Vec3 eye = cam.getEyePosition();
        double d = eye.distanceTo(center);
        if (d <= radius * 1.1) return 1F;
        Vec3 to = center.subtract(eye).normalize();
        float facing = (float) Mth.clamp((cam.getViewVector(1F).dot(to) + 0.2) / 1.2, 0, 1);
        float dist = (float) Mth.clamp(1.0 - (d - radius) / (radius * 6 + 80), 0.15, 1.0);
        return Mth.clamp((0.3F + 0.7F * facing) * dist, 0.12F, 1F);
    }

    // =====================================================================================
    //  Linha do tempo do letreiro (escala com a duração da carga)
    // =====================================================================================
    private static float step() { return 3F * textLen / 60F; }

    private static float line1At() { return 4F * textLen / 60F; }

    private static float line2At() { return 24F * textLen / 60F; }

    private static String line1() { return Component.translatable("hud.isekai_mahou.legends_d.atomic_1").getString(); }

    private static String line2() { return Component.translatable("hud.isekai_mahou.legends_d.atomic_2").getString(); }

    private static int lettersAt(float t) {
        int n1 = Mth.clamp((int) ((t - line1At()) / step()) + 1, 0, line1().length());
        int n2 = Mth.clamp((int) ((t - line2At()) / step()) + 1, 0, line2().length());
        return n1 + n2;
    }

    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            textStart = wordStart = flashStart = holyStart = warnStart = warnEnd = -1;
            return;
        }
        if (!mc.isPaused()) ticks++;
        if (textStart >= 0) {
            int t = ticks - textStart;
            if (t > textLen + 80) {
                textStart = -1;
            } else if (wordStart < 0) {
                // Um tique de cristal a cada letra nova
                int n = lettersAt(t);
                if (n > lettersShown && t >= line1At()) {
                    float pitch = 1.1F + 0.06F * n;
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_HIT, Math.min(2F, pitch), textMine ? 0.5F : 0.3F));
                }
                lettersShown = n;
            }
        }
        if (wordStart >= 0 && ticks - wordStart > 70) wordStart = -1;
        if (flashStart >= 0 && ticks - flashStart > 60) flashStart = -1;
        if (holyStart >= 0 && ticks - holyStart > 20) holyStart = -1;
        if (warnEnd >= 0 && ticks > warnEnd + 2) warnEnd = warnStart = -1;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = delta.getGameTimeDeltaPartialTick(true);
        int w = g.guiWidth(), h = g.guiHeight();
        Font font = mc.font;

        // Borda violeta pulsando para quem está conjurando (cada vez mais forte)
        if (textStart >= 0 && textMine && wordStart < 0) {
            float k = Mth.clamp((ticks - textStart + pt) / textLen, 0F, 1F);
            float pulse = 0.6F + 0.4F * Mth.sin((ticks + pt) * (0.25F + 0.5F * k));
            vignette(g, w, h, VIOLET, (0.15F + 0.45F * k) * pulse, 26 + (int) (30 * k));
        }
        // Moldura azul da bolha protetora
        float bubble = AtomicBubbleClient.localStrength(pt);
        if (bubble > 0.01F) {
            float shimmer = 0.75F + 0.25F * Mth.sin((ticks + pt) * 0.2F);
            vignette(g, w, h, BUBBLE, 0.45F * bubble * shimmer, 34);
        }

        // Clarões por baixo dos letreiros
        if (flashStart >= 0) {
            float t = ticks - flashStart + pt;
            float a = t < 4 ? 1F : (float) Math.exp(-(t - 4) / 9.0);
            a *= flashPower;
            int rgb = LdDraw.mix(0xFFFFFF, 0xC9B4FF, Mth.clamp((t - 6) / 20F, 0F, 1F));
            if (a > 0.01F) g.fill(0, 0, w, h, ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | rgb);
        }
        if (holyStart >= 0) {
            float t = ticks - holyStart + pt;
            float a = holyPower * (t < 2 ? t / 2F : (float) Math.exp(-(t - 2) / 4.0));
            if (a > 0.01F) g.fill(0, 0, w, h, ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | 0xFFF3D0);
        }
        if (mc.options.hideGui) return;

        if (warnEnd >= 0) renderWarning(g, font, w, h, pt);
        if (bubble > 0.01F) {
            float secs = AtomicBubbleClient.localSeconds(pt);
            String label = Component.translatable("hud.isekai_mahou.legends_d.bubble", String.format("%.1f", secs)).getString();
            float a = bubble * (secs > 0F ? 1F : 0.6F);
            int y = h - 72;
            glow(g, w / 2F, y + 4, font.width(label) * 0.7F + 24, 10, BUBBLE, 0.4F * a);
            outlined(g, font, label, w / 2F - font.width(label) / 2F, y, 1F, argb(0xDDF2FF, a), argb(0x0A2440, a * 0.9F));
        }

        // "Eu..." · "...sou..." letra a letra (em escada)
        if (textStart >= 0) {
            float t = ticks - textStart + pt;
            String l1 = line1(), l2 = line2();
            float fade = wordStart >= 0 ? Mth.clamp(1F - (ticks - wordStart + pt) / 8F, 0F, 1F) : 1F;
            if (fade > 0.02F && t >= line1At()) {
                float scale = Math.min(2.8F, w * 0.32F / Math.max(1, Math.max(font.width(l1), font.width(l2))));
                float indent = font.width(l1) * scale * 0.55F;
                float blockW = Math.max(font.width(l1) * scale, indent + font.width(l2) * scale);
                float x0 = w / 2F - blockW / 2F;
                float y1 = h * 0.60F, y2 = y1 + 11 * scale;
                glow(g, w / 2F, y1 + 8 * scale, blockW * 0.65F + 30, 16 * scale, VIOLET, 0.35F * fade);
                letters(g, font, l1, x0, y1, scale, t - line1At(), fade);
                if (t >= line2At()) letters(g, font, l2, x0 + indent, y2, scale, t - line2At(), fade);
            }
        }

        // "ATÔMICO!"
        if (wordStart >= 0) {
            float t = ticks - wordStart + pt;
            String word = Component.translatable("hud.isekai_mahou.legends_d.atomic_3").getString();
            float in = Mth.clamp(t / 5F, 0F, 1F);
            float out = Mth.clamp((70F - t) / 16F, 0F, 1F);
            float a = Math.min(in, out);
            if (a > 0.02F) {
                float base = Math.min(6F, w * 0.85F / Math.max(1, font.width(word)));
                float scale = base * Mth.lerp(1F - (1F - in) * (1F - in), 1.7F, 1F) * (1F + 0.04F * Mth.clamp((t - 5) / 60F, 0F, 1F));
                float shake = t < 14 ? (14 - t) * 0.3F : 0F;
                float sx = (float) Math.sin(t * 9.1) * shake, sy = (float) Math.cos(t * 7.3) * shake;
                float x = w / 2F - font.width(word) * scale / 2F + sx;
                float y = h * 0.42F - 4.5F * scale + sy;
                glow(g, w / 2F, h * 0.42F, font.width(word) * scale * 0.75F + 60, 18 * scale, VIOLET, 0.55F * a);
                // Eco colorido (azul para um lado, violeta para o outro), afastando nos primeiros instantes
                float echo = 2F + Math.max(0F, 10F - t) * 0.8F;
                plain(g, font, word, x - echo, y, scale, argb(BLUE, 0.45F * a));
                plain(g, font, word, x + echo, y, scale, argb(VIOLET, 0.45F * a));
                int col = LdDraw.mix(0xFFFFFF, 0xB79BFF, Mth.clamp(t / 25F, 0F, 1F));
                outlined(g, font, word, x, y, scale, argb(col, a), argb(0x2A0E5C, a));
            }
        }
    }

    /** "⚠ ABRIGUEM-SE! ⚠" + contagem grande + o que fazer. */
    private static void renderWarning(GuiGraphics g, Font font, int w, int h, float pt) {
        Minecraft mc = Minecraft.getInstance();
        float now = ticks + pt;
        float left = Math.max(0F, (warnEnd - now) / 20F);
        if (left <= 0F) return;
        float in = Mth.clamp((now - warnStart) / 6F, 0F, 1F);
        float a = in;
        float pulse = 0.5F + 0.5F * Mth.sin(now * 0.6F);

        String title = Component.translatable("hud.isekai_mahou.legends_d.take_cover").getString();
        float ts = Math.min(2.4F, w * 0.6F / Math.max(1, font.width(title)));
        float ty = h * 0.13F;
        int titleCol = LdDraw.mix(0xFF4A3A, 0xFFC04A, pulse);
        glow(g, w / 2F, ty + 4 * ts, font.width(title) * ts * 0.7F + 30, 12 * ts, 0xFF3B30, 0.35F * a);
        outlined(g, font, title, w / 2F - font.width(title) * ts / 2F, ty, ts, argb(titleCol, a), argb(0x2A0A06, a));

        // Contagem: o número "pula" a cada segundo
        int sec = (int) Math.ceil(left);
        float frac = left - (sec - 1);
        float pop = 1F + 0.5F * Mth.clamp((frac - 0.75F) / 0.25F, 0F, 1F);
        String num = Integer.toString(sec);
        float ns = 3.6F * pop;
        float ny = ty + 12 * ts + 4;
        outlined(g, font, num, w / 2F - font.width(num) * ns / 2F, ny, ns, argb(0xFFFFFF, a), argb(0x3A0C08, a));

        String sub;
        int subCol;
        if (warnSafe) {
            sub = Component.translatable("hud.isekai_mahou.legends_d.take_cover_safe").getString();
            subCol = 0x9FD8FF;
        } else {
            double dist = mc.player == null ? 999 : mc.player.position().distanceTo(warnCenter);
            int need = (int) Math.ceil(warnRadius + 2 - dist);
            if (need > 0) {
                sub = Component.translatable("hud.isekai_mahou.legends_d.take_cover_run", need).getString();
                subCol = 0xFFB08A;
            } else {
                sub = Component.translatable("hud.isekai_mahou.legends_d.take_cover_out").getString();
                subCol = 0xFFE3A0;
            }
        }
        float sy = ny + 9 * ns + 4;
        outlined(g, font, sub, w / 2F - font.width(sub) / 2F, sy, 1F, argb(subCol, a), argb(0x101010, a * 0.9F));
    }

    /** Letras surgindo uma a uma: cada uma cai um pouco, cresce e acende. */
    private static void letters(GuiGraphics g, Font font, String s, float x, float y, float scale, float t, float fade) {
        float step = step();
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            float lt = t - i * step;
            float cw = font.width(ch) * scale;
            if (lt >= 0F) {
                float k = Mth.clamp(lt / 4F, 0F, 1F);
                float sc = scale * Mth.lerp(k, 1.5F, 1F);
                float dy = (1F - k) * -6F * scale;
                float ox = cx + cw / 2F - font.width(ch) * sc / 2F;
                int col = LdDraw.mix(0xFFFFFF, 0xE8DEFF, k);
                outlined(g, font, ch, ox, y + dy, sc, argb(col, k * fade), argb(0x1A0B33, k * fade * 0.9F));
            }
            cx += cw;
        }
    }

    /** Moldura colorida nas bordas da tela (gradiente para dentro). */
    private static void vignette(GuiGraphics g, int w, int h, int rgb, float alpha, int size) {
        int a = (int) (Mth.clamp(alpha, 0F, 1F) * 255);
        if (a <= 2) return;
        int c0 = (a << 24) | (rgb & 0xFFFFFF), c1 = rgb & 0xFFFFFF;
        g.fillGradient(0, 0, w, size, c0, c1);
        g.fillGradient(0, h - size, w, h, c1, c0);
        int steps = 8;
        for (int i = 0; i < steps; i++) {
            float k = 1F - i / (float) steps;
            int ai = (int) (a * k * k);
            if (ai <= 0) continue;
            int x0 = size * i / steps, x1 = size * (i + 1) / steps;
            int col = (ai << 24) | (rgb & 0xFFFFFF);
            g.fill(x0, 0, x1, h, col);
            g.fill(w - x1, 0, w - x0, h, col);
        }
    }

    private static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Texto grande sem contorno (ecos). */
    private static void plain(GuiGraphics g, Font font, String s, float x, float y, float scale, int color) {
        if ((color >>> 24) < 5) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.scale(scale, scale, 1F);
        g.drawString(font, s, 0, 0, color, false);
        ps.popPose();
    }

    /** Texto grande com contorno escuro. */
    private static void outlined(GuiGraphics g, Font font, String s, float x, float y, float scale, int color, int outline) {
        if ((color >>> 24) < 5) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.scale(scale, scale, 1F);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) g.drawString(font, s, dx, dy, outline, false);
            }
        }
        g.drawString(font, s, 0, 0, color, false);
        ps.popPose();
    }

    /** Brilho elíptico suave atrás do texto (faixas horizontais com alfa caindo). */
    private static void glow(GuiGraphics g, float cx, float cy, float halfW, float halfH, int rgb, float alpha) {
        int steps = 10;
        for (int i = steps; i >= 1; i--) {
            float k = i / (float) steps;
            int a = (int) (alpha * (1F - k) * 0.35F * 255);
            if (a <= 0) continue;
            int x0 = (int) (cx - halfW * k), x1 = (int) (cx + halfW * k);
            int y0 = (int) (cy - halfH * k), y1 = (int) (cy + halfH * k);
            g.fill(x0, y0, x1, y1, (a << 24) | (rgb & 0xFFFFFF));
        }
    }

    private AtomicOverlay() {}
}
