package com.renanpontes.isekaimahou.feature.legends_e.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.legends_e.LeNet;
import com.renanpontes.isekaimahou.feature.legends_e.LeUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Efeitos curtos das Magias de Nível Supremo (cliente), vindos do servidor por {@link LeNet.Fx}: lanças de luz caindo
 * do céu, meteoros em brasa com rastro de fogo, ondas de choque em anel e o clarão na tela.
 */
public final class LeFxClient {
    private static final int MAX = 400;
    private static final double DRAW_RANGE = 200;
    /** Ticks que o impacto continua brilhando depois de a lança/meteoro chegar. */
    private static final int AFTER = 10;
    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final List<Fx> FLASHES = new ArrayList<>();
    private static ClientLevel level;

    private static final class Fx {
        final byte kind;
        final Vec3 a;
        final Vec3 b;
        final int color;
        final int travel;
        final float size;
        final long seed;
        int age;

        Fx(LeNet.Fx p, long seed) {
            this.kind = p.kind();
            this.a = p.a();
            this.b = p.b();
            this.color = p.color();
            this.travel = Math.max(1, p.life());
            this.size = p.size();
            this.seed = seed;
        }

        int life() {
            return kind == LeNet.LANCE || kind == LeNet.METEOR ? travel + AFTER : travel;
        }
    }

    public static void add(LeNet.Fx p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level != level) {
            ACTIVE.clear();
            FLASHES.clear();
            level = mc.level;
        }
        List<Fx> list = p.kind() == LeNet.FLASH ? FLASHES : ACTIVE;
        if (list.size() >= MAX) list.remove(0);
        list.add(new Fx(p, mc.level.random.nextLong()));
    }

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            ACTIVE.clear();
            FLASHES.clear();
            level = mc.level;
            return;
        }
        if (mc.isPaused() || level == null) return;
        boolean reduced = LeUtil.reducedParticles();
        Iterator<Fx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            fx.age++;
            // Rastro de fogo e fumaça do meteoro
            if (fx.kind == LeNet.METEOR && fx.age <= fx.travel) {
                Vec3 h = meteorHead(fx, fx.age);
                int n = reduced ? 1 : 3;
                for (int i = 0; i < n; i++) {
                    double s = fx.size * 0.6;
                    level.addParticle(Vfx.largeSmoke(), h.x + (level.random.nextDouble() - 0.5) * s, h.y + (level.random.nextDouble() - 0.5) * s,
                            h.z + (level.random.nextDouble() - 0.5) * s, 0, 0.02, 0);
                    level.addParticle(Vfx.flame(), h.x, h.y, h.z, (level.random.nextDouble() - 0.5) * 0.2, 0.05, (level.random.nextDouble() - 0.5) * 0.2);
                }
            }
            if (fx.age >= fx.life()) it.remove();
        }
        FLASHES.removeIf(f -> ++f.age >= f.life());
    }

    private static Vec3 meteorHead(Fx fx, float age) {
        float t = Mth.clamp(age / fx.travel, 0F, 1F);
        return fx.a.add(fx.b.scale(Math.pow(t, 1.35)));
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
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();

        // Rochas dos meteoros primeiro (translúcidas, escurecem), depois todo o brilho aditivo por cima
        VertexConsumer dark = buffers.getBuffer(LeRenderTypes.DARK);
        for (Fx fx : ACTIVE) {
            if (fx.kind != LeNet.METEOR || !near(fx, cam) || fx.age + pt > fx.travel) continue;
            Vec3 h = meteorHead(fx, fx.age + pt).subtract(cam);
            ps.pushPose();
            ps.translate(h.x, h.y, h.z);
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees((fx.age + pt) * 9F));
            ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees((fx.age + pt) * 6F));
            LeDraw.rock(dark, ps.last().pose(), fx.size, 8, 12, fx.seed,
                    new int[] {0xFF2A1408, 0xFF3A1A0A, 0xFF4A220E, 0xFFB8400E, 0xFF1E0E06});
            ps.popPose();
        }
        buffers.endBatch(LeRenderTypes.DARK);

        VertexConsumer glow = buffers.getBuffer(LeRenderTypes.GLOW);
        Matrix4f m = ps.last().pose();
        for (Fx fx : ACTIVE) {
            if (!near(fx, cam)) continue;
            switch (fx.kind) {
                case LeNet.LANCE -> lance(glow, m, fx, cam, left, up, pt);
                case LeNet.METEOR -> meteor(glow, m, fx, cam, left, up, pt);
                case LeNet.RING -> ring(glow, ps, fx, cam, pt);
                default -> {}
            }
        }
        buffers.endBatch(LeRenderTypes.GLOW);
    }

    private static boolean near(Fx fx, Vec3 cam) {
        return fx.a.distanceToSqr(cam) < DRAW_RANGE * DRAW_RANGE || fx.a.add(fx.b).distanceToSqr(cam) < DRAW_RANGE * DRAW_RANGE;
    }

    /** Lança de luz: a ponta desce rápido deixando um rastro; ao chegar, uma coluna de luz e um clarão no ponto. */
    private static void lance(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, Vector3f left, Vector3f up, float pt) {
        float age = fx.age + pt;
        Vec3 from = fx.a.subtract(cam);
        Vec3 to = from.add(fx.b);
        if (age <= fx.travel) {
            float t = age / fx.travel;
            float ease = t * t;
            Vec3 head = from.add(fx.b.scale(ease));
            Vec3 tail = from.add(fx.b.scale(Math.max(0F, ease - 0.45F)));
            Vec3 side = LeDraw.facingSide(tail, head, Vec3.ZERO);
            LeDraw.softRibbon(vc, m, tail, head, side, fx.size * 0.25F, fx.size * 0.5F, 0xFFFFFF, 0.2F, 1F);
            LeDraw.softRibbon(vc, m, tail, head, side, fx.size * 0.9F, fx.size * 1.6F, fx.color, 0.05F, 0.6F);
            LeDraw.billboard(vc, m, head, left, up, fx.size * 1.8F, LeDraw.argb(0xFFFFFF, 0.9F), LeDraw.argb(fx.color, 0F));
            return;
        }
        float k = 1F - (age - fx.travel) / AFTER;
        if (k <= 0) return;
        Vec3 top = to.add(0, 10, 0);
        Vec3 side = LeDraw.facingSide(to, top, Vec3.ZERO);
        LeDraw.softRibbon(vc, m, to, top, side, fx.size * 1.4F * k, fx.size * 0.4F * k, LeDraw.lighten(fx.color, 0.4F), 0.9F * k, 0F);
        LeDraw.billboard(vc, m, to, left, up, fx.size * (3F + 3F * (1 - k)), LeDraw.argb(0xFFFFFF, k), LeDraw.argb(fx.color, 0F));
    }

    /** Meteoro: halo de fogo em volta da rocha e cauda longa; ao chegar, uma bola de fogo que se apaga. */
    private static void meteor(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, Vector3f left, Vector3f up, float pt) {
        float age = fx.age + pt;
        if (age <= fx.travel) {
            Vec3 h = meteorHead(fx, age).subtract(cam);
            Vec3 dir = fx.b.normalize();
            Vec3 tail = h.subtract(dir.scale(fx.size * 9));
            Vec3 side = LeDraw.facingSide(tail, h, Vec3.ZERO);
            LeDraw.softRibbon(vc, m, tail, h, side, fx.size * 0.3F, fx.size * 1.3F, 0xFF6A1A, 0F, 0.85F);
            LeDraw.softRibbon(vc, m, h.subtract(dir.scale(fx.size * 4)), h, side, fx.size * 0.2F, fx.size * 0.8F, 0xFFE08A, 0F, 0.9F);
            LeDraw.billboard(vc, m, h, left, up, fx.size * 2.2F, LeDraw.argb(0xFFB040, 0.75F), LeDraw.argb(0xFF3A00, 0F));
            return;
        }
        float k = 1F - (age - fx.travel) / AFTER;
        if (k <= 0) return;
        Vec3 to = fx.a.add(fx.b).subtract(cam);
        LeDraw.billboard(vc, m, to, left, up, fx.size * (3F + 5F * (1 - k)), LeDraw.argb(0xFFE6A0, k), LeDraw.argb(0xFF4A00, 0F));
    }

    /** Onda de choque em anel que se expande e some. */
    private static void ring(VertexConsumer vc, PoseStack ps, Fx fx, Vec3 cam, float pt) {
        float t = Mth.clamp((fx.age + pt) / fx.travel, 0F, 1F);
        float ease = 1F - (1F - t) * (1F - t) * (1F - t);
        float r = fx.size * (0.1F + 0.9F * ease);
        float a = (1F - t) * 0.85F;
        float w = 0.2F + r * 0.12F;
        Vec3 c = fx.a.subtract(cam);
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        ps.mulPose(LeDraw.yTo(fx.b));
        Matrix4f mm = ps.last().pose();
        LeDraw.ringFlat(vc, mm, r, w, fx.color, a, 64);
        LeDraw.ringFlat(vc, mm, r * 0.85F, w * 0.5F, 0xFFFFFF, a * 0.6F, 48);
        ps.popPose();
    }

    /** Clarão na tela inteira (some com F1 como o resto do HUD). */
    static void gui(RenderGuiEvent.Post e) {
        if (FLASHES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        int w = e.getGuiGraphics().guiWidth(), h = e.getGuiGraphics().guiHeight();
        for (Fx f : FLASHES) {
            float t = Mth.clamp((f.age + pt) / f.travel, 0F, 1F);
            float a = f.size * (float) Math.pow(1F - t, 1.6);
            if (a <= 0.01F) continue;
            e.getGuiGraphics().fill(0, 0, w, h, LeDraw.argb(f.color, Math.min(0.9F, a)));
        }
    }

    private LeFxClient() {}
}
