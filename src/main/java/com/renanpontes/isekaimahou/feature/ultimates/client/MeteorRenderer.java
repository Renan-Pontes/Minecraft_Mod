package com.renanpontes.isekaimahou.feature.ultimates.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ultimates.entity.MeteorEntity;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Meteoro: a rocha GeckoLib (rachaduras brilhando pelo _glowmask), escalada pelo tamanho e virada na direção da queda,
 * envolta num halo de calor e com um rastro de fogo em fita (sempre de frente para a câmera) que cresce com a rocha.
 */
public class MeteorRenderer extends GeoEntityRenderer<MeteorEntity> {
    public MeteorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("meteor")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0F;
    }

    @Override
    public void render(MeteorEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        // A rocha está em brasa: sempre iluminada
        super.render(entity, entityYaw, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT);
        if (entity.progress(partialTick) >= 1.02F) return;
        drawHeat(entity, partialTick, poseStack, buffers);
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, MeteorEntity animatable,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float s = animatable.size();
        super.scaleModelForRender(widthScale * s, heightScale * s, poseStack, animatable, model, isReRender, partialTick,
                packedLight, packedOverlay);
    }

    @Override
    protected void applyRotations(MeteorEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick,
                                  float nativeScale) {
        Vec3 d = entity.direction();
        float yaw = (float) (Mth.atan2(d.x, d.z) * Mth.RAD_TO_DEG);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
    }

    /** Halo de calor + fita de fogo para trás (direção oposta à queda). */
    private void drawHeat(MeteorEntity entity, float pt, PoseStack ps, MultiBufferSource buffers) {
        Camera cam = this.entityRenderDispatcher.camera;
        if (cam == null) return;
        float s = entity.size();
        int fire = entity.color();
        Vec3 pos = new Vec3(Mth.lerp(pt, entity.xo, entity.getX()), Mth.lerp(pt, entity.yo, entity.getY()),
                Mth.lerp(pt, entity.zo, entity.getZ()));
        Vec3 toCam = cam.getPosition().subtract(pos);
        Vec3 back = entity.direction().scale(-1);
        float time = entity.tickCount + pt;

        ps.pushPose();
        Matrix4f m = ps.last().pose();

        // Halo de calor (quad de frente para a câmera)
        VertexConsumer glow = buffers.getBuffer(UltRenderTypes.additive(UltRenderTypes.GLOW_TEX));
        float hr = s * (1.9F + 0.15F * Mth.sin(time * 0.9F));
        billboard(glow, m, cam, Vec3.ZERO, hr, UltRenderTypes.argb(fire, 0.9F));
        billboard(glow, m, cam, Vec3.ZERO, hr * 0.55F, UltRenderTypes.argb(0xFFF2C0, 0.8F));

        // Rastro: duas fitas (larga laranja, estreita quase branca) com a textura de fogo correndo
        VertexConsumer tail = buffers.getBuffer(UltRenderTypes.additive(UltRenderTypes.FIRE_TAIL));
        float len = 5F + 4F * s;
        float flicker = 1F + 0.08F * Mth.sin(time * 1.7F);
        ribbon(tail, m, back, toCam, len, s * 1.3F * flicker, UltRenderTypes.argb(fire, 0.95F));
        ribbon(tail, m, back, toCam, len * 0.7F, s * 0.6F * flicker, UltRenderTypes.argb(0xFFE8A0, 0.9F));
        ps.popPose();
    }

    private static void billboard(VertexConsumer vc, Matrix4f m, Camera cam, Vec3 c, float r, int argb) {
        org.joml.Vector3f l = cam.getLeftVector(), u = cam.getUpVector();
        float lx = l.x() * r, ly = l.y() * r, lz = l.z() * r, ux = u.x() * r, uy = u.y() * r, uz = u.z() * r;
        float x = (float) c.x, y = (float) c.y, z = (float) c.z;
        vc.addVertex(m, x - lx - ux, y - ly - uy, z - lz - uz).setUv(0, 1).setColor(argb);
        vc.addVertex(m, x - lx + ux, y - ly + uy, z - lz + uz).setUv(0, 0).setColor(argb);
        vc.addVertex(m, x + lx + ux, y + ly + uy, z + lz + uz).setUv(1, 0).setColor(argb);
        vc.addVertex(m, x + lx - ux, y + ly - uy, z + lz - uz).setUv(1, 1).setColor(argb);
    }

    /** Fita do ponto (0,0,0) até {@code dir·len}, larga {@code width} na cabeça, virada para a câmera. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 dir, Vec3 toCam, float len, float width, int argb) {
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1.0E-6) side = dir.cross(new Vec3(0, 1, 0.01));
        side = side.normalize();
        int seg = 6;
        for (int i = 0; i < seg; i++) {
            float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
            float w0 = width * (1F - t0 * 0.75F), w1 = width * (1F - t1 * 0.75F);
            Vec3 p0 = dir.scale(len * t0), p1 = dir.scale(len * t1);
            // A textura já some para a cauda; o "tremular" vem da largura que pulsa
            float v0 = t0, v1 = t1;
            int a0 = fade(argb, 1F - t0 * 0.9F), a1 = fade(argb, 1F - t1 * 0.9F);
            vc.addVertex(m, (float) (p0.x - side.x * w0), (float) (p0.y - side.y * w0), (float) (p0.z - side.z * w0)).setUv(0, v0).setColor(a0);
            vc.addVertex(m, (float) (p0.x + side.x * w0), (float) (p0.y + side.y * w0), (float) (p0.z + side.z * w0)).setUv(1, v0).setColor(a0);
            vc.addVertex(m, (float) (p1.x + side.x * w1), (float) (p1.y + side.y * w1), (float) (p1.z + side.z * w1)).setUv(1, v1).setColor(a1);
            vc.addVertex(m, (float) (p1.x - side.x * w1), (float) (p1.y - side.y * w1), (float) (p1.z - side.z * w1)).setUv(0, v1).setColor(a1);
        }
    }

    private static int fade(int argb, float k) {
        int a = (int) (((argb >>> 24) & 255) * Mth.clamp(k, 0F, 1F));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
