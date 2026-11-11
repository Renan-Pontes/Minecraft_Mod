package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import static com.renanpontes.isekaimahou.feature.class_shinigami.client.ShinigamiRenderTypes.argb;
import static com.renanpontes.isekaimahou.feature.class_shinigami.client.ShinigamiRenderTypes.billboard;
import static com.renanpontes.isekaimahou.feature.class_shinigami.client.ShinigamiRenderTypes.mix;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.class_shinigami.PetalSwarm;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFxNet;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Efeitos de anime do Shinigami no cliente (só visual):
 * <ul>
 *   <li><b>Senbonzakura</b>: centenas de pétalas-lâmina rosas seguindo os pontos de controle do servidor — a torrente
 *   que sai da mão e segue a mira, o oceano de pétalas da Bankai, a esfera do Gōkei, o anel de espadas do Senkei e as
 *   lâminas gigantes do Kageyoshi;</li>
 *   <li><b>Pressão espiritual</b> da liberação (anel no chão, coluna de luz e o comando escrito no ar) e da Bankai;</li>
 *   <li>Shunpo (imagem residual + linhas de velocidade), carga do Cero (esfera rubra crescendo), carga da lâmina,
 *   lampejo da Máscara do Vazio, aura de chamas do Vazio, cicatrizes do Getsuga no chão e a tela negra do Mugetsu.</li>
 * </ul>
 * A quantidade de pétalas segue a configuração de partículas. Tudo some ao trocar de mundo.
 */
public final class ShinigamiFxClient {
    private static final int PINK = 0xFF8FC0;
    private static final int PINK_LIGHT = 0xFFD6E8;
    private static final int PINK_DEEP = 0xE2507E;
    private static final float GOLDEN = 0.6180339887F;
    private static final int MAX_LOOSE = 1500;

    // =====================================================================================
    //  Estado
    // =====================================================================================
    private static final class SwarmC {
        final int owner;
        int mode;
        boolean bankai;
        Vec3 server;
        Vec3 head;
        Vec3 prevHead;
        float param;
        int target;
        long modeStart;
        long lastPacket;
        final ArrayDeque<Vec3> hist = new ArrayDeque<>();

        SwarmC(int owner, Vec3 at) {
            this.owner = owner;
            this.server = at;
            this.head = at;
            this.prevHead = at;
        }
    }

    private static final class Fx {
        final ShinigamiFxNet.Burst p;
        final long start;
        final int life;
        int age;
        boolean spawned;

        Fx(ShinigamiFxNet.Burst p, long start, int life) {
            this.p = p;
            this.start = start;
            this.life = life;
        }
    }

    private static final class Petal {
        double x, y, z, vx, vy, vz;
        float rot, spin, size;
        int rgb, age, life;
    }

    private static final Map<Integer, SwarmC> SWARMS = new HashMap<>();
    private static final List<Fx> FX = new ArrayList<>();
    private static final List<Petal> LOOSE = new ArrayList<>();
    private static final RandomSource RND = RandomSource.create();
    private static ClientLevel level;

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    static void onSwarm(ShinigamiFxNet.Swarm p) {
        ClientLevel l = sync();
        if (l == null) return;
        long now = l.getGameTime();
        SwarmC s = SWARMS.get(p.owner());
        if (p.mode() == PetalSwarm.OFF) {
            if (s != null) {
                // a lâmina se refaz: as últimas pétalas se espalham/voltam
                scatter(s.head, s.bankai ? 50 : 30, 0.12, s.mode == PetalSwarm.RETURN ? 0.2F : 1F);
                SWARMS.remove(p.owner());
            }
            return;
        }
        if (s == null) {
            Entity owner = l.getEntity(p.owner());
            Vec3 start = p.mode() == PetalSwarm.STREAM && owner instanceof LivingEntity le ? bladePos(le, 1F) : p.pos();
            s = new SwarmC(p.owner(), start);
            SWARMS.put(p.owner(), s);
            s.modeStart = now;
        }
        if (s.mode != p.mode()) {
            s.mode = p.mode();
            s.modeStart = now;
            if (s.mode == PetalSwarm.STREAM) {
                Entity owner = l.getEntity(p.owner());
                if (owner instanceof LivingEntity le && s.head.distanceToSqr(bladePos(le, 1F)) > 64) s.hist.clear();
            }
        }
        s.server = p.pos();
        s.bankai = p.bankai();
        s.param = p.param();
        s.target = p.target();
        s.lastPacket = now;
    }

    static void onBurst(ShinigamiFxNet.Burst p) {
        ClientLevel l = sync();
        if (l == null) return;
        int life = switch (p.kind()) {
            case ShinigamiFxNet.RELEASE -> 60;
            case ShinigamiFxNet.BANKAI -> 90;
            case ShinigamiFxNet.KAGEYOSHI -> 46;
            case ShinigamiFxNet.SHUNPO -> 14;
            case ShinigamiFxNet.CERO_CHARGE, ShinigamiFxNet.BLADE_CHARGE -> Math.max(6, (int) p.size()) + 4;
            case ShinigamiFxNet.MUGETSU -> 72;
            case ShinigamiFxNet.VISORED -> 16;
            case ShinigamiFxNet.GOKEI_HIT -> 10;
            case ShinigamiFxNet.SCAR -> 90;
            default -> 20;
        };
        if (FX.size() > 400) FX.remove(0);
        FX.add(new Fx(p, l.getGameTime(), life));
        if (p.kind() == ShinigamiFxNet.GOKEI_HIT) scatter(p.pos(), 90, 0.45, 1F);
        if (p.kind() == ShinigamiFxNet.SCAR) scarDebris(l, p);
    }

    private static ClientLevel sync() {
        ClientLevel l = Minecraft.getInstance().level;
        if (l != level) {
            clear();
            level = l;
        }
        return l;
    }

    static void clear() {
        SWARMS.clear();
        FX.clear();
        LOOSE.clear();
        level = null;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    static void tick(ClientTickEvent.Post e) {
        ClientLevel l = sync();
        if (l == null || Minecraft.getInstance().isPaused()) return;
        long now = l.getGameTime();
        Iterator<SwarmC> it = SWARMS.values().iterator();
        while (it.hasNext()) {
            SwarmC s = it.next();
            if (now - s.lastPacket > 40) {
                it.remove();
                continue;
            }
            s.prevHead = s.head;
            double k = s.mode == PetalSwarm.STREAM || s.mode == PetalSwarm.RETURN ? 0.5 : 0.35;
            s.head = s.head.add(s.server.subtract(s.head).scale(k));
            s.hist.addFirst(s.head);
            while (s.hist.size() > 14) s.hist.removeLast();
            // algumas pétalas se soltam do enxame (vivo, não "duro")
            if (s.mode == PetalSwarm.STREAM && RND.nextFloat() < 0.6F * ShinigamiRenderTypes.particleScale()) {
                Petal p = newPetal(s.head.x, s.head.y, s.head.z, 0.05);
                p.life = 20 + RND.nextInt(20);
                LOOSE.add(p);
            }
        }
        Iterator<Fx> fi = FX.iterator();
        while (fi.hasNext()) {
            Fx fx = fi.next();
            fx.age++;
            if (fx.p.kind() == ShinigamiFxNet.KAGEYOSHI && fx.age == 34 && !fx.spawned) {
                fx.spawned = true;
                // as lâminas gigantes viram pétalas
                for (int i = 0; i < 16; i++) {
                    double a = i * Mth.TWO_PI / 16;
                    double r = i % 2 == 0 ? fx.p.size() * 0.7 : fx.p.size();
                    scatter(fx.p.pos().add(Math.cos(a) * r, 2.5 + (i % 3), Math.sin(a) * r), 14, 0.25, 1F);
                }
            }
            if (fx.age >= fx.life) fi.remove();
        }
        Iterator<Petal> pi = LOOSE.iterator();
        while (pi.hasNext()) {
            Petal p = pi.next();
            if (++p.age >= p.life) {
                pi.remove();
                continue;
            }
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            p.vx *= 0.9;
            p.vz *= 0.9;
            p.vy = p.vy * 0.9 - 0.004;
            p.rot += p.spin;
        }
    }

    private static Petal newPetal(double x, double y, double z, double speed) {
        Petal p = new Petal();
        p.x = x;
        p.y = y;
        p.z = z;
        p.vx = (RND.nextDouble() - 0.5) * 2 * speed;
        p.vy = (RND.nextDouble() - 0.3) * 2 * speed;
        p.vz = (RND.nextDouble() - 0.5) * 2 * speed;
        p.rot = RND.nextFloat() * Mth.TWO_PI;
        p.spin = (RND.nextFloat() - 0.5F) * 0.5F;
        p.size = 0.08F + RND.nextFloat() * 0.07F;
        float c = RND.nextFloat();
        p.rgb = c < 0.45F ? PINK : c < 0.8F ? PINK_LIGHT : PINK_DEEP;
        p.life = 30 + RND.nextInt(30);
        return p;
    }

    /** Explosão de pétalas soltas (quantidade × configuração de partículas). */
    private static void scatter(Vec3 at, int count, double speed, float lifeMul) {
        int n = Math.round(count * ShinigamiRenderTypes.particleScale());
        for (int i = 0; i < n && LOOSE.size() < MAX_LOOSE; i++) {
            Petal p = newPetal(at.x, at.y, at.z, speed);
            p.life = Math.max(6, Math.round(p.life * lifeMul));
            LOOSE.add(p);
        }
    }

    private static void scarDebris(ClientLevel l, ShinigamiFxNet.Burst p) {
        BlockPos below = BlockPos.containing(p.pos().subtract(0, 0.5, 0));
        BlockState st = l.getBlockState(below);
        if (st.isAir()) return;
        int n = Math.round(4 * ShinigamiRenderTypes.particleScale());
        for (int i = 0; i < n; i++) {
            l.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), p.pos().x + (RND.nextDouble() - 0.5) * p.size(), p.pos().y + 0.1,
                    p.pos().z + (RND.nextDouble() - 0.5) * p.size(), (RND.nextDouble() - 0.5) * 0.3, 0.3 + RND.nextDouble() * 0.3,
                    (RND.nextDouble() - 0.5) * 0.3);
        }
    }

    // =====================================================================================
    //  Posições
    // =====================================================================================
    /** De onde as pétalas saem (a lâmina na mão) — igual ao servidor, mas interpolado. */
    static Vec3 bladePos(LivingEntity e, float pt) {
        Vec3 look = e.getViewVector(pt);
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        return e.getEyePosition(pt).add(look.scale(0.7)).add(right.scale(0.35)).add(0, -0.45, 0);
    }

    private static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
    }

    // =====================================================================================
    //  Mundo
    // =====================================================================================
    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level) return;
        boolean masks = HollowClient.any();
        if (SWARMS.isEmpty() && FX.isEmpty() && LOOSE.isEmpty() && !masks) return;
        float pt = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = e.getPoseStack();
        Matrix4f m = ps.last().pose();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float time = (mc.level.getGameTime() % 100000L) + pt;
        float scale = ShinigamiRenderTypes.particleScale();

        // ---------------- lâminas, imagem residual, máscara, cicatrizes (mistura normal) ----------------
        if (!FX.isEmpty() || hasSenkei()) {
            RenderType swordT = ShinigamiRenderTypes.translucent(ShinigamiRenderTypes.SWORD);
            VertexConsumer vc = buffers.getBuffer(swordT);
            for (Fx fx : FX) if (fx.p.kind() == ShinigamiFxNet.KAGEYOSHI) drawKageyoshi(vc, m, fx, cam, pt);
            for (SwarmC s : SWARMS.values()) if (s.mode == PetalSwarm.SENKEI) drawSenkei(vc, m, s, cam, time, pt, false);
            buffers.endBatch(swordT);

            RenderType ghostT = ShinigamiRenderTypes.translucent(ShinigamiRenderTypes.GHOST);
            vc = buffers.getBuffer(ghostT);
            for (Fx fx : FX) if (fx.p.kind() == ShinigamiFxNet.SHUNPO) drawShunpoGhost(vc, m, fx, cam, pt);
            buffers.endBatch(ghostT);

            RenderType maskT = ShinigamiRenderTypes.translucent(ShinigamiRenderTypes.MASK);
            vc = buffers.getBuffer(maskT);
            for (Fx fx : FX) if (fx.p.kind() == ShinigamiFxNet.VISORED) drawMask(vc, m, left, up, fx, cam, pt);
            buffers.endBatch(maskT);

            RenderType scarT = ShinigamiRenderTypes.translucent(ShinigamiRenderTypes.SLASH);
            vc = buffers.getBuffer(scarT);
            for (Fx fx : FX) if (fx.p.kind() == ShinigamiFxNet.SCAR) drawScar(vc, m, fx, cam, pt, false);
            buffers.endBatch(scarT);
        }

        // ---------------- pétalas ----------------
        if (!SWARMS.isEmpty() || !LOOSE.isEmpty()) {
            RenderType petalT = ShinigamiRenderTypes.translucent(ShinigamiRenderTypes.PETAL);
            VertexConsumer vc = buffers.getBuffer(petalT);
            for (SwarmC s : SWARMS.values()) drawSwarm(vc, m, left, up, s, cam, time, pt, scale);
            for (Petal p : LOOSE) {
                // Pétala colada na câmera (1ª pessoa, soltando a Shikai) vira um quadrado rosa gigante: some de perto
                double ddx = p.x - cam.x, ddy = p.y - cam.y, ddz = p.z - cam.z;
                if (ddx * ddx + ddy * ddy + ddz * ddz < 0.9) continue;
                float a = Mth.clamp((p.life - p.age - pt) / 10F, 0F, 1F);
                float flip = 0.35F + 0.65F * Math.abs(Mth.cos(time * 0.3F + p.rot));
                billboard(vc, m, left, up, (float) (p.x + p.vx * pt - cam.x), (float) (p.y + p.vy * pt - cam.y), (float) (p.z + p.vz * pt - cam.z),
                        p.size * flip, p.size, p.rot + p.spin * pt, argb(p.rgb, 0.95F * a));
            }
            buffers.endBatch(petalT);
        }

        // ---------------- chamas escuras (aura do Vazio, carga negra) ----------------
        if (masks || !FX.isEmpty()) {
            RenderType darkT = ShinigamiRenderTypes.translucent(ShinigamiRenderTypes.FLAME);
            VertexConsumer dvc = buffers.getBuffer(darkT);
            if (masks) HollowClient.forEach((id, kind) -> drawHollowAura(dvc, m, mc.level.getEntity(id), kind, cam, time, pt, true));
            for (Fx fx : FX) {
                if (fx.p.kind() == ShinigamiFxNet.BLADE_CHARGE && luma(fx.p.rgb()) < 0.3F) drawChargeAura(dvc, m, fx, cam, time, pt, true);
            }
            buffers.endBatch(darkT);
        }

        // ---------------- brilhos aditivos ----------------
        if (!FX.isEmpty() || masks || !SWARMS.isEmpty()) {
            RenderType flameT = ShinigamiRenderTypes.additive(ShinigamiRenderTypes.FLAME);
            VertexConsumer fvc = buffers.getBuffer(flameT);
            if (masks) HollowClient.forEach((id, kind) -> drawHollowAura(fvc, m, mc.level.getEntity(id), kind, cam, time, pt, false));
            for (Fx fx : FX) {
                switch (fx.p.kind()) {
                    case ShinigamiFxNet.RELEASE, ShinigamiFxNet.BANKAI -> drawColumn(fvc, m, fx, cam, time, pt);
                    case ShinigamiFxNet.BLADE_CHARGE -> drawChargeAura(fvc, m, fx, cam, time, pt, false);
                    default -> {}
                }
            }
            buffers.endBatch(flameT);

            RenderType ringT = ShinigamiRenderTypes.additive(ShinigamiRenderTypes.RING);
            VertexConsumer vc = buffers.getBuffer(ringT);
            for (Fx fx : FX) {
                switch (fx.p.kind()) {
                    case ShinigamiFxNet.RELEASE, ShinigamiFxNet.BANKAI, ShinigamiFxNet.KAGEYOSHI, ShinigamiFxNet.GOKEI_HIT,
                         ShinigamiFxNet.VISORED -> drawRings(vc, m, fx, cam, pt);
                    default -> {}
                }
            }
            buffers.endBatch(ringT);

            RenderType glowT = ShinigamiRenderTypes.additive(ShinigamiRenderTypes.GLOW);
            vc = buffers.getBuffer(glowT);
            for (Fx fx : FX) {
                switch (fx.p.kind()) {
                    case ShinigamiFxNet.CERO_CHARGE -> drawCeroCharge(vc, m, left, up, fx, cam, time, pt);
                    case ShinigamiFxNet.BLADE_CHARGE -> drawBladeGlow(vc, m, left, up, fx, cam, time, pt);
                    case ShinigamiFxNet.GOKEI_HIT -> drawFlash(vc, m, left, up, fx, cam, pt);
                    default -> {}
                }
            }
            for (SwarmC s : SWARMS.values()) if (s.mode == PetalSwarm.GOKEI) drawGokeiGlow(vc, m, left, up, s, cam, pt);
            buffers.endBatch(glowT);

            RenderType slashT = ShinigamiRenderTypes.additive(ShinigamiRenderTypes.SLASH);
            vc = buffers.getBuffer(slashT);
            for (Fx fx : FX) {
                if (fx.p.kind() == ShinigamiFxNet.SHUNPO) drawSpeedLines(vc, m, fx, cam, pt);
                else if (fx.p.kind() == ShinigamiFxNet.SCAR) drawScar(vc, m, fx, cam, pt, true);
            }
            buffers.endBatch(slashT);

            if (hasSenkei()) {
                RenderType swordGlow = ShinigamiRenderTypes.additive(ShinigamiRenderTypes.SWORD);
                vc = buffers.getBuffer(swordGlow);
                for (SwarmC s : SWARMS.values()) if (s.mode == PetalSwarm.SENKEI) drawSenkei(vc, m, s, cam, time, pt, true);
                buffers.endBatch(swordGlow);
            }
        }

        // ---------------- o comando de liberação escrito no ar ----------------
        for (Fx fx : FX) {
            if ((fx.p.kind() == ShinigamiFxNet.RELEASE || fx.p.kind() == ShinigamiFxNet.BANKAI) && !fx.p.text().getString().isEmpty()) {
                drawText(mc, ps, buffers, camera, fx, pt);
            }
        }
        buffers.endBatch();
    }

    private static boolean hasSenkei() {
        for (SwarmC s : SWARMS.values()) if (s.mode == PetalSwarm.SENKEI) return true;
        return false;
    }

    private static float luma(int rgb) {
        return (((rgb >> 16) & 255) * 0.3F + ((rgb >> 8) & 255) * 0.59F + (rgb & 255) * 0.11F) / 255F;
    }

    // ---------------- Senbonzakura ----------------
    private static void drawSwarm(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, SwarmC s, Vec3 cam, float time, float pt,
                                  float scale) {
        Entity owner = level.getEntity(s.owner);
        Vec3 head = s.prevHead.lerp(s.head, pt);
        switch (s.mode) {
            case PetalSwarm.STREAM, PetalSwarm.RETURN -> {
                List<Vec3> pts = new ArrayList<>(16);
                if (owner instanceof LivingEntity le) pts.add(bladePos(le, pt));
                Vec3[] h = s.hist.toArray(new Vec3[0]);
                for (int i = h.length - 1; i >= 1; i--) pts.add(h[i]);
                pts.add(head);
                int n = Math.round((s.bankai ? 560 : 320) * scale);
                drawStream(vc, m, left, up, pts, n, s.bankai, cam, time);
            }
            case PetalSwarm.OCEAN -> {
                if (owner == null) return;
                Vec3 c = owner.getPosition(pt);
                drawOcean(vc, m, left, up, c, Math.round(460 * scale), 7.5F, cam, time, 1F);
            }
            case PetalSwarm.GOKEI -> {
                float prog = Mth.clamp(s.param + (level.getGameTime() - s.lastPacket + pt) / 24F, 0F, 1.2F);
                drawSphere(vc, m, left, up, head, Math.round(420 * scale), prog, cam, time);
            }
            case PetalSwarm.SENKEI -> drawOcean(vc, m, left, up, s.server, Math.round(200 * scale), PetalSwarm.SENKEI_RADIUS, cam, time, 0.6F);
            default -> {}
        }
    }

    /** A torrente: pétalas distribuídas ao longo do caminho mão → cabeça, mais densas e largas na cabeça. */
    private static void drawStream(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, List<Vec3> pts, int n, boolean bankai, Vec3 cam,
                                   float time) {
        int k = pts.size();
        if (k < 2) {
            pts.add(pts.get(0).add(0, 0.01, 0));
            k = 2;
        }
        double[] cum = new double[k];
        for (int i = 1; i < k; i++) cum[i] = cum[i - 1] + pts.get(i).distanceTo(pts.get(i - 1));
        double total = Math.max(1.0E-3, cum[k - 1]);
        float spread = bankai ? 1.7F : 1.0F;
        int seg = 0;
        for (int i = 0; i < n; i++) {
            float s1 = hash(i, 1), s2 = hash(i, 2), s3 = hash(i, 3), s4 = hash(i, 4);
            float u = (float) Math.pow((i * GOLDEN) % 1F, 0.55);
            double dist = u * total;
            seg = 1;
            while (seg < k - 1 && cum[seg] < dist) seg++;
            double segLen = Math.max(1.0E-4, cum[seg] - cum[seg - 1]);
            double f = Mth.clamp((dist - cum[seg - 1]) / segLen, 0, 1);
            Vec3 a = pts.get(seg - 1), b = pts.get(seg);
            Vec3 p = a.lerp(b, f);
            Vec3 t = b.subtract(a);
            t = t.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : t.normalize();
            Vec3 n1 = Math.abs(t.y) > 0.9 ? new Vec3(1, 0, 0).cross(t).normalize() : new Vec3(0, 1, 0).cross(t).normalize();
            Vec3 n2 = t.cross(n1);
            float bulb = Mth.clamp((u - 0.78F) / 0.22F, 0F, 1F);
            float radius = (0.18F + 0.75F * u + 0.9F * bulb * bulb) * spread;
            float ang = s2 * Mth.TWO_PI + time * (0.12F + 0.22F * s3) * (i % 2 == 0 ? 1 : -1);
            float d = radius * Mth.sqrt(s4);
            float along = Mth.sin(time * 0.21F + s1 * 30F) * 0.25F;
            double x = p.x + n1.x * Mth.cos(ang) * d + n2.x * Mth.sin(ang) * d + t.x * along;
            double y = p.y + n1.y * Mth.cos(ang) * d + n2.y * Mth.sin(ang) * d + t.y * along;
            double z = p.z + n1.z * Mth.cos(ang) * d + n2.z * Mth.sin(ang) * d + t.z * along;
            petal(vc, m, left, up, x - cam.x, y - cam.y, z - cam.z, i, time, bankai ? 1.15F : 1F, 0.95F);
        }
    }

    /** O oceano de pétalas: anéis girando em volta de {@code c} (raio até {@code maxR}). */
    private static void drawOcean(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Vec3 c, int n, float maxR, Vec3 cam, float time,
                                  float alpha) {
        for (int i = 0; i < n; i++) {
            float s1 = hash(i, 11), s2 = hash(i, 12), s3 = hash(i, 13);
            float r = 1.4F + s1 * (maxR - 1.4F);
            float dir = i % 3 == 0 ? -1 : 1;
            float ang = s2 * Mth.TWO_PI + time * (0.03F + 0.09F * (1 - r / (maxR + 1))) * dir;
            float rr = r + Mth.sin(time * 0.07F + ang * 3F) * 0.45F;
            float h = s3 * 3.4F + Mth.sin(time * 0.05F + s1 * 20F) * 0.5F;
            petal(vc, m, left, up, c.x + Mth.cos(ang) * rr - cam.x, c.y + 0.2 + h - cam.y, c.z + Mth.sin(ang) * rr - cam.z, i, time, 1.1F,
                    0.9F * alpha);
        }
    }

    /** Gōkei: a esfera de pétalas se fechando no alvo. */
    private static void drawSphere(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Vec3 c, int n, float prog, Vec3 cam, float time) {
        float e = Mth.clamp(prog, 0F, 1F);
        float r = Mth.lerp(e * e, 5.0F, 1.1F);
        float spin = time * (0.05F + 0.25F * e);
        for (int i = 0; i < n; i++) {
            float yy = 1 - 2 * (i + 0.5F) / n;
            float rad = Mth.sqrt(Math.max(0F, 1 - yy * yy));
            float th = i * 2.3999632F + spin * (i % 2 == 0 ? 1 : -1);
            float jitter = 1F + (hash(i, 21) - 0.5F) * 0.25F;
            double x = c.x + Mth.cos(th) * rad * r * jitter, y = c.y + yy * r * jitter, z = c.z + Mth.sin(th) * rad * r * jitter;
            petal(vc, m, left, up, x - cam.x, y - cam.y, z - cam.z, i, time, 1.1F, 0.95F);
        }
    }

    private static void petal(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, double x, double y, double z, int i, float time,
                              float sizeMul, float alpha) {
        float s5 = hash(i, 5), s6 = hash(i, 6);
        float size = (0.075F + 0.075F * s5) * sizeMul;
        float rot = s6 * Mth.TWO_PI + time * (0.2F + 0.3F * s5) * (i % 2 == 0 ? 1 : -1);
        float flip = 0.3F + 0.7F * Math.abs(Mth.cos(time * (0.25F + 0.2F * s6) + s5 * 9F));
        int rgb = s6 < 0.45F ? PINK : s6 < 0.8F ? PINK_LIGHT : PINK_DEEP;
        billboard(vc, m, left, up, (float) x, (float) y, (float) z, size * flip, size, rot, argb(rgb, alpha));
    }

    private static void drawGokeiGlow(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, SwarmC s, Vec3 cam, float pt) {
        float prog = Mth.clamp(s.param + (level.getGameTime() - s.lastPacket + pt) / 24F, 0F, 1F);
        Vec3 c = s.prevHead.lerp(s.head, pt).subtract(cam);
        float r = Mth.lerp(prog, 4.0F, 1.6F);
        billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, r, r, 0F, argb(PINK, 0.25F + 0.35F * prog));
    }

    /** Senkei: fileiras de espadas brilhantes em anel (de pé, ponta para baixo); no fim, todas descem ao centro. */
    private static void drawSenkei(VertexConsumer vc, Matrix4f m, SwarmC s, Vec3 cam, float time, float pt, boolean glow) {
        float prog = s.param + (level.getGameTime() - s.lastPacket + pt) / 160F;
        float rise = Mth.clamp(prog * 160F / 14F, 0F, 1F);
        float conv = Mth.clamp((prog - 1F) * 160F / 8F, 0F, 1F);
        float fade = 1F - Mth.clamp((prog - 1F) * 160F / 10F - 0.4F, 0F, 1F);
        if (fade <= 0.01F) return;
        Vec3 c = s.server;
        float R = PetalSwarm.SENKEI_RADIUS;
        int perRow = 28;
        for (int row = 0; row < 3; row++) {
            float rr = R * (1F - conv) + 0.4F;
            float h = 2.6F + row * 0.5F;
            float base = (float) (c.y - 0.2) + row * 0.15F + (1 - rise) * -h + conv * 1.0F;
            for (int i = 0; i < perRow; i++) {
                float a = (i + row * 0.33F) * Mth.TWO_PI / perRow + time * 0.004F * (row % 2 == 0 ? 1 : -1);
                float x = (float) (c.x + Mth.cos(a) * rr - cam.x), z = (float) (c.z + Mth.sin(a) * rr - cam.z);
                float y0 = (float) (base - cam.y), y1 = y0 + h;
                float hw = 0.32F;
                float tx = -Mth.sin(a) * hw, tz = Mth.cos(a) * hw;
                int col = glow ? argb(0xFFC8E0, 0.35F * fade * (0.8F + 0.2F * Mth.sin(time * 0.3F + i))) : argb(0xE8E8F4, 0.95F * fade);
                // ponta para baixo: v=1 embaixo é a ponta na textura (cabo em cima)
                ShinigamiRenderTypes.quad(vc, m, x - tx, y0, z - tz, x + tx, y0, z + tz, x + tx, y1, z + tz, x - tx, y1, z - tz, col);
                float rx = Mth.cos(a) * hw, rz = Mth.sin(a) * hw;
                ShinigamiRenderTypes.quad(vc, m, x - rx, y0, z - rz, x + rx, y0, z + rz, x + rx, y1, z + rz, x - rx, y1, z - rz, col);
            }
        }
    }

    /** Kageyoshi: a espada cai e afunda; duas fileiras de lâminas gigantes sobem do chão e viram pétalas. */
    private static void drawKageyoshi(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float pt) {
        float t = fx.age + pt;
        Vec3 c = fx.p.pos();
        float R = fx.p.size();
        // a espada do dono afundando no centro
        if (t < 12) {
            float sink = Mth.clamp(t / 10F, 0F, 1F);
            float y0 = (float) (c.y - cam.y) + 1.6F - sink * 2.4F;
            float x = (float) (c.x - cam.x), z = (float) (c.z - cam.z);
            int col = argb(0xF0F0FA, 1F - Mth.clamp((t - 9) / 3F, 0F, 1F));
            ShinigamiRenderTypes.quad(vc, m, x - 0.12F, y0, z, x + 0.12F, y0, z, x + 0.12F, y0 + 1.4F, z, x - 0.12F, y0 + 1.4F, z, col);
            ShinigamiRenderTypes.quad(vc, m, x, y0, z - 0.12F, x, y0, z + 0.12F, x, y0 + 1.4F, z + 0.12F, x, y0 + 1.4F, z - 0.12F, col);
        }
        if (t < 8) return;
        float rise = Mth.clamp((t - 8) / 14F, 0F, 1F);
        float ease = 1F - (1F - rise) * (1F - rise) * (1F - rise);
        float fade = 1F - Mth.clamp((t - 30) / 6F, 0F, 1F);
        if (fade <= 0.01F) return;
        for (int i = 0; i < 16; i++) {
            boolean inner = i % 2 == 0;
            float r = inner ? R * 0.7F : R;
            float a = i * Mth.TWO_PI / 16 + (inner ? 0F : 0.2F);
            float H = inner ? 7.5F : 9.5F;
            float hw = inner ? 0.7F : 0.85F;
            float x = (float) (c.x + Mth.cos(a) * r - cam.x), z = (float) (c.z + Mth.sin(a) * r - cam.z);
            float y0 = (float) (c.y - cam.y) - H + ease * H * 0.85F;
            float y1 = y0 + H;
            float tx = -Mth.sin(a) * hw, tz = Mth.cos(a) * hw;
            int col = argb(0xF2F0FA, 0.92F * fade);
            // ponta para CIMA: inverte o v (cabo da textura embaixo)
            vc.addVertex(m, x - tx, y0, z - tz).setUv(0, 0).setColor(col);
            vc.addVertex(m, x + tx, y0, z + tz).setUv(1, 0).setColor(col);
            vc.addVertex(m, x + tx, y1, z + tz).setUv(1, 1).setColor(col);
            vc.addVertex(m, x - tx, y1, z - tz).setUv(0, 1).setColor(col);
        }
    }

    // ---------------- pressão espiritual ----------------
    private static void drawRings(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float pt) {
        float t = fx.age + pt;
        int kind = fx.p.kind();
        Vec3 c = fx.p.pos();
        if (kind == ShinigamiFxNet.VISORED || kind == ShinigamiFxNet.GOKEI_HIT) {
            Entity e = kind == ShinigamiFxNet.VISORED ? level.getEntity(fx.p.entity()) : null;
            Vec3 at = e != null ? e.getPosition(pt).add(0, e.getBbHeight() * 0.6, 0) : c;
            float k = Mth.clamp(t / fx.life, 0F, 1F);
            float r = (kind == ShinigamiFxNet.VISORED ? 2.6F * fx.p.size() : fx.p.size() * 1.6F) * easeOut(k);
            ShinigamiRenderTypes.flat(vc, m, (float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z), r, 0F,
                    argb(fx.p.rgb(), (1 - k) * 0.9F));
            return;
        }
        int rings = kind == ShinigamiFxNet.BANKAI ? 3 : kind == ShinigamiFxNet.KAGEYOSHI ? 1 : 2;
        float maxR = kind == ShinigamiFxNet.KAGEYOSHI ? fx.p.size() * 1.6F : fx.p.size();
        for (int k = 0; k < rings; k++) {
            float delay = kind == ShinigamiFxNet.KAGEYOSHI ? 30 : k * 6;
            float dur = kind == ShinigamiFxNet.BANKAI ? 26F : 18F;
            float local = (t - delay) / dur;
            if (local < 0 || local > 1) continue;
            float r = 0.5F + maxR * easeOut(local);
            float a = (1F - local) * (kind == ShinigamiFxNet.BANKAI ? 1F : 0.85F);
            int rgb = ShinigamiRenderTypes.mix(fx.p.rgb(), 0xFFFFFF, k == 0 ? 0.35F : 0F);
            ShinigamiRenderTypes.flat(vc, m, (float) (c.x - cam.x), (float) (c.y + 0.08 + k * 0.02 - cam.y), (float) (c.z - cam.z), r, k * 0.7F,
                    argb(rgb, a));
        }
    }

    /** Coluna de Reiatsu subindo (cilindro de línguas de chama aditivas) na liberação. */
    private static void drawColumn(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float time, float pt) {
        float t = fx.age + pt;
        boolean bankai = fx.p.kind() == ShinigamiFxNet.BANKAI;
        float dur = bankai ? 50F : 26F;
        if (t > dur) return;
        Entity e = level.getEntity(fx.p.entity());
        Vec3 c = e != null ? e.getPosition(pt) : fx.p.pos();
        float grow = Mth.clamp(t / 5F, 0F, 1F);
        float fade = 1F - Mth.clamp((t - dur * 0.45F) / (dur * 0.55F), 0F, 1F);
        float H = (bankai ? 16F : 6F) * grow;
        float R = bankai ? 1.6F : 0.9F;
        int sides = bankai ? 10 : 7;
        int rgb = mix(fx.p.rgb(), 0xFFFFFF, 0.25F);
        for (int i = 0; i < sides; i++) {
            float a = i * Mth.TWO_PI / sides + time * 0.08F;
            float x = (float) (c.x - cam.x), y = (float) (c.y - cam.y) - 0.1F, z = (float) (c.z - cam.z);
            float cx = Mth.cos(a) * R, cz = Mth.sin(a) * R;
            float tx = -Mth.sin(a) * R * 0.9F, tz = Mth.cos(a) * R * 0.9F;
            ShinigamiRenderTypes.quad(vc, m, x + cx - tx, y, z + cz - tz, x + cx + tx, y, z + cz + tz, x + cx * 0.4F + tx, y + H, z + cz * 0.4F + tz,
                    x + cx * 0.4F - tx, y + H, z + cz * 0.4F - tz, argb(rgb, 0.55F * fade));
        }
    }

    private static void drawText(Minecraft mc, PoseStack ps, MultiBufferSource buffers, Camera camera, Fx fx, float pt) {
        float t = fx.age + pt;
        float a = Math.min(Mth.clamp(t / 6F, 0F, 1F), Mth.clamp((fx.life - t) / 15F, 0F, 1F));
        if (a < 0.08F) return;
        Entity e = level.getEntity(fx.p.entity());
        Vec3 at = (e != null ? e.getPosition(pt).add(0, e.getBbHeight() + 1.0, 0) : fx.p.pos().add(0, 2.6, 0)).add(0, t * 0.012, 0);
        Vec3 rel = at.subtract(camera.getPosition());
        if (rel.lengthSqr() > 48 * 48) return;
        Font font = mc.font;
        Component text = fx.p.text();
        boolean bankai = fx.p.kind() == ShinigamiFxNet.BANKAI;
        float sc = (bankai ? 0.045F : 0.035F) * (1F + 0.25F * (1F - Mth.clamp(t / 6F, 0F, 1F)));
        ps.pushPose();
        ps.translate(rel.x, rel.y, rel.z);
        ps.mulPose(camera.rotation());
        ps.scale(sc, -sc, sc);
        float w = font.width(text);
        int col = ((int) (a * 255) << 24) | (mix(fx.p.rgb(), 0xFFFFFF, 0.3F) & 0xFFFFFF);
        font.drawInBatch(text, -w / 2F, 0, col, true, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, ((int) (a * 90) << 24),
                LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    // ---------------- Shunpo ----------------
    private static void drawShunpoGhost(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float pt) {
        float t = fx.age + pt;
        float k = Mth.clamp(t / fx.life, 0F, 1F);
        Vec3 from = fx.p.pos(), to = fx.p.vec();
        float H = Math.max(1F, fx.p.size()) * 1.05F, W = H * 0.5F;
        for (int j = 0; j < 3; j++) {
            float along = j * 0.22F;
            float a = (1 - k) * (j == 0 ? 0.75F : 0.4F - j * 0.1F);
            if (a <= 0.01F) continue;
            Vec3 p = from.lerp(to, along).subtract(cam);
            // de frente para a câmera só no eixo horizontal (fica em pé)
            Vec3 toCam = new Vec3(-p.x, 0, -p.z);
            toCam = toCam.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : toCam.normalize();
            float sx = (float) -toCam.z * W / 2, sz = (float) toCam.x * W / 2;
            float x = (float) p.x, y = (float) p.y, z = (float) p.z;
            int col = argb(fx.p.rgb(), a);
            ShinigamiRenderTypes.quad(vc, m, x - sx, y, z - sz, x + sx, y, z + sz, x + sx, y + H, z + sz, x - sx, y + H, z - sz, col);
        }
    }

    private static void drawSpeedLines(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float pt) {
        float t = fx.age + pt;
        float a = 1F - Mth.clamp(t / 8F, 0F, 1F);
        if (a <= 0.01F) return;
        Vec3 from = fx.p.pos(), to = fx.p.vec();
        Vec3 d = to.subtract(from);
        if (d.lengthSqr() < 0.25) return;
        float H = Math.max(1F, fx.p.size());
        for (int i = 0; i < 7; i++) {
            float h = 0.15F + hash(i, 31) * H;
            float side = (hash(i, 32) - 0.5F) * 0.8F;
            Vec3 dir = d.normalize();
            Vec3 sv = new Vec3(-dir.z, 0, dir.x).scale(side);
            Vec3 a0 = from.add(sv).add(0, h, 0).subtract(cam), a1 = to.add(sv).add(0, h, 0).subtract(cam);
            float w = 0.04F + 0.04F * hash(i, 33);
            ShinigamiRenderTypes.quad(vc, m, (float) a0.x, (float) a0.y - w, (float) a0.z, (float) a1.x, (float) a1.y - w, (float) a1.z,
                    (float) a1.x, (float) a1.y + w, (float) a1.z, (float) a0.x, (float) a0.y + w, (float) a0.z, argb(0xE8F4FF, 0.8F * a));
        }
    }

    // ---------------- Cero e cargas ----------------
    private static Vec3 handOrMouth(Fx fx, float pt) {
        Entity e = level.getEntity(fx.p.entity());
        if (e instanceof LivingEntity le) return le.getEyePosition(pt).add(le.getViewVector(pt).scale(0.9)).add(0, -0.15, 0);
        return fx.p.pos();
    }

    private static void drawCeroCharge(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx fx, Vec3 cam, float time, float pt) {
        float t = fx.age + pt;
        float charge = Mth.clamp(t / Math.max(1F, fx.life - 4F), 0F, 1F);
        float fade = t > fx.life - 4 ? Mth.clamp((fx.life - t) / 4F, 0F, 1F) : 1F;
        Vec3 c = handOrMouth(fx, pt).subtract(cam);
        float x = (float) c.x, y = (float) c.y, z = (float) c.z;
        float r = 0.15F + 0.75F * charge * (1F + 0.12F * Mth.sin(time * 2.2F));
        billboard(vc, m, left, up, x, y, z, r * 2.2F, r * 2.2F, 0, argb(fx.p.rgb(), 0.6F * fade));
        billboard(vc, m, left, up, x, y, z, r, r, 0, argb(0xFF6070, 0.9F * fade));
        billboard(vc, m, left, up, x, y, z, r * 0.45F, r * 0.45F, 0, argb(0xFFFFFF, fade));
        // faíscas sendo sugadas para o centro
        for (int i = 0; i < 10; i++) {
            float ph = (time * 0.09F + hash(i, 41)) % 1F;
            float dist = (1F - ph) * (1.2F + charge);
            float th = hash(i, 42) * Mth.TWO_PI, phi = hash(i, 43) * Mth.PI;
            float sx = x + Mth.sin(phi) * Mth.cos(th) * dist, sy = y + Mth.cos(phi) * dist, sz = z + Mth.sin(phi) * Mth.sin(th) * dist;
            billboard(vc, m, left, up, sx, sy, sz, 0.08F, 0.08F, 0, argb(fx.p.rgb(), ph * fade));
        }
    }

    private static void drawBladeGlow(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx fx, Vec3 cam, float time, float pt) {
        Entity e = level.getEntity(fx.p.entity());
        if (!(e instanceof LivingEntity le)) return;
        float t = fx.age + pt;
        float charge = Mth.clamp(t / Math.max(1F, fx.life - 4F), 0F, 1F);
        float fade = t > fx.life - 4 ? Mth.clamp((fx.life - t) / 4F, 0F, 1F) : 1F;
        int rgb = luma(fx.p.rgb()) < 0.3F ? 0xC0102A : fx.p.rgb();
        Vec3 c = bladePos(le, pt).subtract(cam);
        float r = 0.25F + 0.45F * charge * (1F + 0.15F * Mth.sin(time * 1.7F));
        billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, r * 2F, r * 2F, 0, argb(rgb, 0.55F * fade));
        billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, r * 0.6F, r * 0.6F, 0, argb(0xFFFFFF, 0.7F * fade * charge));
    }

    /** Aura de chamas em volta do corpo enquanto carrega (escura numa passada normal, colorida na aditiva). */
    private static void drawChargeAura(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float time, float pt, boolean dark) {
        Entity e = level.getEntity(fx.p.entity());
        if (e == null) return;
        float t = fx.age + pt;
        float charge = Mth.clamp(t / Math.max(1F, fx.life - 4F), 0F, 1F);
        float fade = t > fx.life - 4 ? Mth.clamp((fx.life - t) / 4F, 0F, 1F) : 1F;
        int rgb = dark ? fx.p.rgb() : luma(fx.p.rgb()) < 0.3F ? 0xC0102A : fx.p.rgb();
        auraFlames(vc, m, e, cam, time, pt, rgb, (dark ? 0.8F : 0.5F) * fade * (0.4F + 0.6F * charge), 0.8F + 0.6F * charge, dark ? 5 : 6);
    }

    private static void auraFlames(VertexConsumer vc, Matrix4f m, Entity e, Vec3 cam, float time, float pt, int rgb, float alpha, float scale,
                                   int count) {
        Vec3 c = e.getPosition(pt).subtract(cam);
        float H = e.getBbHeight() * (1.25F + 0.25F * scale);
        float R = e.getBbWidth() * 0.75F + 0.15F;
        for (int i = 0; i < count; i++) {
            float flick = 0.85F + 0.15F * Mth.sin(time * (0.6F + 0.1F * i) + i * 2F);
            float a = i * Mth.TWO_PI / count + time * 0.05F;
            float x = (float) c.x + Mth.cos(a) * R * 0.5F, z = (float) c.z + Mth.sin(a) * R * 0.5F;
            float y = (float) c.y - 0.1F;
            float tx = -Mth.sin(a) * R * scale, tz = Mth.cos(a) * R * scale;
            float h = H * flick;
            ShinigamiRenderTypes.quad(vc, m, x - tx, y, z - tz, x + tx, y, z + tz, x + tx, y + h, z + tz, x - tx, y + h, z - tz, argb(rgb, alpha));
        }
    }

    /** Aura do Vazio: Máscara (tremor rubro discreto) e Vasto Lorde (chamas negras e rubras altas). */
    private static void drawHollowAura(VertexConsumer vc, Matrix4f m, Entity e, int kind, Vec3 cam, float time, float pt, boolean dark) {
        if (!(e instanceof LivingEntity) || e.isInvisible()) return;
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) return;
        if (e.position().distanceToSqr(cam) > 64 * 64) return;
        if (kind == 2) {
            auraFlames(vc, m, e, cam, time, pt, dark ? 0x120206 : 0xE01030, dark ? 0.7F : 0.45F, dark ? 1.3F : 1.1F, dark ? 6 : 7);
        } else if (!dark) {
            auraFlames(vc, m, e, cam, time, pt, 0xC0102A, 0.18F + 0.08F * Mth.sin(time * 0.4F), 0.7F, 4);
        }
    }

    private static void drawMask(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx fx, Vec3 cam, float pt) {
        Entity e = level.getEntity(fx.p.entity());
        if (!(e instanceof LivingEntity le)) return;
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) return;
        float t = fx.age + pt;
        float k = Mth.clamp(t / fx.life, 0F, 1F);
        float a = Math.min(Mth.clamp(t / 3F, 0F, 1F), 1F - Mth.clamp((t - 8) / 8F, 0F, 1F));
        float s = Mth.lerp(easeOut(Mth.clamp(t / 8F, 0F, 1F)), 1.6F, 0.42F) * fx.p.size();
        Vec3 c = le.getEyePosition(pt).add(le.getViewVector(pt).scale(0.45)).subtract(cam);
        billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, s, s, (1 - k) * 0.6F, argb(0xFFFFFF, a));
    }

    private static void drawFlash(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx fx, Vec3 cam, float pt) {
        float k = Mth.clamp((fx.age + pt) / fx.life, 0F, 1F);
        Vec3 c = fx.p.pos().subtract(cam);
        float r = fx.p.size() * (0.6F + 0.9F * easeOut(k));
        billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, r, r, 0, argb(PINK, (1 - k) * (1 - k)));
        billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, r * 0.4F, r * 0.4F, 0, argb(0xFFFFFF, (1 - k)));
    }

    /** Cicatriz do Getsuga no chão: risco escuro deitado na direção da onda (e um fio de luz que some rápido). */
    private static void drawScar(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float pt, boolean glow) {
        float t = fx.age + pt;
        float a = glow ? 1F - Mth.clamp(t / 16F, 0F, 1F) : 1F - Mth.clamp((t - 40) / 50F, 0F, 1F);
        if (a <= 0.01F) return;
        Vec3 d = new Vec3(fx.p.vec().x, 0, fx.p.vec().z);
        d = d.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : d.normalize();
        Vec3 side = new Vec3(-d.z, 0, d.x);
        float len = 2.4F, w = Math.max(0.3F, fx.p.size() * (glow ? 0.18F : 0.35F));
        Vec3 c = fx.p.pos().subtract(cam);
        float y = (float) c.y + (glow ? 0.03F : 0.02F);
        Vec3 a0 = c.subtract(d.scale(len)), a1 = c.add(d.scale(len));
        int col = glow ? argb(fx.p.rgb(), 0.9F * a) : argb(0x0C0608, 0.75F * a);
        // a textura do corte corre no eixo u (comprimento); v é a espessura
        vc.addVertex(m, (float) (a0.x - side.x * w), y, (float) (a0.z - side.z * w)).setUv(0, 0).setColor(col);
        vc.addVertex(m, (float) (a1.x - side.x * w), y, (float) (a1.z - side.z * w)).setUv(1, 0).setColor(col);
        vc.addVertex(m, (float) (a1.x + side.x * w), y, (float) (a1.z + side.z * w)).setUv(1, 1).setColor(col);
        vc.addVertex(m, (float) (a0.x + side.x * w), y, (float) (a0.z + side.z * w)).setUv(0, 1).setColor(col);
    }

    // =====================================================================================
    //  Tela
    // =====================================================================================
    static void overlay(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.level != level || FX.isEmpty()) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        Vec3 me = mc.player.position();
        for (Fx fx : FX) {
            float t = fx.age + pt;
            switch (fx.p.kind()) {
                case ShinigamiFxNet.MUGETSU -> mugetsu(g, w, h, t);
                case ShinigamiFxNet.RELEASE, ShinigamiFxNet.BANKAI -> {
                    double d = me.distanceTo(fx.p.pos());
                    float range = fx.p.kind() == ShinigamiFxNet.BANKAI ? 32F : 16F;
                    if (d > range) break;
                    float near = 1F - (float) d / range;
                    float k = Mth.clamp(t / (fx.p.kind() == ShinigamiFxNet.BANKAI ? 40F : 22F), 0F, 1F);
                    float a = near * (1F - k) * (fx.p.kind() == ShinigamiFxNet.BANKAI ? 0.55F : 0.35F);
                    if (a <= 0.01F) break;
                    int edge = ((int) (a * 255) << 24) | (fx.p.rgb() & 0xFFFFFF);
                    int clear = fx.p.rgb() & 0xFFFFFF;
                    g.fillGradient(0, 0, w, h / 4, edge, clear);
                    g.fillGradient(0, h - h / 4, w, h, clear, edge);
                }
                case ShinigamiFxNet.VISORED -> {
                    if (fx.p.entity() != mc.player.getId()) break;
                    float a = 1F - Mth.clamp(t / 12F, 0F, 1F);
                    if (t < 3) g.fill(0, 0, w, h, ((int) (200 * (1 - t / 3F)) << 24) | 0xFFFFFF);
                    int red = ((int) (120 * a) << 24) | 0xC0102A;
                    g.fillGradient(0, 0, w, h / 3, red, 0x00C0102A);
                    g.fillGradient(0, h - h / 3, w, h, 0x00C0102A, red);
                }
                default -> {}
            }
        }
    }

    /** Mugetsu: escurece tudo; no meio da escuridão, um único corte atravessa a tela; depois a luz volta devagar. */
    private static void mugetsu(GuiGraphics g, int w, int h, float t) {
        float black = t < 12 ? t / 12F : t < 38 ? 1F : 1F - Mth.clamp((t - 38) / 34F, 0F, 1F);
        if (black > 0.01F) g.fill(0, 0, w, h, ((int) (black * 250) << 24));
        if (t >= 20 && t <= 40) {
            float k = Mth.clamp((t - 20) / 4F, 0F, 1F);
            float out = 1F - Mth.clamp((t - 30) / 10F, 0F, 1F);
            float len = (float) Math.hypot(w, h) * 1.2F * k;
            g.pose().pushPose();
            g.pose().translate(w / 2F, h / 2F, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-28F));
            int glowH = Math.max(2, (int) (14 * out));
            g.fillGradient((int) (-len / 2), -glowH, (int) (len / 2), 0, 0x00300A50, ((int) (170 * out) << 24) | 0x5A1E8A);
            g.fillGradient((int) (-len / 2), 0, (int) (len / 2), glowH, ((int) (170 * out) << 24) | 0x5A1E8A, 0x00300A50);
            g.fill((int) (-len / 2), -1, (int) (len / 2), 1, ((int) (255 * out) << 24) | 0xF4ECFF);
            g.pose().popPose();
        }
    }

    private static float easeOut(float t) {
        float k = 1F - t;
        return 1F - k * k * k;
    }

    private ShinigamiFxClient() {}
}
