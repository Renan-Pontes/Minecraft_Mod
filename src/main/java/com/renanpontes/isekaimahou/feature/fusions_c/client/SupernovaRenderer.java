package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.SupernovaEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Supernova: enquanto carrega, uma estrela pequena cresce e pulsa cada vez mais rápido — superfície de plasma rolando,
 * coroa de raios de luz girando e um anel de runas solares em volta. Na explosão, uma casca de luz branca-dourada se
 * expande até o raio e some. Contrariada: a estrela escurece e encolhe.
 */
public class SupernovaRenderer extends EntityRenderer<SupernovaEntity> {
    private static final int RAYS = 12;

    public SupernovaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(SupernovaEntity e, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(e.getBoundingBox().inflate(e.scale() + 4));
    }

    @Override
    public void render(SupernovaEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float c = e.charge(pt);
        float b = e.blast(pt);
        float radius = e.scale();
        VertexConsumer glow = FcQuads.additive(buffers, FcQuads.GLOW);
        ps.pushPose();
        if (e.fizzled()) {
            float k = Mth.clamp((e.lifeLeft() - pt) / 8F, 0F, 1F);
            FcQuads.sphere(ps, FcQuads.glow(buffers, FcQuads.NOISE), 1.2F * k, 1.2F * k, 0, 0, 2, 1, t * 0.05F, 0x5A3A20, 0.8F * k, 10, 16);
            ps.popPose();
            return;
        }
        if (b <= 0F) {
            float pulse = 1F + (0.06F + 0.12F * c) * Mth.sin(t * (0.4F + 1.4F * c));
            float star = (0.35F + 1.45F * c) * pulse;
            // Superfície de plasma (duas camadas girando em sentidos opostos)
            VertexConsumer surf = FcQuads.glow(buffers, FcQuads.NOISE);
            FcQuads.sphere(ps, surf, star, star, 0, 0, 2, 1, t * 0.03F, 0xFFB000, 0.95F, 12, 20);
            FcQuads.sphere(ps, surf, star * 1.08F, star * 1.08F, 0, 0, 2, 1, -t * 0.05F, 0xFFF0A0, 0.5F, 12, 20);
            // Coroa: raios de luz em volta (ficam mais longos ao carregar)
            VertexConsumer beam = FcQuads.additive(buffers, FcQuads.BEAM);
            for (int i = 0; i < RAYS; i++) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(i * 360F / RAYS + t * 2.5F));
                ps.mulPose(Axis.ZP.rotationDegrees(70F + 40F * Mth.sin(i * 1.7F + t * 0.05F)));
                FcQuads.beam(ps, beam, 0.12F + 0.12F * c, star * (1.6F + 1.8F * c), -t * 0.2F, 2F, 0xFFC040, 0.75F);
                ps.popPose();
            }
            // Anel de runas solares em volta
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(20F * Mth.sin(t * 0.03F)));
            FcQuads.flat(ps, FcQuads.additive(buffers, FcQuads.RUNE), star * 2.4F, t * 4F, 0xFFB000, 0.85F * c);
            ps.popPose();
            ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
            FcQuads.billboard(ps, glow, star * 3.2F, t * 2, 0xFF8A20, 0.7F);
            FcQuads.billboard(ps, glow, star * 1.6F, -t * 3, 0xFFFFFF, 0.9F);
        } else {
            // Explosão: casca de luz que se expande e some
            float rr = radius * (0.2F + 0.8F * b);
            float k = 1F - b;
            FcQuads.sphere(ps, FcQuads.glow(buffers, FcQuads.NOISE), rr, rr, 0, 0, 3, 1, t * 0.05F, 0xFFE070, 0.75F * k, 14, 24);
            ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
            FcQuads.billboard(ps, glow, rr * 2.2F, t, 0xFFF4C8, k);
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SupernovaEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
