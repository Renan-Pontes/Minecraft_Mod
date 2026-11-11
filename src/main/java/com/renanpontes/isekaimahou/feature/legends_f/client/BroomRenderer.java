package com.renanpontes.isekaimahou.feature.legends_f.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_f.entity.BroomEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Vassoura da Bruxa Viajante: cabo de madeira com veios, amarração de couro, uma cerda longa e farta que se abre em
 * leque (balançando com o vento) e uma fitinha na ponta. Inclina para onde a bruxa olha. Pisca no fim do tempo.
 */
public class BroomRenderer extends EntityRenderer<BroomEntity> {
    public BroomRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.4F;
    }

    @Override
    public ResourceLocation getTextureLocation(BroomEntity e) {
        return LfRenderTypes.GRAIN;
    }

    @Override
    public void render(BroomEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        int left = e.lifeLeft();
        if (left < 100 && (e.tickCount / 3) % 2 == 0) return;
        float ry = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float rx = Mth.lerp(pt, e.xRotO, e.getXRot());
        float time = e.tickCount + pt;
        pose.pushPose();
        pose.translate(0, 0.22 + 0.04 * Mth.sin(time * 0.1F), 0);
        pose.mulPose(Axis.YP.rotationDegrees(-ry));
        pose.mulPose(Axis.XP.rotationDegrees(rx));
        PoseStack.Pose p = pose.last();
        VertexConsumer wood = buffers.getBuffer(RenderType.entityCutoutNoCull(LfRenderTypes.GRAIN));
        int handle = 0xFF8A5A32, dark = 0xFF5A3418, straw = 0xFFE0C070, strawDark = 0xFFB89048, ribbon = 0xFFD84A8A;
        // Cabo (levemente torto, em 3 gomos)
        LfDraw.segment(wood, p, new Vec3(0, 0, -0.85), new Vec3(0, 0.01, -0.1), 0.045F, 0.042F, handle, light);
        LfDraw.segment(wood, p, new Vec3(0, 0.01, -0.1), new Vec3(0.01, 0.02, 0.55), 0.042F, 0.04F, handle, light);
        LfDraw.segment(wood, p, new Vec3(0.01, 0.02, 0.55), new Vec3(0, 0.05, 0.92), 0.04F, 0.035F, dark, light);
        LfDraw.cbox(wood, p, 0, 0.055F, 0.95F, 0.045F, 0.045F, 0.045F, dark, light);
        // Amarração
        LfDraw.cbox(wood, p, 0, 0, -0.86F, 0.085F, 0.085F, 0.05F, 0xFF3A2414, light);
        LfDraw.cbox(wood, p, 0, 0, -0.97F, 0.1F, 0.1F, 0.03F, 0xFF3A2414, light);
        // Cerdas em leque, balançando
        float sway = Mth.sin(time * 0.15F) * 0.05F;
        Vec3 root = new Vec3(0, 0, -0.9);
        for (int i = 0; i < 18; i++) {
            double ang = Math.PI * 2 * i / 18;
            double spread = 0.12 + 0.12 * ((i * 7) % 4) / 3.0;
            Vec3 tip = new Vec3(Math.cos(ang) * spread + sway, Math.sin(ang) * spread - 0.03, -1.55 - 0.06 * ((i * 5) % 3));
            LfDraw.segment(wood, p, root.add(Math.cos(ang) * 0.05, Math.sin(ang) * 0.05, 0), tip, 0.03F, 0.018F,
                    i % 3 == 0 ? strawDark : straw, light);
        }
        LfDraw.segment(wood, p, root, new Vec3(sway, -0.02, -1.5), 0.09F, 0.06F, straw, light);
        // Fitinha na ponta do cabo
        float flap = Mth.sin(time * 0.3F) * 0.08F;
        LfDraw.segment(wood, p, new Vec3(0, 0.05, 0.85), new Vec3(0.12 + flap, -0.05, 0.75), 0.02F, 0.012F, ribbon, light);
        LfDraw.segment(wood, p, new Vec3(0, 0.05, 0.85), new Vec3(-0.1 - flap, -0.08, 0.78), 0.02F, 0.012F, ribbon, light);
        pose.popPose();
    }
}
