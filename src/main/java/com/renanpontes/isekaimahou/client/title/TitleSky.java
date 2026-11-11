package com.renanpontes.isekaimahou.client.title;

import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.client.MahouUi;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Fundo animado do menu principal (por cima do panorama do vanilla): céu noturno violeta, aurora ondulando, estrelas
 * piscando, estrela cadente de vez em quando, um grande círculo mágico girando atrás do logo (anéis, hexagrama e runas
 * do alfabeto galáctico — a mesma escrita da mesa de encantamento), runas e partículas de mana subindo e névoa
 * embaixo. Tudo desenhado com retângulos (sem texturas novas); leve paralaxe com o mouse. Relógio real (anima com o
 * jogo carregando/pausado).
 */
final class TitleSky {
    /** Fonte do vanilla com a escrita da mesa de encantamento. */
    private static final Style RUNES = Style.EMPTY.withFont(ResourceLocation.withDefaultNamespace("alt"));
    private static final String RUNE_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final int STARS = 150;
    private static final int MOTES = 70;
    private static final int GLYPHS = 18;

    private final RandomSource random = RandomSource.create();
    private final float[] starX = new float[STARS], starY = new float[STARS], starPhase = new float[STARS];
    private final int[] starSize = new int[STARS];
    private final float[] moteX = new float[MOTES], moteY = new float[MOTES], moteSpeed = new float[MOTES], motePhase = new float[MOTES];
    private final int[] moteColor = new int[MOTES];
    private final float[] glyphX = new float[GLYPHS], glyphY = new float[GLYPHS], glyphSpeed = new float[GLYPHS], glyphScale = new float[GLYPHS];
    private final char[] glyphChar = new char[GLYPHS];
    private final int[] glyphColor = new int[GLYPHS];
    private float shootT = -1F, shootX, shootY, nextShoot = 4F;
    private long lastNanos;
    private boolean seeded;

    private static final int[] PALETTE = {0xFFB98CFF, 0xFF8FD3FF, 0xFFFFD86B, 0xFFE0B8FF, 0xFF9FE8FF};

    private void seed() {
        seeded = true;
        for (int i = 0; i < STARS; i++) {
            starX[i] = random.nextFloat();
            starY[i] = random.nextFloat() * 0.75F;
            starPhase[i] = random.nextFloat() * Mth.TWO_PI;
            starSize[i] = random.nextFloat() < 0.12F ? 2 : 1;
        }
        for (int i = 0; i < MOTES; i++) resetMote(i, true);
        for (int i = 0; i < GLYPHS; i++) resetGlyph(i, true);
    }

    private void resetMote(int i, boolean anywhere) {
        moteX[i] = random.nextFloat();
        moteY[i] = anywhere ? random.nextFloat() : 1.02F + random.nextFloat() * 0.1F;
        moteSpeed[i] = 0.02F + random.nextFloat() * 0.05F;
        motePhase[i] = random.nextFloat() * Mth.TWO_PI;
        moteColor[i] = PALETTE[random.nextInt(PALETTE.length)];
    }

    private void resetGlyph(int i, boolean anywhere) {
        glyphX[i] = 0.04F + random.nextFloat() * 0.92F;
        glyphY[i] = anywhere ? 0.3F + random.nextFloat() * 0.7F : 1.05F;
        glyphSpeed[i] = 0.012F + random.nextFloat() * 0.02F;
        glyphScale[i] = 1F + random.nextFloat() * 1.2F;
        glyphChar[i] = RUNE_CHARS.charAt(random.nextInt(RUNE_CHARS.length()));
        glyphColor[i] = PALETTE[random.nextInt(PALETTE.length)];
    }

    /**
     * Desenha tudo menos o panorama. {@code fade} 0..1 (entrada), {@code circleY} = centro do círculo mágico
     * (atrás do logo), {@code mouseX/Y} para a paralaxe.
     */
    void render(GuiGraphics g, Font font, int w, int h, int mouseX, int mouseY, int circleY, float fade, float seconds) {
        if (!seeded) seed();
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0F : Mth.clamp((now - lastNanos) / 1.0E9F, 0F, 0.1F);
        lastNanos = now;
        float px = (mouseX - w / 2F) / Math.max(1, w), py = (mouseY - h / 2F) / Math.max(1, h);

        // Véu do céu: violeta escuro em cima, quase preto embaixo (o panorama aparece por baixo, apagado)
        g.fillGradient(0, 0, w, h, MahouUi.alpha(0xFF120830, Math.round(0xB8 * fade)), MahouUi.alpha(0xFF05020C, Math.round(0xDC * fade)));

        aurora(g, w, h, seconds, fade, px);
        stars(g, w, h, seconds, fade, px, py);
        shootingStar(g, w, h, dt, fade);
        magicCircle(g, font, w / 2 + Math.round(px * -10), circleY + Math.round(py * -6), Math.min(w, h) * 0.34F, seconds, fade);
        glyphs(g, font, w, h, dt, seconds, fade);
        motes(g, w, h, dt, seconds, fade);

        // Névoa embaixo e escurecido nas bordas de cima
        g.fillGradient(0, Math.round(h * 0.68F), w, h, 0x00000000, MahouUi.alpha(0xFF1A0C30, Math.round(0x90 * fade)));
        g.fillGradient(0, 0, w, Math.round(h * 0.12F), MahouUi.alpha(0xFF000000, Math.round(0x70 * fade)), 0x00000000);
    }

    private void aurora(GuiGraphics g, int w, int h, float t, float fade, float px) {
        for (int band = 0; band < 2; band++) {
            float base = h * (0.14F + band * 0.09F);
            int c0 = band == 0 ? 0xFF7F5CFF : 0xFF4FD8C8;
            for (int x = 0; x < w; x += 3) {
                float k = x + px * 30 * (band + 1);
                float yc = base + Mth.sin(k * 0.011F + t * (0.35F + band * 0.15F) + band * 2F) * h * 0.045F
                        + Mth.sin(k * 0.027F - t * 0.5F) * h * 0.012F;
                float glow = 0.5F + 0.5F * Mth.sin(k * 0.018F + t * 0.8F + band);
                int a = Math.round((0x16 + 0x30 * glow) * fade);
                if (a <= 2) continue;
                int top = Math.round(yc - 16 - glow * 10), mid = Math.round(yc), bot = Math.round(yc + 22);
                g.fillGradient(x, top, x + 3, mid, MahouUi.alpha(c0, 0), MahouUi.alpha(c0, a));
                g.fillGradient(x, mid, x + 3, bot, MahouUi.alpha(c0, a), MahouUi.alpha(c0, 0));
            }
        }
    }

    private void stars(GuiGraphics g, int w, int h, float t, float fade, float px, float py) {
        for (int i = 0; i < STARS; i++) {
            float tw = 0.45F + 0.55F * Mth.sin(t * (1.2F + (i % 5) * 0.35F) + starPhase[i]);
            int a = Math.round(255 * tw * fade * (0.35F + 0.65F * (1F - starY[i])));
            if (a < 8) continue;
            int x = Math.round(starX[i] * w + px * -6), y = Math.round(starY[i] * h + py * -4);
            int s = starSize[i];
            int c = (i % 7 == 0) ? 0xFFFFE8B0 : (i % 5 == 0 ? 0xFFB8E0FF : 0xFFFFFFFF);
            g.fill(x, y, x + s, y + s, MahouUi.alpha(c, a));
            if (s == 2 && tw > 0.85F) {
                int ga = a / 3;
                g.fill(x - 2, y, x, y + 2, MahouUi.alpha(c, ga));
                g.fill(x + 2, y, x + 4, y + 2, MahouUi.alpha(c, ga));
                g.fill(x, y - 2, x + 2, y, MahouUi.alpha(c, ga));
                g.fill(x, y + 2, x + 2, y + 4, MahouUi.alpha(c, ga));
            }
        }
    }

    private void shootingStar(GuiGraphics g, int w, int h, float dt, float fade) {
        if (shootT < 0) {
            nextShoot -= dt;
            if (nextShoot <= 0) {
                shootT = 0;
                shootX = w * (0.2F + random.nextFloat() * 0.7F);
                shootY = h * (0.04F + random.nextFloat() * 0.2F);
            }
            return;
        }
        shootT += dt / 0.9F;
        if (shootT >= 1F) {
            shootT = -1F;
            nextShoot = 5F + random.nextFloat() * 7F;
            return;
        }
        float k = 1F - (1F - shootT) * (1F - shootT);
        float hx = shootX - k * w * 0.25F, hy = shootY + k * h * 0.12F;
        float fadeOut = shootT < 0.8F ? 1F : (1F - shootT) / 0.2F;
        for (int i = 0; i < 26; i++) {
            float tx = hx + i * 2.4F, ty = hy - i * 1.15F;
            int a = Math.round((255 - i * 9) * fadeOut * fade);
            if (a <= 4) break;
            g.fill(Math.round(tx), Math.round(ty), Math.round(tx) + (i < 3 ? 2 : 1), Math.round(ty) + 1, MahouUi.alpha(0xFFFFF4D0, a));
        }
    }

    private void magicCircle(GuiGraphics g, Font font, int cx, int cy, float r, float t, float fade) {
        int ringA = Math.round(0x60 * fade);
        if (ringA < 4) return;
        float rot = t * 6F;
        ring(g, cx, cy, r, MahouUi.alpha(0xFFB98CFF, ringA), 1);
        ring(g, cx, cy, r - 5, MahouUi.alpha(0xFFFFD86B, Math.round(ringA * 0.7F)), 1);
        ring(g, cx, cy, r * 0.62F, MahouUi.alpha(0xFFB98CFF, Math.round(ringA * 0.8F)), 1);
        ring(g, cx, cy, r * 0.58F, MahouUi.alpha(0xFF8FD3FF, Math.round(ringA * 0.5F)), 1);

        // Hexagrama (dois triângulos girando em sentidos opostos)
        int lineA = Math.round(ringA * 0.75F);
        polygon(g, cx, cy, r * 0.6F, 3, rot * 0.6F, MahouUi.alpha(0xFFFFD86B, lineA));
        polygon(g, cx, cy, r * 0.6F, 3, -rot * 0.6F + 60F, MahouUi.alpha(0xFFE0B8FF, lineA));
        polygon(g, cx, cy, r - 5, 8, -rot * 0.25F, MahouUi.alpha(0xFFB98CFF, Math.round(lineA * 0.45F)));

        // Runas em volta (entre os dois anéis de fora), voltadas para fora
        int runeA = Math.round(0xB0 * fade);
        int count = Mth.clamp(Math.round(r / 6F), 16, 40);
        for (int i = 0; i < count; i++) {
            float ang = (i / (float) count) * 360F + rot;
            float rad = ang * Mth.DEG_TO_RAD;
            float rx = cx + Mth.cos(rad) * (r - 11), ry = cy + Mth.sin(rad) * (r - 11);
            float pulse = 0.55F + 0.45F * Mth.sin(t * 2F + i * 0.7F);
            int c = MahouUi.alpha(i % 3 == 0 ? 0xFFFFD86B : 0xFFD9C2FF, Math.max(5, Math.round(runeA * pulse)));
            Component ch = Component.literal(String.valueOf(RUNE_CHARS.charAt(i % RUNE_CHARS.length()))).withStyle(RUNES);
            g.pose().pushPose();
            g.pose().translate(rx, ry, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(ang + 90F));
            g.pose().scale(0.75F, 0.75F, 1F);
            g.drawString(font, ch, -font.width(ch) / 2, -4, c, false);
            g.pose().popPose();
        }
        // Brilho no centro
        int glowA = Math.round((0x18 + 0x10 * Mth.sin(t * 1.5F)) * fade);
        for (int k = 3; k >= 1; k--) {
            int s = Math.round(r * 0.18F * k);
            MahouUi.diamond(g, cx, cy, s, MahouUi.alpha(0xFF7F5CFF, glowA));
        }
    }

    /** Anel de pontinhos (um a cada ~1,5 px de arco). */
    private static void ring(GuiGraphics g, int cx, int cy, float r, int color, int size) {
        int n = Mth.clamp(Math.round(Mth.TWO_PI * r / 1.5F), 24, 900);
        for (int i = 0; i < n; i++) {
            float a = i / (float) n * Mth.TWO_PI;
            int x = Math.round(cx + Mth.cos(a) * r), y = Math.round(cy + Mth.sin(a) * r);
            g.fill(x, y, x + size, y + size, color);
        }
    }

    /** Polígono regular de {@code sides} lados (traço de 1 px). */
    private static void polygon(GuiGraphics g, int cx, int cy, float r, int sides, float rotDeg, int color) {
        for (int i = 0; i < sides; i++) {
            float a0 = (rotDeg + i * 360F / sides) * Mth.DEG_TO_RAD, a1 = (rotDeg + (i + 1) * 360F / sides) * Mth.DEG_TO_RAD;
            line(g, cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, color);
        }
    }

    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int color) {
        int steps = Math.max(1, Math.round(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
        for (int i = 0; i <= steps; i += 1) {
            int x = Math.round(x0 + (x1 - x0) * i / steps), y = Math.round(y0 + (y1 - y0) * i / steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private void glyphs(GuiGraphics g, Font font, int w, int h, float dt, float t, float fade) {
        for (int i = 0; i < GLYPHS; i++) {
            glyphY[i] -= glyphSpeed[i] * dt;
            if (glyphY[i] < 0.18F) resetGlyph(i, false);
            float life = Mth.clamp((1.05F - glyphY[i]) / 0.2F, 0F, 1F) * Mth.clamp((glyphY[i] - 0.18F) / 0.25F, 0F, 1F);
            int a = Math.round(0xA0 * life * fade);
            if (a < 6) continue;
            float x = glyphX[i] * w + Mth.sin(t * 0.8F + i) * 6F;
            float y = glyphY[i] * h;
            Component ch = Component.literal(String.valueOf(glyphChar[i])).withStyle(RUNES);
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(glyphScale[i], glyphScale[i], 1F);
            g.drawString(font, ch, -font.width(ch) / 2, -4, MahouUi.alpha(glyphColor[i], a), false);
            g.pose().popPose();
        }
    }

    private void motes(GuiGraphics g, int w, int h, float dt, float t, float fade) {
        for (int i = 0; i < MOTES; i++) {
            moteY[i] -= moteSpeed[i] * dt;
            if (moteY[i] < -0.02F) resetMote(i, false);
            float x = moteX[i] * w + Mth.sin(t * 0.9F + motePhase[i]) * 8F;
            float y = moteY[i] * h;
            float tw = 0.5F + 0.5F * Mth.sin(t * 3F + motePhase[i]);
            int a = Math.round(0xE0 * tw * fade * Mth.clamp(moteY[i] * 1.6F, 0F, 1F));
            if (a < 6) continue;
            int ix = Math.round(x), iy = Math.round(y);
            g.fill(ix - 1, iy - 1, ix + 2, iy + 2, MahouUi.alpha(moteColor[i], a / 5));
            g.fill(ix, iy, ix + 1, iy + 1, MahouUi.alpha(moteColor[i], a));
        }
    }
}
