package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.FusionShockwaveEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Ondas de choque das Misturas C: anel brilhante no chão + casca de esfera que se expande e some.
 * GELO (Zero Absoluto): um campo de ESPINHOS DE GELO de verdade (textura de gelo compactado) brota atrás da frente da
 * onda e derrete no fim. SOL: casca dourada ofuscante. SAGRADA: anel dourado com colunas de luz. VAPOR: nuvem branca.
 */
public class FusionShockwaveRenderer extends EntityRenderer<FusionShockwaveEntity> {
    private static final int SPIKE_RINGS = 6;

    public FusionShockwaveRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(FusionShockwaveEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        FusionShockwaveEntity.Kind kind = e.kind();
        float t = e.tickCount + pt;
        float p = e.progress(pt);
        float r = Math.max(0.1F, e.radius(pt));
        float fade = Mth.clamp((e.lifeLeft() - pt) / (e.maxLife() * 0.4F), 0F, 1F);
        int color = switch (kind) {
            case STEAM -> 0xF2F7FA;
            case FROST -> 0xA8E8FF;
            case SOLAR -> 0xFFC040;
            case HOLY -> 0xFFE9A8;
            case POLLEN -> 0xFFB040;
        };
        int core = switch (kind) {
            case STEAM -> 0xFFFFFF;
            case FROST -> 0xE8FBFF;
            case SOLAR -> 0xFFF4C8;
            case HOLY -> 0xFFFFFF;
            case POLLEN -> 0xFFE066;
        };
        ps.pushPose();
        ps.translate(0, 0.06, 0);
        // Anel da frente da onda
        VertexConsumer ring = FcQuads.additive(buffers, FcQuads.RING);
        float band = Math.max(0.6F, r * 0.22F);
        FcQuads.ring(ps, ring, Math.max(0, r - band), r, color, 0.95F * fade, 48);
        FcQuads.ring(ps, ring, Math.max(0, r - band * 0.4F), r + 0.15F, core, 0.6F * fade, 48);

        // Casca da esfera (domo), mais forte no começo
        float shell = (1F - p) * fade;
        if (shell > 0.02F) {
            VertexConsumer sv = FcQuads.glow(buffers, FcQuads.NOISE);
            float ry = kind == FusionShockwaveEntity.Kind.FROST ? r * 0.35F : r * 0.7F;
            FcQuads.sphere(ps, sv, r, ry, 0, 0, 2, 1, t * 0.02F, color, (kind == FusionShockwaveEntity.Kind.SOLAR ? 0.6F : 0.35F) * shell, 10, 20);
        }
        if (kind == FusionShockwaveEntity.Kind.SOLAR || kind == FusionShockwaveEntity.Kind.POLLEN) {
            ps.pushPose();
            ps.translate(0, r * 0.25F, 0);
            ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
            FcQuads.billboard(ps, FcQuads.additive(buffers, FcQuads.GLOW), r * 1.2F * (1 - p * 0.5F), t * 3, core, 0.9F * (1 - p));
            ps.popPose();
        }
        if (kind == FusionShockwaveEntity.Kind.HOLY) {
            VertexConsumer beam = FcQuads.additive(buffers, FcQuads.BEAM);
            for (int i = 0; i < 6; i++) {
                float a = i * Mth.TWO_PI / 6 + t * 0.05F;
                ps.pushPose();
                ps.translate(Mth.cos(a) * r, 0, Mth.sin(a) * r);
                FcQuads.beam(ps, beam, 0.25F, 3.5F * fade, -t * 0.05F, 2F, 0xFFF2C0, 0.8F * fade);
                ps.popPose();
            }
        }
        if (kind == FusionShockwaveEntity.Kind.FROST) spikes(e, ps, buffers, r, fade, light);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    /** Espinhos de gelo em anéis concêntricos: cada anel nasce quando a frente passa por ele. */
    private void spikes(FusionShockwaveEntity e, PoseStack ps, MultiBufferSource buffers, float r, float fade, int light) {
        TextureAtlasSprite ice = FcQuads.sprite("packed_ice");
        VertexConsumer vc = FcQuads.blocks(buffers);
        float maxR = e.scale();
        java.util.Random rnd = new java.util.Random(e.getId() * 31L);
        for (int k = 1; k <= SPIKE_RINGS; k++) {
            float rr = maxR * k / (SPIKE_RINGS + 0.5F);
            int n = 6 + k * 4;
            for (int i = 0; i < n; i++) {
                float a = (i + rnd.nextFloat() * 0.6F) * Mth.TWO_PI / n;
                float h = (0.9F + rnd.nextFloat() * 1.6F) * (1.2F - 0.08F * k);
                float lean = (rnd.nextFloat() - 0.5F) * 30F;
                float w = 0.28F + 0.12F * rnd.nextFloat();
                if (r < rr) continue;
                float grow = Mth.clamp((r - rr) / 1.5F, 0F, 1F) * fade;
                if (grow <= 0.02F) continue;
                ps.pushPose();
                ps.translate(Mth.cos(a) * rr, -0.1, Mth.sin(a) * rr);
                ps.mulPose(Axis.YP.rotation(-a));
                ps.mulPose(Axis.ZP.rotationDegrees(lean));
                shard(ps, vc, ice, w, h * grow, light);
                ps.popPose();
            }
        }
    }

    /** Pirâmide de 4 lados com a textura de gelo (espinho). */
    private static void shard(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float w, float h, int light) {
        PoseStack.Pose p = ps.last();
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1(), um = (u0 + u1) * 0.5F;
        int lit = LightTexture.pack(Math.max(LightTexture.block(light), 8), LightTexture.sky(light));
        float[][] base = {{-w, -w}, {w, -w}, {w, w}, {-w, w}};
        for (int i = 0; i < 4; i++) {
            float[] a = base[i], b = base[(i + 1) % 4];
            float nx = (a[0] + b[0]) * 0.5F, nz = (a[1] + b[1]) * 0.5F;
            FcQuads.litVertex(vc, p, a[0], 0, a[1], u0, v1, 0xDDF4FF, 220, lit, nx, 0.4F, nz);
            FcQuads.litVertex(vc, p, b[0], 0, b[1], u1, v1, 0xDDF4FF, 220, lit, nx, 0.4F, nz);
            FcQuads.litVertex(vc, p, 0, h, 0, um, v0, 0xFFFFFF, 235, lit, nx, 0.4F, nz);
            FcQuads.litVertex(vc, p, 0, h, 0, um, v0, 0xFFFFFF, 235, lit, nx, 0.4F, nz);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(FusionShockwaveEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
