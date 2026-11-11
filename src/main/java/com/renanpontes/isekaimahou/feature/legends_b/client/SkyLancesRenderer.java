package com.renanpontes.isekaimahou.feature.legends_b.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_b.entity.SkyLancesEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Lanças do Céu: um grande círculo mágico girando devagar no céu e, em volta dele, as lentes — cada uma com dois
 * círculos de runas girando em sentidos opostos, um disco de lente que brilha mais forte perto de cada disparo
 * e um clarão embaixo carregando o feixe. Tudo aparece crescendo e some no fim.
 */
public class SkyLancesRenderer extends EntityRenderer<SkyLancesEntity> {
    private static final ResourceLocation LENS = LbRenderTypes.tex("sky_lens");

    public SkyLancesRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SkyLancesEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float ageF = e.age() + pt;
        float pres = e.presence(ageF);
        if (pres <= 0F) return;
        float ease = pres * pres * (3 - 2 * pres);
        float charge = e.charge(ageF);
        int rgb = e.color();
        int soft = LbDraw.lighten(rgb, 0.35F);
        Vec3 entPos = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        int n = e.count();

        // Círculos de runas (textura branca somada à cena, tingida pela cor da magia)
        VertexConsumer tex = buffers.getBuffer(LbRenderTypes.additiveTexture(LENS));
        pose.pushPose();
        pose.translate(0, 0.35, 0);
        pose.mulPose(Axis.YP.rotationDegrees(ageF * 0.6F));
        LbDraw.texFlat(tex, pose.last().pose(), (e.ring() + 2.4F) * ease, LbDraw.argb(rgb, 0.55F * pres));
        pose.popPose();
        for (int i = 0; i < n; i++) {
            Vec3 lp = e.lensPos(i, ageF).subtract(entPos);
            float s = ease * (1F + 0.08F * charge);
            pose.pushPose();
            pose.translate(lp.x, lp.y, lp.z);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(ageF * 2.2F + i * 40F));
            LbDraw.texFlat(tex, pose.last().pose(), 1.75F * s, LbDraw.argb(soft, 0.95F * pres));
            pose.popPose();
            pose.pushPose();
            pose.translate(0, -0.14, 0);
            pose.mulPose(Axis.YP.rotationDegrees(-ageF * 3.4F + i * 17F));
            LbDraw.texFlat(tex, pose.last().pose(), 1.05F * s, LbDraw.argb(0xFFFFFF, (0.55F + 0.35F * charge) * pres));
            pose.popPose();
            pose.popPose();
        }

        // Discos das lentes, clarões de carga e a luz "vazando" para baixo
        VertexConsumer g = buffers.getBuffer(LbRenderTypes.GLOW);
        for (int i = 0; i < n; i++) {
            Vec3 lp = e.lensPos(i, ageF).subtract(entPos);
            float s = ease * (1F + 0.08F * charge);
            pose.pushPose();
            pose.translate(lp.x, lp.y - 0.07, lp.z);
            Matrix4f m = pose.last().pose();
            LbDraw.radialFlat(g, m, 0F, 1.3F * s, LbDraw.argb(0xFFFFFF, (0.3F + 0.45F * charge) * pres),
                    LbDraw.argb(rgb, 0.18F * pres), 28);
            LbDraw.ringFlat(g, m, 1.3F * s, 0.08F, soft, 0.8F * pres, 36);
            // Coluna de luz fraca saindo da lente
            float col = 0.35F + 0.5F * charge;
            LbDraw.softRibbon(g, m, Vec3.ZERO, new Vec3(0, -2.2F - 1.5F * charge, 0), new Vec3(1, 0, 0), 0.5F * s, 0.15F * s,
                    soft, 0.35F * col * pres, 0F);
            LbDraw.softRibbon(g, m, Vec3.ZERO, new Vec3(0, -2.2F - 1.5F * charge, 0), new Vec3(0, 0, 1), 0.5F * s, 0.15F * s,
                    soft, 0.35F * col * pres, 0F);
            // Clarão de carga embaixo da lente (virado para a câmera)
            pose.translate(0, -0.25, 0);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            LbDraw.radial(g, pose.last().pose(), 0F, (0.3F + 0.9F * charge) * s, LbDraw.argb(0xFFFFFF, charge * pres),
                    LbDraw.argb(rgb, 0F), 16);
            pose.popPose();
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SkyLancesEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
