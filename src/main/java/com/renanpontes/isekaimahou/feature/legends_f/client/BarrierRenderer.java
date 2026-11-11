package com.renanpontes.isekaimahou.feature.legends_f.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_f.entity.BarrierEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Barreira Defensiva Geral: cúpula de hexágonos de mana (textura repetida numa meia-esfera um pouco achatada embaixo),
 * com faixas de brilho correndo, borda acesa no chão e um clarão nos hexágonos a cada impacto. Conforme a resistência
 * cai, a cúpula pisca e fica mais fraca. Aparece crescendo e some esmaecendo.
 */
public class BarrierRenderer extends EntityRenderer<BarrierEntity> {
    public BarrierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public ResourceLocation getTextureLocation(BarrierEntity e) {
        return LfRenderTypes.HEX;
    }

    @Override
    public boolean shouldRender(BarrierEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(BarrierEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.age() + pt;
        float grow = LfDraw.smooth(age / 8F);
        float fadeOut = Mth.clamp((e.life() - age) / 10F, 0F, 1F);
        float r = e.radius() * (0.2F + 0.8F * grow);
        float hp = e.hpFraction();
        float hit = Mth.clamp(1F - (age - e.lastHit()) / 8F, 0F, 1F);
        float flicker = hp < 0.3F && ((int) (age * 0.7F)) % 3 == 0 ? 0.5F : 1F;
        float a = (0.28F + 0.2F * hp + 0.5F * hit) * fadeOut * flicker;
        int col = LfDraw.mix(0xFF6A6A, e.color(), Mth.clamp(hp * 1.3F, 0F, 1F));
        int bright = LfDraw.lighten(col, 0.6F);

        pose.pushPose();
        VertexConsumer hex = buffers.getBuffer(LfRenderTypes.additive(LfRenderTypes.HEX));
        float wave = age * 0.02F;
        final float aa = a;
        LfDraw.texSphere(hex, pose.last().pose(), r, 14, 28, 0.56F, Math.max(6F, r * 2.4F), Math.max(3F, r * 1.4F), wave,
                row -> LfDraw.argb(row % 4 == ((int) (age * 0.25F)) % 4 ? bright : col, aa * (row % 4 == ((int) (age * 0.25F)) % 4 ? 1.4F : 1F)),
                LfDraw.argb(col, a));
        pose.popPose();

        VertexConsumer glow = buffers.getBuffer(LfRenderTypes.GLOW);
        pose.pushPose();
        pose.translate(0, 0.05, 0);
        LfDraw.ringFlat(glow, pose.last().pose(), r, 0.15F, bright, 0.8F * fadeOut, 64);
        pose.mulPose(Axis.YP.rotationDegrees(age * 2F));
        LfDraw.ringFlat(glow, pose.last().pose(), r * 0.92F, 0.06F, col, 0.5F * fadeOut, 6);
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }
}
