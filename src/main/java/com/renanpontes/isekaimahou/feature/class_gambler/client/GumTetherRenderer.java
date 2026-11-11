package com.renanpontes.isekaimahou.feature.class_gambler.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.GumTetherEntity;

import net.minecraft.client.renderer.LightTexture;
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
 * Desenha a GOMA ELÁSTICA: uma fita rosa grossa (duas faixas cruzadas, como uma guia), da mão do dono até o alvo
 * (ou até o ponto grudado), que balança e cede no meio quando está frouxa e fica reta e fina quando contrai.
 */
public class GumTetherRenderer extends EntityRenderer<GumTetherEntity> {
    private static final int SEGMENTS = 24;

    public GumTetherRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public ResourceLocation getTextureLocation(GumTetherEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    private static Vec3 lerpPos(Entity e, float pt) {
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    /** As duas pontas (mão do dono, alvo/âncora) no mundo; null se o dono ainda não chegou ao cliente. */
    private static Vec3[] ends(GumTetherEntity e, float pt) {
        Entity owner = e.owner();
        if (owner == null) return null;
        Vec3 a = owner.getRopeHoldPosition(pt);
        Vec3 b;
        if (e.anchored()) {
            b = e.anchor();
        } else {
            Entity t = e.target();
            if (t == null) return null;
            b = lerpPos(t, pt).add(0, t.getBbHeight() * 0.5, 0);
        }
        return new Vec3[] {a, b};
    }

    @Override
    public boolean shouldRender(GumTetherEntity e, Frustum frustum, double camX, double camY, double camZ) {
        Vec3[] ends = ends(e, 1F);
        if (ends == null) return false;
        return frustum.isVisible(new AABB(ends[0], ends[1]).inflate(1.0));
    }

    @Override
    public void render(GumTetherEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3[] ends = ends(e, pt);
        if (ends == null) return;
        Vec3 base = lerpPos(e, pt);
        Vec3 a = ends[0].subtract(base);
        Vec3 b = ends[1].subtract(base);
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.05) return;
        int state = e.state();
        float time = e.tickCount + pt;
        double sag = state == 0 ? Math.min(0.7, len * 0.05) : 0.05;
        float width = state == 0 ? 0.06F : 0.04F;
        Vec3 dir = d.scale(1.0 / len);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(width, 0, 0) : side.normalize().scale(width);
        Vec3 up = new Vec3(0, width, 0);
        VertexConsumer vc = buffers.getBuffer(RenderType.leash());
        Matrix4f m = pose.last().pose();
        strip(vc, m, a, d, sag, side, time, state);
        strip(vc, m, a, d, sag, up, time, state);
    }

    private static void strip(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 d, double sag, Vec3 off, float time, int state) {
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = i / (float) SEGMENTS;
            double wob = state == 0 ? Math.sin(time * 0.25 + t * 9.0) * 0.05 * Math.sin(Math.PI * t) : 0;
            double x = a.x + d.x * t;
            double y = a.y + d.y * t - Math.sin(Math.PI * t) * sag + wob;
            double z = a.z + d.z * t + wob * 0.5;
            boolean light = i % 3 == 0;
            float r = 1.0F, g = light ? 0.66F : 0.43F, b = light ? 0.87F : 0.78F;
            // vértices repetidos nas pontas: triângulos vazios separam esta faixa das outras no mesmo buffer
            if (i == 0) vc.addVertex(m, (float) (x - off.x), (float) (y - off.y), (float) (z - off.z)).setColor(r, g, b, 1F).setLight(LightTexture.FULL_BRIGHT);
            vc.addVertex(m, (float) (x - off.x), (float) (y - off.y), (float) (z - off.z)).setColor(r, g, b, 1F).setLight(LightTexture.FULL_BRIGHT);
            vc.addVertex(m, (float) (x + off.x), (float) (y + off.y), (float) (z + off.z)).setColor(r, g, b, 1F).setLight(LightTexture.FULL_BRIGHT);
            if (i == SEGMENTS) vc.addVertex(m, (float) (x + off.x), (float) (y + off.y), (float) (z + off.z)).setColor(r, g, b, 1F).setLight(LightTexture.FULL_BRIGHT);
        }
    }
}
