package com.renanpontes.isekaimahou.client.stack;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.stack.StackPayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Letreiro "CONTRAMÁGICA!" no alto da tela (para quem está a até 48 blocos de uma contramágica): faixa escura com
 * rachaduras de vidro, título que "estoura" e treme, quem contrariou o quê, e o aviso de chefe atordoado.
 * Várias contramágicas seguidas viram "×N".
 */
public final class StackBanner implements LayeredDraw.Layer {
    private static final int DURATION = 46;
    private static final int CRACKS = 7;

    private static double start = -1000;
    private static int count;
    private static Component sub = Component.empty();
    @Nullable private static Component extra;
    private static int rgb = 0xB98CFF;
    private static boolean ultimate;
    private static long seed;

    static void trigger(StackPayloads.Countered p) {
        double now = ClientStack.ticks();
        count = now - start < 10 ? count + 1 : 1;
        start = now;
        rgb = p.rgb() & 0xFFFFFF;
        ultimate = p.tier() == SpellStack.Tier.ULTIMATE.ordinal();
        seed = p.id() * 31L + (long) (p.x() * 7) + (long) (p.z() * 13);
        boolean hasBy = (p.flags() & StackPayloads.FX_HAS_BY) != 0;
        sub = hasBy
                ? Component.translatable("stack.isekai_mahou.banner.sub", p.byName(), p.spellName(), p.casterName())
                : Component.translatable("stack.isekai_mahou.banner.sub_noby", p.spellName(), p.casterName());
        extra = (p.flags() & StackPayloads.FX_STUNNED) != 0
                ? Component.translatable(((p.flags() & StackPayloads.FX_BOSS) != 0)
                        ? "stack.isekai_mahou.banner.boss_stunned" : "stack.isekai_mahou.banner.stunned", p.casterName())
                : null;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, ultimate ? 0.6F : 0.9F, 0.8F));
    }

    static void clear() {
        start = -1000;
        count = 0;
        extra = null;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        double now = ClientStack.ticks() + (mc.isPaused() ? 0 : dt.getGameTimeDeltaPartialTick(false));
        float age = (float) (now - start);
        if (age < 0 || age >= DURATION) return;

        float in = Mth.clamp(age / 3F, 0F, 1F);
        float out = Mth.clamp((DURATION - age) / 10F, 0F, 1F);
        float a = in * out;
        if (a <= 0.03F) return;
        Font font = mc.font;
        int gw = g.guiWidth(), gh = g.guiHeight();
        int cx = gw / 2;
        int cy = Math.round(gh * 0.27F);
        int accent = 0xFF000000 | rgb;
        int bandW = Math.min(gw - 20, 360);
        int bandH = extra != null ? 50 : 40;
        int top = cy - 14;
        // HudLayout: a faixa não passa por cima do minimapa (canto superior direito) — encolhe simétrica até a borda dele
        int limit = HudLayout.rightLimit(top, top + bandH);
        bandW = Math.min(bandW, Math.max(160, 2 * (limit - cx)));

        band(g, cx, top, bandW, bandH, a, accent);
        cracks(g, cx, top + bandH / 2, bandW, bandH, age, a);

        // Título: estoura (escala), treme nos primeiros ticks
        Component title = Component.translatable("stack.isekai_mahou.banner").withStyle(ChatFormatting.BOLD);
        if (count > 1) title = title.copy().append(Component.literal(" ×" + count));
        float pop = Math.max(0F, 1F - age / 5F);
        float scale = 2.0F + 0.6F * pop * pop;
        float shake = Math.max(0F, 6F - age) * 0.45F;
        RandomSource r = RandomSource.create(seed + (long) age);
        float jx = (r.nextFloat() - 0.5F) * 2F * shake, jy = (r.nextFloat() - 0.5F) * 2F * shake;
        int tw = font.width(title);
        int ai = Math.max(5, Math.round(a * 255));
        int glow = MahouUi.mix(0xFFFFFFFF, accent, 0.35F + 0.25F * StackHud.pulse(500));
        g.pose().pushPose();
        g.pose().translate(cx + jx, top + 6 + jy, 0);
        g.pose().scale(scale, scale, 1F);
        int outline = MahouUi.alpha(MahouUi.darken(accent, 0.75F), ai);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox != 0 || oy != 0) g.drawString(font, title, -tw / 2 + ox, oy, outline, false);
            }
        }
        g.drawString(font, title, -tw / 2, 0, MahouUi.alpha(glow, ai), false);
        g.pose().popPose();

        int sy = top + 6 + Math.round(9 * scale) + 3;
        g.drawCenteredString(font, sub, cx, sy, MahouUi.alpha(MahouUi.TEXT, ai));
        if (extra != null) {
            g.drawCenteredString(font, extra, cx, sy + 11, MahouUi.alpha(MahouUi.GOLD, ai));
        }
    }

    /** Faixa escura que some nas pontas, com fios dourados em cima e embaixo. */
    private static void band(GuiGraphics g, int cx, int top, int w, int h, float a, int accent) {
        int slices = 24;
        int sw = Math.max(1, w / slices);
        for (int i = 0; i < slices; i++) {
            int x = cx - w / 2 + i * sw;
            float edge = 1F - Math.abs((i + 0.5F) / slices * 2F - 1F);
            float k = Mth.clamp(edge * 1.8F, 0F, 1F) * a;
            g.fillGradient(x, top, x + sw, top + h, MahouUi.alpha(0xFF120A1E, Math.round(k * 0xC8)),
                    MahouUi.alpha(0xFF07050C, Math.round(k * 0xB0)));
            int line = MahouUi.alpha(ultimate ? accent : MahouUi.GOLD, Math.round(k * 0xD0));
            g.fill(x, top, x + sw, top + 1, line);
            g.fill(x, top + h - 1, x + sw, top + h, line);
        }
    }

    /** Rachaduras de vidro saindo do centro (crescem nos primeiros ticks). */
    private static void cracks(GuiGraphics g, int cx, int cy, int w, int h, float age, float a) {
        float grow = Mth.clamp(age / 4F, 0F, 1F);
        int color = MahouUi.alpha(0xFFFFFFFF, Math.round(a * 0x70));
        for (int k = 0; k < CRACKS; k++) {
            // Uma semente por rachadura: o desenho não "pula" enquanto ela cresce
            RandomSource r = RandomSource.create(seed * 31 + k);
            double ang = (k + r.nextFloat() * 0.6) / CRACKS * Math.PI * 2;
            float len = (w * 0.28F + r.nextFloat() * w * 0.2F) * grow;
            float px = cx, py = cy;
            float walked = 0;
            while (walked < len) {
                ang += (r.nextFloat() - 0.5F) * 0.9F;
                float step = 5F + r.nextFloat() * 6F;
                float nx = px + (float) Math.cos(ang) * step;
                float ny = py + (float) Math.sin(ang) * step * 0.35F;
                ny = Mth.clamp(ny, cy - h / 2F + 1, cy + h / 2F - 2);
                segment(g, Math.round(px), Math.round(py), Math.round(nx), Math.round(ny), color);
                px = nx;
                py = ny;
                walked += step;
            }
        }
    }

    private static void segment(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int px = x0 + (steps == 0 ? 0 : Math.round((x1 - x0) * i / (float) steps));
            int py = y0 + (steps == 0 ? 0 : Math.round((y1 - y0) * i / (float) steps));
            g.fill(px, py, px + 1, py + 1, color);
        }
    }
}
