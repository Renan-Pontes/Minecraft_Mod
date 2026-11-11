package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.CrystalLotusEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Lótus de Cristal: três coroas de pétalas de cristal translúcido (rosa, lilás, branco) que desabrocham, giram com a
 * espiral dos disparos e brilham nas bordas; no centro, um pistilo de luz pulsando e um anel no chão.
 */
public class CrystalLotusRenderer extends EntityRenderer<CrystalLotusEntity> {
    private static final int[] FILL = {0xE07AF0, 0xF0A8FF, 0xFFE4FF};
    private static final int[] EDGE = {0xFF9CFF, 0xFFD0FF, 0xFFFFFF};
    private static final int[] COUNT = {9, 7, 5};
    private static final float[] LEN = {1.7F, 1.2F, 0.7F};
    private static final float[] WID = {0.7F, 0.55F, 0.32F};
    private static final float[] OPEN_ELEV = {22F, 48F, 70F};

    public CrystalLotusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(CrystalLotusEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float life = e.openness(pt, 6);
        if (life <= 0) return;
        float t = e.tickCount + pt;
        float bloom = Mth.clamp(t / 12F, 0F, 1F);
        bloom = 1F - (1F - bloom) * (1F - bloom);
        bloom *= life;
        float rot = e.spiral(pt) * 0.3F;
        ps.pushPose();
        Matrix4f m = ps.last().pose();
        // Dois passes: primeiro o cristal translúcido, depois as bordas acesas (pedir outro buffer encerra o anterior)
        petals(ps, buffers.getBuffer(FdQuads.dark()), bloom, rot, life, false);
        VertexConsumer glow = buffers.getBuffer(FdQuads.glow());
        petals(ps, glow, bloom, rot, life, true);
        // Pistilo de luz e anel no chão
        float pulse = 0.8F + 0.2F * Mth.sin(t * 0.5F);
        Vec3 cam = FdQuads.cameraLocal(e, pt);
        Vec3 core = new Vec3(0, 0.75, 0);
        FdQuads.halo(glow, m, core, cam, 0, 0.45 * pulse * bloom, FdQuads.argb(0xFFFFFF, 0.95F * life), FdQuads.argb(0xFF9CFF, 0F), 20);
        FdQuads.halo(glow, m, core, cam, 0.2, 1.1 * bloom, FdQuads.argb(0xFFB0FF, 0.35F * life), FdQuads.argb(0xE07AF0, 0F), 24);
        FdQuads.ring(glow, m, 0.03, 1.6 * bloom, 1.75 * bloom, FdQuads.argb(0xFFC8FF, 0.6F * life), FdQuads.argb(0xE07AF0, 0.05F), 40, rot);
        FdQuads.ring(glow, m, 0.02, 0, 2.2 * bloom, FdQuads.argb(0xE07AF0, 0.18F * life), FdQuads.argb(0xE07AF0, 0F), 32, 0);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    private static void petals(PoseStack ps, VertexConsumer vc, float bloom, float rot, float life, boolean edges) {
        Matrix4f m = ps.last().pose();
        Vec3 base = new Vec3(0, 0.12, 0);
        for (int layer = 0; layer < 3; layer++) {
            int n = COUNT[layer];
            float elev = (float) Math.toRadians(Mth.lerp(bloom, 84F, OPEN_ELEV[layer]));
            float len = LEN[layer] * (0.4F + 0.6F * bloom), w = WID[layer];
            for (int k = 0; k < n; k++) {
                double a = rot * (layer % 2 == 0 ? 1 : -0.6) + Math.PI * 2 * (k + layer * 0.5) / n;
                Vec3 d = new Vec3(Math.cos(a) * Math.cos(elev), Math.sin(elev), Math.sin(a) * Math.cos(elev));
                Vec3 p = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(w * 0.5);
                Vec3 mid = base.add(d.scale(len * 0.42)).add(0, 0.08, 0);
                Vec3 tip = base.add(d.scale(len)).add(0, 0.18 * (1 - layer * 0.3), 0);
                if (!edges) {
                    FdQuads.tex(vc, ps.last(), base, mid.add(p), tip, mid.subtract(p), FdQuads.argb(FILL[layer], 0.5F * life), 0, 0, 1, 1);
                    continue;
                }
                int ce = FdQuads.argb(EDGE[layer], 0.75F * life), c0 = FdQuads.argb(EDGE[layer], 0.1F * life);
                FdQuads.color(vc, m, base, c0, mid.add(p), ce, mid.add(p.scale(0.82)), c0, base, c0);
                FdQuads.color(vc, m, mid.add(p), ce, tip, ce, tip, ce, mid.add(p.scale(0.82)), c0);
                FdQuads.color(vc, m, base, c0, mid.subtract(p), ce, mid.subtract(p.scale(0.82)), c0, base, c0);
                FdQuads.color(vc, m, mid.subtract(p), ce, tip, ce, tip, ce, mid.subtract(p.scale(0.82)), c0);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CrystalLotusEntity e) {
        return FdQuads.WHITE;
    }
}
