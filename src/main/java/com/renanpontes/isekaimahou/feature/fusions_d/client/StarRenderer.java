package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.StarfallStarEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Estrela cadente: estrela de quatro pontas girando, halo azulado e um rastro longo de luz até o céu. */
public class StarRenderer extends EntityRenderer<StarfallStarEntity> {
    public StarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(StarfallStarEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        Vec3 cam = FdQuads.cameraLocal(e, pt);
        Matrix4f m = ps.last().pose();
        VertexConsumer glow = buffers.getBuffer(FdQuads.glow());
        Vec3 here = e.getPosition(pt);
        Vec3 back = e.at(Math.max(0F, t - 6F)).subtract(here);
        float tw = 1F + 0.2F * Mth.sin(t * 1.7F);
        FdQuads.softBeam(glow, m, Vec3.ZERO, back, cam, 0.5, FdQuads.argb(0xE6EEFF, 0.85F), FdQuads.argb(0x9DB4FF, 0F));
        FdQuads.softBeam(glow, m, Vec3.ZERO, back.scale(0.5), cam, 0.15, FdQuads.argb(0xFFFFFF, 1F), FdQuads.argb(0xFFFFFF, 0F));
        FdQuads.halo(glow, m, Vec3.ZERO, cam, 0, 1.1 * tw, FdQuads.argb(0xFFFFFF, 0.9F), FdQuads.argb(0x9DB4FF, 0F), 20);
        FdQuads.star(glow, m, Vec3.ZERO, cam, 1.7 * tw, t * 0.15, FdQuads.argb(0xFFFFFF, 1F), FdQuads.argb(0xBFD0FF, 0F));
        FdQuads.star(glow, m, Vec3.ZERO, cam, 1.0 * tw, t * 0.15 + Math.PI / 4, FdQuads.argb(0xFFFFFF, 0.8F), FdQuads.argb(0xBFD0FF, 0F));
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public boolean shouldRender(StarfallStarEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(StarfallStarEntity e) {
        return FdQuads.WHITE;
    }
}
