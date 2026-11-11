package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.CrystalSpikeEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Estilhaço: bipirâmide de cristal alongada, apontando para onde voa, com núcleo claro e rastro curto. */
public class CrystalSpikeRenderer extends EntityRenderer<CrystalSpikeEntity> {
    public CrystalSpikeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(CrystalSpikeEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        Vec3 v = e.getDeltaMovement();
        if (v.lengthSqr() < 1.0E-6) return;
        Vec3 d = v.normalize();
        Vec3 s = d.cross(new Vec3(0, 1, 0));
        if (s.lengthSqr() < 1.0E-6) s = new Vec3(1, 0, 0);
        s = s.normalize().scale(0.11);
        Vec3 u = s.cross(d).normalize().scale(0.11);
        Vec3 front = d.scale(0.55), back = d.scale(-0.3);
        Vec3[] ring = {s, u, s.reverse(), u.reverse()};
        Matrix4f m = ps.last().pose();
        int cf = FdQuads.argb(0xF0A8FF, 0.75F), cg = FdQuads.argb(0xFFE0FF, 0.9F), cb = FdQuads.argb(0xB050E0, 0.3F);
        VertexConsumer fill = buffers.getBuffer(FdQuads.dark());
        for (int i = 0; i < 4; i++) {
            Vec3 a = ring[i], b = ring[(i + 1) % 4];
            FdQuads.tex(fill, ps.last(), a, b, front, front, cf, 0, 0, 1, 1);
            FdQuads.tex(fill, ps.last(), a, b, back, back, cf, 0, 0, 1, 1);
        }
        // Um buffer por vez (pedir outro encerra o anterior)
        VertexConsumer glow = buffers.getBuffer(FdQuads.glow());
        for (int i = 0; i < 4; i++) FdQuads.color(glow, m, ring[i], cb, front, cg, front, cg, ring[(i + 1) % 4], cb);
        Vec3 cam = FdQuads.cameraLocal(e, pt);
        FdQuads.softBeam(glow, m, Vec3.ZERO, d.scale(-1.6), cam, 0.09, FdQuads.argb(0xFFC8FF, 0.6F), FdQuads.argb(0xE07AF0, 0F));
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CrystalSpikeEntity e) {
        return FdQuads.WHITE;
    }
}
