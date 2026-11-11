package com.renanpontes.isekaimahou.feature.legends_b.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.legends_b.LegendsBNet;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Efeitos curtos desenhados no mundo (cliente), vindos do servidor por {@link LegendsBNet.LegendFx}:
 * feixes estilo farol (Lanças do Céu), riscos brancos de corte (Tempestade de Cortes) e ondas de choque em anel.
 * Desenhados depois das partículas, com brilho aditivo.
 */
public final class LegendFxClient {
    private static final int MAX = 600;
    private static final double DRAW_RANGE = 160;
    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static ClientLevel level;

    private static final class Fx {
        final byte kind;
        final Vec3 a;
        final Vec3 b;
        final int color;
        final int life;
        final float size;
        final long seed;
        int age;

        Fx(LegendsBNet.LegendFx p, long seed) {
            this.kind = p.kind();
            this.a = p.a();
            this.b = p.b();
            this.color = p.color();
            this.life = Math.max(1, p.life());
            this.size = p.size();
            this.seed = seed;
        }
    }

    /** Chegou um efeito do servidor (thread principal). */
    public static void add(LegendsBNet.LegendFx p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level != level) {
            ACTIVE.clear();
            level = mc.level;
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
            if (++fx.age >= fx.life) it.remove();
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
        float time = (mc.level.getGameTime() % 100000L) + pt;

        // Feixes primeiro (núcleo opaco escreve profundidade), depois os brilhos aditivos por cima
        for (Fx fx : ACTIVE) {
            if (fx.kind == LegendsBNet.BEAM && near(fx, cam)) drawBeam(ps, buffers, fx, cam, pt, time);
        }
        buffers.endBatch();
        VertexConsumer glow = buffers.getBuffer(LbRenderTypes.GLOW_FX);
        Matrix4f m = ps.last().pose();
        for (Fx fx : ACTIVE) {
            if (!near(fx, cam)) continue;
            switch (fx.kind) {
                case LegendsBNet.BEAM -> beamFlare(glow, m, fx, cam, camera, pt);
                case LegendsBNet.SLASH -> drawSlash(glow, m, fx, cam, pt);
                case LegendsBNet.RING -> drawRing(glow, ps, fx, cam, pt);
                default -> {}
            }
        }
        buffers.endBatch(LbRenderTypes.GLOW_FX);
    }

    private static boolean near(Fx fx, Vec3 cam) {
        return fx.a.distanceToSqr(cam) < DRAW_RANGE * DRAW_RANGE || fx.a.add(fx.b).distanceToSqr(cam) < DRAW_RANGE * DRAW_RANGE;
    }

    // ---------------- Feixe ----------------
    private static void drawBeam(PoseStack ps, MultiBufferSource buffers, Fx fx, Vec3 cam, float pt, float time) {
        float t = (fx.age + pt) / fx.life;
        // Liga num estalo, segura e afina até sumir
        float shape = t < 0.15F ? 0.6F + t / 0.15F * 0.4F : 1F - (float) Math.pow((t - 0.15F) / 0.85F, 2.2);
        float r = fx.size * Math.max(0F, shape);
        LbDraw.beaconBeam(ps, buffers, fx.a.subtract(cam), fx.b, fx.color, r, r * 1.6F, Math.max(0F, shape), time);
    }

    /** Clarão na ponta do feixe e um "núcleo" branco aditivo ao longo dele. */
    private static void beamFlare(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, Camera camera, float pt) {
        float t = (fx.age + pt) / fx.life;
        float a = Mth.clamp(1F - t, 0F, 1F);
        Vec3 from = fx.a.subtract(cam);
        Vec3 to = from.add(fx.b);
        Vec3 dir = fx.b.normalize();
        Vec3 side = dir.cross(to.scale(-1));
        side = side.lengthSqr() < 1.0E-8 ? new Vec3(1, 0, 0) : side.normalize();
        float w = fx.size * 0.9F * a;
        LbDraw.softRibbon(vc, m, from, to, side, w, w, 0xFFFFFF, 0.8F * a, 0.8F * a);
        LbDraw.softRibbon(vc, m, from, to, side, w * 4F, w * 4F, fx.color, 0.35F * a, 0.35F * a);
        // Clarão (quadradinho virado para a câmera) no ponto de impacto
        float s = fx.size * (3.5F + 2F * a) * a;
        billboard(vc, m, to, camera, s, LbDraw.argb(LbDraw.lighten(fx.color, 0.5F), 0.9F * a), LbDraw.argb(fx.color, 0F));
        billboard(vc, m, from, camera, s * 0.6F, LbDraw.argb(0xFFFFFF, 0.6F * a), LbDraw.argb(fx.color, 0F));
    }

    /** "Sol" radial virado para a câmera, desenhado em coordenadas relativas (sem mexer na pose). */
    private static void billboard(VertexConsumer vc, Matrix4f m, Vec3 c, Camera camera, float r, int cIn, int cOut) {
        org.joml.Vector3f left = camera.getLeftVector();
        org.joml.Vector3f up = camera.getUpVector();
        int seg = 12;
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
            vtx(vc, m, c, left, up, 0, 0, cIn);
            vtx(vc, m, c, left, up, x0, y0, cOut);
            vtx(vc, m, c, left, up, x1, y1, cOut);
            vtx(vc, m, c, left, up, 0, 0, cIn);
        }
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Vec3 c, org.joml.Vector3f left, org.joml.Vector3f up, float x, float y, int argb) {
        vc.addVertex(m, (float) (c.x + left.x() * x + up.x() * y), (float) (c.y + left.y() * x + up.y() * y),
                (float) (c.z + left.z() * x + up.z() * y)).setColor(argb);
    }

    // ---------------- Corte ----------------
    /**
     * Meia-lua fina: aparece correndo de uma ponta à outra em 2 ticks, segura um instante e some.
     * A fita fica sempre de frente para a câmera; a curvatura é no plano da fita.
     */
    private static void drawSlash(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float pt) {
        float age = fx.age + pt;
        float reveal = Mth.clamp(age / 2F, 0.05F, 1F);
        float fade = age < 2F ? 1F : Mth.clamp(1F - (age - 2F) / Math.max(1F, fx.life - 2F), 0F, 1F);
        Vec3 c = fx.a.subtract(cam);
        double len = fx.b.length();
        Vec3 dir = fx.b.scale(1.0 / Math.max(1.0E-4, len));
        Vec3 side = dir.cross(c.scale(-1));
        side = side.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : side.normalize();
        // Metade dos cortes curva para cada lado
        float bowSign = (fx.seed & 1L) == 0 ? 1F : -1F;
        int n = 12;
        float uEnd = -1F + 2F * reveal;
        Vec3 prev = null;
        float prevW = 0;
        for (int k = 0; k <= n; k++) {
            float u = -1F + (uEnd + 1F) * k / n;
            float shape = 1F - u * u;
            Vec3 p = c.add(dir.scale(u * len * 0.5)).add(side.scale(bowSign * shape * len * 0.09));
            float w = fx.size * shape * (0.5F + 0.5F * fade);
            if (prev != null) {
                // Brilho azulado suave em volta + fio branco cortante no meio
                LbDraw.softRibbon(vc, m, prev, p, side, prevW * 3.2F, w * 3.2F, 0xBFD8FF, 0.4F * fade, 0.4F * fade);
                LbDraw.ribbon(vc, m, prev, p, side, prevW, w, LbDraw.argb(fx.color, fade), LbDraw.argb(fx.color, fade));
            }
            prev = p;
            prevW = w;
        }
    }

    // ---------------- Onda de choque ----------------
    private static void drawRing(VertexConsumer vc, PoseStack ps, Fx fx, Vec3 cam, float pt) {
        float t = Mth.clamp((fx.age + pt) / fx.life, 0F, 1F);
        float ease = 1F - (1F - t) * (1F - t) * (1F - t);
        float r = fx.size * (0.15F + 0.85F * ease);
        float a = (1F - t) * 0.85F;
        float w = 0.15F + r * 0.16F;
        Vec3 c = fx.a.subtract(cam);
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        ps.mulPose(LbDraw.yTo(fx.b));
        Matrix4f mm = ps.last().pose();
        LbDraw.ringFlat(vc, mm, r, w, fx.color, a, 48);
        LbDraw.ringFlat(vc, mm, r * 0.82F, w * 0.5F, 0xFFFFFF, a * 0.6F, 40);
        ps.popPose();
    }

    private LegendFxClient() {}
}
