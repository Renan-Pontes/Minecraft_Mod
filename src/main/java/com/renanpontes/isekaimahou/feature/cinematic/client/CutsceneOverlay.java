package com.renanpontes.isekaimahou.feature.cinematic.client;

import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Parte 2D das cutscenes: faixas de cinema, vinheta, clarão inicial, título grande com subtítulo e o aviso de "pular".
 * Duas camadas: "cinematic_bars" (por BAIXO do HUD: quem não está travado continua vendo a hotbar)
 * e "cinematic_overlay" (por cima de tudo: clarão e título).
 */
final class CutsceneOverlay {

    /** Camada de baixo: vinheta + faixas pretas. */
    static void renderBars(GuiGraphics g, DeltaTracker delta) {
        CutsceneDirector.Scene s = CutsceneDirector.current();
        if (s == null) return;
        float time = s.time(partial(delta));
        StyleOverlay.renderUnder(g, s, time);   // v1.1: escuridão, relógio, linhas de velocidade... (por baixo das faixas)
        float open = CutsceneDirector.barsOpen(s, time);
        if (open <= 0.001F) return;
        int w = g.guiWidth(), h = g.guiHeight();

        CinematicDraw.vignette(g, 0.6F * open);
        float maxBar = h * (s.ownerView ? 0.12F : 0.085F);
        int bar = Math.round(maxBar * CinematicDraw.easeOut(open));
        if (bar <= 0) return;
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        // brilho da cor da cena na borda interna das faixas
        int glow = CinematicDraw.argb(s.rgb, 0.35F * open), clear = CinematicDraw.argb(s.rgb, 0F);
        g.fillGradient(0, bar, w, bar + 6, glow, clear);
        g.fillGradient(0, h - bar - 6, w, h - bar, clear, glow);
        CinematicDraw.fillGradientH(g, 0, bar - 1, w / 2F, bar, clear, CinematicDraw.argb(s.rgb, 0.8F * open));
        CinematicDraw.fillGradientH(g, w / 2F, bar - 1, w, bar, CinematicDraw.argb(s.rgb, 0.8F * open), clear);
        CinematicDraw.fillGradientH(g, 0, h - bar, w / 2F, h - bar + 1, clear, CinematicDraw.argb(s.rgb, 0.8F * open));
        CinematicDraw.fillGradientH(g, w / 2F, h - bar, w, h - bar + 1, CinematicDraw.argb(s.rgb, 0.8F * open), clear);
    }

    /** Camada de cima: clarão, título, subtítulo e "pular". */
    static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        CutsceneDirector.Scene s = CutsceneDirector.current();
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float time = s.time(partial(delta));
        int w = g.guiWidth(), h = g.guiHeight();

        // Clarão inicial (branco puxado para a cor da cena)
        if (time < 8F && s.shot.flash() > 0F) {
            float k = 1F - time / 8F;
            float a = s.shot.flash() * k * k * mc.options.screenEffectScale().get().floatValue();
            if (a > 0.01F) g.fill(0, 0, w, h, CinematicDraw.argb(CinematicDraw.mix(0xFFFFFF, s.rgb, 0.25F), Math.min(0.9F, a)));
        }

        renderTitle(g, font, s, time, w, h);
        StyleOverlay.renderOver(g, s, time);    // v1.1: clarão das batidas e quadro de impacto

        // Aviso "[Enter] Pular" dentro da faixa de baixo
        float open = CutsceneDirector.barsOpen(s, time);
        if (!s.skipping && time > 8F && open > 0.5F && !CinematicKeys.SKIP.isUnbound()) {
            Component hint = Component.translatable("cinematic.isekai_mahou.skip_hint", CinematicKeys.SKIP.getTranslatedKeyMessage());
            FormattedCharSequence seq = hint.getVisualOrderText();
            float maxBar = h * (s.ownerView ? 0.12F : 0.085F) * CinematicDraw.easeOut(open);
            float a = Mth.clamp((time - 8F) / 8F, 0F, 1F) * 0.75F;
            CinematicDraw.shadowed(g, font, seq, w - font.width(seq) - 10, h - maxBar / 2F - 4, CinematicDraw.argb(0xC8C8C8, a));
        }
    }

    private static void renderTitle(GuiGraphics g, Font font, CutsceneDirector.Scene s, float time, int w, int h) {
        String title = s.title.getString();
        if (title.isEmpty()) return;
        boolean slam = s.choreo != null && s.choreo.slam;
        float start = s.choreo != null ? s.choreo.titleAt * s.total : Math.min(6F, s.total * 0.15F);
        float out = Mth.clamp((s.end - time - 1F) / 8F, 0F, 1F);
        float alpha = Mth.clamp((time - start) / (slam ? 2F : 6F), 0F, 1F) * out;
        if (alpha <= 0.02F) return;

        int[] cps = title.codePoints().toArray();
        float[] widths = new float[cps.length];
        float raw = 0;
        for (int i = 0; i < cps.length; i++) {
            widths[i] = font.width(FormattedCharSequence.forward(new String(Character.toChars(cps[i])), CinematicDraw.BOLD));
            raw += widths[i];
        }
        float intro = slam ? 1F : CinematicDraw.easeOut((time - start) / 14F);
        float spacing = Mth.lerp(intro, 7F, 0.6F);
        float total = raw + spacing * (cps.length - 1);
        // Marco da campanha: título menor (é uma crônica, não um grito de ultimate)
        boolean story = CutsceneDirector.isStory(s.kind);
        float scale = Math.min(story ? 2.2F : 3.2F, w * 0.86F / Math.max(1F, raw + 0.6F * (cps.length - 1)));
        float cx = w / 2F, cy = h * 0.64F;
        float halfH = 9 * scale / 2F;

        CinematicDraw.glow(g, cx, cy, (raw * scale) * 1.6F + 60, 9 * scale * 3.6F, s.rgb, 0.5F * alpha);

        // v1.1 — título que "bate" na tela (transformações, ultimates, JoJo): vem grande, encolhe num estalo, treme
        // um instante e tem uma pincelada de cor atrás
        float slamMul = 1F, jx = 0F, jy = 0F;
        if (slam) {
            float d = time - start;
            slamMul = 1F + 1.3F * (1F - CinematicDraw.easeOut(d / 3.5F));
            if (d < 5F) {
                float j = 3F * (1F - d / 5F);
                int seed = (int) (time * 2F);
                jx = (ScreenFx.hash(seed, 1) - 0.5F) * j;
                jy = (ScreenFx.hash(seed, 2) - 0.5F) * j;
            }
            float bw = (raw * scale + 90F) * CinematicDraw.easeOut(d / 4F) / 2F;
            int brush = CinematicDraw.argb(CinematicDraw.darken(s.rgb, 0.35F), 0.55F * alpha);
            int clearBrush = CinematicDraw.argb(CinematicDraw.darken(s.rgb, 0.35F), 0F);
            float by0 = cy - halfH * 1.15F, by1 = cy + halfH * 1.15F;
            CinematicDraw.fillGradientH(g, cx - bw, by0, cx, by1, clearBrush, brush);
            CinematicDraw.fillGradientH(g, cx, by0, cx + bw, by1, brush, clearBrush);
        }

        // Etiqueta do tipo de cena ("ULTIMATE", "CHEFE"...) com linhas e losangos
        Component kind = Component.translatableWithFallback("cinematic.isekai_mahou.kind." + s.base, s.base.toUpperCase(Locale.ROOT));
        FormattedCharSequence kindSeq = kind.getVisualOrderText();
        float kw = font.width(kindSeq);
        float ky = cy - halfH - 16;
        int kindRgb = CinematicDraw.mix(s.rgb, 0xFFFFFF, 0.35F);
        float lineLen = 40F * intro;
        CinematicDraw.shadowed(g, font, kindSeq, cx - kw / 2F, ky, CinematicDraw.argb(kindRgb, alpha));
        CinematicDraw.fadingLine(g, cx - kw / 2F - 10 - lineLen, cx - kw / 2F - 10, ky + 3.5F, 1F, s.rgb, alpha, true);
        CinematicDraw.fadingLine(g, cx + kw / 2F + 10, cx + kw / 2F + 10 + lineLen, ky + 3.5F, 1F, s.rgb, alpha, false);
        CinematicDraw.diamond(g, cx - kw / 2F - 6, ky + 4, 7, s.rgb, alpha);
        CinematicDraw.diamond(g, cx + kw / 2F + 6, ky + 4, 7, s.rgb, alpha);

        // Título letra a letra: as letras se aproximam e um reflexo atravessa o nome
        float band = Mth.lerp(Mth.clamp((time - start - 3F) / 18F, 0F, 1F), -0.3F, 1.3F);
        int outlineRgb = CinematicDraw.darken(s.rgb, 0.82F);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx + jx, cy + jy, 0F);
        ps.scale(scale * slamMul, scale * slamMul, 1F);
        float x = -total / 2F;
        float stagger = Math.min(0.6F, 12F / cps.length);   // nomes longos: cascata mais rápida
        for (int i = 0; i < cps.length; i++) {
            float f = (x + total / 2F + widths[i] / 2F) / Math.max(1F, total);
            float shine = Math.max(0F, 1F - Math.abs(f - band) / 0.13F);
            int rgb = CinematicDraw.mix(CinematicDraw.mix(s.rgb, 0xFFFFFF, 0.2F), 0xFFFFFF, shine * 0.85F);
            // cada letra cai de leve no lugar, em cascata, surgindo enquanto cai
            float land = slam ? 1F : (time - start - i * stagger) / 6F;
            float drop = (1F - CinematicDraw.easeOut(land)) * -6F;
            float letterAlpha = alpha * Mth.clamp(land * 1.5F + 0.15F, 0F, 1F);
            FormattedCharSequence ch = FormattedCharSequence.forward(new String(Character.toChars(cps[i])), CinematicDraw.BOLD);
            CinematicDraw.outlined(g, font, ch, x, -4.5F + drop, CinematicDraw.argb(rgb, letterAlpha),
                    CinematicDraw.argb(outlineRgb, letterAlpha));
            x += widths[i] + spacing;
        }
        ps.popPose();

        // Subtítulo com linhas que crescem do centro
        String sub = s.subtitle.getString();
        if (sub.isEmpty()) return;
        float subAlpha = Mth.clamp((time - start - 5F) / 6F, 0F, 1F) * out;
        if (subAlpha <= 0.02F) return;
        float subScale = story ? 1.1F : 1.35F;
        float sy = cy + halfH + 8;
        // Frase comprida (narração de um marco): quebra em linhas centradas em vez de sair da tela
        java.util.List<FormattedCharSequence> wrapped = font.split(s.subtitle, Math.max(80, (int) (w * 0.74F / subScale)));
        if (wrapped.size() > 1) {
            int textRgb = CinematicDraw.mix(s.rgb, 0xFFFFFF, 0.75F);
            float widest = 0F;
            for (FormattedCharSequence line : wrapped) widest = Math.max(widest, font.width(line) * subScale);
            float growWide = (widest / 2F + 20F) * CinematicDraw.easeOut((time - start - 5F) / 10F);
            CinematicDraw.fadingLine(g, cx - growWide, cx, sy - 3, 1F, s.rgb, 0.8F * subAlpha, true);
            CinematicDraw.fadingLine(g, cx, cx + growWide, sy - 3, 1F, s.rgb, 0.8F * subAlpha, false);
            for (int i = 0; i < wrapped.size() && i < 4; i++) {
                FormattedCharSequence line = wrapped.get(i);
                // cada linha surge um pouco depois da anterior (ritmo de leitura)
                float la = subAlpha * Mth.clamp((time - start - 5F - i * 8F) / 8F, 0F, 1F);
                if (la <= 0.02F) continue;
                ps.pushPose();
                ps.translate(cx - font.width(line) * subScale / 2F, sy + 1 + i * 11F * subScale, 0F);
                ps.scale(subScale, subScale, 1F);
                CinematicDraw.outlined(g, font, line, 0, 0, CinematicDraw.argb(textRgb, la), CinematicDraw.argb(0x000000, 0.55F * la));
                ps.popPose();
            }
            return;
        }
        FormattedCharSequence subSeq = s.subtitle.getVisualOrderText();
        float sw = font.width(subSeq) * subScale;
        float grow = (sw / 2F + 30F) * CinematicDraw.easeOut((time - start - 5F) / 10F);
        CinematicDraw.fadingLine(g, cx - grow, cx, sy - 3, 1F, s.rgb, 0.8F * subAlpha, true);
        CinematicDraw.fadingLine(g, cx, cx + grow, sy - 3, 1F, s.rgb, 0.8F * subAlpha, false);
        ps.pushPose();
        ps.translate(cx - sw / 2F, sy + 1, 0F);
        ps.scale(subScale, subScale, 1F);
        CinematicDraw.outlined(g, font, subSeq, 0, 0, CinematicDraw.argb(CinematicDraw.mix(s.rgb, 0xFFFFFF, 0.7F), subAlpha),
                CinematicDraw.argb(0x000000, 0.55F * subAlpha));
        ps.popPose();
    }

    private static float partial(DeltaTracker delta) {
        return delta.getGameTimeDeltaPartialTick(true);   // pausado: o timer congela a fração sozinho
    }

    private CutsceneOverlay() {}
}
