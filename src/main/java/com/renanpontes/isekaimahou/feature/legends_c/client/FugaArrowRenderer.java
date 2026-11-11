package com.renanpontes.isekaimahou.feature.legends_c.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.entity.FugaArrowEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Fuga: (1) um ARCO DE CHAMAS se forma na mão (o arco cresce das pontas, a corda puxa a flecha para trás, labaredas
 * saem dos braços do arco); (2) a FLECHA flamejante (núcleo branco-amarelo, chama laranja, línguas de fogo); (3) a
 * COLUNA DE FOGO — dois cilindros de chama que ondulam e sobem, núcleo claro, anel de brasa no chão.
 */
public class FugaArrowRenderer extends EntityRenderer<FugaArrowEntity> {
    private static final int YELLOW = 0xFFE08A;
    private static final int ORANGE = LcFx.FIRE;
    private static final int DEEP = 0xD0300A;

    public FugaArrowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(FugaArrowEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.phaseAge() + pt;
        float time = e.tickCount + pt;
        Vec3 pos = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 cam = entityRenderDispatcher.camera.getPosition();
        switch (e.phase()) {
            case FugaArrowEntity.DRAW -> renderBow(e, age, time, pos, cam, pt, pose, buffers);
            case FugaArrowEntity.FLY -> renderArrow(e, time, cam.subtract(pos), pose, buffers);
            default -> renderColumn(e, age, time, cam.subtract(pos), pose, buffers);
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    // =====================================================================================
    //  Arco na mão
    // =====================================================================================
    private void renderBow(FugaArrowEntity e, float age, float time, Vec3 pos, Vec3 cam, float pt, PoseStack pose, MultiBufferSource buffers) {
        LivingEntity o = e.ownerEntity();
        Vec3 hand = o != null ? FugaArrowEntity.handPos(o, pt) : pos;
        Vec3 f = o != null ? o.getViewVector(pt) : e.dir();
        Vec3 right = f.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(f).normalize();
        float prog = Mth.clamp(age / FugaArrowEntity.DRAW_TICKS, 0F, 1F);
        float grow = Mth.clamp(age / 8F, 0F, 1F);
        Vec3 base = hand.subtract(pos);
        Vec3 camRel = cam.subtract(hand);
        VertexConsumer g = buffers.getBuffer(LcRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        float R = 0.85F;
        // Braços do arco (crescem do punho para as pontas)
        int n = 12;
        Vec3 prev = null;
        Vec3 tipTop = null, tipBottom = null;
        for (int s = -1; s <= 1; s += 2) {
            prev = null;
            for (int i = 0; i <= n; i++) {
                float th = s * (i / (float) n) * 1.15F * grow;
                Vec3 p = base.add(up.scale(R * Mth.sin(th))).add(f.scale(R * (Mth.cos(th) - 0.55F) * 0.9F));
                if (prev != null) {
                    Vec3 side = sideFor(prev, p, base, camRel);
                    LcDraw.softRibbon(g, m, prev, p, side, 0.16F, 0.12F, ORANGE, 0.85F, 0.8F);
                    LcDraw.ribbon(g, m, prev, p, side, 0.035F, 0.03F, LcDraw.argb(YELLOW, 1F), LcDraw.argb(YELLOW, 1F));
                }
                prev = p;
            }
            if (s < 0) tipBottom = prev;
            else tipTop = prev;
        }
        // Labaredas saindo do arco
        RandomSource rnd = RandomSource.create((long) (time * 0.5F) * 31L + e.getId());
        for (int i = 0; i < 10; i++) {
            float th = (rnd.nextFloat() * 2F - 1F) * 1.15F * grow;
            Vec3 p = base.add(up.scale(R * Mth.sin(th))).add(f.scale(R * (Mth.cos(th) - 0.55F) * 0.9F));
            Vec3 tip = p.add(0, 0.25 + rnd.nextFloat() * 0.35, 0).add(f.scale(rnd.nextFloat() * 0.1));
            LcDraw.softRibbon(g, m, p, tip, sideFor(p, tip, base, camRel), 0.07F, 0.0F, ORANGE, 0.7F, 0F);
        }
        if (tipTop == null || tipBottom == null || grow < 1F) return;
        // Corda puxando a flecha para trás
        Vec3 nock = base.add(f.scale(-0.45F * prog - 0.05F));
        LcDraw.ribbon(g, m, tipTop, nock, sideFor(tipTop, nock, base, camRel), 0.015F, 0.015F, LcDraw.argb(YELLOW, 0.9F), LcDraw.argb(YELLOW, 0.9F));
        LcDraw.ribbon(g, m, tipBottom, nock, sideFor(tipBottom, nock, base, camRel), 0.015F, 0.015F, LcDraw.argb(YELLOW, 0.9F), LcDraw.argb(YELLOW, 0.9F));
        Vec3 head = nock.add(f.scale(1.25));
        Vec3 side = sideFor(nock, head, base, camRel);
        LcDraw.softRibbon(g, m, nock, head, side, 0.14F * prog + 0.04F, 0.2F * prog + 0.05F, ORANGE, 0.9F, 0.9F);
        LcDraw.ribbon(g, m, nock, head, side, 0.03F, 0.05F, LcDraw.argb(0xFFFFFF, 1F), LcDraw.argb(YELLOW, 1F));
        pose.pushPose();
        pose.translate(head.x, head.y, head.z);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        LcDraw.halo(g, pose.last().pose(), 0F, 0.12F + 0.25F * prog, 0.4F + 0.9F * prog, YELLOW, 0.9F, 16);
        pose.popPose();
    }

    /** Lado de uma fita entre {@code a} e {@code b} (relativos à entidade), virada para a câmera. */
    private static Vec3 sideFor(Vec3 a, Vec3 b, Vec3 base, Vec3 camRelHand) {
        Vec3 camLocal = camRelHand.add(base);
        Vec3 side = b.subtract(a).cross(camLocal.subtract(a));
        return side.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : side.normalize();
    }

    // =====================================================================================
    //  Flecha em voo
    // =====================================================================================
    private void renderArrow(FugaArrowEntity e, float time, Vec3 camRel, PoseStack pose, MultiBufferSource buffers) {
        Vec3 d = e.dir();
        VertexConsumer g = buffers.getBuffer(LcRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        Vec3 tail = d.scale(-5.0), head = d.scale(1.3);
        Vec3 side = head.subtract(tail).cross(camRel);
        side = side.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : side.normalize();
        LcDraw.softRibbon(g, m, tail, head, side, 0.05F, 0.55F, DEEP, 0F, 0.9F);
        LcDraw.softRibbon(g, m, tail.scale(0.6), head, side, 0.05F, 0.3F, ORANGE, 0F, 1F);
        LcDraw.ribbon(g, m, tail.scale(0.4), head, side, 0.01F, 0.07F, LcDraw.argb(YELLOW, 0F), LcDraw.argb(0xFFFFFF, 1F));
        RandomSource rnd = RandomSource.create((long) time * 17L + e.getId());
        for (int i = 0; i < 8; i++) {
            Vec3 p = d.scale(-rnd.nextFloat() * 4.0);
            Vec3 t = p.add(LcDraw.randomOnSphere(rnd).scale(0.4)).add(0, 0.3, 0);
            Vec3 s = t.subtract(p).cross(camRel);
            s = s.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : s.normalize();
            LcDraw.softRibbon(g, m, p, t, s, 0.12F, 0F, ORANGE, 0.7F, 0F);
        }
        pose.pushPose();
        pose.translate(head.x, head.y, head.z);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        LcDraw.halo(g, pose.last().pose(), 0F, 0.35F, 1.6F, YELLOW, 0.95F, 18);
        pose.popPose();
    }

    // =====================================================================================
    //  Coluna de fogo
    // =====================================================================================
    private void renderColumn(FugaArrowEntity e, float age, float time, Vec3 camRel, PoseStack pose, MultiBufferSource buffers) {
        int total = e.columnTicks();
        float rise = Mth.clamp(age / 6F, 0F, 1F);
        float end = Mth.clamp((total - age) / 15F, 0F, 1F);
        float k = rise * end;
        if (k <= 0F) return;
        float r = e.columnRadius() * (0.6F + 0.4F * rise);
        float h = FugaArrowEntity.COLUMN_HEIGHT * rise;
        VertexConsumer g = buffers.getBuffer(LcRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        cylinder(g, m, r * 1.25F, h * 1.05F, time, DEEP, ORANGE, 0.55F * k, 0F);
        cylinder(g, m, r, h, time * 1.3F, ORANGE, YELLOW, 0.8F * k, 1.7F);
        cylinder(g, m, r * 0.45F, h * 0.9F, time * 1.7F, YELLOW, 0xFFFFFF, 0.9F * k, 3.1F);
        // Anel de brasa no chão
        LcDraw.ringFlat(g, m, r * 1.6F + Mth.sin(time * 0.3F) * 0.2F, r * 0.6F, ORANGE, 0.7F * k, 48);
        LcDraw.radialFlat(g, m, 0F, r * 2.2F, LcDraw.argb(YELLOW, 0.5F * k), LcDraw.argb(DEEP, 0F), 32);
        // Espiral de chamas subindo
        for (int s = 0; s < 3; s++) {
            Vec3 prev = null;
            for (int i = 0; i <= 16; i++) {
                float y = h * i / 16F;
                float a = time * 0.15F + s * Mth.TWO_PI / 3F + i * 0.45F;
                float rr = r * (1.1F + 0.15F * Mth.sin(time * 0.2F + i));
                Vec3 p = new Vec3(Mth.cos(a) * rr, y, Mth.sin(a) * rr);
                if (prev != null) {
                    Vec3 side = p.subtract(prev).cross(camRel.subtract(prev));
                    side = side.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : side.normalize();
                    float al = 0.8F * k * (1F - i / 16F);
                    LcDraw.softRibbon(g, m, prev, p, side, 0.35F, 0.3F, YELLOW, al, al);
                }
                prev = p;
            }
        }
        // Clarão no topo
        pose.pushPose();
        pose.translate(0, h, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        LcDraw.halo(g, pose.last().pose(), 0F, r * 0.8F, r * 3F, ORANGE, 0.7F * k, 24);
        pose.popPose();
    }

    /** Cilindro de chama ondulante: base {@code cBottom} → topo {@code cTop}, sumindo no alto. */
    private static void cylinder(VertexConsumer g, Matrix4f m, float r, float h, float time, int cBottom, int cTop, float alpha, float phase) {
        int seg = 20, bands = 10;
        for (int i = 0; i < bands; i++) {
            float y0 = h * i / bands, y1 = h * (i + 1) / bands;
            float t0 = i / (float) bands, t1 = (i + 1) / (float) bands;
            float a0 = alpha * (1F - t0 * t0), a1 = alpha * (1F - t1 * t1);
            int c0 = mix(cBottom, cTop, t0), c1 = mix(cBottom, cTop, t1);
            for (int j = 0; j < seg; j++) {
                float p0 = Mth.TWO_PI * j / seg, p1 = Mth.TWO_PI * (j + 1) / seg;
                float w00 = r * (1F + 0.14F * Mth.sin(time * 0.5F + y0 * 0.7F + j * 1.3F + phase));
                float w01 = r * (1F + 0.14F * Mth.sin(time * 0.5F + y0 * 0.7F + (j + 1) * 1.3F + phase));
                float w10 = r * (1F + 0.14F * Mth.sin(time * 0.5F + y1 * 0.7F + j * 1.3F + phase)) * (1F - 0.35F * t1);
                float w11 = r * (1F + 0.14F * Mth.sin(time * 0.5F + y1 * 0.7F + (j + 1) * 1.3F + phase)) * (1F - 0.35F * t1);
                g.addVertex(m, Mth.cos(p0) * w00, y0, Mth.sin(p0) * w00).setColor(LcDraw.argb(c0, a0));
                g.addVertex(m, Mth.cos(p1) * w01, y0, Mth.sin(p1) * w01).setColor(LcDraw.argb(c0, a0));
                g.addVertex(m, Mth.cos(p1) * w11, y1, Mth.sin(p1) * w11).setColor(LcDraw.argb(c1, a1));
                g.addVertex(m, Mth.cos(p0) * w10, y1, Mth.sin(p0) * w10).setColor(LcDraw.argb(c1, a1));
            }
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    @Override
    public boolean shouldRender(FugaArrowEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(FugaArrowEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
