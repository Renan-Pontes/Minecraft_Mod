package com.renanpontes.isekaimahou.feature.legends_f.client;

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
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.legends_f.LfNet;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Cliente dos efeitos do legends_f: efeitos curtos ({@link LfNet.Fx}: anéis, pulso de mana, pilar do "Levante-se!",
 * Bang!, troca de sombras, flores, barreira estilhaçando, selo do contrato) e estados por entidade
 * ({@link LfNet.State}: "Z" do sono e espinhos de roseira, aura e selo do Monarca, anel da meditação, pergaminho do
 * contrato queimando, brilho da Mana Oculta). As motosserras e os olhos do Modo Sábio ficam em {@link PlayerLayers}.
 */
public final class LfFxClient {
    private static final int MAX = 300;
    private static final double DRAW_RANGE = 160;
    private static final List<Fx> ACTIVE = new ArrayList<>();
    /** id da entidade -> estados (fim em ticks do cliente, duração total, valor). */
    private static final Map<Integer, long[][]> STATES = new HashMap<>();
    private static ClientLevel level;
    private static long clientTicks;

    private static final class Fx {
        final byte kind;
        final Vec3 pos;
        final Vec3 dir;
        final int color;
        final int life;
        final float size;
        final long seed;
        int age;

        Fx(LfNet.Fx p, long seed) {
            this.kind = p.kind();
            this.pos = p.pos();
            this.dir = p.dir();
            this.color = p.color();
            this.life = Math.max(1, p.life());
            this.size = p.size();
            this.seed = seed;
        }
    }

    private static boolean reduced() {
        try {
            return MahouConfig.REDUCED_PARTICLES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void checkLevel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            ACTIVE.clear();
            STATES.clear();
            level = mc.level;
        }
    }

    public static void add(LfNet.Fx p) {
        checkLevel();
        if (level == null) return;
        if (ACTIVE.size() >= MAX) ACTIVE.remove(0);
        ACTIVE.add(new Fx(p, level.random.nextLong()));
    }

    public static void state(LfNet.State p) {
        checkLevel();
        if (level == null || p.kind() < 0 || p.kind() >= LfNet.STATE_KINDS) return;
        long[][] s = STATES.computeIfAbsent(p.entity(), k -> new long[LfNet.STATE_KINDS][]);
        s[p.kind()] = p.ticks() <= 0 ? null : new long[] {clientTicks + p.ticks(), Math.max(p.ticks(), s[p.kind()] == null ? 0 : s[p.kind()][1]), p.value()};
    }

    /** A entidade está no estado? */
    public static boolean has(int entityId, byte kind) {
        long[][] s = STATES.get(entityId);
        return s != null && s[kind] != null && s[kind][0] > clientTicks;
    }

    /** 0..1 do tempo que falta no estado (1 = acabou de começar). */
    static float remaining(int entityId, byte kind, float pt) {
        long[][] s = STATES.get(entityId);
        if (s == null || s[kind] == null) return 0F;
        return Mth.clamp((s[kind][0] - clientTicks - pt) / Math.max(1F, s[kind][1]), 0F, 1F);
    }

    static int value(int entityId, byte kind) {
        long[][] s = STATES.get(entityId);
        return s == null || s[kind] == null ? 0 : (int) s[kind][2];
    }

    static long ticks() {
        return clientTicks;
    }

    static void tick(ClientTickEvent.Post e) {
        checkLevel();
        Minecraft mc = Minecraft.getInstance();
        if (level == null || mc.isPaused()) return;
        clientTicks++;
        ACTIVE.removeIf(f -> ++f.age >= f.life);
        if (clientTicks % 40 == 0) {
            Iterator<Map.Entry<Integer, long[][]>> it = STATES.entrySet().iterator();
            while (it.hasNext()) {
                long[][] s = it.next().getValue();
                boolean any = false;
                for (long[] k : s) if (k != null && k[0] > clientTicks) any = true;
                if (!any) it.remove();
            }
        }
        if (reduced() || clientTicks % 3 != 0) return;
        for (Map.Entry<Integer, long[][]> en : STATES.entrySet()) {
            Entity ent = level.getEntity(en.getKey());
            if (ent == null) continue;
            if (has(en.getKey(), LfNet.S_CHAINSAW) && level.random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SMOKE, ent.getX(), ent.getY() + ent.getBbHeight() + 0.2, ent.getZ(), 0, 0.03, 0);
            }
            if (has(en.getKey(), LfNet.S_SAGE) && level.random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.WAX_ON, ent.getX() + level.random.nextGaussian() * 0.3, ent.getY() + level.random.nextDouble() * ent.getBbHeight(),
                        ent.getZ() + level.random.nextGaussian() * 0.3, 0, 0.02, 0);
            }
            if (has(en.getKey(), LfNet.S_CONCEAL) && level.random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.MYCELIUM, ent.getX() + level.random.nextGaussian() * 0.3, ent.getY() + level.random.nextDouble() * ent.getBbHeight(),
                        ent.getZ() + level.random.nextGaussian() * 0.3, 0, 0, 0);
            }
        }
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================

    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level || (ACTIVE.isEmpty() && STATES.isEmpty())) return;
        float pt = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        float time = clientTicks + pt;

        // ---------- sombras (translúcido que escurece) ----------
        VertexConsumer dark = buffers.getBuffer(LfRenderTypes.DARK);
        Matrix4f m = ps.last().pose();
        for (Fx fx : ACTIVE) {
            if (fx.pos.distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            float t = Mth.clamp((fx.age + pt) / fx.life, 0F, 1F);
            Vec3 c = fx.pos.subtract(cam);
            if (fx.kind == LfNet.ARISE) arisePillarDark(dark, m, c, fx, t, cam);
            else if (fx.kind == LfNet.SWAP) swapDark(dark, m, c, fx, t, left, up);
            else if (fx.kind == LfNet.BLOOM) bloom(dark, ps, c, fx, t);
            else if (fx.kind == LfNet.BANG) LfDraw.starBurst(dark, m, c, left, up, fx.size * (0.6F + 1.2F * t), fx.size * 0.3F, 9,
                    fx.seed % 7, LfDraw.argb(0x1A0000, 0.85F * (1 - t)), LfDraw.argb(0x000000, 0F));
        }
        for (Map.Entry<Integer, long[][]> en : STATES.entrySet()) {
            Entity ent = level.getEntity(en.getKey());
            if (ent == null || ent.position().distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            Vec3 p = ent.getPosition(pt).subtract(cam);
            if (has(en.getKey(), LfNet.S_MONARCH)) monarchFlames(dark, m, p, ent, time, cam);
            if (has(en.getKey(), LfNet.S_SLEEP)) sleepVines(dark, ps, p, ent, time);
        }
        buffers.endBatch(LfRenderTypes.DARK);

        // ---------- brilho aditivo ----------
        VertexConsumer glow = buffers.getBuffer(LfRenderTypes.GLOW);
        m = ps.last().pose();
        for (Fx fx : ACTIVE) {
            if (fx.pos.distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            float t = Mth.clamp((fx.age + pt) / fx.life, 0F, 1F);
            Vec3 c = fx.pos.subtract(cam);
            switch (fx.kind) {
                case LfNet.RING -> ring(glow, ps, c, fx, t);
                case LfNet.PULSE -> pulseRings(glow, ps, c, fx, t);
                case LfNet.ARISE -> ariseGlow(glow, ps, c, fx, t, left, up);
                case LfNet.BANG -> {
                    LfDraw.starBurst(glow, m, c, left, up, fx.size * (0.4F + 0.9F * t), fx.size * 0.22F, 7, fx.seed % 5 + 0.3F,
                            LfDraw.argb(0xFFFFFF, 1 - t), LfDraw.argb(fx.color, 0F));
                    LfDraw.billboard(glow, m, c, left, up, fx.size * (0.5F + t), LfDraw.argb(fx.color, 0.9F * (1 - t)), LfDraw.argb(fx.color, 0F));
                }
                case LfNet.MUZZLE -> LfDraw.starBurst(glow, m, c, left, up, fx.size * (1F - t * 0.5F), fx.size * 0.25F, 6, 0,
                        LfDraw.argb(0xFFFFFF, 1 - t), LfDraw.argb(fx.color, 0F));
                case LfNet.SWAP -> {
                    LfDraw.billboard(glow, m, c, left, up, 1.2F * (1 - t), LfDraw.argb(fx.color, 0.7F * (1 - t)), LfDraw.argb(fx.color, 0F));
                    LfDraw.billboard(glow, m, c.add(fx.dir), left, up, 1.2F * (1 - t), LfDraw.argb(fx.color, 0.7F * (1 - t)), LfDraw.argb(fx.color, 0F));
                }
                case LfNet.SHATTER -> shatter(glow, ps, c, fx, t);
                default -> {}
            }
        }
        for (Map.Entry<Integer, long[][]> en : STATES.entrySet()) {
            Entity ent = level.getEntity(en.getKey());
            if (ent == null || ent.position().distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            if (ent == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()
                    && !has(en.getKey(), LfNet.S_MEDITATE) && !has(en.getKey(), LfNet.S_MONARCH)) continue;
            Vec3 p = ent.getPosition(pt).subtract(cam);
            int id = en.getKey();
            if (has(id, LfNet.S_MONARCH)) monarchGlow(glow, ps, p, ent, time);
            if (has(id, LfNet.S_MEDITATE)) meditateGlow(glow, ps, p, time, 1F - remaining(id, LfNet.S_MEDITATE, pt));
            if (has(id, LfNet.S_SAGE)) {
                ps.pushPose();
                ps.translate(p.x, p.y + 0.05, p.z);
                LfDraw.ringFlat(glow, ps.last().pose(), 0.9F + 0.08F * Mth.sin(time * 0.2F), 0.12F, 0xF08A24, 0.45F, 32);
                ps.popPose();
            }
            if (has(id, LfNet.S_CONCEAL)) {
                ps.pushPose();
                ps.translate(p.x, p.y + 0.05, p.z);
                LfDraw.ringFlat(glow, ps.last().pose(), 0.7F, 0.06F, 0x5A7A88, 0.25F, 24);
                ps.popPose();
            }
            if (has(id, LfNet.S_FAUST)) {
                ps.pushPose();
                ps.translate(p.x, p.y + 0.03, p.z);
                ps.mulPose(Axis.YP.rotationDegrees(time * 2F));
                LfDraw.ringFlat(glow, ps.last().pose(), 1.1F, 0.08F, 0xC0182E, 0.5F, 5);
                ps.popPose();
            }
        }
        buffers.endBatch(LfRenderTypes.GLOW);

        // ---------- texturas aditivas: hexágonos, selos, pergaminho, "Z" ----------
        VertexConsumer hex = buffers.getBuffer(LfRenderTypes.additive(LfRenderTypes.HEX));
        for (Fx fx : ACTIVE) {
            if (fx.kind != LfNet.PULSE || fx.pos.distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            float t = Mth.clamp((fx.age + pt) / fx.life, 0F, 1F);
            Vec3 c = fx.pos.subtract(cam);
            float r = fx.size * LfDraw.smooth(t * 1.2F);
            float a = 0.55F * (1 - t);
            ps.pushPose();
            ps.translate(c.x, c.y, c.z);
            int col = LfDraw.argb(fx.color, a);
            LfDraw.texSphere(hex, ps.last().pose(), Math.max(0.3F, r), 12, 24, 1F, Math.max(4, r * 0.8F), Math.max(2, r * 0.4F), 0,
                    row -> 0, col);
            ps.popPose();
        }
        buffers.endBatch(LfRenderTypes.additive(LfRenderTypes.HEX));

        VertexConsumer sigil = buffers.getBuffer(LfRenderTypes.additive(LfRenderTypes.MONARCH_SIGIL));
        for (Map.Entry<Integer, long[][]> en : STATES.entrySet()) {
            if (!has(en.getKey(), LfNet.S_MONARCH)) continue;
            Entity ent = level.getEntity(en.getKey());
            if (ent == null || ent.position().distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            Vec3 p = ent.getPosition(pt).subtract(cam);
            ps.pushPose();
            ps.translate(p.x, p.y + 0.04, p.z);
            ps.mulPose(Axis.YP.rotationDegrees(time * 1.5F));
            LfDraw.texFlat(sigil, ps.last().pose(), 3.2F, LfDraw.argb(0x8A5BFF, 0.75F));
            ps.mulPose(Axis.YP.rotationDegrees(-time * 3.5F));
            LfDraw.texFlat(sigil, ps.last().pose(), 1.8F, LfDraw.argb(0xB89BFF, 0.5F));
            ps.popPose();
        }
        buffers.endBatch(LfRenderTypes.additive(LfRenderTypes.MONARCH_SIGIL));

        VertexConsumer seal = buffers.getBuffer(LfRenderTypes.additive(LfRenderTypes.CONTRACT_SEAL));
        for (Fx fx : ACTIVE) {
            if (fx.kind != LfNet.SEAL || fx.pos.distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            float t = Mth.clamp((fx.age + pt) / fx.life, 0F, 1F);
            Vec3 c = fx.pos.subtract(cam);
            ps.pushPose();
            ps.translate(c.x, c.y, c.z);
            ps.mulPose(Axis.YP.rotationDegrees((fx.age + pt) * 4F));
            float grow = LfDraw.smooth(t * 4F);
            LfDraw.texFlat(seal, ps.last().pose(), fx.size * grow, LfDraw.argb(fx.color, (1 - t) * 0.95F));
            ps.popPose();
        }
        buffers.endBatch(LfRenderTypes.additive(LfRenderTypes.CONTRACT_SEAL));

        VertexConsumer page = buffers.getBuffer(LfRenderTypes.additive(LfRenderTypes.CONTRACT_PAGE));
        for (Map.Entry<Integer, long[][]> en : STATES.entrySet()) {
            int id = en.getKey();
            if (!has(id, LfNet.S_FAUST)) continue;
            Entity ent = level.getEntity(id);
            if (ent == null || ent.position().distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            if (ent == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) continue;
            Vec3 p = ent.getPosition(pt).subtract(cam);
            float yaw = (ent instanceof net.minecraft.world.entity.LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : ent.getYRot());
            float rem = remaining(id, LfNet.S_FAUST, pt);
            ps.pushPose();
            ps.translate(p.x, p.y + ent.getBbHeight() + 0.55 + 0.06 * Mth.sin(time * 0.12F), p.z);
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.translate(0.55, 0, 0.1);
            ps.mulPose(Axis.YP.rotationDegrees(25F));
            float h = 0.32F * Math.max(0.15F, rem);
            ps.translate(0, h - 0.32F, 0);
            int prog = value(id, LfNet.S_FAUST);
            int col = LfDraw.mix(0xD0B080, 0xFFD84A, prog / 100F);
            LfDraw.texUpright(page, ps.last().pose(), 0.24F, h, LfDraw.argb(col, 0.85F));
            ps.popPose();
        }
        buffers.endBatch(LfRenderTypes.additive(LfRenderTypes.CONTRACT_PAGE));

        VertexConsumer zz = buffers.getBuffer(LfRenderTypes.additive(LfRenderTypes.SLEEP_Z));
        for (Map.Entry<Integer, long[][]> en : STATES.entrySet()) {
            if (!has(en.getKey(), LfNet.S_SLEEP)) continue;
            Entity ent = level.getEntity(en.getKey());
            if (ent == null || ent.position().distanceToSqr(cam) > DRAW_RANGE * DRAW_RANGE) continue;
            Vec3 p = ent.getPosition(pt).subtract(cam);
            for (int k = 0; k < 3; k++) {
                float ph = ((time + k * 20 + en.getKey() * 7) % 60F) / 60F;
                Vec3 z = p.add(0.3 + ph * 0.5 + k * 0.08, ent.getBbHeight() + 0.2 + ph * 1.2, 0.1 * Mth.sin(time * 0.1F + k));
                float a = ph < 0.15F ? ph / 0.15F : 1F - (ph - 0.15F) / 0.85F;
                LfDraw.texBillboard(zz, ps.last().pose(), z, left, up, 0.14F + ph * 0.12F, LfDraw.argb(0xF8C8F0, a * 0.95F));
            }
        }
        buffers.endBatch(LfRenderTypes.additive(LfRenderTypes.SLEEP_Z));
    }

    // =====================================================================================
    //  Peças
    // =====================================================================================

    /** Anel que se abre (tamanho positivo) ou se fecha (negativo). */
    private static void ring(VertexConsumer vc, PoseStack ps, Vec3 c, Fx fx, float t) {
        float ease = 1F - (1F - t) * (1F - t) * (1F - t);
        float size = Math.abs(fx.size);
        float r = fx.size >= 0 ? size * (0.1F + 0.9F * ease) : size * (1F - 0.9F * ease);
        float a = (1F - t) * 0.85F;
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        ps.mulPose(LfDraw.yTo(fx.dir));
        Matrix4f mm = ps.last().pose();
        LfDraw.ringFlat(vc, mm, r, 0.15F + r * 0.1F, fx.color, a, 64);
        LfDraw.ringFlat(vc, mm, r * 0.85F, 0.08F + r * 0.04F, 0xFFFFFF, a * 0.5F, 48);
        ps.popPose();
    }

    /** Detectar Mana: anéis horizontais e verticais acompanhando a esfera hexagonal. */
    private static void pulseRings(VertexConsumer vc, PoseStack ps, Vec3 c, Fx fx, float t) {
        float r = fx.size * LfDraw.smooth(t * 1.2F);
        float a = 0.7F * (1 - t);
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        LfDraw.ringFlat(vc, ps.last().pose(), r, 0.25F, fx.color, a, 96);
        for (int k = 0; k < 3; k++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(k * 60F));
            LfDraw.ringUpright(vc, ps.last().pose(), r, 0.18F, fx.color, a * 0.6F, 96);
            ps.popPose();
        }
        ps.popPose();
    }

    /** "Levante-se!": coluna de chamas negras subindo (camada escura). */
    private static void arisePillarDark(VertexConsumer vc, Matrix4f m, Vec3 c, Fx fx, float t, Vec3 cam) {
        float h = fx.size * LfDraw.smooth(t * 2.2F);
        float a = t < 0.7F ? 0.85F : 0.85F * (1 - (t - 0.7F) / 0.3F);
        int n = 8;
        for (int k = 0; k < n; k++) {
            double ang = Math.PI * 2 * k / n + (fx.seed % 10) * 0.1 + t * 2;
            double rr = 0.35 + 0.25 * ((k * 31 + fx.seed) & 3) / 3.0;
            Vec3 b = c.add(Math.cos(ang) * rr, 0, Math.sin(ang) * rr);
            Vec3 top = b.add(Math.cos(ang + 1.2) * 0.25, h * (0.7 + 0.3 * ((k * 17) % 5) / 4.0), Math.sin(ang + 1.2) * 0.25);
            Vec3 side = LfDraw.facingSide(b, top, Vec3.ZERO);
            LfDraw.softRibbon(vc, m, b, top, side, 0.35F, 0.05F, 0x05000A, a, 0F);
        }
    }

    /** "Levante-se!": brilho roxo na base e olhos que se acendem no alto. */
    private static void ariseGlow(VertexConsumer vc, PoseStack ps, Vec3 c, Fx fx, float t, Vector3f left, Vector3f up) {
        float a = (1 - t);
        Matrix4f m = ps.last().pose();
        LfDraw.billboard(vc, m, c.add(0, 0.3, 0), left, up, 1.6F + t, LfDraw.argb(fx.color, 0.8F * a), LfDraw.argb(fx.color, 0F));
        float h = fx.size * LfDraw.smooth(t * 2.2F);
        Vec3 side = LfDraw.facingSide(c, c.add(0, h, 0), Vec3.ZERO);
        LfDraw.softRibbon(vc, m, c, c.add(0, h, 0), side, 0.5F, 0.1F, fx.color, 0.6F * a, 0F);
        ps.pushPose();
        ps.translate(c.x, c.y + 0.05, c.z);
        LfDraw.ringFlat(vc, ps.last().pose(), 0.6F + 1.6F * t, 0.15F, fx.color, 0.8F * a, 48);
        ps.popPose();
        if (t > 0.35F && t < 0.95F) {
            float ea = Mth.sin((t - 0.35F) / 0.6F * (float) Math.PI);
            Vec3 eye = c.add(0, h * 0.8, 0);
            LfDraw.billboard(vc, m, eye.add(left.x() * 0.12, 0, left.z() * 0.12), left, up, 0.12F, LfDraw.argb(0xD8C8FF, ea), LfDraw.argb(0x8A5BFF, 0F));
            LfDraw.billboard(vc, m, eye.add(-left.x() * 0.12, 0, -left.z() * 0.12), left, up, 0.12F, LfDraw.argb(0xD8C8FF, ea), LfDraw.argb(0x8A5BFF, 0F));
        }
    }

    private static void swapDark(VertexConsumer vc, Matrix4f m, Vec3 c, Fx fx, float t, Vector3f left, Vector3f up) {
        float r = 1.3F * (1 - t);
        LfDraw.billboard(vc, m, c, left, up, r, LfDraw.argb(0x000000, 0.8F * (1 - t)), LfDraw.argb(0x000000, 0F));
        LfDraw.billboard(vc, m, c.add(fx.dir), left, up, r, LfDraw.argb(0x000000, 0.8F * (1 - t)), LfDraw.argb(0x000000, 0F));
    }

    /** Pétalas abrindo em anel no chão. */
    private static void bloom(VertexConsumer vc, PoseStack ps, Vec3 c, Fx fx, float t) {
        int n = 28;
        float r = Math.abs(fx.size) * LfDraw.smooth(t * 1.5F);
        float a = t < 0.6F ? 0.9F : 0.9F * (1 - (t - 0.6F) / 0.4F);
        ps.pushPose();
        ps.translate(c.x, c.y + 0.03, c.z);
        Matrix4f m = ps.last().pose();
        for (int k = 0; k < n; k++) {
            double ang = Math.PI * 2 * k / n + (fx.seed % 13) * 0.2;
            float rr = r * (0.6F + 0.4F * ((k * 37) % 7) / 6F);
            float x = (float) Math.cos(ang) * rr, z = (float) Math.sin(ang) * rr;
            int col = (k % 3 == 0) ? fx.color : (k % 3 == 1 ? LfDraw.lighten(fx.color, 0.5F) : 0x6FCF5A);
            float s = 0.12F;
            float rot = (float) ang + t * 3;
            float cs = Mth.cos(rot) * s, sn = Mth.sin(rot) * s;
            int ac = LfDraw.argb(col, a);
            LfDraw.v(vc, m, x - cs, 0.02F + t * 0.3F, z - sn, ac);
            LfDraw.v(vc, m, x - sn * 0.5F, 0.02F + t * 0.3F, z + cs * 0.5F, ac);
            LfDraw.v(vc, m, x + cs, 0.02F + t * 0.3F, z + sn, ac);
            LfDraw.v(vc, m, x + sn * 0.5F, 0.02F + t * 0.3F, z - cs * 0.5F, ac);
        }
        ps.popPose();
    }

    /** Barreira estilhaçando: cacos hexagonais voando. */
    private static void shatter(VertexConsumer vc, PoseStack ps, Vec3 c, Fx fx, float t) {
        int n = 48;
        java.util.Random rnd = new java.util.Random(fx.seed);
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        Matrix4f m = ps.last().pose();
        for (int k = 0; k < n; k++) {
            double th = rnd.nextDouble() * Math.PI * 0.5, ph = rnd.nextDouble() * Math.PI * 2;
            Vec3 d = new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
            Vec3 p = d.scale(fx.size * (1 + t * 0.8)).add(0, -t * t * 2.5, 0);
            float s = 0.18F * (1 - t * 0.5F);
            int a = LfDraw.argb(fx.color, 0.9F * (1 - t));
            float px = (float) p.x, py = (float) p.y, pz = (float) p.z;
            LfDraw.v(vc, m, px - s, py, pz, a);
            LfDraw.v(vc, m, px, py + s, pz, a);
            LfDraw.v(vc, m, px + s, py, pz, a);
            LfDraw.v(vc, m, px, py - s, pz, a);
        }
        ps.popPose();
    }

    /** Monarca: chamas negras subindo em volta do corpo. */
    private static void monarchFlames(VertexConsumer vc, Matrix4f m, Vec3 p, Entity ent, float time, Vec3 cam) {
        float h = ent.getBbHeight();
        int n = 10;
        for (int k = 0; k < n; k++) {
            double ang = Math.PI * 2 * k / n + time * 0.05;
            float ph = ((time * 0.04F + k * 0.37F) % 1F);
            double rr = 0.45 + 0.1 * Math.sin(time * 0.2 + k);
            Vec3 b = p.add(Math.cos(ang) * rr, h * 0.05, Math.sin(ang) * rr);
            Vec3 top = b.add(Math.cos(ang) * 0.15, h * (0.8 + 0.5 * ph), Math.sin(ang) * 0.15);
            Vec3 side = LfDraw.facingSide(b, top, Vec3.ZERO);
            LfDraw.softRibbon(vc, m, b, top, side, 0.22F, 0.02F, 0x08000F, 0.55F, 0F);
        }
    }

    /** Monarca: contorno roxo e olhos acesos. */
    private static void monarchGlow(VertexConsumer vc, PoseStack ps, Vec3 p, Entity ent, float time) {
        ps.pushPose();
        ps.translate(p.x, p.y + 0.06, p.z);
        LfDraw.ringFlat(vc, ps.last().pose(), 1.4F + 0.1F * Mth.sin(time * 0.15F), 0.2F, 0x6A3DD8, 0.6F, 48);
        LfDraw.ringFlat(vc, ps.last().pose(), 3.4F, 0.12F, 0x8A5BFF, 0.35F, 64);
        ps.popPose();
    }

    /** Meditação: anel que se fecha e brilho verde-laranja subindo. */
    private static void meditateGlow(VertexConsumer vc, PoseStack ps, Vec3 p, float time, float progress) {
        ps.pushPose();
        ps.translate(p.x, p.y + 0.05, p.z);
        Matrix4f m = ps.last().pose();
        float r = 4.5F * (1F - progress) + 0.8F;
        LfDraw.ringFlat(vc, m, r, 0.2F, 0x8FE07A, 0.6F, 64);
        LfDraw.ringFlat(vc, m, 0.9F, 0.1F + progress * 0.2F, 0xF08A24, 0.3F + 0.5F * progress, 48);
        ps.popPose();
    }

    /** Sono: espinhos de roseira girando devagar em volta dos pés, com rosas. */
    private static void sleepVines(VertexConsumer vc, PoseStack ps, Vec3 p, Entity ent, float time) {
        float r = Math.max(0.5F, ent.getBbWidth() * 0.75F);
        ps.pushPose();
        ps.translate(p.x, p.y + 0.15, p.z);
        ps.mulPose(Axis.YP.rotationDegrees(time * 0.8F));
        Matrix4f m = ps.last().pose();
        int seg = 24;
        for (int k = 0; k < seg; k++) {
            float a0 = (float) (Math.PI * 2 * k / seg), a1 = (float) (Math.PI * 2 * (k + 1) / seg);
            float y0 = 0.15F * Mth.sin(a0 * 3), y1 = 0.15F * Mth.sin(a1 * 3);
            int g = LfDraw.argb(0x2F5A1E, 0.9F);
            LfDraw.v(vc, m, Mth.cos(a0) * r, y0 - 0.04F, Mth.sin(a0) * r, g);
            LfDraw.v(vc, m, Mth.cos(a1) * r, y1 - 0.04F, Mth.sin(a1) * r, g);
            LfDraw.v(vc, m, Mth.cos(a1) * r, y1 + 0.04F, Mth.sin(a1) * r, g);
            LfDraw.v(vc, m, Mth.cos(a0) * r, y0 + 0.04F, Mth.sin(a0) * r, g);
            if (k % 6 == 0) {
                float x = Mth.cos(a0) * r, z = Mth.sin(a0) * r;
                int rose = LfDraw.argb(0xC0182E, 0.95F);
                LfDraw.v(vc, m, x - 0.08F, y0, z, rose);
                LfDraw.v(vc, m, x, y0 + 0.1F, z, rose);
                LfDraw.v(vc, m, x + 0.08F, y0, z, rose);
                LfDraw.v(vc, m, x, y0 - 0.06F, z, rose);
            } else if (k % 2 == 1) {
                float x = Mth.cos(a0) * r, z = Mth.sin(a0) * r;
                int thorn = LfDraw.argb(0x1A3010, 0.9F);
                LfDraw.v(vc, m, x, y0, z, thorn);
                LfDraw.v(vc, m, x * 1.12F, y0 + 0.12F, z * 1.12F, thorn);
                LfDraw.v(vc, m, x * 1.04F, y0 + 0.02F, z * 1.04F, thorn);
                LfDraw.v(vc, m, x, y0, z, thorn);
            }
        }
        ps.popPose();
    }

    private LfFxClient() {}
}
