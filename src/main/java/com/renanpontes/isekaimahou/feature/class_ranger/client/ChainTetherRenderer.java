package com.renanpontes.isekaimahou.feature.class_ranger.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.ChainTetherEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Desenha a CORRENTE CONJURADA com a textura da corrente do jogo: dois planos cruzados (como o bloco de corrente) em
 * gomos de 1 bloco, da mão do Ranger até o alvo. A corrente da prisão é prateada; a sagrada, dourada e brilhante.
 */
public class ChainTetherRenderer extends EntityRenderer<ChainTetherEntity> {
    private static final ResourceLocation CHAIN = ResourceLocation.withDefaultNamespace("textures/block/chain.png");
    /** Meia largura de cada plano (3 px do bloco = 0,1875). */
    private static final float HALF = 0.09375F;

    public ChainTetherRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ChainTetherEntity e) {
        return CHAIN;
    }

    private static Vec3 lerpPos(Entity e, float pt) {
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    private static Vec3[] ends(ChainTetherEntity e, float pt) {
        Entity owner = e.owner();
        Entity target = e.target();
        if (owner == null || target == null) return null;
        return new Vec3[] {owner.getRopeHoldPosition(pt), lerpPos(target, pt).add(0, target.getBbHeight() * 0.5, 0)};
    }

    @Override
    public boolean shouldRender(ChainTetherEntity e, Frustum frustum, double camX, double camY, double camZ) {
        Vec3[] ends = ends(e, 1F);
        return ends != null && frustum.isVisible(new AABB(ends[0], ends[1]).inflate(1.0));
    }

    @Override
    public void render(ChainTetherEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3[] ends = ends(e, pt);
        if (ends == null) return;
        Vec3 base = lerpPos(e, pt);
        Vec3 a = ends[0].subtract(base);
        Vec3 d = ends[1].subtract(ends[0]);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 dir = d.scale(1.0 / len);
        Vec3 s1 = dir.cross(new Vec3(0, 1, 0));
        if (s1.lengthSqr() < 1.0E-6) s1 = dir.cross(new Vec3(1, 0, 0));
        s1 = s1.normalize().scale(HALF);
        Vec3 s2 = dir.cross(s1).normalize().scale(HALF);
        boolean holy = e.holy();
        float r = 1F, g = holy ? 0.86F : 1F, b = holy ? 0.45F : 1F;
        int lit = holy ? LightTexture.FULL_BRIGHT : light;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CHAIN));
        PoseStack.Pose p = pose.last();
        int segs = Math.min(64, (int) Math.ceil(len));
        for (int i = 0; i < segs; i++) {
            double t0 = i / len, t1 = Math.min(len, i + 1.0) / len;
            float v1 = (float) ((t1 - t0) * len);
            Vec3 p0 = a.add(d.scale(t0)), p1 = a.add(d.scale(t1));
            quad(vc, p, p0, p1, s1, 0F, 3F / 16F, v1, r, g, b, lit);
            quad(vc, p, p0, p1, s2, 3F / 16F, 6F / 16F, v1, r, g, b, lit);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, Vec3 p0, Vec3 p1, Vec3 side, float u0, float u1, float v1, float r, float g,
                             float b, int light) {
        vert(vc, p, p0.subtract(side), u0, 0F, r, g, b, light);
        vert(vc, p, p0.add(side), u1, 0F, r, g, b, light);
        vert(vc, p, p1.add(side), u1, v1, r, g, b, light);
        vert(vc, p, p1.subtract(side), u0, v1, r, g, b, light);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose p, Vec3 v, float u, float vv, float r, float g, float b, int light) {
        vc.addVertex(p, (float) v.x, (float) v.y, (float) v.z).setColor(r, g, b, 1F).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, 0F, 1F, 0F);
    }
}
