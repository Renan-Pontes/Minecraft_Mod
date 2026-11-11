package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.BallLightningEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Bola de Raio: núcleo branco-azulado pulsante, casca de plasma, filamentos de raio estalando na superfície (mudam a
 * cada 2 ticks) e, quando dispara, relâmpagos quebrados até cada alvo atingido.
 */
public class BallLightningRenderer extends EntityRenderer<BallLightningEntity> {
    public BallLightningRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(BallLightningEntity e, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(e.getBoundingBox().inflate(8));
    }

    @Override
    public void render(BallLightningEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float r = e.scale() * e.openness(pt, 5);
        if (r <= 0.01F) return;
        VertexConsumer glow = FcQuads.additive(buffers, FcQuads.GLOW);
        ps.pushPose();
        // Casca de plasma (esfera com ruído rolando)
        FcQuads.sphere(ps, FcQuads.glow(buffers, FcQuads.NOISE), r * 0.85F, r * 0.85F, 0, 0, 2, 1, t * 0.08F, 0x9FE8FF, 0.55F, 8, 14);
        // Filamentos estalando na superfície
        long seed = e.getId() * 977L + e.tickCount / 2;
        java.util.Random rnd = new java.util.Random(seed);
        for (int i = 0; i < 5; i++) {
            Vec3 a = randomOnSphere(rnd).scale(r * 0.3);
            Vec3 b = randomOnSphere(rnd).scale(r * (1.1 + rnd.nextDouble() * 0.6));
            FcQuads.bolt(ps, glow, a, b, seed + i, 0.025F, 0x7FDBFF, 0.9F);
        }
        // Raios até os alvos do último disparo (somem em 6 ticks)
        float since = e.age() + pt - e.zapAge();
        if (since >= 0 && since < 6) {
            float fade = 1F - since / 6F;
            Vec3 me = e.getPosition(pt);
            for (int id : e.zapTargets()) {
                if (id < 0) continue;
                Entity target = e.level().getEntity(id);
                if (target == null) continue;
                Vec3 to = target.getPosition(pt).add(0, target.getBbHeight() * 0.5, 0).subtract(me);
                FcQuads.bolt(ps, glow, Vec3.ZERO, to, seed * 31 + id, 0.06F, 0x7FDBFF, fade);
            }
        }
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        float pulse = 1F + 0.15F * Mth.sin(t * 1.3F);
        FcQuads.billboard(ps, glow, r * 1.9F * pulse, t * 7, 0x3FA9FF, 0.75F);
        FcQuads.billboard(ps, glow, r * 0.9F * pulse, -t * 11, 0xFFFFFF, 1F);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    private static Vec3 randomOnSphere(java.util.Random rnd) {
        double u = rnd.nextDouble() * 2 - 1, th = rnd.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
        return new Vec3(s * Math.cos(th), u, s * Math.sin(th));
    }

    @Override
    public ResourceLocation getTextureLocation(BallLightningEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
