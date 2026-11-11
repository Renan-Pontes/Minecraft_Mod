package com.renanpontes.isekaimahou.feature.legends_a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.legends_a.entity.CraterBlastEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A Explosão Colossal em 5 camadas, todas animadas pelo tempo de vida:
 * círculo mágico no chão que se expande · onda de choque (anel que corre pelo chão) · bola de fogo que nasce num
 * clarão e se dissolve · coluna de fogo que sobe (com uma "manga" de fumaça) · nuvem em cogumelo que cresce no topo,
 * passa de laranja incandescente a cinza e se desfaz.
 */
public class CraterBlastRenderer extends EntityRenderer<CraterBlastEntity> {

    private static final int HOT = 0xFFF2C0;
    private static final int FIRE = 0xFF7A1A;
    private static final int EMBER = 0xFF4A10;
    private static final int ASH_LIGHT = 0x8A7A70;
    private static final int ASH_DARK = 0x3A3434;

    public CraterBlastRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(CraterBlastEntity e, Frustum frustum, double camX, double camY, double camZ) {
        return e.shouldRender(camX, camY, camZ) && frustum.isVisible(e.getBoundingBoxForCulling());
    }

    @Override
    public void render(CraterBlastEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float R = e.radius();
        float H = e.columnHeight();
        int spell = e.color();
        int fire = LegendQuads.mix(FIRE, spell, 0.3F);

        // ---------- Círculo mágico no chão ----------
        if (t < 26) {
            float k = t / 26F;
            ps.pushPose();
            ps.translate(0, 0.06F, 0);
            LegendQuads.flat(ps, LegendQuads.glow(buffers, LegendQuads.SIGIL), R * (1.15F + 0.5F * easeOut(k)), t * 6, spell, 1 - k);
            ps.popPose();
        }

        // ---------- Onda de choque ----------
        if (t < 18) {
            float k = easeOut(t / 18F);
            float inner = R * (0.5F + 3.2F * k);
            float outer = inner + R * (0.35F + 0.4F * (1 - k));
            ps.pushPose();
            ps.translate(0, 0.12F, 0);
            LegendQuads.ring(ps, LegendQuads.glow(buffers, LegendQuads.SHOCKWAVE), inner, outer, LegendQuads.lighten(fire, 0.4F), 1 - t / 18F);
            ps.popPose();
        }

        // ---------- Bola de fogo ----------
        if (t < 32) {
            float grow = t < 6 ? easeOut(t / 6F) : 1.0F + 0.15F * (t - 6) / 26F;
            float rf = R * 1.15F * grow;
            float a = t < 8 ? 1.0F : Mth.clamp(1.0F - (t - 8) / 24F, 0, 1);
            ps.pushPose();
            ps.translate(0, rf * 0.35F, 0);
            LegendQuads.sphere(ps, LegendQuads.glow(buffers, LegendQuads.FIRE), rf, rf * 0.85F, t * 0.01F, -t * 0.03F, 3,
                    fire, HOT, a, a * 0.9F);
            // Núcleo branco do clarão
            if (t < 10) {
                float c = 1 - t / 10F;
                LegendQuads.sphere(ps, LegendQuads.glow(buffers, LegendQuads.FIRE), rf * 0.6F, rf * 0.55F, 0, -t * 0.05F, 2,
                        HOT, 0xFFFFFF, c, c);
            }
            ps.popPose();
            ps.pushPose();
            ps.translate(0, rf * 0.4F, 0);
            ps.mulPose(entityRenderDispatcher.cameraOrientation());
            LegendQuads.disc(ps, LegendQuads.additive(buffers, LegendQuads.GLOW), rf * 2.4F, 0, LegendQuads.lighten(fire, 0.3F), a * 0.8F, true);
            ps.popPose();
        }

        // ---------- Coluna de fogo + manga de fumaça ----------
        if (t > 2 && t < 100) {
            float h = H * easeOut(Mth.clamp((t - 2) / 22F, 0, 1));
            float a = t < 50 ? 0.95F : Mth.clamp(1.0F - (t - 50) / 50F, 0, 1) * 0.95F;
            VertexConsumer fireVc = LegendQuads.glow(buffers, LegendQuads.FIRE);
            LegendQuads.tube(ps, fireVc, R * 0.38F, R * 0.24F, 0, h, -t * 0.045F, 2, Math.max(1, h / (R * 1.2F)), fire, a, a * 0.8F);
            LegendQuads.tube(ps, fireVc, R * 0.2F, R * 0.12F, 0, h, -t * 0.07F, 1, Math.max(1, h / (R * 1.2F)), HOT, a, a * 0.7F);
            int smoke = LegendQuads.mix(ASH_LIGHT, ASH_DARK, Mth.clamp((t - 10) / 50F, 0, 1));
            LegendQuads.tube(ps, LegendQuads.glow(buffers, LegendQuads.SMOKE), R * 0.6F, R * 0.4F, 0, h * 0.95F, -t * 0.02F, 2,
                    Math.max(1, h / (R * 1.5F)), smoke, a * 0.55F, a * 0.35F);
        }

        // ---------- Nuvem em cogumelo ----------
        if (t > 8) {
            float k = easeOut(Mth.clamp((t - 8) / 36F, 0, 1));
            float rx = R * (0.45F + 1.1F * k);
            float ry = rx * 0.48F;
            float a = t < 70 ? 0.92F : Mth.clamp(1.0F - (t - 70) / 40F, 0, 1) * 0.92F;
            if (a > 0.01F) {
                float hot = Mth.clamp(1.0F - (t - 8) / 50F, 0, 1);
                int bottom = LegendQuads.mix(ASH_LIGHT, EMBER, hot);
                int top = LegendQuads.mix(ASH_DARK, fire, hot * 0.6F);
                float h = H * easeOut(Mth.clamp((t - 2) / 22F, 0, 1));
                ps.pushPose();
                ps.translate(0, h + ry * 0.2F, 0);
                LegendQuads.sphere(ps, LegendQuads.glow(buffers, LegendQuads.SMOKE), rx, ry, t * 0.004F, -t * 0.012F, 3,
                        bottom, top, a, a * 0.85F);
                // Barriga incandescente por baixo do cogumelo enquanto está quente
                if (hot > 0.05F) {
                    ps.translate(0, -ry * 0.25F, 0);
                    LegendQuads.sphere(ps, LegendQuads.glow(buffers, LegendQuads.FIRE), rx * 0.82F, ry * 0.7F, -t * 0.006F, t * 0.02F, 3,
                            HOT, fire, hot * a * 0.8F, 0);
                }
                ps.popPose();
            }
        }
        super.render(e, yaw, pt, ps, buffers, light);
    }

    private static float easeOut(float t) {
        t = Mth.clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    @Override
    public ResourceLocation getTextureLocation(CraterBlastEntity entity) {
        return LegendQuads.FIRE;
    }
}
