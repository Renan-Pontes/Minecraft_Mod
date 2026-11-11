package com.renanpontes.isekaimahou.feature.forms_a.client;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
import com.renanpontes.isekaimahou.feature.forms_a.entity.SpellBeamEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Raio do Feixe, estilo farol: uma fita que gira em volta do próprio eixo para sempre encarar a câmera, em três
 * camadas — halo largo ADITIVO na cor da magia (brilha no escuro), núcleo translúcido claro com textura de energia
 * correndo do conjurador para o alvo, e um fio branco quente no meio — mais clarões nas duas pontas.
 * Abre em ~1 tick, pulsa de leve e afina até sumir no fim da vida da entidade. Sempre aceso (sem luz do mundo).
 * As texturas são em tons de cinza/branco para receberem a cor da magia.
 */
public class SpellBeamRenderer extends EntityRenderer<SpellBeamEntity> {
    private static final ResourceLocation CORE = IsekaiMahou.id("textures/entity/spell_beam.png");
    private static final ResourceLocation GLOW = IsekaiMahou.id("textures/entity/spell_beam_glow.png");
    private static final ResourceLocation FLARE = IsekaiMahou.id("textures/entity/spell_beam_flare.png");
    /** v1.1 (spellfx): anel de energia que corre pelo raio. */
    private static final ResourceLocation RING = IsekaiMahou.id("textures/entity/fx_beam_ring.png");
    private static final float RING_SPACING = 2.2F;
    /** Quantos blocos de raio uma repetição da textura cobre (a textura corre ao longo do raio). */
    private static final float TEXTURE_BLOCKS = 2.0F;

    public SpellBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    protected int getBlockLightLevel(SpellBeamEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(SpellBeamEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        Vector3f e = entity.end();
        float len = e.length();
        if (len < 0.01F) return;

        // Vida: abre num instante, segura, depois afina até zero
        float age = entity.tickCount + partialTick;
        float life = Math.max(2, entity.life());
        float t = Mth.clamp(age / life, 0F, 1F);
        float open = Mth.clamp(age / 1.2F, 0F, 1F);
        float fade = t < 0.4F ? 1F : 1F - (t - 0.4F) / 0.6F;
        fade = fade * fade * (3F - 2F * fade);
        if (fade <= 0.01F) return;
        float pulse = 1F + 0.08F * Mth.sin(age * 2.6F);
        float w = entity.width() * (0.45F + 0.55F * open) * (0.3F + 0.7F * fade) * pulse;
        int rgb = entity.color() & 0xFFFFFF;

        // Lado da fita: perpendicular ao raio e à linha até a câmera (a fita sempre "de frente")
        Vec3 dir = new Vec3(e.x, e.y, e.z);
        Vec3 base = entity.getPosition(partialTick);
        Vec3 toCam = this.entityRenderDispatcher.camera.getPosition().subtract(base.add(dir.scale(0.5)));
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1.0E-6) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6) side = new Vec3(1, 0, 0);
        side = side.normalize();

        // A energia corre da saída para a chegada
        float v0 = -age * 0.35F;
        float v1 = v0 + len / TEXTURE_BLOCKS;
        PoseStack.Pose p = pose.last();

        // 1) Halo largo aditivo
        ribbon(buffers.getBuffer(RenderType.eyes(GLOW)), p, dir, side, w * 2.6F, 0xFF000000 | scale(rgb, 0.8F * fade), v0 * 0.6F, v1 * 0.6F);
        // 2) Núcleo translúcido, um pouco mais claro que a cor da magia (de dia o halo aditivo some; o núcleo segura a cor)
        int coreA = (int) (235 * fade) << 24;
        ribbon(buffers.getBuffer(RenderType.beaconBeam(CORE, true)), p, dir, side, w, coreA | FormsFx.lighten(rgb, 0.3F), v0, v1);
        // 3) Fio branco quente no centro
        ribbon(buffers.getBuffer(RenderType.eyes(GLOW)), p, dir, side, w * 0.4F, 0xFF000000 | scale(0xFFFFFF, 0.75F * fade), v0 * 1.4F, v1 * 1.4F);

        // 4) Anéis de energia correndo da saída para a chegada (raios grossos: Expandir/Amplificar)
        if (entity.width() >= 0.2F && len > 1.5F) rings(buffers, pose, dir, len, w, rgb, fade, age);

        // Clarões nas pontas (girando): a saída um pouco menor que o ponto de chegada
        VertexConsumer flare = buffers.getBuffer(RenderType.eyes(FLARE));
        flare(flare, pose, 0F, 0F, 0F, w * 3.4F, age * 9F, 0xFF000000 | scale(FormsFx.lighten(rgb, 0.3F), fade));
        flare(flare, pose, e.x, e.y, e.z, w * 4.6F, -age * 7F, 0xFF000000 | scale(FormsFx.lighten(rgb, 0.3F), fade));

        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    /** Anéis perpendiculares ao raio, a cada {@link #RING_SPACING} blocos, deslizando para a frente e girando. */
    private static void rings(MultiBufferSource buffers, PoseStack pose, Vec3 dir, float len, float w, int rgb, float fade, float age) {
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(RING));
        Vec3 d = dir.normalize();
        float yRot = (float) Mth.atan2(d.x, d.z);
        float xRot = (float) -Math.asin(Mth.clamp(d.y, -1, 1));
        float offset = (age * 0.45F) % RING_SPACING;
        for (float t = offset; t < len - 0.3F; t += RING_SPACING) {
            float edge = Mth.clamp(Math.min(t, len - t) / 1.2F, 0F, 1F);
            pose.pushPose();
            pose.translate(d.x * t, d.y * t, d.z * t);
            pose.mulPose(Axis.YP.rotation(yRot));
            pose.mulPose(Axis.XP.rotation(xRot));
            pose.mulPose(Axis.ZP.rotationDegrees(age * 12F + t * 40F));
            float s = w * 2.4F * (0.85F + 0.15F * Mth.sin(age * 0.8F + t));
            PoseStack.Pose p = pose.last();
            int argb = 0xFF000000 | scale(FormsFx.lighten(rgb, 0.35F), 0.7F * fade * edge);
            vertex(vc, p, argb, -s, -s, 0, 0, 1);
            vertex(vc, p, argb, s, -s, 0, 1, 1);
            vertex(vc, p, argb, s, s, 0, 1, 0);
            vertex(vc, p, argb, -s, s, 0, 0, 0);
            vertex(vc, p, argb, -s, s, 0, 0, 0);
            vertex(vc, p, argb, s, s, 0, 1, 0);
            vertex(vc, p, argb, s, -s, 0, 1, 1);
            vertex(vc, p, argb, -s, -s, 0, 0, 1);
            pose.popPose();
        }
    }

    /** Fita retangular da origem até {@code dir}, larga {@code width}; desenhada nos dois sentidos (sem corte de face). */
    private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vec3 dir, Vec3 side, float width, int argb, float v0, float v1) {
        float sx = (float) side.x * width * 0.5F, sy = (float) side.y * width * 0.5F, sz = (float) side.z * width * 0.5F;
        float ex = (float) dir.x, ey = (float) dir.y, ez = (float) dir.z;
        // frente
        vertex(vc, pose, argb, -sx, -sy, -sz, 0, v0);
        vertex(vc, pose, argb, sx, sy, sz, 1, v0);
        vertex(vc, pose, argb, ex + sx, ey + sy, ez + sz, 1, v1);
        vertex(vc, pose, argb, ex - sx, ey - sy, ez - sz, 0, v1);
        // verso
        vertex(vc, pose, argb, ex - sx, ey - sy, ez - sz, 0, v1);
        vertex(vc, pose, argb, ex + sx, ey + sy, ez + sz, 1, v1);
        vertex(vc, pose, argb, sx, sy, sz, 1, v0);
        vertex(vc, pose, argb, -sx, -sy, -sz, 0, v0);
    }

    /** Quadrado virado para a câmera no ponto (x, y, z), girado {@code spin} graus (nos dois sentidos: sem corte de face). */
    private void flare(VertexConsumer vc, PoseStack pose, float x, float y, float z, float size, float spin, int argb) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.ZP.rotationDegrees(spin));
        pose.scale(size, size, size);
        PoseStack.Pose p = pose.last();
        vertex(vc, p, argb, -0.5F, -0.5F, 0, 0, 1);
        vertex(vc, p, argb, 0.5F, -0.5F, 0, 1, 1);
        vertex(vc, p, argb, 0.5F, 0.5F, 0, 1, 0);
        vertex(vc, p, argb, -0.5F, 0.5F, 0, 0, 0);
        vertex(vc, p, argb, -0.5F, 0.5F, 0, 0, 0);
        vertex(vc, p, argb, 0.5F, 0.5F, 0, 1, 0);
        vertex(vc, p, argb, 0.5F, -0.5F, 0, 1, 1);
        vertex(vc, p, argb, -0.5F, -0.5F, 0, 0, 1);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int argb, float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** Multiplica o brilho de uma cor RGB (para as camadas aditivas: mais escuro = mais transparente). */
    private static int scale(int rgb, float f) {
        f = Mth.clamp(f, 0F, 1F);
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public ResourceLocation getTextureLocation(SpellBeamEntity entity) {
        return CORE;
    }
}
