package com.renanpontes.isekaimahou.feature.legends_i.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.legends_i.LiNet;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Cliente dos efeitos dos glifos de anime: efeitos curtos ({@link LiNet.Fx}), estados por entidade ({@link LiNet.State}:
 * costelas do Susanoo, asas de papel, mão do Chidori — os braços negros do Haki ficam em {@link HakiArms}) e os
 * contornos do Haki da Observação ({@link LiNet.Sense}, desenhados através das paredes só para quem usou).
 * Tudo com teto de quantidade e alcance; no preset gráfico baixo os desenhos ficam mais simples.
 */
public final class LiFxClient {
    private static final int MAX = 200;
    private static final double DRAW_RANGE = 128;
    private static final List<Fx> ACTIVE = new ArrayList<>();
    /** id da entidade → fim (em ticks do cliente) de cada estado. */
    static final Map<Integer, long[]> STATES = new HashMap<>();
    /** presenças sentidas: id → (fim, ameaça?) */
    private static final Map<Integer, long[]> SENSED = new HashMap<>();
    private static ClientLevel level;
    static long clientTicks;

    private static final class Fx {
        final byte kind;
        final Vec3 pos;
        final Vec3 dir;
        final int color;
        final int life;
        final float size;
        final long seed;
        int age;

        Fx(LiNet.Fx p, long seed) {
            this.kind = p.kind();
            this.pos = p.pos();
            this.dir = p.dir();
            this.color = p.color();
            this.life = Math.max(1, p.life());
            this.size = p.size();
            this.seed = seed;
        }
    }

    static boolean reduced() {
        return !FxQuality.atLeast(MahouConfig.Graphics.HIGH);
    }

    private static void checkLevel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            ACTIVE.clear();
            STATES.clear();
            SENSED.clear();
            level = mc.level;
        }
    }

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    public static void add(LiNet.Fx p) {
        checkLevel();
        if (ACTIVE.size() >= MAX) ACTIVE.remove(0);
        ACTIVE.add(new Fx(p, clientTicks * 31 + ACTIVE.size() * 7919L + (long) (p.pos().x * 13 + p.pos().z * 7)));
    }

    public static void state(LiNet.State p) {
        checkLevel();
        if (p.kind() < 0 || p.kind() >= LiNet.STATE_KINDS) return;
        long[] arr = STATES.computeIfAbsent(p.entity(), k -> new long[LiNet.STATE_KINDS]);
        arr[p.kind()] = p.ticks() > 0 ? clientTicks + p.ticks() : 0;
    }

    public static void sense(LiNet.Sense p) {
        checkLevel();
        SENSED.clear();
        for (int i = 0; i < p.ids().length; i++) SENSED.put(p.ids()[i], new long[]{clientTicks + p.ticks(), p.hostile()[i] ? 1 : 0});
    }

    static boolean has(int entity, byte kind) {
        long[] arr = STATES.get(entity);
        return arr != null && arr[kind] > clientTicks;
    }

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        checkLevel();
        clientTicks++;
        for (Iterator<Fx> it = ACTIVE.iterator(); it.hasNext(); ) {
            Fx fx = it.next();
            if (++fx.age >= fx.life) it.remove();
        }
        if (clientTicks % 20 == 0) {
            STATES.values().removeIf(arr -> {
                for (long end : arr) if (end > clientTicks) return false;
                return true;
            });
            SENSED.values().removeIf(v -> v[0] <= clientTicks);
        }
        PaperGlide.tick(mc);
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level || (ACTIVE.isEmpty() && STATES.isEmpty() && SENSED.isEmpty())) return;
        float pt = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        float time = clientTicks + pt;
        boolean low = reduced();

        // ---------- peças "sólidas" (braço de borracha, núcleo negro, pedras, asas, relâmpago negro) ----------
        VertexConsumer solid = buffers.getBuffer(LiRenderTypes.SOLID);
        Matrix4f m = ps.last().pose();
        for (Fx fx : ACTIVE) {
            if (fx.pos.distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            float t = Mth.clamp((fx.age + pt) / fx.life, 0F, 1F);
            Vec3 c = fx.pos.subtract(cam);
            switch (fx.kind) {
                case LiNet.GOMU -> LiShapes.gomuArm(solid, m, c, fx.dir, fx.size, fx.color, t);
                case LiNet.CHIBAKU -> LiShapes.chibakuSolid(solid, m, c, fx.size, t, fx.age + pt, fx.life, left, up, low, fx.seed);
                case LiNet.CONQUEROR -> LiShapes.bolts(solid, m, c, fx.size, 0x0A0006, 0.12F, 0.9F * (1 - t), fx.seed + fx.age / 2,
                        low ? 5 : 10, cam.subtract(fx.pos));
                default -> { }
            }
        }
        for (Map.Entry<Integer, long[]> en : STATES.entrySet()) {
            if (en.getValue()[LiNet.S_WINGS] <= clientTicks) continue;
            Entity ent = level.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity le) || skipSelf(mc, ent) || ent.position().distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            LiShapes.paperWings(solid, ps, le.getPosition(pt).subtract(cam), le, pt, time);
        }
        buffers.endBatch(LiRenderTypes.SOLID);

        // ---------- brilho aditivo ----------
        VertexConsumer glow = buffers.getBuffer(LiRenderTypes.GLOW);
        m = ps.last().pose();
        for (Fx fx : ACTIVE) {
            if (fx.pos.distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            float t = Mth.clamp((fx.age + pt) / fx.life, 0F, 1F);
            Vec3 c = fx.pos.subtract(cam);
            Vec3 toCam = cam.subtract(fx.pos);
            switch (fx.kind) {
                case LiNet.CONQUEROR -> {
                    LiShapes.bolts(glow, m, c, fx.size, fx.color, 0.35F, 0.9F * (1 - t), fx.seed + fx.age / 2, low ? 5 : 10, toCam);
                    LiShapes.billboard(glow, m, c, left, up, 1.5F + fx.size * 0.15F * (1 - t), LiShapes.argb(fx.color, 0.7F * (1 - t)), LiShapes.argb(fx.color, 0F));
                }
                case LiNet.SHINRA -> LiShapes.shinra(glow, ps, c, fx.size, fx.color, t, left, up);
                case LiNet.BANSHO -> LiShapes.wavyLine(glow, m, c, fx.dir, fx.color, 0.18F, 1F - t, time, toCam);
                case LiNet.CHIDORI -> LiShapes.lightning(glow, m, c, fx.dir, fx.color, fx.size, 1F - t, fx.seed + fx.age, toCam, low ? 1 : 3);
                case LiNet.SUSANOO_SLASH -> LiShapes.crescent(glow, m, c, fx.dir, fx.size * (0.7F + 0.3F * t), fx.color, 1F - t);
                case LiNet.CHIBAKU -> LiShapes.chibakuGlow(glow, m, c, fx.size, t, fx.age + pt, fx.life, left, up, fx.color);
                case LiNet.GOMU -> {
                    if (t < 0.35F) {
                        Vec3 tip = c.add(fx.dir.scale(LiShapes.armReach(t)));
                        LiShapes.billboard(glow, m, tip, left, up, 0.5F, LiShapes.argb(0xFFFFFF, 0.5F * (1 - t * 2)), LiShapes.argb(0xFFFFFF, 0F));
                    }
                }
                default -> { }
            }
        }
        for (Map.Entry<Integer, long[]> en : STATES.entrySet()) {
            long[] arr = en.getValue();
            boolean susanoo = arr[LiNet.S_SUSANOO] > clientTicks, chidori = arr[LiNet.S_CHIDORI] > clientTicks;
            if (!susanoo && !chidori) continue;
            Entity ent = level.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity le) || ent.position().distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            Vec3 p = le.getPosition(pt).subtract(cam);
            if (susanoo) {
                float left0 = (arr[LiNet.S_SUSANOO] - clientTicks - pt);
                float fade = Mth.clamp(left0 / 10F, 0F, 1F);
                LiShapes.susanoo(glow, ps, p, le, pt, time, fade, skipSelf(mc, ent), low);
            }
            if (chidori && !skipSelf(mc, ent)) LiShapes.chidoriHand(glow, m, p, le, pt, time, left, up, cam.subtract(le.getPosition(pt)));
        }
        buffers.endBatch(LiRenderTypes.GLOW);

        // ---------- contornos do Haki da Observação (através das paredes) ----------
        if (!SENSED.isEmpty()) {
            VertexConsumer lines = buffers.getBuffer(LiRenderTypes.XRAY);
            float pulse = 0.55F + 0.25F * Mth.sin(time * 0.25F);
            for (Map.Entry<Integer, long[]> en : SENSED.entrySet()) {
                Entity ent = level.getEntity(en.getKey());
                if (ent == null || !ent.isAlive()) continue;
                long[] v = en.getValue();
                float fade = Mth.clamp((v[0] - clientTicks - pt) / 10F, 0F, 1F);
                if (fade <= 0) continue;
                Vec3 p = ent.getPosition(pt);
                AABB box = ent.getBoundingBox().move(p.subtract(ent.position())).move(-cam.x, -cam.y, -cam.z).inflate(0.05);
                boolean hostile = v[1] == 1;
                LevelRenderer.renderLineBox(ps, lines, box, hostile ? 1F : 0.95F, hostile ? 0.2F : 0.95F, hostile ? 0.25F : 0.95F,
                        pulse * fade * (hostile ? 1F : 0.6F));
            }
            buffers.endBatch(LiRenderTypes.XRAY);
        }
    }

    /** O próprio jogador em primeira pessoa (não desenha coisas na cara da câmera). */
    static boolean skipSelf(Minecraft mc, Entity ent) {
        return ent == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }

    static RandomSource rng(long seed) {
        return RandomSource.create(seed);
    }

    private LiFxClient() {}
}
