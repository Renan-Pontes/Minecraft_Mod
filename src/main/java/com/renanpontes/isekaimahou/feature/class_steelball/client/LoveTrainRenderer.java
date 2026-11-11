package com.renanpontes.isekaimahou.feature.class_steelball.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.class_steelball.SbLoveTrain;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.LoveTrainEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * LOVE TRAIN — a cortina de luz dimensional: 3 anéis de LÂMINAS de luz branca-dourada em volta do portador, com
 * FRESTAS entre elas (a "luz com buracos" do Santo), girando devagar em sentidos opostos e acompanhando o portador.
 * Cada lâmina usa a textura do feixe do sinalizador rolando para cima (luz escorrendo), some nas pontas de cima e de
 * baixo e cintila. Raios finos (aditivos) sobem acima da cabeça. Aparece e some suave; sob Acerto Garantido as lâminas
 * piscam em vermelho-pálido. Em primeira pessoa (câmera dentro da cortina) fica bem mais fraca, para não cegar.
 */
public class LoveTrainRenderer extends EntityRenderer<LoveTrainEntity> {
    private static final ResourceLocation BEAM = BeaconRenderer.BEAM_LOCATION;
    /** Anéis: raio extra (além da cortina), nº de lâminas, fração do arco coberta, velocidade (graus/tick), cor. */
    private static final float[][] RINGS = {
            {0.00F, 9, 0.62F, 0.9F},
            {0.28F, 12, 0.55F, -0.6F},
            {0.55F, 7, 0.45F, 0.4F},
    };
    private static final int[] RING_RGB = {0xFFFFF4, 0xFFF0B8, 0xFFD24A};

    public LoveTrainRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(LoveTrainEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        LivingEntity o = e.ownerEntity();
        if (o == null) return;
        float time = e.tickCount + pt;
        float appear = Mth.clamp(time / 12F, 0F, 1F);
        float fade = Mth.clamp((e.life() - time) / 25F, 0F, 1F);
        float k = appear * fade;
        if (k <= 0.01F) return;
        boolean pierced = e.pierced();
        if (pierced) k *= 0.35F + 0.45F * RandomSource.create((long) (time * 0.5F) * 31L + e.getId()).nextFloat();

        Vec3 entPos = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 feet = o.getPosition(pt);
        Vec3 cam = entityRenderDispatcher.camera.getPosition();
        float base = (float) (SbLoveTrain.CURTAIN_RADIUS + o.getBbWidth() * 0.5);
        double dx = cam.x - feet.x, dz = cam.z - feet.z;
        boolean inside = dx * dx + dz * dz < (base + 0.7) * (base + 0.7) && cam.y > feet.y - 0.5 && cam.y < feet.y + o.getBbHeight() + 1.2;
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == o;
        if (inside || firstPerson) k *= 0.28F;

        float h = o.getBbHeight();
        float y0 = -0.25F, y1 = h * 0.5F, y2 = h + 1.1F;

        pose.pushPose();
        pose.translate(feet.x - entPos.x, feet.y - entPos.y, feet.z - entPos.z);
        PoseStack.Pose last = pose.last();
        VertexConsumer sheets = buffers.getBuffer(RenderType.beaconBeam(BEAM, true));
        for (int ring = 0; ring < RINGS.length; ring++) {
            float[] cfg = RINGS[ring];
            float r = base + cfg[0];
            int panels = (int) cfg[1];
            float cover = cfg[2];
            float rot = (float) Math.toRadians(time * cfg[3] + ring * 37F);
            int rgb = pierced ? 0xFFB8B0 : RING_RGB[ring];
            float span = (float) (Math.PI * 2 / panels);
            for (int j = 0; j < panels; j++) {
                float shimmer = 0.55F + 0.45F * Mth.sin(time * 0.17F + j * 1.7F + ring * 2.3F);
                float a = (ring == 0 ? 0.55F : ring == 1 ? 0.40F : 0.30F) * k * shimmer;
                if (a <= 0.01F) continue;
                float a0 = rot + j * span;
                float a1 = a0 + span * cover;
                float vScroll = -time * 0.035F * (1F + ring * 0.3F) + j * 0.37F;
                // cada lâmina em 3 fatias (curva acompanhando o anel)
                for (int s = 0; s < 3; s++) {
                    float b0 = a0 + (a1 - a0) * s / 3F, b1 = a0 + (a1 - a0) * (s + 1) / 3F;
                    float u0 = s / 3F, u1 = (s + 1) / 3F;
                    // alpha some nas beiradas laterais da lâmina (luz sem quina)
                    float e0 = edge(u0), e1 = edge(u1);
                    sheet(sheets, last, r, b0, b1, y0, y1, u0, u1, vScroll, vScroll + (y1 - y0) * 0.25F, rgb, 0F, a * e0, 0F, a * e1);
                    sheet(sheets, last, r, b0, b1, y1, y2, u0, u1, vScroll + (y1 - y0) * 0.25F, vScroll + (y2 - y0) * 0.25F,
                            rgb, a * e0, 0F, a * e1, 0F);
                }
            }
        }

        // Raios finos subindo acima da cabeça (aditivos, piscando)
        if (!firstPerson) {
            VertexConsumer rays = buffers.getBuffer(RenderType.lightning());
            Matrix4f m = last.pose();
            RandomSource rnd = RandomSource.create((long) (time / 3F) * 977L + e.getId());
            for (int i = 0; i < 6; i++) {
                float ang = rnd.nextFloat() * Mth.TWO_PI;
                float rr = base + rnd.nextFloat() * 0.6F;
                float top = h + 2.5F + rnd.nextFloat() * 3.5F;
                float w = 0.04F + rnd.nextFloat() * 0.05F;
                float alpha = (0.25F + rnd.nextFloat() * 0.35F) * k;
                ray(rays, m, Mth.cos(ang) * rr, Mth.sin(ang) * rr, -0.2F, top, w, pierced ? 0xFFB0A0 : 0xFFF4C8, alpha);
            }
        }
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    /** 0 nas beiradas da lâmina, 1 no meio. */
    private static float edge(float u) {
        return Mth.clamp(1F - Math.abs(u - 0.5F) * 2F, 0F, 1F) * 0.7F + 0.3F;
    }

    /** Um pedaço curvo de lâmina (quad vertical entre os ângulos b0..b1), nas duas faces. Alphas: baixo-esq, cima-esq, baixo-dir, cima-dir. */
    private static void sheet(VertexConsumer vc, PoseStack.Pose p, float r, float b0, float b1, float ya, float yb, float u0, float u1,
            float va, float vb, int rgb, float aBL, float aTL, float aBR, float aTR) {
        float x0 = Mth.cos(b0) * r, z0 = Mth.sin(b0) * r, x1 = Mth.cos(b1) * r, z1 = Mth.sin(b1) * r;
        float nx = Mth.cos((b0 + b1) * 0.5F), nz = Mth.sin((b0 + b1) * 0.5F);
        int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
        // frente
        v(vc, p, x0, ya, z0, cr, cg, cb, aBL, u0, vb, nx, nz);
        v(vc, p, x1, ya, z1, cr, cg, cb, aBR, u1, vb, nx, nz);
        v(vc, p, x1, yb, z1, cr, cg, cb, aTR, u1, va, nx, nz);
        v(vc, p, x0, yb, z0, cr, cg, cb, aTL, u0, va, nx, nz);
        // verso
        v(vc, p, x0, yb, z0, cr, cg, cb, aTL, u0, va, -nx, -nz);
        v(vc, p, x1, yb, z1, cr, cg, cb, aTR, u1, va, -nx, -nz);
        v(vc, p, x1, ya, z1, cr, cg, cb, aBR, u1, vb, -nx, -nz);
        v(vc, p, x0, ya, z0, cr, cg, cb, aBL, u0, vb, -nx, -nz);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, int r, int g, int b, float a, float u, float vv,
            float nx, float nz) {
        vc.addVertex(p, x, y, z).setColor(r, g, b, Mth.clamp((int) (a * 255F), 0, 255)).setUv(u, vv)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, 0F, nz);
    }

    /** Um raio vertical fino (cruz de 2 quads para ter volume de qualquer ângulo). */
    private static void ray(VertexConsumer vc, Matrix4f m, float x, float z, float yBot, float yTop, float w, int rgb, float a) {
        int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
        int ab = Mth.clamp((int) (a * 255F), 0, 255);
        for (int axis = 0; axis < 2; axis++) {
            float ox = axis == 0 ? w : 0F, oz = axis == 0 ? 0F : w;
            vc.addVertex(m, x - ox, yBot, z - oz).setColor(cr, cg, cb, ab);
            vc.addVertex(m, x + ox, yBot, z + oz).setColor(cr, cg, cb, ab);
            vc.addVertex(m, x + ox, yTop, z + oz).setColor(cr, cg, cb, 0);
            vc.addVertex(m, x - ox, yTop, z - oz).setColor(cr, cg, cb, 0);
            vc.addVertex(m, x - ox, yTop, z - oz).setColor(cr, cg, cb, 0);
            vc.addVertex(m, x + ox, yTop, z + oz).setColor(cr, cg, cb, 0);
            vc.addVertex(m, x + ox, yBot, z + oz).setColor(cr, cg, cb, ab);
            vc.addVertex(m, x - ox, yBot, z - oz).setColor(cr, cg, cb, ab);
        }
    }

    @Override
    public boolean shouldRender(LoveTrainEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(LoveTrainEntity e) {
        return BEAM;
    }
}
