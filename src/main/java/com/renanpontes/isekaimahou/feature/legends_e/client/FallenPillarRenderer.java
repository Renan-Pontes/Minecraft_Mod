package com.renanpontes.isekaimahou.feature.legends_e.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_e.entity.FallenPillarEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Queda Final: um círculo se acende no chão e outro no céu, ligados por um fio de luz; o pilar desce do céu e, ao tocar
 * o chão, vira uma coluna descomunal azul-branca em camadas (núcleo branco, manto azul, halo) com anéis de luz
 * escorrendo para baixo, um disco de impacto e a onda de calor laranja se espalhando pelo chão.
 */
public class FallenPillarRenderer extends EntityRenderer<FallenPillarEntity> {
    private static final int CORE = 0xFFFFFF;
    private static final int MANTLE = 0xA8D8FF;
    private static final int HALO = 0x5AA8FF;
    private static final float SKY = 70F;
    private static final float TOP = 220F;

    public FallenPillarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(FallenPillarEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float ageF = e.age() + pt;
        float r = e.radius();
        Vec3 cam = entityRenderDispatcher.camera.getPosition()
                .subtract(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float tin = LeDraw.smooth(ageF / 12F);
        float strength = e.strength(ageF);
        float descent = e.descent(ageF);
        boolean impacted = ageF >= FallenPillarEntity.IMPACT;
        float post = impacted ? ageF - FallenPillarEntity.IMPACT : 0F;
        float targeting = impacted ? Mth.clamp(1F - post / 6F, 0F, 1F) : tin;

        // ---------------- Círculos (chão e céu) ----------------
        if (targeting > 0.01F) {
            int col = LeDraw.argb(0xDDEEFF, 0.9F * targeting);
            texFlat(pose, buffers, LeRenderTypes.RING_MID, 0.08F, ageF * 1.2F, r * 1.25F * tin, col);
            texFlat(pose, buffers, LeRenderTypes.SIGIL_CORE, 0.12F, -ageF * 2F, r * 0.7F * tin, col);
            texFlat(pose, buffers, LeRenderTypes.RING_OUTER, SKY, -ageF * 0.8F, r * 2.2F * tin, LeDraw.argb(0xDDEEFF, targeting));
            texFlat(pose, buffers, LeRenderTypes.RING_MID, SKY + 0.3F, ageF * 1.1F, r * 1.4F * tin, LeDraw.argb(0xFFFFFF, targeting * 0.8F));
        }

        VertexConsumer g = buffers.getBuffer(LeRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        if (!impacted) {
            // Fio de luz ligando os círculos; depois o pilar descendo do céu
            LeDraw.cylinder(g, m, 0.12F + 0.15F * descent, 0F, SKY, 12, CORE, 0.7F * tin, 0.9F * tin, cam, 1F);
            if (descent > 0) {
                float bottom = Mth.lerp(descent * descent, SKY, 0F);
                pillar(g, m, r * (0.6F + 0.4F * descent), bottom, 1F, cam, ageF);
            }
            super.render(e, yaw, pt, pose, buffers, light);
            return;
        }

        // ---------------- Pilar ----------------
        float s = strength;
        if (s > 0.01F) {
            float rr = r * (0.35F + 0.65F * s) * (1F + 0.15F * Math.max(0F, 1F - post / 5F));
            pillar(g, m, rr, -r * 1.6F, s, cam, ageF);
            // Disco de impacto no chão
            pose.pushPose();
            pose.translate(0, 0.15, 0);
            Matrix4f dm = pose.last().pose();
            LeDraw.radialFlat(g, dm, 0F, r * 2.2F, LeDraw.argb(CORE, 0.75F * s), LeDraw.argb(MANTLE, 0F), 48);
            LeDraw.ringFlat(g, dm, r * 1.05F, 0.6F, CORE, 0.9F * s, 64);
            pose.popPose();
        }
        // Onda de calor laranja que corre pelo chão
        float heatT = post / (FallenPillarEntity.HEAT_TICKS + 6F);
        if (heatT < 1F) {
            float front = r + 2.2F * r * Math.min(1F, post / FallenPillarEntity.HEAT_TICKS);
            pose.pushPose();
            pose.translate(0, 0.3, 0);
            Matrix4f hm = pose.last().pose();
            LeDraw.ringFlat(g, hm, front, 0.8F + front * 0.08F, 0xFFB060, (1F - heatT) * 0.9F, 96);
            LeDraw.ringFlat(g, hm, front * 0.92F, 0.5F, 0xFFF0C0, (1F - heatT) * 0.6F, 80);
            pose.popPose();
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    /** O pilar: três cascas com borda brilhante e anéis de luz escorrendo para baixo. */
    private static void pillar(VertexConsumer g, Matrix4f m, float r, float bottom, float s, Vec3 cam, float ageF) {
        LeDraw.cylinder(g, m, r * 0.45F, bottom, TOP, 32, CORE, 0.85F * s, 0.55F * s, cam, 0.15F);
        LeDraw.cylinder(g, m, r * 0.8F, bottom, TOP, 32, MANTLE, 0.22F * s, 0.65F * s, cam, 0.1F);
        LeDraw.cylinder(g, m, r * 1.15F, bottom, TOP, 32, HALO, 0.04F * s, 0.35F * s, cam, 0F);
        float span = TOP - Math.max(bottom, 0F);
        for (int j = 0; j < 10; j++) {
            float y = TOP - ((ageF * 2.4F + j * span / 10F) % span);
            if (y < bottom) continue;
            float fadeTop = 1F - y / TOP;
            LeDraw.ringFlat(g, offset(m, y), r * 1.02F, 0.35F + r * 0.05F, CORE, 0.6F * s * fadeTop, 48);
        }
    }

    private static Matrix4f offset(Matrix4f m, float y) {
        return new Matrix4f(m).translate(0, y, 0);
    }

    private static void texFlat(PoseStack pose, MultiBufferSource buffers, ResourceLocation tex, float y, float rot, float half, int argb) {
        if (half <= 0.01F || (argb >>> 24) == 0) return;
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(rot));
        LeDraw.texFlat(buffers.getBuffer(LeRenderTypes.additive(tex)), pose.last().pose(), half, argb);
        pose.popPose();
    }

    @Override
    public boolean shouldRender(FallenPillarEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(FallenPillarEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
