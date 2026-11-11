package com.renanpontes.isekaimahou.feature.class_ranger.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.BulletLineEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Desenha a LINHA DE BALA: durante a mira, um feixe vermelho fino e pulsante (luz aditiva, como um laser) dos olhos
 * do atirador até o alvo; no disparo, um traçante branco-dourado grosso que some em poucos ticks.
 */
public class BulletLineRenderer extends EntityRenderer<BulletLineEntity> {
    public BulletLineRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public ResourceLocation getTextureLocation(BulletLineEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    private static Vec3 start(BulletLineEntity e, float pt) {
        Entity owner = e.owner();
        if (owner != null && e.phase() == 0) return owner.getEyePosition(pt).subtract(0, 0.12, 0);
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) - 0.12, Mth.lerp(pt, e.zo, e.getZ()));
    }

    @Override
    public boolean shouldRender(BulletLineEntity e, Frustum frustum, double camX, double camY, double camZ) {
        return frustum.isVisible(new AABB(start(e, 1F), e.end()).inflate(0.5));
    }

    @Override
    public void render(BulletLineEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 base = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 a = start(e, pt).subtract(base);
        Vec3 b = e.end().subtract(base);
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.1) return;
        float time = e.tickCount + pt;
        boolean fired = e.phase() == 1;
        float w, r, g, bl, alpha;
        if (fired) {
            w = 0.07F;
            r = 1F;
            g = 0.95F;
            bl = 0.7F;
            alpha = 0.85F;
        } else {
            w = 0.018F + 0.008F * Mth.sin(time * 0.8F);
            r = 1F;
            g = 0.12F;
            bl = 0.12F;
            alpha = 0.75F;
        }
        Vec3 dir = d.scale(1.0 / len);
        Vec3 s1 = dir.cross(new Vec3(0, 1, 0));
        if (s1.lengthSqr() < 1.0E-6) s1 = dir.cross(new Vec3(1, 0, 0));
        s1 = s1.normalize().scale(w);
        Vec3 s2 = dir.cross(s1).normalize().scale(w);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        quad(vc, m, a, b, s1, r, g, bl, alpha);
        quad(vc, m, a, b, s2, r, g, bl, alpha);
        if (!fired) {
            // núcleo mais claro
            quad(vc, m, a, b, s1.scale(0.4), 1F, 0.6F, 0.6F, alpha);
        }
    }

    /** Quadrado dos dois lados (o tipo de render do raio descarta a face de trás). */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 s, float r, float g, float bl, float alpha) {
        Vec3[] v = {a.subtract(s), a.add(s), b.add(s), b.subtract(s)};
        for (int i = 0; i < 4; i++) vc.addVertex(m, (float) v[i].x, (float) v[i].y, (float) v[i].z).setColor(r, g, bl, alpha);
        for (int i = 3; i >= 0; i--) vc.addVertex(m, (float) v[i].x, (float) v[i].y, (float) v[i].z).setColor(r, g, bl, alpha);
    }
}
