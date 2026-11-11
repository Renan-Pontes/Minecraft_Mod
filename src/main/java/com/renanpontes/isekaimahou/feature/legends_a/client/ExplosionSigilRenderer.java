package com.renanpontes.isekaimahou.feature.legends_a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.legends_a.entity.ExplosionSigilEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Círculos mágicos empilhados no céu (Explosão Colossal). O de baixo é o maior; cada um aparece com um "estalo"
 * conforme a conjuração avança e gira no sentido oposto ao vizinho. Ao soltar a magia: clarão branco, giro rápido e um
 * feixe de luz até o chão; interrompida: os círculos encolhem e se apagam.
 */
public class ExplosionSigilRenderer extends EntityRenderer<ExplosionSigilEntity> {

    public ExplosionSigilRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(ExplosionSigilEntity e, Frustum frustum, double camX, double camY, double camZ) {
        return e.shouldRender(camX, camY, camZ) && frustum.isVisible(e.getBoundingBoxForCulling());
    }

    @Override
    public void render(ExplosionSigilEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float age = e.tickCount + pt;
        byte state = e.state();
        int layers = Math.max(1, e.layers());
        float R = e.radius();
        int base = e.color();

        float fade = 1.0F;
        float flash = 0.0F;
        float spin = 1.0F;
        float shrink = 1.0F;
        float charge = e.chargeFraction(pt);
        if (state == ExplosionSigilEntity.RELEASED) {
            float t = e.stateAge(pt);
            charge = 1.0F;
            flash = Mth.clamp(1.0F - t / 8.0F, 0, 1);
            spin = 1.0F + 5.0F * Mth.clamp(1.0F - t / 25.0F, 0, 1);
            fade = Mth.clamp(1.0F - (t - 12.0F) / (ExplosionSigilEntity.RELEASE_TICKS - 12.0F), 0, 1);
            shrink = 1.0F + 0.15F * (1.0F - fade);
        } else if (state == ExplosionSigilEntity.FIZZLE) {
            float t = e.stateAge(pt) / ExplosionSigilEntity.FIZZLE_TICKS;
            fade = Mth.clamp(1.0F - t, 0, 1);
            shrink = Mth.clamp(1.0F - t * 0.7F, 0.05F, 1);
        }
        if (fade <= 0.01F) return;

        // Duas passadas (círculos, depois halos): cada camada usa UM buffer por vez — pegar outro buffer no meio
        // encerra o anterior no MultiBufferSource
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = pass == 0 ? LegendQuads.glow(buffers, LegendQuads.SIGIL) : LegendQuads.additive(buffers, LegendQuads.GLOW);
            for (int i = 0; i < layers; i++) {
                // Cada círculo surge numa fração da conjuração (o de baixo primeiro)
                float appearAt = i / (float) layers;
                float k = Mth.clamp((charge - appearAt) * layers * 1.5F, 0, 1);
                if (k <= 0) continue;
                float pop = k < 1 ? easeOutBack(k) : 1.0F;
                float r = R * (1.0F - 0.17F * i) * pop * shrink;
                float dir = i % 2 == 0 ? 1 : -1;
                float rot = age * (1.5F + i * 0.7F) * dir * spin;
                int rgb = LegendQuads.mix(base, LegendQuads.lighten(base, 0.55F), i / (float) Math.max(1, layers - 1));
                rgb = LegendQuads.mix(rgb, 0xFFFFFF, flash);
                float alpha = fade * (0.75F + 0.25F * Mth.sin(age * 0.3F + i)) * Math.min(1, k * 1.5F);
                ps.pushPose();
                ps.translate(0, i * ExplosionSigilEntity.GAP, 0);
                if (pass == 0) LegendQuads.flat(ps, vc, r, rot, rgb, alpha);
                else LegendQuads.flatBoth(ps, vc, r * 1.25F, -rot * 0.5F, rgb, 0.35F * alpha + 0.5F * flash * fade);
                ps.popPose();
            }
        }

        // Feixe de luz até o chão quando a magia é solta
        if (state == ExplosionSigilEntity.RELEASED) {
            float t = e.stateAge(pt);
            float beamA = Mth.clamp(1.0F - t / 20.0F, 0, 1) * fade;
            if (beamA > 0.01F) {
                int rgb = LegendQuads.lighten(base, 0.5F);
                float top = (layers - 1) * ExplosionSigilEntity.GAP + 1.0F;
                float w = R * 0.18F * (0.6F + 0.4F * beamA);
                VertexConsumer glow = LegendQuads.additive(buffers, LegendQuads.GLOW);
                LegendQuads.beam(ps, glow, w, -e.beam(), top, rgb, beamA);
                LegendQuads.beam(ps, glow, w * 0.4F, -e.beam(), top, 0xFFFFFF, beamA);
            }
        }
        super.render(e, yaw, pt, ps, buffers, light);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1;
        float x = t - 1;
        return 1 + c3 * x * x * x + c1 * x * x;
    }

    @Override
    public ResourceLocation getTextureLocation(ExplosionSigilEntity entity) {
        return LegendQuads.SIGIL;
    }
}
