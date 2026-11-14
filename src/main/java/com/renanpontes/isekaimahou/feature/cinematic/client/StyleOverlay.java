package com.renanpontes.isekaimahou.feature.cinematic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * v1.1 — Camada 2D de cada estilo das cutscenes coreografadas (desenhada por baixo do título e do HUD):
 * escuridão com só o personagem iluminado, relógio, linhas de velocidade, calor, gelo, tom carmim do King Crimson,
 * tom rosa e ponteiros ao contrário do Bites the Dust... E, na camada de cima, o clarão e o quadro de impacto.
 */
final class StyleOverlay {

    /** Camada de baixo (junto das faixas de cinema). */
    static void renderUnder(GuiGraphics g, CutsceneDirector.Scene s, float time) {
        Choreo c = s.choreo;
        if (c == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2F, cy = h / 2F;
        float burstT = c.burstAt * s.total;
        float since = time - burstT;
        boolean before = since < 0F;
        float charge = before ? CinematicDraw.smooth(time / Math.max(1F, burstT)) : 1F;
        float out = Mth.clamp((s.end - time) / 6F, 0F, 1F) * Mth.clamp(time / 3F, 0F, 1F);
        float k = c.overlay * out;
        if (k <= 0.01F) return;
        float after = before ? 0F : 1F - Mth.clamp(since / 14F, 0F, 1F);   // 1 logo após a explosão, some em 14 ticks
        float minSide = Math.min(w, h);

        switch (c.look) {
            case HOLY -> {
                CinematicDraw.glow(g, cx, 0F, w * 1.2F, h * 0.9F, 0xFFE9A0, 0.45F * k * (before ? charge : 0.6F + 0.4F * after));
                if (!before) ScreenFx.radial(g, cx, cy, h * 0.35F, h * 0.95F, 0xFFF6D0, (0.15F + 0.25F * after) * k);
            }
            case DARK -> {
                // tudo escurece até quase preto; só o personagem (centro) fica iluminado. Na explosão, abre.
                float a = before ? 0.9F * charge : Mth.lerp(Mth.clamp(since / 6F, 0F, 1F), 0.9F, 0.4F);
                float r0 = before ? h * 0.16F : Mth.lerp(Mth.clamp(since / 6F, 0F, 1F), h * 0.16F, h * 0.32F);
                ScreenFx.radial(g, cx, cy, r0, r0 + h * 0.28F, 0x05000A, a * k);
                if (!before) ScreenFx.radial(g, cx, cy, h * 0.4F, h, s.rgb, 0.25F * k);
            }
            case TIME -> {
                ScreenFx.tint(g, 0x8C8C98, 0.36F * k);
                float alpha = (before ? 0.22F * charge : 0.38F) * k;
                ScreenFx.clock(g, cx, cy, h * 0.36F, CinematicDraw.mix(s.rgb, 0xFFFFFF, 0.5F), alpha,
                        -time * 0.35F, -time * 0.03F);
            }
            case SPEED -> {
                ScreenFx.radial(g, cx, cy, h * 0.5F, h * 1.1F, 0xDDE6F0, 0.18F * k);
            }
            case FIRE -> {
                ScreenFx.bands(g, time, 0xFF8A3A, 0.06F * k * (0.5F + 0.5F * charge), 26F, 10F, -1.6F);
                g.fillGradient(0, h / 2, w, h, CinematicDraw.argb(0xFF5A1A, 0F), CinematicDraw.argb(0xFF5A1A, 0.22F * k * charge));
                ScreenFx.radial(g, cx, cy, h * 0.42F, h * 1.0F, s.rgb, (0.2F + 0.2F * after) * k);
            }
            case BLOOD -> {
                float pulse = 0.5F + 0.5F * Mth.sin(time * 0.55F);
                ScreenFx.tint(g, 0x400008, 0.1F * k * charge);
                ScreenFx.radial(g, cx, cy, h * 0.3F, h * 0.85F, 0x7A0010, (0.3F + 0.2F * pulse + 0.2F * after) * k);
            }
            case ICE -> {
                ScreenFx.tint(g, 0xDDF4FF, 0.06F * k);
                ScreenFx.radial(g, cx, cy, h * 0.42F, h * 1.0F, 0xD8F4FF, (0.3F + 0.2F * after) * k * (0.5F + 0.5F * charge));
            }
            case STORM -> {
                ScreenFx.tint(g, 0x0A1430, 0.22F * k);
                ScreenFx.radial(g, cx, cy, h * 0.4F, h, 0x050A20, 0.35F * k);
            }
            case BEAST -> {
                float pulse = 0.5F + 0.5F * Mth.sin(time * 0.8F);
                ScreenFx.radial(g, cx, cy, h * 0.3F, h * 0.9F, 0x3A1408, (0.35F + 0.15F * pulse + 0.2F * after) * k);
            }
            case SPIRIT -> {
                ScreenFx.radial(g, cx, cy, h * 0.38F, h * 0.95F, s.rgb, (before ? 0.15F * charge : 0.3F + 0.2F * after) * k);
            }
            case WATER -> {
                ScreenFx.bands(g, time, 0x9AD8FF, 0.04F * k, 34F, 14F, 0.8F);
                ScreenFx.radial(g, cx, cy, h * 0.42F, h, s.rgb, (0.18F + 0.2F * after) * k);
            }
            case EARTH -> ScreenFx.radial(g, cx, cy, h * 0.38F, h * 0.95F, 0x2A1A0A, (0.3F + 0.2F * after) * k);
            case WIND -> ScreenFx.radial(g, cx, cy, h * 0.45F, h * 1.05F, 0xE0FFF4, (0.12F + 0.2F * after) * k);
            case NATURE -> ScreenFx.radial(g, cx, cy, h * 0.42F, h, 0x1E5A1A, (0.18F + 0.2F * after) * k);
            case DEFAULT, ARCANE -> ScreenFx.radial(g, cx, cy, h * 0.38F, h * 0.95F,
                    before ? 0x000000 : s.rgb, (before ? 0.3F * charge : 0.2F + 0.25F * after) * k);
            case DOMAIN -> {
                if (before) ScreenFx.radial(g, cx, cy, h * 0.3F, h * 0.85F, 0x000000, 0.4F * charge * k);
            }
            case KING_CRIMSON -> {
                float p = time / s.total;
                boolean frozen = p < 0.5F;
                ScreenFx.tint(g, 0x5A0010, (frozen ? 0.16F : 0.08F) * k);
                ScreenFx.radial(g, cx, cy, h * 0.22F, h * 0.85F, 0x8B0010, (frozen ? 0.7F : 0.45F) * k);
                if (frozen) {
                    int step = (int) (time / 4F);
                    // o "salto" do quadro: 1 tick mais escuro a cada pulo (suave)
                    if (time - step * 4F < 1F) ScreenFx.tint(g, 0x200004, 0.25F * k);
                    ScreenFx.glitch(g, (int) time, 0xC8102E, 0.35F * k, 6);
                }
            }
            case BITES_THE_DUST -> {
                ScreenFx.tint(g, 0xFF7FBF, 0.18F * k);
                ScreenFx.bands(g, time, 0xFFFFFF, 0.07F * k, 18F, 3F, -5F);
                ScreenFx.clock(g, cx, cy, h * 0.3F, 0xFFD0EA, 0.45F * k, -time * 0.9F, -time * 0.08F);
            }
        }

        // Linhas de velocidade de mangá: estouram na explosão; "speed"/"wind"/"spirit" mantêm um pouco
        if (CinematicSettings.speedLines() && c.look != Choreo.Look.DOMAIN && c.look != Choreo.Look.KING_CRIMSON) {
            float keep = switch (c.look) {
                case SPEED -> 0.45F;
                case WIND, SPIRIT -> 0.22F;
                default -> 0F;
            };
            float a = Math.max(after * 0.6F, keep * (before && c.look != Choreo.Look.SPEED ? 0F : 1F));
            if (c.look == Choreo.Look.BITES_THE_DUST) a = time < 6F ? 0.5F * (1F - time / 6F) : 0F;
            if (a > 0.01F) {
                int rgb = switch (c.look) {
                    case SPEED, WIND -> 0xF0F6FF;
                    case DARK -> 0x000000;
                    default -> CinematicDraw.mix(s.rgb, 0xFFFFFF, 0.55F);
                };
                ScreenFx.speedLines(g, cx, cy, minSide * 0.34F, rgb, a * k, (int) (time / 1.5F), 56);
            }
        }
    }

    /** Camada de cima (depois do título): clarão das batidas e quadro de impacto. */
    static void renderOver(GuiGraphics g, CutsceneDirector.Scene s, float time) {
        if (s.choreo == null) return;
        Minecraft mc = Minecraft.getInstance();
        float effects = mc.options.screenEffectScale().get().floatValue();
        if (s.flashAt >= 0F) {
            float d = time - s.flashAt;
            if (d >= 0F && d < 6F) {
                float f = 1F - d / 6F;
                float a = s.flashPow * f * f * Math.max(0.35F, effects);
                ScreenFx.tint(g, CinematicDraw.mix(0xFFFFFF, s.rgb, s.flashMix), Math.min(0.85F, a));
            }
        }
        if (s.impactAt >= 0F && CinematicSettings.impactFrames()) {
            float d = time - s.impactAt;
            if (d >= 0F && d < s.impactLen) {
                ScreenFx.invert(g, 0.85F * Math.min(1F, effects + 0.3F));
                if (s.impactRed) ScreenFx.tint(g, 0xC8102E, 0.3F);
            }
        }
    }

    private StyleOverlay() {}
}
