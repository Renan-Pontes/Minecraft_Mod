package com.renanpontes.isekaimahou.feature.legends_c.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.LcNet;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Efeitos curtos desenhados no mundo (cliente), vindos do servidor por {@link LcNet.Fx}: os cortes do Desmantelar (fio
 * branco-avermelhado + lâmina do corte), a grade do Clivar sobre o alvo, ondas esféricas, blocos sugados pelo Azul,
 * clarões e ondulações na casca do Infinito. Desenhados depois das partículas, com brilho aditivo.
 */
public final class LcFxClient {
    private static final int MAX = 700;
    private static final int MAX_BLOCKS = 160;
    private static final double DRAW_RANGE = 160;
    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static ClientLevel level;

    private static final class Fx {
        final LcNet.Fx p;
        final long seed;
        int age;

        Fx(LcNet.Fx p, long seed) {
            this.p = p;
            this.seed = seed;
        }

        int life() { return Math.max(1, p.life()); }
    }

    public static void add(LcNet.Fx p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level != level) {
            ACTIVE.clear();
            level = mc.level;
        }
        if (p.kind() == LcNet.SUCK) {
            int blocks = 0;
            for (Fx f : ACTIVE) if (f.p.kind() == LcNet.SUCK) blocks++;
            if (blocks >= MAX_BLOCKS) return;
        }
        if (ACTIVE.size() >= MAX) ACTIVE.remove(0);
        ACTIVE.add(new Fx(p, mc.level.random.nextLong()));
    }

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            ACTIVE.clear();
            level = mc.level;
            return;
        }
        if (mc.isPaused()) return;
        Iterator<Fx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            if (++fx.age >= fx.life()) it.remove();
        }
    }

    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level) return;
        float pt = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // Blocos sugados primeiro (modelos de verdade, com profundidade)
        boolean any = false;
        for (Fx fx : ACTIVE) {
            if (fx.p.kind() == LcNet.SUCK && near(fx, cam)) {
                drawBlock(mc, ps, buffers, fx, cam, pt);
                any = true;
            }
        }
        if (any) buffers.endBatch();

        VertexConsumer glow = buffers.getBuffer(LcRenderTypes.GLOW_FX);
        Matrix4f m = ps.last().pose();
        for (Fx fx : ACTIVE) {
            if (!near(fx, cam)) continue;
            switch (fx.p.kind()) {
                case LcNet.SLASH -> drawSlash(glow, m, fx, cam, pt);
                case LcNet.GRID -> drawGrid(glow, m, fx, cam, camera, pt);
                case LcNet.WAVE -> drawWave(glow, ps, fx, cam, pt);
                case LcNet.FLASH -> drawFlash(glow, m, fx, cam, camera, pt);
                case LcNet.RIPPLE -> drawRipple(glow, ps, fx, cam, pt);
                default -> {}
            }
        }
        buffers.endBatch(LcRenderTypes.GLOW_FX);
    }

    private static boolean near(Fx fx, Vec3 cam) {
        if (fx.p.a().distanceToSqr(cam) < DRAW_RANGE * DRAW_RANGE) return true;
        return fx.p.kind() == LcNet.SLASH && fx.p.b().distanceToSqr(cam) < DRAW_RANGE * DRAW_RANGE;
    }

    private static float fade(Fx fx, float pt, float hold) {
        float age = fx.age + pt;
        if (age < hold) return 1F;
        return Mth.clamp(1F - (age - hold) / Math.max(1F, fx.life() - hold), 0F, 1F);
    }

    // ---------------- Desmantelar: fio + lâmina ----------------
    private static void drawSlash(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float pt) {
        float age = fx.age + pt;
        float reveal = Mth.clamp(age / 2F, 0.08F, 1F);
        float f = fade(fx, pt, 3F);
        Vec3 a = fx.p.a().subtract(cam);
        Vec3 full = fx.p.b().subtract(cam);
        Vec3 b = a.add(full.subtract(a).scale(reveal));
        Vec3 dir = full.subtract(a);
        double len = dir.length();
        if (len < 0.05) return;
        dir = dir.scale(1.0 / len);
        Vec3 side = LcDraw.faceSide(a, b);
        // "Cima" da lâmina: perpendicular ao corte, quase vertical
        Vec3 flat = dir.cross(new Vec3(0, 1, 0));
        flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : flat.normalize();
        Vec3 up = flat.cross(dir).normalize();
        float h = fx.p.size();
        // Lâmina do corte (o plano onde tudo foi fatiado): véu vermelho que some rápido
        float sheet = Mth.clamp(1F - age / 6F, 0F, 1F) * f;
        if (sheet > 0.01F) LcDraw.softRibbon(vc, m, a, b, up, h * 0.5F, h * 0.5F, LcFx.RED, 0.22F * sheet, 0.22F * sheet);
        // Brilho vermelho em volta + fio branco cortante
        LcDraw.softRibbon(vc, m, a, b, side, 0.45F * f + 0.05F, 0.45F * f + 0.05F, LcFx.RED, 0.55F * f, 0.55F * f);
        LcDraw.ribbon(vc, m, a, b, side, 0.05F, 0.05F, LcDraw.argb(fx.p.color(), f), LcDraw.argb(fx.p.color(), f));
        // Pós-imagens paralelas (o corte "vibra")
        for (int k = -1; k <= 1; k += 2) {
            Vec3 off = up.scale(k * (0.12 + 0.05 * Mth.sin(age * 3F + k)));
            LcDraw.ribbon(vc, m, a.add(off), b.add(off), side, 0.02F, 0.02F, LcDraw.argb(0xFFFFFF, 0.5F * f), LcDraw.argb(0xFFFFFF, 0.5F * f));
        }
    }

    // ---------------- Clivar: grade sobre o alvo ----------------
    private static void drawGrid(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, Camera camera, float pt) {
        float age = fx.age + pt;
        Vec3 c = fx.p.a().subtract(cam);
        float hw = (float) fx.p.b().x, hh = (float) fx.p.b().y, spin = (float) fx.p.b().z;
        Vector3f lv = camera.getLeftVector(), uv = camera.getUpVector();
        Vec3 left = new Vec3(lv.x(), lv.y(), lv.z()), upv = new Vec3(uv.x(), uv.y(), uv.z());
        int cuts = Math.max(2, fx.p.extra());
        float ext = Math.max(hw, hh) * 1.35F;
        int perFamily = (cuts + 1) / 2;
        for (int k = 0; k < cuts; k++) {
            float t0 = k * 0.8F;
            if (age < t0) break;
            float reveal = Mth.clamp((age - t0) / 1.2F, 0F, 1F);
            float f = Mth.clamp(1F - (age - t0 - 2F) / Math.max(1F, fx.life() - 4F), 0F, 1F);
            if (f <= 0F) continue;
            int fam = k % 2;
            int idx = k / 2;
            float ang = spin + fam * Mth.HALF_PI + 0.35F * Mth.sin(k * 1.7F + spin);
            float off = perFamily <= 1 ? 0F : (idx / (float) (perFamily - 1) - 0.5F) * 2F * ext * 0.6F;
            // Linha no plano da tela (de frente para a câmera), em volta do alvo
            float ca = Mth.cos(ang), sa = Mth.sin(ang);
            Vec3 along = left.scale(ca * hw / Math.max(hw, hh)).add(upv.scale(sa * hh / Math.max(hw, hh))).normalize();
            Vec3 normal = left.scale(-sa).add(upv.scale(ca)).normalize();
            Vec3 mid = c.add(normal.scale(off));
            Vec3 a = mid.subtract(along.scale(ext));
            Vec3 b = a.add(along.scale(ext * 2F * reveal));
            Vec3 side = LcDraw.faceSide(a, b);
            LcDraw.softRibbon(vc, m, a, b, side, 0.22F, 0.22F, LcFx.RED, 0.6F * f, 0.6F * f);
            LcDraw.ribbon(vc, m, a, b, side, 0.035F, 0.035F, LcDraw.argb(fx.p.color(), f), LcDraw.argb(fx.p.color(), f));
        }
    }

    // ---------------- Onda esférica ----------------
    private static void drawWave(VertexConsumer vc, PoseStack ps, Fx fx, Vec3 cam, float pt) {
        float t = Mth.clamp((fx.age + pt) / fx.life(), 0F, 1F);
        float ease = 1F - (1F - t) * (1F - t) * (1F - t);
        float r = fx.p.size() * (0.1F + 0.9F * ease);
        float a = (1F - t);
        Vec3 c = fx.p.a().subtract(cam);
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        Matrix4f m = ps.last().pose();
        LcDraw.glowSphere(vc, m, r, 12, 20, fx.p.color(), 0.04F * a, 0.75F * a, cam.subtract(fx.p.a()));
        ps.mulPose(LcDraw.yTo(fx.p.b()));
        Matrix4f rm = ps.last().pose();
        float w = 0.2F + r * 0.12F;
        LcDraw.ringFlat(vc, rm, r * 1.05F, w, fx.p.color(), 0.9F * a, 64);
        LcDraw.ringFlat(vc, rm, r * 0.85F, w * 0.4F, 0xFFFFFF, 0.6F * a, 48);
        ps.popPose();
    }

    // ---------------- Clarão ----------------
    private static void drawFlash(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, Camera camera, float pt) {
        float t = Mth.clamp((fx.age + pt) / fx.life(), 0F, 1F);
        float r = fx.p.size() * (0.6F + 0.4F * (float) Math.sqrt(t));
        float a = (1F - t) * (1F - t);
        Vec3 c = fx.p.a().subtract(cam);
        Vector3f lv = camera.getLeftVector(), uv = camera.getUpVector();
        int seg = 20;
        int cIn = LcDraw.argb(LcDraw.lighten(fx.p.color(), 0.7F), a), cOut = LcDraw.argb(fx.p.color(), 0F);
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
            bb(vc, m, c, lv, uv, 0, 0, cIn);
            bb(vc, m, c, lv, uv, x0, y0, cOut);
            bb(vc, m, c, lv, uv, x1, y1, cOut);
            bb(vc, m, c, lv, uv, 0, 0, cIn);
        }
    }

    private static void bb(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float x, float y, int argb) {
        vc.addVertex(m, (float) (c.x + left.x() * x + up.x() * y), (float) (c.y + left.y() * x + up.y() * y),
                (float) (c.z + left.z() * x + up.z() * y)).setColor(argb);
    }

    // ---------------- Ondulação na casca do Infinito ----------------
    private static void drawRipple(VertexConsumer vc, PoseStack ps, Fx fx, Vec3 cam, float pt) {
        float t = Mth.clamp((fx.age + pt) / fx.life(), 0F, 1F);
        float r = fx.p.size() * (0.2F + 0.8F * (float) Math.sqrt(t));
        float a = (1F - t) * 0.8F;
        Vec3 c = fx.p.a().subtract(cam);
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        ps.mulPose(LcDraw.yTo(fx.p.b()));
        Matrix4f m = ps.last().pose();
        LcDraw.ringFlat(vc, m, r, 0.08F + r * 0.15F, fx.p.color(), a, 32);
        LcDraw.ringFlat(vc, m, r * 0.55F, 0.05F + r * 0.1F, 0xFFFFFF, a * 0.7F, 24);
        ps.popPose();
    }

    // ---------------- Bloco sugado pelo Azul ----------------
    private static void drawBlock(Minecraft mc, PoseStack ps, MultiBufferSource buffers, Fx fx, Vec3 cam, float pt) {
        BlockState st = Block.stateById(fx.p.extra());
        if (st.isAir() || st.getRenderShape() != RenderShape.MODEL) return;
        float t = Mth.clamp((fx.age + pt) / fx.life(), 0F, 1F);
        float ease = t * t;
        Vec3 from = fx.p.a(), to = fx.p.b();
        // Espiral para dentro
        Vec3 d = to.subtract(from);
        Vec3 perp = d.cross(new Vec3(0, 1, 0));
        perp = perp.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : perp.normalize();
        Vec3 p = from.add(d.scale(ease)).add(perp.scale(Mth.sin(t * Mth.PI) * d.length() * 0.25F));
        float s = Math.max(0.05F, 0.9F * (1F - 0.85F * t));
        Vec3 rel = p.subtract(cam);
        ps.pushPose();
        ps.translate(rel.x, rel.y, rel.z);
        ps.mulPose(Axis.YP.rotationDegrees((fx.age + pt) * 25F + (fx.seed & 63)));
        ps.mulPose(Axis.XP.rotationDegrees((fx.age + pt) * 17F));
        ps.scale(s, s, s);
        ps.translate(-0.5, -0.5, -0.5);
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
        mc.getBlockRenderer().renderSingleBlock(st, ps, buffers, light, OverlayTexture.NO_OVERLAY,
                net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
        ps.popPose();
    }

    private LcFxClient() {}
}
