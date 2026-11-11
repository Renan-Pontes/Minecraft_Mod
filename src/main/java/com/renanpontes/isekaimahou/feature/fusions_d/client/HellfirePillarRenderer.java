package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.HellfirePillarEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Pilar de Chamas Negras: no aviso, rachaduras vermelhas brilhando no chão; na erupção, camadas de fogo NEGRO (textura
 * de labaredas subindo, girando em sentidos opostos) dentro de uma aura carmesim que some no alto.
 */
public class HellfirePillarRenderer extends EntityRenderer<HellfirePillarEntity> {
    public HellfirePillarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(HellfirePillarEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float r = e.radius();
        Matrix4f m = ps.last().pose();
        VertexConsumer glow = buffers.getBuffer(FdQuads.glow());
        if (e.state() == HellfirePillarEntity.CHARGE) {
            float k = Mth.clamp(t / HellfirePillarEntity.CHARGE_TICKS, 0F, 1F);
            FdQuads.ring(glow, m, 0.04, 0, r * k, FdQuads.argb(0xFF3040, 0.5F * k), FdQuads.argb(0x600010, 0.1F), 32, 0);
            FdQuads.ring(glow, m, 0.05, r * 0.95, r * 1.05, FdQuads.argb(0xFF2030, 0.9F), FdQuads.argb(0xFF2030, 0.9F), 40, 0);
            Vec3 cam = FdQuads.cameraLocal(e, pt);
            for (int i = 0; i < 9; i++) {
                double a = i * 2.39996 + e.getId();
                double len = r * (0.6 + 0.4 * ((i * 37 % 10) / 10.0)) * k;
                Vec3 mid = new Vec3(Math.cos(a + 0.3) * len * 0.5, 0.06, Math.sin(a + 0.3) * len * 0.5);
                Vec3 end = new Vec3(Math.cos(a) * len, 0.06, Math.sin(a) * len);
                FdQuads.beam(glow, m, new Vec3(0, 0.06, 0), mid, cam.add(0, 50, 0), 0.06, FdQuads.argb(0xFF5060, 0.9F), FdQuads.argb(0xFF3040, 0.8F));
                FdQuads.beam(glow, m, mid, end, cam.add(0, 50, 0), 0.05, FdQuads.argb(0xFF3040, 0.8F), FdQuads.argb(0xFF3040, 0.1F));
            }
            super.render(e, yaw, pt, ps, buffers, light);
            return;
        }
        float rise = Mth.clamp((e.stateAge() + pt) / 4F, 0F, 1F);
        float fade = e.openness(pt, 12);
        float h = e.height() * rise;
        float w = r * (0.4F + 0.6F * fade) * (1F + 0.04F * Mth.sin(t * 0.9F));
        VertexConsumer flame = buffers.getBuffer(FdQuads.flame());
        float scroll = -t * 0.06F;
        FdQuads.texCylinder(flame, ps.last(), w * 0.62, w * 0.5, 0, h, FdQuads.argb(0xFFFFFF, 0.95F * fade), FdQuads.argb(0xFFFFFF, 0.6F * fade),
                16, t * 0.05, 3F, h / 5F, scroll);
        FdQuads.texCylinder(flame, ps.last(), w * 0.9, w * 0.65, 0, h * 0.92, FdQuads.argb(0xFFFFFF, 0.7F * fade), FdQuads.argb(0xFFFFFF, 0F),
                18, -t * 0.08, 4F, h / 6F, scroll * 1.6F);
        // Pegar outro buffer encerra o anterior: o de brilho é pedido de novo depois das chamas
        glow = buffers.getBuffer(FdQuads.glow());
        FdQuads.cylinder(glow, m, w * 1.08, w * 0.7, 0, h, FdQuads.argb(0xE0202E, 0.55F * fade), FdQuads.argb(0x800010, 0F), 20, t * 0.1);
        FdQuads.cylinder(glow, m, w * 1.35, w * 1.1, 0, h * 0.35, FdQuads.argb(0xFF4050, 0.35F * fade), FdQuads.argb(0xFF4050, 0F), 20, -t * 0.1);
        FdQuads.ring(glow, m, 0.05, w * 0.6, w * 2.2, FdQuads.argb(0xFF3040, 0.6F * fade), FdQuads.argb(0x400008, 0F), 32, 0);
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public boolean shouldRender(HellfirePillarEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(HellfirePillarEntity e) {
        return FdQuads.BLACK_FLAME;
    }
}
