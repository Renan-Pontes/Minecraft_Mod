package com.renanpontes.isekaimahou.feature.origins_a.client;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.origins_a.rune.ExplosiveSealEntity;
import com.renanpontes.isekaimahou.feature.origins_a.rune.TeleportCircleEntity;
import com.renanpontes.isekaimahou.feature.origins_a.rune.WardEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Renderers das runas do Mago: círculos desenhados no chão (e a parede do Ward). */
final class RuneRenderers {

    private static boolean isMine(UUID owner) {
        Minecraft mc = Minecraft.getInstance();
        return owner != null && mc.player != null && owner.equals(mc.player.getUUID());
    }

    // =====================================================================================
    /** Selo Explosivo: o dono vê a runa nítida e pulsando; os outros quase não enxergam. */
    static class Seal extends EntityRenderer<ExplosiveSealEntity> {
        private static final ResourceLocation TEX = OaQuad.tex("origin_explosive_seal");

        Seal(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0;
        }

        @Override
        public void render(ExplosiveSealEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float t = e.age() + pt;
            boolean armed = e.armed();
            float pulse = armed ? 0.8F + 0.2F * Mth.sin(t * 0.25F) : 0.5F + 0.5F * Mth.sin(t * 0.8F);
            float alpha = (isMine(e.ownerId()) ? 0.85F : 0.14F) * pulse * e.fade(pt);
            ps.pushPose();
            ps.translate(0, 0.015, 0);
            ps.mulPose(Axis.YP.rotationDegrees(e.getYRot() + t * 0.6F));
            OaQuad.flat(ps, OaQuad.glow(buffers, TEX), 0.6F, OaQuad.lighten(e.color(), 0.25F), alpha);
            ps.popPose();
            super.render(e, yaw, pt, ps, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(ExplosiveSealEntity e) {
            return TEX;
        }
    }

    // =====================================================================================
    /** Círculo de Teleporte: anel externo e estrela interna girando em sentidos opostos; apagado sem par. */
    static class Circle extends EntityRenderer<TeleportCircleEntity> {
        private static final ResourceLocation RING = OaQuad.tex("origin_teleport_circle");
        private static final ResourceLocation STAR = OaQuad.tex("origin_teleport_star");

        Circle(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0;
        }

        @Override
        public void render(TeleportCircleEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float t = e.age() + pt;
            boolean linked = e.linked();
            float fade = e.fade(pt);
            float base = linked ? 0.85F : 0.4F;
            float pulse = 0.85F + 0.15F * Mth.sin(t * 0.1F);
            int color = OaQuad.lighten(e.color(), 0.2F);
            ps.pushPose();
            ps.translate(0, 0.02, 0);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * (linked ? 0.8F : 0.2F)));
            OaQuad.flat(ps, OaQuad.glow(buffers, RING), TeleportCircleEntity.RADIUS * 1.15F, color, base * pulse * fade);
            ps.popPose();
            ps.translate(0, 0.01, 0);
            ps.mulPose(Axis.YP.rotationDegrees(-t * (linked ? 1.6F : 0.4F)));
            OaQuad.flat(ps, OaQuad.glow(buffers, STAR), TeleportCircleEntity.RADIUS * 0.75F, OaQuad.lighten(color, 0.3F), base * fade);
            if (linked) {
                // Coluna de luz fraca: dá para achar o círculo de longe
                ps.translate(0, 0.01, 0);
                OaQuad.cylinder(ps, OaQuad.glow(buffers, WardRenderer.WALL), TeleportCircleEntity.RADIUS * 0.95F, 1.4F, 24,
                        -t * 0.02F, color, 0.35F * pulse * fade);
            }
            ps.popPose();
            super.render(e, yaw, pt, ps, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(TeleportCircleEntity e) {
            return RING;
        }
    }

    // =====================================================================================
    /** Ward: círculo dourado no chão + parede translúcida em cilindro com runas subindo. */
    static class WardRenderer extends EntityRenderer<WardEntity> {
        static final ResourceLocation WALL = OaQuad.tex("origin_ward_wall");
        private static final ResourceLocation GROUND = OaQuad.tex("origin_ward");

        WardRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0;
        }

        @Override
        public void render(WardEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float t = e.age() + pt;
            float r = e.radius();
            float fade = e.fade(pt);
            int color = OaQuad.lighten(e.color(), 0.35F);
            float pulse = 0.8F + 0.2F * Mth.sin(t * 0.08F);
            ps.pushPose();
            ps.translate(0, 0.02, 0);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * 0.15F));
            OaQuad.flat(ps, OaQuad.glow(buffers, GROUND), r, color, 0.55F * pulse * fade);
            ps.popPose();
            float h = Math.min(4.0F, 1.5F + r * 0.35F);
            OaQuad.cylinder(ps, OaQuad.glow(buffers, WALL), r, h, Math.max(24, (int) (r * 8)), -t * 0.01F, color, 0.45F * pulse * fade);
            ps.popPose();
            super.render(e, yaw, pt, ps, buffers, light);
        }

        @Override
        public boolean shouldRender(WardEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
            return frustum.isVisible(e.getBoundingBoxForCulling());
        }

        @Override
        public ResourceLocation getTextureLocation(WardEntity e) {
            return GROUND;
        }
    }

    private RuneRenderers() {}
}
