package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.TwilightPillarEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Pilar do Ragnarök: coluna até o céu — de LUZ (núcleo branco, aura dourada) ou de SOMBRA (núcleo negro opaco com
 * aura violeta) — que nasce larga, afina e some; um anel de choque corre pelo chão.
 */
public class TwilightPillarRenderer extends EntityRenderer<TwilightPillarEntity> {
    private static final float HEIGHT = 48F;

    public TwilightPillarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(TwilightPillarEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float f = Mth.clamp(t / TwilightPillarEntity.LIFE, 0F, 1F);
        float a = 1F - f * f;
        float r = e.radius() * (1.15F - 0.85F * f) * Math.min(1F, t / 2F);
        boolean shadow = e.shadow();
        Matrix4f m = ps.last().pose();
        if (shadow) {
            VertexConsumer dark = buffers.getBuffer(FdQuads.dark());
            int c0 = FdQuads.argb(0x0A0212, 0.9F * a), c1 = FdQuads.argb(0x1A0630, 0F);
            for (int i = 0; i < 16; i++) {
                double a0 = Math.PI * 2 * i / 16 + t * 0.1, a1 = Math.PI * 2 * (i + 1) / 16 + t * 0.1;
                double rr = r * 0.55;
                FdQuads.tex4(dark, ps.last(), new net.minecraft.world.phys.Vec3(Math.cos(a0) * rr, 0, Math.sin(a0) * rr), c0, 0, 1,
                        new net.minecraft.world.phys.Vec3(Math.cos(a1) * rr, 0, Math.sin(a1) * rr), c0, 1, 1,
                        new net.minecraft.world.phys.Vec3(Math.cos(a1) * rr, HEIGHT, Math.sin(a1) * rr), c1, 1, 0,
                        new net.minecraft.world.phys.Vec3(Math.cos(a0) * rr, HEIGHT, Math.sin(a0) * rr), c1, 0, 0);
            }
        }
        VertexConsumer glow = buffers.getBuffer(FdQuads.glow());
        int core = shadow ? 0x9B4DFF : 0xFFFFFF, aura = shadow ? 0x4A1E6E : 0xFFD27A;
        if (!shadow) FdQuads.cylinder(glow, m, r * 0.5, r * 0.5, 0, HEIGHT, FdQuads.argb(core, 0.95F * a), FdQuads.argb(core, 0F), 16, t * 0.2);
        FdQuads.cylinder(glow, m, r, r * 0.8, 0, HEIGHT, FdQuads.argb(aura, 0.6F * a), FdQuads.argb(aura, 0F), 20, -t * 0.15);
        FdQuads.cylinder(glow, m, r * 1.4, r * 1.1, 0, HEIGHT * 0.3, FdQuads.argb(aura, 0.3F * a), FdQuads.argb(aura, 0F), 20, t * 0.1);
        float ring = e.radius() * (0.5F + f * 3F);
        FdQuads.ring(glow, m, 0.06, ring * 0.85, ring, FdQuads.argb(aura, 0F), FdQuads.argb(shadow ? 0xC06AFF : 0xFFF0B0, 0.8F * a), 40, 0);
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public boolean shouldRender(TwilightPillarEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(TwilightPillarEntity e) {
        return FdQuads.WHITE;
    }
}
