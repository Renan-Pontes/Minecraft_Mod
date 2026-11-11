package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.PrismBeamEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Canhão Prismático: feixe branco do conjurador até um prisma de cristal girando, que abre um leque de sete feixes
 * nas cores do arco-íris (núcleo branco e bordas coloridas), com clarões onde cada feixe bate.
 */
public class PrismBeamRenderer extends EntityRenderer<PrismBeamEntity> {
    public PrismBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(PrismBeamEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float life = e.openness(pt, 5);
        if (life <= 0) return;
        float t = e.tickCount + pt;
        Vec3 here = e.getPosition(pt);
        Vec3 cam = FdQuads.cameraLocal(e, pt);
        Matrix4f m = ps.last().pose();
        // Prisma (triangular, girando): primeiro as faces translúcidas
        double rot = t * 0.12;
        Vec3[] top = new Vec3[3], bot = new Vec3[3];
        for (int k = 0; k < 3; k++) {
            double a = rot + Math.PI * 2 * k / 3;
            top[k] = new Vec3(Math.cos(a) * 0.55, 0.65, Math.sin(a) * 0.55);
            bot[k] = new Vec3(Math.cos(a) * 0.55, -0.65, Math.sin(a) * 0.55);
        }
        int cf = FdQuads.argb(0xF4F0FF, 0.35F * life), ce = FdQuads.argb(0xFFFFFF, 0.9F * life);
        VertexConsumer fill = buffers.getBuffer(FdQuads.dark());
        for (int k = 0; k < 3; k++) {
            int n = (k + 1) % 3;
            FdQuads.tex(fill, ps.last(), bot[k], bot[n], top[n], top[k], cf, 0, 0, 1, 1);
        }
        FdQuads.tex(fill, ps.last(), top[0], top[1], top[2], top[2], cf, 0, 0, 1, 1);
        FdQuads.tex(fill, ps.last(), bot[0], bot[1], bot[2], bot[2], cf, 0, 0, 1, 1);
        // Depois tudo o que brilha (um buffer por vez)
        VertexConsumer glow = buffers.getBuffer(FdQuads.glow());
        for (int k = 0; k < 3; k++) {
            int n = (k + 1) % 3;
            FdQuads.beam(glow, m, top[k], bot[k], cam, 0.03, ce, ce);
            FdQuads.beam(glow, m, top[k], top[n], cam, 0.03, ce, ce);
            FdQuads.beam(glow, m, bot[k], bot[n], cam, 0.03, ce, ce);
        }
        float pulse = 0.85F + 0.15F * Mth.sin(t * 1.3F);
        // Feixe branco de entrada
        Vec3 in = e.origin().subtract(here);
        FdQuads.softBeam(glow, m, in, Vec3.ZERO, cam, 0.42 * life * pulse, FdQuads.argb(0xFFFFFF, 0.95F), FdQuads.argb(0xDDE8FF, 0F));
        FdQuads.softBeam(glow, m, in, Vec3.ZERO, cam, 0.16 * life, FdQuads.argb(0xFFFFFF, 1F), FdQuads.argb(0xFFFFFF, 0.2F));
        // Sete feixes coloridos
        for (int i = 0; i < 7; i++) {
            Vec3 dir = e.beamDir(i, t);
            Vec3 end = e.beamEnd(here, dir, e.length() * Math.min(1F, (e.tickCount + pt) / 4F)).subtract(here);
            int c = PrismBeamEntity.COLORS[i];
            FdQuads.softBeam(glow, m, Vec3.ZERO, end, cam, 0.34 * life * pulse, FdQuads.argb(c, 0.9F), FdQuads.argb(c, 0F));
            FdQuads.softBeam(glow, m, Vec3.ZERO, end, cam, 0.09 * life, FdQuads.argb(0xFFFFFF, 0.8F), FdQuads.argb(c, 0.3F));
            FdQuads.halo(glow, m, end, cam, 0, 0.7 * life * pulse, FdQuads.argb(0xFFFFFF, 0.8F), FdQuads.argb(c, 0F), 16);
        }
        FdQuads.halo(glow, m, Vec3.ZERO, cam, 0, 1.3 * life * pulse, FdQuads.argb(0xFFFFFF, 0.7F), FdQuads.argb(0xC060FF, 0F), 24);
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public boolean shouldRender(PrismBeamEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(PrismBeamEntity e) {
        return FdQuads.WHITE;
    }
}
