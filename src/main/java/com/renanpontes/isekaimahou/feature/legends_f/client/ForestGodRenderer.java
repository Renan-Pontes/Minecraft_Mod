package com.renanpontes.isekaimahou.feature.legends_f.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_f.entity.ForestGodEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Deus da Floresta: um cervo colossal (≈7 blocos com a galhada) desenhado peça por peça — corpo, pescoço longo, rosto
 * pálido quase humano com marcas vermelhas, pernas finas que andam devagar e uma galhada imensa que se ramifica como
 * árvore. Translúcido e com um halo; no modo "só morte" vira um gigante noturno azul-escuro de contorno brilhante.
 */
public class ForestGodRenderer extends EntityRenderer<ForestGodEntity> {
    public ForestGodRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ForestGodEntity e) {
        return LfRenderTypes.WHITE;
    }

    @Override
    public boolean shouldRender(ForestGodEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ForestGodEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float fade = e.fade(pt);
        if (fade <= 0.01F) return;
        boolean death = e.deathMode();
        float time = e.age() + pt;
        float walk = e.walking() ? time * 0.22F : 0F;
        int full = LightTexture.FULL_BRIGHT;
        float alpha = 0.78F * fade;
        int body = LfDraw.argb(death ? 0x2E3A52 : 0xB07048, alpha);
        int belly = LfDraw.argb(death ? 0x3A4A66 : 0xD8B48A, alpha);
        int leg = LfDraw.argb(death ? 0x222C40 : 0x6A4028, alpha);
        int antler = LfDraw.argb(death ? 0x9AB0FF : 0xEADFC4, alpha);
        int face = LfDraw.argb(death ? 0xC8D8FF : 0xF2E6D8, alpha);
        int mark = LfDraw.argb(0xC0182E, alpha);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        float bob = Mth.sin(walk * 2F) * 0.06F;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(LfRenderTypes.WHITE));
        PoseStack.Pose root = pose.last();
        // Pernas (andam em pares diagonais)
        float[][] hips = {{0.5F, 1.35F, 0F}, {-0.5F, 1.35F, (float) Math.PI}, {0.5F, -1.3F, (float) Math.PI}, {-0.5F, -1.3F, 0F}};
        for (float[] h : hips) {
            float sw = Mth.sin(walk + h[2]);
            Vec3 hip = new Vec3(h[0], 3.0 + bob, h[1]);
            Vec3 knee = new Vec3(h[0] * 1.05, 1.6, h[1] + 0.35 * sw + (h[1] > 0 ? 0.1 : -0.15));
            Vec3 hoof = new Vec3(h[0], Math.max(0, 0.15 * Mth.cos(walk + h[2])), h[1] + 0.6 * sw);
            LfDraw.segment(vc, root, hip, knee, 0.2F, 0.11F, leg, full);
            LfDraw.segment(vc, root, knee, hoof, 0.1F, 0.08F, leg, full);
            LfDraw.cbox(vc, root, (float) hoof.x, (float) hoof.y + 0.08F, (float) hoof.z, 0.12F, 0.08F, 0.14F, LfDraw.argb(0x2A1A10, alpha), full);
        }
        // Corpo
        pose.pushPose();
        pose.translate(0, 3.35 + bob, 0);
        LfDraw.ellipsoid(vc, pose.last(), 0.85F, 0.8F, 1.85F, 10, 16, body, full);
        pose.translate(0, -0.35, 0);
        LfDraw.ellipsoid(vc, pose.last(), 0.7F, 0.5F, 1.5F, 6, 12, belly, full);
        pose.popPose();
        // Cauda
        LfDraw.segment(vc, root, new Vec3(0, 3.6 + bob, -1.8), new Vec3(0, 3.1 + bob, -2.15), 0.14F, 0.06F, belly, full);
        // Pescoço e cabeça
        float nod = Mth.sin(walk) * 4F;
        Vec3 neckBase = new Vec3(0, 3.8 + bob, 1.45);
        Vec3 neckTop = new Vec3(0, 5.0 + bob, 2.25);
        LfDraw.segment(vc, root, neckBase, neckTop, 0.48F, 0.32F, body, full);
        pose.pushPose();
        pose.translate(neckTop.x, neckTop.y + 0.1, neckTop.z + 0.25);
        pose.mulPose(Axis.XP.rotationDegrees(15F + nod));
        PoseStack.Pose head = pose.last();
        LfDraw.ellipsoid(vc, head, 0.36F, 0.4F, 0.5F, 8, 12, body, full);
        // Rosto pálido (quase humano) com marcas vermelhas
        LfDraw.segment(vc, head, new Vec3(0, -0.05, 0.25), new Vec3(0, -0.2, 0.78), 0.25F, 0.16F, face, full);
        LfDraw.cbox(vc, head, 0.14F, 0.05F, 0.42F, 0.05F, 0.02F, 0.02F, mark, full);
        LfDraw.cbox(vc, head, -0.14F, 0.05F, 0.42F, 0.05F, 0.02F, 0.02F, mark, full);
        LfDraw.cbox(vc, head, 0F, 0.16F, 0.36F, 0.02F, 0.06F, 0.02F, mark, full);
        // Orelhas
        LfDraw.segment(vc, head, new Vec3(0.25, 0.25, -0.05), new Vec3(0.6, 0.35, -0.2), 0.08F, 0.03F, body, full);
        LfDraw.segment(vc, head, new Vec3(-0.25, 0.25, -0.05), new Vec3(-0.6, 0.35, -0.2), 0.08F, 0.03F, body, full);
        // Galhada (árvore de galhos)
        for (int s = -1; s <= 1; s += 2) {
            Vec3 start = new Vec3(0.18 * s, 0.32, -0.05);
            branch(vc, head, start, new Vec3(0.45 * s, 1.0, -0.35).normalize(), 1.3F, 0.09F, 0, s, antler, full);
        }
        pose.popPose();
        pose.popPose();

        // Olhos e halo (brilho)
        VertexConsumer glow = buffers.getBuffer(LfRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        int halo = death ? 0x6A8AFF : 0xCFFFE0;
        LfDraw.billboard(glow, m, new Vec3(0, 3.6, 0), left, up, 4.5F, LfDraw.argb(halo, 0.18F * fade), LfDraw.argb(halo, 0F));
        float yawR = -Mth.rotLerp(pt, e.yRotO, e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 eyes = new Vec3(0, 5.25 + bob, 2.85).yRot(yawR);
        Vec3 side = new Vec3(0.2, 0, 0).yRot(yawR);
        int eyeCol = death ? 0xB0C8FF : 0xFFE8A0;
        LfDraw.billboard(glow, m, eyes.add(side), left, up, 0.1F, LfDraw.argb(0xFFFFFF, fade), LfDraw.argb(eyeCol, 0F));
        LfDraw.billboard(glow, m, eyes.subtract(side), left, up, 0.1F, LfDraw.argb(0xFFFFFF, fade), LfDraw.argb(eyeCol, 0F));
        pose.pushPose();
        pose.translate(0, 0.05, 0);
        LfDraw.ringFlat(glow, pose.last().pose(), e.radius(), 0.18F, death ? 0x4A5A30 : 0x9FF0A0, 0.35F * fade, 72);
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    /** Galho recursivo: tronco + pontas que se abrem para fora e para cima. */
    private static void branch(VertexConsumer vc, PoseStack.Pose p, Vec3 from, Vec3 dir, float len, float w, int depth, int side, int col, int light) {
        Vec3 to = from.add(dir.scale(len));
        LfDraw.segment(vc, p, from, to, w, w * 0.65F, col, light);
        if (depth >= 3) return;
        int forks = depth == 0 ? 4 : 2;
        for (int k = 0; k < forks; k++) {
            float t = (k + 1F) / (forks + 1F);
            Vec3 at = from.add(dir.scale(len * (0.35 + 0.6 * t)));
            double spin = (k % 2 == 0 ? 1 : -1) * (0.5 + 0.2 * depth);
            Vec3 nd = dir.add(side * 0.35 * Math.cos(spin), 0.35, 0.4 * Math.sin(spin) - 0.1 * depth).normalize();
            branch(vc, p, at, nd, len * 0.55F, w * 0.6F, depth + 1, side, col, light);
        }
        if (depth == 0) branch(vc, p, to, dir.add(side * 0.2, 0.3, -0.2).normalize(), len * 0.7F, w * 0.65F, depth + 1, side, col, light);
    }
}
