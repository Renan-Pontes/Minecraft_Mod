package com.renanpontes.isekaimahou.feature.legends_c.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.entity.VoidFieldEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Vazio Infinito por dentro: para quem está DENTRO do domínio, o interior vira cosmos — uma cúpula de céu estrelado
 * (shader do portal do End), nebulosas coloridas, uma faixa de galáxia girando e estrelas que cintilam. Quem está fora
 * só vê a cúpula do módulo domain.
 */
public class VoidFieldRenderer extends EntityRenderer<VoidFieldEntity> {
    private static final int[] NEBULA = {0x6A3BFF, 0x2F6BFF, 0xC04BFF, 0x3BC8FF, 0xFF5AC8};

    public VoidFieldRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(VoidFieldEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float k = e.presence(pt);
        if (k <= 0.01F) return;
        Vec3 pos = e.getPosition(pt);
        Vec3 cam = entityRenderDispatcher.camera.getPosition();
        float R = e.radius();
        if (cam.distanceToSqr(pos) > (double) R * R) return;
        float time = e.tickCount + pt;
        float dome = R * 0.96F;

        // Céu estrelado (a cúpula cresce do centro ao abrir e encolhe ao fechar)
        LcDraw.posSphere(buffers.getBuffer(RenderType.endPortal()), pose.last().pose(), dome * (0.3F + 0.7F * k), 16, 28);

        VertexConsumer g = buffers.getBuffer(LcRenderTypes.GLOW);
        RandomSource rnd = RandomSource.create(e.getId() * 4099L);
        // Nebulosas
        for (int i = 0; i < 9; i++) {
            Vec3 d = LcDraw.randomOnSphere(rnd);
            int c = NEBULA[i % NEBULA.length];
            float size = R * (0.25F + rnd.nextFloat() * 0.25F);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(time * 0.15F));
            Vec3 p = d.scale(dome * 0.92F * k);
            pose.translate(p.x, p.y, p.z);
            pose.mulPose(LcDraw.yTo(d));
            Matrix4f m = pose.last().pose();
            float breathe = 0.8F + 0.2F * Mth.sin(time * 0.05F + i);
            LcDraw.radialFlat(g, m, 0F, size * breathe, LcDraw.argb(LcDraw.lighten(c, 0.2F), 0.3F * k), LcDraw.argb(c, 0F), 24);
            LcDraw.radialFlat(g, m, 0F, size * 0.35F, LcDraw.argb(0xFFFFFF, 0.15F * k), LcDraw.argb(c, 0F), 16);
            pose.popPose();
        }
        // Faixa da galáxia girando
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(23F));
        pose.mulPose(Axis.YP.rotationDegrees(time * 0.4F));
        LcDraw.ringFlat(g, pose.last().pose(), dome * 0.85F * k, dome * 0.12F, 0xB9A8FF, 0.22F * k, 96);
        LcDraw.ringFlat(g, pose.last().pose(), dome * 0.85F * k, dome * 0.03F, 0xFFFFFF, 0.3F * k, 96);
        pose.popPose();
        // Estrelas que cintilam
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(time * 0.15F));
        for (int i = 0; i < 140; i++) {
            Vec3 d = LcDraw.randomOnSphere(rnd);
            float tw = 0.5F + 0.5F * Mth.sin(time * (0.1F + rnd.nextFloat() * 0.3F) + i);
            float s = 0.08F + rnd.nextFloat() * 0.22F + R * 0.004F;
            Vec3 p = d.scale(dome * 0.88F * k);
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            int c = rnd.nextInt(4) == 0 ? LcFx.BLUE_LIGHT : 0xFFFFFF;
            LcDraw.halo(g, pose.last().pose(), 0F, s * 0.3F, s * (1.5F + tw), c, (0.6F + 0.4F * tw) * k, 8);
            pose.popPose();
        }
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public boolean shouldRender(VoidFieldEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(VoidFieldEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
