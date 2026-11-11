package com.renanpontes.isekaimahou.feature.legends_c.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.entity.MalevolentShrineEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * O Santuário Maligno: modelo GeckoLib (×4 — ~12 blocos de altura) com as bocas/olhos/véu brilhando (glowmask) e,
 * por cima, a AURA vermelha desenhada: véu cilíndrico que pulsa, brilho no chão e riscos vermelhos subindo.
 */
public class ShrineRenderer extends GeoEntityRenderer<MalevolentShrineEntity> {
    public static final float SCALE = 4F;

    public ShrineRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("malevolent_shrine")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        withScale(SCALE);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(MalevolentShrineEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        // Entidade comum (não viva): o GeckoLib não gira pelo yaw — giramos nós (frente do modelo = olhar do dono)
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        super.render(e, yaw, pt, pose, buffers, light);
        pose.popPose();
        aura(e, pt, pose, buffers);
    }

    private void aura(MalevolentShrineEntity e, float pt, PoseStack pose, MultiBufferSource buffers) {
        float k = e.presence(pt);
        if (k <= 0.01F) return;
        float time = e.tickCount + pt;
        float pulse = 0.75F + 0.25F * Mth.sin(time * 0.15F);
        VertexConsumer g = buffers.getBuffer(LcRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        float r = 7.5F, h = 15F * k;
        int seg = 32;
        // Véu: cilindro vermelho que some no alto
        for (int j = 0; j < seg; j++) {
            float p0 = Mth.TWO_PI * j / seg, p1 = Mth.TWO_PI * (j + 1) / seg;
            float w0 = r * (1F + 0.05F * Mth.sin(time * 0.2F + j)), w1 = r * (1F + 0.05F * Mth.sin(time * 0.2F + j + 1));
            int cb = LcDraw.argb(LcFx.RED, 0.35F * k * pulse), ct = LcDraw.argb(LcFx.RED_DEEP, 0F);
            g.addVertex(m, Mth.cos(p0) * w0, 0, Mth.sin(p0) * w0).setColor(cb);
            g.addVertex(m, Mth.cos(p1) * w1, 0, Mth.sin(p1) * w1).setColor(cb);
            g.addVertex(m, Mth.cos(p1) * w1 * 0.8F, h, Mth.sin(p1) * w1 * 0.8F).setColor(ct);
            g.addVertex(m, Mth.cos(p0) * w0 * 0.8F, h, Mth.sin(p0) * w0 * 0.8F).setColor(ct);
        }
        // Brilho vermelho no chão
        pose.pushPose();
        pose.translate(0, 0.05, 0);
        Matrix4f gm = pose.last().pose();
        LcDraw.radialFlat(g, gm, 0F, r * 1.6F, LcDraw.argb(LcFx.RED, 0.45F * k * pulse), LcDraw.argb(LcFx.RED_DEEP, 0F), 40);
        LcDraw.ringFlat(g, gm, r * 1.2F, 0.5F, LcFx.RED, 0.7F * k, 48);
        pose.popPose();
        // Riscos vermelhos subindo (fumaça de energia maldita)
        RandomSource rnd = RandomSource.create(e.getId() * 31L);
        Vec3 camRel = entityRenderDispatcher.camera.getPosition().subtract(e.getPosition(pt));
        for (int i = 0; i < 18; i++) {
            float a = rnd.nextFloat() * Mth.TWO_PI;
            float rr = 3F + rnd.nextFloat() * 5F;
            float speed = 0.02F + rnd.nextFloat() * 0.03F;
            float ph = (time * speed + rnd.nextFloat()) % 1F;
            float y = ph * 16F;
            Vec3 p0 = new Vec3(Mth.cos(a) * rr, y, Mth.sin(a) * rr);
            Vec3 p1 = p0.add(0, 1.5 + rnd.nextFloat() * 2, 0);
            Vec3 side = p1.subtract(p0).cross(camRel.subtract(p0));
            side = side.lengthSqr() < 1.0E-8 ? new Vec3(1, 0, 0) : side.normalize();
            float al = Mth.sin(ph * Mth.PI) * 0.7F * k;
            LcDraw.softRibbon(g, m, p0, p1, side, 0.12F, 0.02F, LcFx.RED, al, 0F);
        }
    }

    @Override
    public boolean shouldRender(MalevolentShrineEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return e.shouldRenderAtSqrDistance(e.distanceToSqr(x, y, z));
    }
}
