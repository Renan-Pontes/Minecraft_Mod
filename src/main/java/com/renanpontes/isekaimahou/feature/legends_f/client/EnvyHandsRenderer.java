package com.renanpontes.isekaimahou.feature.legends_f.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.legends_f.entity.EnvyHandsEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Mãos da Inveja: braços negros e compridos brotam do chão (curvando como fumaça sólida), cada um termina numa mão de
 * dedos longos — aberta procurando, ou fechada em volta do inimigo que agarrou. Contorno roxo fraco e uma poça negra
 * no chão de onde saem.
 */
public class EnvyHandsRenderer extends EntityRenderer<EnvyHandsEntity> {
    private static final int SKIN = 0xFF120818;
    private static final int RIM = 0x8A4FC8;

    public EnvyHandsRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public ResourceLocation getTextureLocation(EnvyHandsEntity e) {
        return LfRenderTypes.WHITE;
    }

    @Override
    public boolean shouldRender(EnvyHandsEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(EnvyHandsEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.age() + pt;
        int life = e.life();
        float h = age < EnvyHandsEntity.RISE ? LfDraw.smooth(age / EnvyHandsEntity.RISE)
                : age > life ? 1F - LfDraw.smooth((age - life) / EnvyHandsEntity.SINK) : 1F;
        if (h <= 0.01F) return;
        Vec3 base = e.getPosition(pt);
        int[] grips = e.grips();
        var level = Minecraft.getInstance().level;
        int dim = LightTexture.pack(0, Math.max(4, LightTexture.sky(light)));
        // Poça negra
        VertexConsumer dark = buffers.getBuffer(LfRenderTypes.DARK);
        pose.pushPose();
        pose.translate(0, 0.03, 0);
        LfDraw.radialFlat(dark, pose.last().pose(), 0, e.radius() * 0.9F * h, LfDraw.argb(0x05000A, 0.85F), LfDraw.argb(0x05000A, 0F), 40);
        pose.popPose();
        // Curvas dos braços (calculadas uma vez; desenhadas em duas passadas: sólido e contorno)
        int n = e.hands();
        Vec3[][] curves = new Vec3[n][];
        boolean[] holding = new boolean[n];
        for (int i = 0; i < n; i++) {
            Vec3 a = e.anchor(i).subtract(e.position());
            Vec3 end;
            Entity t = grips.length > i && grips[i] >= 0 && level != null ? level.getEntity(grips[i]) : null;
            if (t != null) {
                end = t.getPosition(pt).add(0, t.getBbHeight() * 0.55, 0).subtract(base);
                holding[i] = true;
            } else {
                float sway = Mth.sin(age * 0.12F + i * 1.7F) * 0.35F;
                end = a.add(sway, 2.2 + 0.3 * Mth.sin(age * 0.09F + i), Mth.cos(age * 0.1F + i) * 0.35F);
            }
            end = a.add(end.subtract(a).scale(h));
            Vec3 ctrl = a.add(0, Math.max(1.2, end.subtract(a).length() * 0.8) * h, 0)
                    .add(Mth.sin(i * 2.1F + age * 0.05F) * 0.4, 0, Mth.cos(i * 1.3F) * 0.4);
            curves[i] = new Vec3[] {a, ctrl, end};
        }
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(LfRenderTypes.WHITE));
        for (int i = 0; i < n; i++) {
            Vec3 a = curves[i][0], ctrl = curves[i][1], end = curves[i][2];
            Vec3 prev = a;
            int seg = 9;
            for (int k = 1; k <= seg; k++) {
                float s = k / (float) seg;
                Vec3 q = bez(a, ctrl, end, s);
                LfDraw.segment(solid, pose.last(), prev, q, 0.2F - 0.08F * (s - 1F / seg), 0.2F - 0.08F * s, SKIN, dim);
                prev = q;
            }
            Vec3 dir = end.subtract(bez(a, ctrl, end, 0.85F)).normalize();
            hand(solid, pose.last(), end, dir, holding[i], age + i * 10, dim);
        }
        // Contorno roxo
        VertexConsumer glow = buffers.getBuffer(LfRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        Vec3 camRel = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(base);
        for (int i = 0; i < n; i++) {
            Vec3 a = curves[i][0], ctrl = curves[i][1], end = curves[i][2];
            Vec3 p0 = a;
            for (int k = 1; k <= 6; k++) {
                Vec3 q = bez(a, ctrl, end, k / 6F);
                LfDraw.softRibbon(glow, m, p0, q, LfDraw.facingSide(p0, q, camRel), 0.32F, 0.3F, RIM, 0.18F * h, 0.18F * h);
                p0 = q;
            }
        }
    }

    private static Vec3 bez(Vec3 a, Vec3 c, Vec3 b, float t) {
        float u = 1 - t;
        return a.scale(u * u).add(c.scale(2 * u * t)).add(b.scale(t * t));
    }

    /** Mão: palma e 5 dedos longos (abertos ou fechados em garra). */
    private static void hand(VertexConsumer vc, PoseStack.Pose p, Vec3 at, Vec3 dir, boolean closed, float time, int light) {
        Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = dir.cross(ref).normalize();
        Vec3 s2 = dir.cross(s1).normalize();
        Vec3 palm = at.subtract(dir.scale(0.15));
        LfDraw.segment(vc, p, palm.subtract(dir.scale(0.2)), palm.add(dir.scale(0.1)), 0.2F, 0.22F, SKIN, light);
        float open = closed ? 0.15F : 0.8F + 0.15F * Mth.sin(time * 0.2F);
        for (int f = 0; f < 5; f++) {
            double ang = -0.9 + f * 0.45;
            Vec3 side = s1.scale(Math.sin(ang)).add(s2.scale(Math.cos(ang) * 0.4));
            Vec3 knuckle = palm.add(dir.scale(0.1)).add(side.scale(0.16));
            Vec3 mid = knuckle.add(dir.scale(0.28)).add(side.scale(0.25 * open));
            Vec3 tip = mid.add(dir.scale(0.22 * open + 0.05)).add(side.scale(closed ? -0.22 : 0.12)).add(s2.scale(closed ? -0.15 : 0));
            LfDraw.segment(vc, p, knuckle, mid, 0.05F, 0.04F, SKIN, light);
            LfDraw.segment(vc, p, mid, tip, 0.04F, 0.015F, SKIN, light);
        }
    }
}
