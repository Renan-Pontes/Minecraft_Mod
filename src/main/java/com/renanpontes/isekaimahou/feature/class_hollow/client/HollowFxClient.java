package com.renanpontes.isekaimahou.feature.class_hollow.client;

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
import com.renanpontes.isekaimahou.feature.class_hollow.Hollow;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * SÓ CLIENTE. Os efeitos do Hollow desenhados no cliente (o servidor só manda {@link HollowNetwork.Fx}):
 * <ul>
 *   <li>a carga do Cero na boca (orbe que cresce, faíscas sugadas; no Gran Rey Cero, a gota de sangue caindo nele);</li>
 *   <li>o FEIXE do Cero (núcleo branco, corpo na cor do rank, anéis na saída) e o risco da Bala;</li>
 *   <li>Sonído (imagem residual + linhas de velocidade), Garganta/Descorrer (o céu rasgado com dentes), Pesquisa (pulso),
 *   almas voando para a boca ao devorar, o casulo da Resurrección, ondas de choque com destroços, Hierro, Regeneração;</li>
 *   <li>o Reiatsu rubro-negro em volta de todo Hollow à vista, os marcadores da Pesquisa (através das paredes) e o tremor
 *   de câmera.</li>
 * </ul>
 * Tudo passa pelo {@link FxQuality} (quantidades, extras só no Alto, distância máxima).
 */
public final class HollowFxClient {
    private static final class Fx {
        final HollowNetwork.Fx p;
        final int life;
        int age;
        final long seed;

        Fx(HollowNetwork.Fx p, int life, long seed) {
            this.p = p;
            this.life = life;
            this.seed = seed;
        }
    }

    private static final List<Fx> FX = new ArrayList<>();
    private static final Map<Integer, Integer> SENSE = new HashMap<>();
    private static long senseUntil;
    private static float shake;
    private static ClientLevel level;

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    static void accept(HollowNetwork.Fx p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (level != mc.level) {
            FX.clear();
            level = mc.level;
        }
        // v1.4: a carga acabou (disparou ou cancelou)
        if (p.kind() == HollowNetwork.CHARGE_STOP) {
            FX.removeIf(f -> f.p.entity() == p.entity() && (f.p.kind() == HollowNetwork.CERO_CHARGE || f.p.kind() == HollowNetwork.GRAN_REY_CHARGE));
            return;
        }
        // longe demais para o preset: ignora (o tremor tem raio próprio)
        if (p.kind() != HollowNetwork.SHAKE && mc.player.position().distanceToSqr(p.pos()) > sq(FxQuality.range() + 32)) return;
        int life = switch (p.kind()) {
            case HollowNetwork.CERO_CHARGE, HollowNetwork.GRAN_REY_CHARGE -> Math.max(6, (int) p.size());
            case HollowNetwork.CERO_BEAM -> 16;
            case HollowNetwork.OSCURAS_BEAM -> 26;
            case HollowNetwork.NEGACION, HollowNetwork.RELEASE_CHANNEL -> Math.max(10, (int) p.size());
            case HollowNetwork.BALA -> 5;
            case HollowNetwork.SONIDO -> 14;
            case HollowNetwork.GARGANTA -> 200;
            case HollowNetwork.RIFT -> 30;
            case HollowNetwork.PESQUISA -> 24;
            case HollowNetwork.DEVOUR -> 22;
            case HollowNetwork.RESURRECCION -> 40;
            case HollowNetwork.HIERRO -> 12;
            case HollowNetwork.REGEN -> 16;
            case HollowNetwork.SHOCKWAVE -> 14;
            case HollowNetwork.MASK_FORM -> 22;
            case HollowNetwork.SHAKE -> 0;
            case HollowNetwork.CLAWS -> Mth.clamp((int) Math.ceil(p.vec().length() / Math.max(0.2F, p.size())) + 6, 6, 60);
            default -> 10;
        };
        if (p.kind() == HollowNetwork.SHAKE) {
            double d = Math.sqrt(mc.player.position().distanceToSqr(p.pos()));
            float s = p.size() * (float) Mth.clamp(1.0 - d / 40.0, 0.0, 1.0);
            shake = Math.max(shake, s);
            return;
        }
        // um efeito novo da mesma entidade e do mesmo tipo de carga substitui o anterior
        if (p.kind() == HollowNetwork.CERO_CHARGE || p.kind() == HollowNetwork.GRAN_REY_CHARGE) {
            FX.removeIf(f -> f.p.entity() == p.entity() && (f.p.kind() == HollowNetwork.CERO_CHARGE || f.p.kind() == HollowNetwork.GRAN_REY_CHARGE));
        }
        if (FX.size() > 96) FX.remove(0);
        FX.add(new Fx(p, life, mc.level.random.nextLong()));
        onSpawn(mc.level, p);
    }

    static void acceptSense(HollowNetwork.Sense p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        SENSE.clear();
        for (int i = 0; i < p.ids().length && i < p.kinds().length; i++) SENSE.put(p.ids()[i], (int) p.kinds()[i]);
        senseUntil = mc.level.getGameTime() + p.ticks();
    }

    /** Tremor pedido por outras partes do cliente (cena da hollowificação). */
    static void addShake(float s) {
        shake = Math.max(shake, s);
    }

    static void clear() {
        FX.clear();
        SENSE.clear();
        shake = 0;
        level = null;
    }

    private static double sq(double v) {
        return v * v;
    }

    // =====================================================================================
    //  Partículas na hora (destroços, faíscas) — multiplicadas pelo preset
    // =====================================================================================
    private static void onSpawn(ClientLevel lv, HollowNetwork.Fx p) {
        RandomSource r = lv.random;
        Vec3 pos = p.pos();
        DustParticleOptions col = dust(p.rgb(), 1.3F);
        switch (p.kind()) {
            case HollowNetwork.CERO_BEAM -> {
                Vec3 to = p.vec();
                Vec3 d = to.subtract(pos);
                int n = FxQuality.count(26);
                for (int i = 0; i < n; i++) {
                    Vec3 at = pos.add(d.scale(r.nextDouble()));
                    lv.addParticle(col, at.x + gauss(r, 0.3), at.y + gauss(r, 0.3), at.z + gauss(r, 0.3), 0, 0, 0);
                }
                debris(lv, to, FxQuality.count(14), 0.5 + p.size() * 0.2);
                for (int i = 0; i < FxQuality.count(10); i++) {
                    lv.addParticle(ParticleTypes.LARGE_SMOKE, to.x + gauss(r, 0.6), to.y + gauss(r, 0.5), to.z + gauss(r, 0.6),
                            gauss(r, 0.08), 0.05 + r.nextDouble() * 0.06, gauss(r, 0.08));
                }
                if (FxQuality.atLeast(MahouConfig.Graphics.HIGH)) {
                    for (int i = 0; i < FxQuality.count(8); i++) {
                        lv.addParticle(ParticleTypes.LAVA, to.x, to.y, to.z, 0, 0, 0);
                    }
                }
            }
            case HollowNetwork.OSCURAS_BEAM -> {
                Vec3 to = p.vec();
                Vec3 d = to.subtract(pos);
                int n = FxQuality.count(30);
                for (int i = 0; i < n; i++) {
                    Vec3 at = pos.add(d.scale(r.nextDouble()));
                    lv.addParticle(i % 3 == 0 ? col : dust(0x0A0004, 1.8F), at.x + gauss(r, 0.5), at.y + gauss(r, 0.5), at.z + gauss(r, 0.5), 0, 0, 0);
                }
                debris(lv, to, FxQuality.count(20), 0.8 + p.size() * 0.3);
                for (int i = 0; i < FxQuality.count(14); i++) {
                    lv.addParticle(ParticleTypes.SQUID_INK, to.x + gauss(r, 0.8), to.y + gauss(r, 0.6), to.z + gauss(r, 0.8),
                            gauss(r, 0.1), 0.05 + r.nextDouble() * 0.08, gauss(r, 0.1));
                }
                if (FxQuality.atLeast(MahouConfig.Graphics.HIGH)) {
                    for (int i = 0; i < FxQuality.count(10); i++) {
                        lv.addParticle(ParticleTypes.SOUL, to.x + gauss(r, 1.0), to.y + gauss(r, 0.6), to.z + gauss(r, 1.0), 0, 0.06, 0);
                    }
                }
            }
            case HollowNetwork.NEGACION -> {
                for (int i = 0; i < FxQuality.count(24); i++) {
                    lv.addParticle(ParticleTypes.END_ROD, pos.x + gauss(r, 0.8), pos.y + r.nextDouble() * 3, pos.z + gauss(r, 0.8), 0, 0.06, 0);
                }
            }
            case HollowNetwork.SHOCKWAVE -> {
                debris(lv, pos, FxQuality.count(10), p.size() * 0.4);
                int n = FxQuality.count(18);
                for (int i = 0; i < n; i++) {
                    double a = i * Mth.TWO_PI / n;
                    lv.addParticle(ParticleTypes.CLOUD, pos.x + Math.cos(a) * 0.6, pos.y + 0.1, pos.z + Math.sin(a) * 0.6,
                            Math.cos(a) * 0.35 * Math.max(1, p.size() / 3), 0.02, Math.sin(a) * 0.35 * Math.max(1, p.size() / 3));
                }
            }
            case HollowNetwork.SONIDO -> {
                for (int i = 0; i < FxQuality.count(10); i++) {
                    lv.addParticle(ParticleTypes.SMOKE, pos.x + gauss(r, 0.3), pos.y + r.nextDouble() * 1.8, pos.z + gauss(r, 0.3), 0, 0.01, 0);
                }
                for (int i = 0; i < FxQuality.count(6); i++) {
                    lv.addParticle(ParticleTypes.POOF, p.vec().x + gauss(r, 0.3), p.vec().y + 0.1, p.vec().z + gauss(r, 0.3), 0, 0.02, 0);
                }
            }
            case HollowNetwork.REGEN -> {
                for (int i = 0; i < FxQuality.count(18); i++) {
                    lv.addParticle(dust(i % 3 == 0 ? 0xFFFFFF : 0xB01020, 0.9F), pos.x + gauss(r, 0.35), pos.y + r.nextDouble() * 1.8,
                            pos.z + gauss(r, 0.35), 0, 0.05, 0);
                }
            }
            case HollowNetwork.HIERRO -> {
                for (int i = 0; i < FxQuality.count(16); i++) {
                    lv.addParticle(ParticleTypes.CRIT, pos.x + gauss(r, 0.4), pos.y + r.nextDouble() * 1.9, pos.z + gauss(r, 0.4),
                            gauss(r, 0.1), 0.05, gauss(r, 0.1));
                }
            }
            case HollowNetwork.RESURRECCION -> {
                for (int i = 0; i < FxQuality.count(40); i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double sp = 0.3 + r.nextDouble() * 0.5;
                    lv.addParticle(col, pos.x, pos.y + 1 + gauss(r, 0.5), pos.z, Math.cos(a) * sp, gauss(r, 0.2), Math.sin(a) * sp);
                }
                debris(lv, pos, FxQuality.count(16), p.size() * 0.4);
            }
            case HollowNetwork.GARGANTA, HollowNetwork.RIFT -> {
                for (int i = 0; i < FxQuality.count(20); i++) {
                    lv.addParticle(ParticleTypes.REVERSE_PORTAL, pos.x + gauss(r, 0.4), pos.y + gauss(r, 1.0), pos.z + gauss(r, 0.4), 0, 0, 0);
                }
            }
            default -> { }
        }
    }

    /** Destroços do chão em volta de {@code at}. */
    private static void debris(ClientLevel lv, Vec3 at, int n, double spread) {
        BlockPos ground = BlockPos.containing(at.x, at.y - 0.5, at.z);
        BlockState st = null;
        for (int i = 0; i < 4; i++) {
            BlockState s = lv.getBlockState(ground.below(i));
            if (!s.isAir()) {
                st = s;
                ground = ground.below(i);
                break;
            }
        }
        if (st == null) return;
        RandomSource r = lv.random;
        BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, st);
        for (int i = 0; i < n; i++) {
            lv.addParticle(opt, at.x + gauss(r, spread), ground.getY() + 1.1, at.z + gauss(r, spread), gauss(r, 0.3), 0.3 + r.nextDouble() * 0.5,
                    gauss(r, 0.3));
        }
    }

    private static double gauss(RandomSource r, double s) {
        return r.nextGaussian() * s;
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    // =====================================================================================
    //  Tick: idade, partículas contínuas, Reiatsu em volta dos Hollows
    // =====================================================================================
    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (level != mc.level) {
            FX.clear();
            SENSE.clear();
            level = mc.level;
        }
        shake *= 0.86F;
        if (shake < 0.01F) shake = 0;
        Iterator<Fx> it = FX.iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            if (++f.age > f.life) {
                it.remove();
                continue;
            }
            tickFx(mc.level, f);
        }
        if (!SENSE.isEmpty() && mc.level.getGameTime() > senseUntil) SENSE.clear();
        aura(mc);
    }

    private static void tickFx(ClientLevel lv, Fx f) {
        RandomSource r = lv.random;
        HollowNetwork.Fx p = f.p;
        switch (p.kind()) {
            case HollowNetwork.CERO_CHARGE, HollowNetwork.GRAN_REY_CHARGE -> {
                Entity ent = lv.getEntity(p.entity());
                if (ent == null || f.age % 2 != 0) return;
                Vec3 m = muzzle(ent, 1F, p.vec().x > 0.5);
                boolean grand = p.kind() == HollowNetwork.GRAN_REY_CHARGE;
                for (int i = 0; i < FxQuality.count(grand ? 4 : 2); i++) {
                    Vec3 from = m.add(gauss(r, 1.2), gauss(r, 1.0), gauss(r, 1.2));
                    Vec3 v = m.subtract(from).scale(0.18);
                    lv.addParticle(dust(i % 2 == 0 ? p.rgb() : 0x1A0608, 1.0F), from.x, from.y, from.z, v.x, v.y, v.z);
                }
                if (grand && f.age % 6 == 0) {
                    lv.addParticle(ParticleTypes.DRIPPING_LAVA, m.x, m.y + 0.6, m.z, 0, 0, 0);
                }
            }
            case HollowNetwork.GARGANTA -> {
                if (f.age % 3 != 0 || f.age > f.life - 20) return;
                Vec3 c = p.pos();
                lv.addParticle(dust(0x0A0002, 1.8F), c.x + gauss(r, 0.5), c.y + gauss(r, 1.2), c.z + gauss(r, 0.5), 0, -0.01, 0);
                if (FxQuality.atLeast(MahouConfig.Graphics.HIGH)) {
                    lv.addParticle(ParticleTypes.ASH, c.x + gauss(r, 1.5), c.y + gauss(r, 1.5), c.z + gauss(r, 1.5), 0, 0, 0);
                }
            }
            case HollowNetwork.NEGACION -> {
                Entity ent = lv.getEntity(p.entity());
                if (ent == null || f.age % 2 != 0) return;
                for (int i = 0; i < FxQuality.count(3); i++) {
                    double a = r.nextDouble() * Mth.TWO_PI, rr = 0.8 + r.nextDouble() * 0.8;
                    lv.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : dust(0xFFE9A0, 1.0F), ent.getX() + Math.cos(a) * rr,
                            ent.getY() + r.nextDouble() * 2.5, ent.getZ() + Math.sin(a) * rr, 0, 0.08, 0);
                }
            }
            case HollowNetwork.RELEASE_CHANNEL -> {
                Entity ent = lv.getEntity(p.entity());
                if (ent == null) return;
                float u = f.age / (float) f.life;
                int n = FxQuality.count(4);
                for (int i = 0; i < n; i++) {
                    double a = f.age * 0.45 + i * Mth.TWO_PI / n;
                    double rr = 1.6 - 1.0 * u;
                    double y = ent.getY() + ((f.age * 0.12 + i * 0.5) % 3.0);
                    lv.addParticle(dust(i % 2 == 0 ? p.rgb() : 0x120306, 1.4F), ent.getX() + Math.cos(a) * rr, y, ent.getZ() + Math.sin(a) * rr,
                            0, 0.02, 0);
                }
                if (f.age % 5 == 0) debris(lv, ent.position(), FxQuality.count(3), 1.2);
                if (f.age % 8 == 0) addShake(0.15F + 0.25F * u);
            }
            case HollowNetwork.RESURRECCION -> {
                if (f.age > 24 || f.age % 2 != 0) return;
                Vec3 c = p.pos();
                for (int i = 0; i < FxQuality.count(3); i++) {
                    double a = r.nextDouble() * Mth.TWO_PI, rr = 0.6 + r.nextDouble() * 0.6;
                    lv.addParticle(dust(i % 2 == 0 ? p.rgb() : 0x1A0608, 1.6F), c.x + Math.cos(a) * rr, c.y + r.nextDouble() * 3,
                            c.z + Math.sin(a) * rr, 0, 0.12, 0);
                }
            }
            default -> { }
        }
    }

    /** O Reiatsu rubro-negro que vaza de todo Hollow à vista (mais forte liberado ou faminto). */
    private static void aura(Minecraft mc) {
        if (!HollowLooks.any() || mc.level.getGameTime() % 3 != 0) return;
        double range = FxQuality.range();
        RandomSource r = mc.level.random;
        HollowLooks.forEach((id, look) -> {
            Entity e = mc.level.getEntity(id);
            if (!(e instanceof LivingEntity le) || le.isInvisible() || le.distanceToSqr(mc.player) > range * range) return;
            if (le == mc.player && mc.options.getCameraType().isFirstPerson() && !look.released()) return;
            int n = FxQuality.count(look.released() ? 3 : 1);
            int rgb = look.released() && look.release() > 0 ? releaseRgb(look.release()) : Hollow.REIATSU_RGB;
            for (int i = 0; i < n; i++) {
                double y = le.getY() + r.nextDouble() * le.getBbHeight();
                ParticleOptions opt = i % 2 == 0 ? dust(rgb, 0.9F + 0.2F * look.rank()) : dust(0x120306, 1.2F);
                lv(mc).addParticle(opt, le.getX() + gauss(r, 0.35), y, le.getZ() + gauss(r, 0.35), 0, 0.03, 0);
            }
            if (look.starving() && r.nextInt(4) == 0) {
                lv(mc).addParticle(ParticleTypes.SMOKE, le.getX(), le.getY() + 1.6, le.getZ(), 0, 0.02, 0);
            }
        });
    }

    private static ClientLevel lv(Minecraft mc) {
        return mc.level;
    }

    static int releaseRgb(int release) {
        Hollow.Release r = Hollow.Release.byIndex(release - 1);
        return r == null ? Hollow.REIATSU_RGB : r.rgb;
    }

    private static Vec3 mouth(Entity e, float pt) {
        return e.getEyePosition(pt).add(e.getViewVector(pt).scale(0.55)).add(0, -0.15, 0);
    }

    /** De onde sai o Cero: a boca, ou a ponta dos dedos da mão direita (Arrancar). Igual ao servidor. */
    private static Vec3 muzzle(Entity e, float pt, boolean hand) {
        if (!hand) return mouth(e, pt);
        Vec3 look = e.getViewVector(pt);
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        return e.getEyePosition(pt).add(look.scale(0.85)).add(right.scale(0.38)).add(0, -0.4, 0);
    }

    /** Cor quase negra (não aparece somando luz: vai na passada escura). */
    private static boolean dark(int rgb) {
        return ((rgb >> 16) & 255) < 0x40 && ((rgb >> 8) & 255) < 0x40 && (rgb & 255) < 0x40;
    }

    // =====================================================================================
    //  Tremor de câmera
    // =====================================================================================
    static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        if (shake <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = (float) (mc.level.getGameTime() + e.getPartialTick());
        float s = Math.min(1.6F, shake) * MahouConfig.shakeScale();
        e.setYaw(e.getYaw() + Mth.sin(t * 2.7F) * 1.6F * s);
        e.setPitch(e.getPitch() + Mth.cos(t * 3.3F) * 1.2F * s);
        e.setRoll(e.getRoll() + Mth.sin(t * 1.9F) * 1.4F * s);
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level && !FX.isEmpty()) return;
        if (FX.isEmpty() && SENSE.isEmpty()) return;
        float pt = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = e.getPoseStack();
        Matrix4f m = ps.last().pose();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float time = (mc.level.getGameTime() % 100000L) + pt;

        // ---- negros (mistura normal): rasgos da Garganta, imagem residual
        if (!FX.isEmpty()) {
            RenderType darkT = HollowRenderTypes.translucent(HollowRenderTypes.GLOW);
            VertexConsumer vc = buffers.getBuffer(darkT);
            for (Fx f : FX) {
                switch (f.p.kind()) {
                    case HollowNetwork.GARGANTA -> drawTear(vc, m, f.p.pos(), f.age + pt, f.life, f.p.size(), cam, time, true);
                    case HollowNetwork.RIFT -> {
                        drawTear(vc, m, f.p.pos(), f.age + pt, f.life, f.p.size(), cam, time, true);
                        drawTear(vc, m, f.p.vec(), f.age + pt, f.life, f.p.size(), cam, time, true);
                    }
                    case HollowNetwork.SONIDO -> drawGhostDark(vc, m, f, cam, pt);
                    case HollowNetwork.OSCURAS_BEAM -> drawOscurasDark(vc, m, f, cam, pt);
                    case HollowNetwork.CERO_CHARGE -> {
                        if (dark(f.p.rgb())) drawDarkOrb(vc, m, left, up, f, cam, time, pt);
                    }
                    default -> { }
                }
            }
            buffers.endBatch(darkT);

            RenderType ghostT = HollowRenderTypes.translucent(HollowRenderTypes.GHOST);
            vc = buffers.getBuffer(ghostT);
            for (Fx f : FX) if (f.p.kind() == HollowNetwork.SONIDO) drawAfterimage(vc, m, f, cam, pt);
            buffers.endBatch(ghostT);

            RenderType spikeT = HollowRenderTypes.translucent(HollowRenderTypes.SPIKE);
            vc = buffers.getBuffer(spikeT);
            for (Fx f : FX) {
                if (f.p.kind() == HollowNetwork.GARGANTA) drawTeeth(vc, m, left, up, f.p.pos(), f.age + pt, f.life, f.p.size(), cam);
                else if (f.p.kind() == HollowNetwork.MASK_FORM) drawMaskShards(vc, m, left, up, f, cam, pt);
            }
            buffers.endBatch(spikeT);

            // ---- brilhos (aditivo)
            RenderType glowT = HollowRenderTypes.additive(HollowRenderTypes.GLOW);
            vc = buffers.getBuffer(glowT);
            for (Fx f : FX) {
                switch (f.p.kind()) {
                    case HollowNetwork.CERO_CHARGE -> drawCharge(vc, m, left, up, f, cam, time, pt, false);
                    case HollowNetwork.GRAN_REY_CHARGE -> drawCharge(vc, m, left, up, f, cam, time, pt, true);
                    case HollowNetwork.CERO_BEAM -> drawBeam(vc, m, left, up, f, cam, time, pt);
                    case HollowNetwork.OSCURAS_BEAM -> drawOscurasRim(vc, m, left, up, f, cam, time, pt);
                    case HollowNetwork.NEGACION -> drawNegacion(vc, m, left, up, f, cam, time, pt);
                    case HollowNetwork.RELEASE_CHANNEL -> drawReleaseColumn(vc, m, f, cam, pt);
                    case HollowNetwork.BALA -> drawBala(vc, m, f, cam, pt);
                    case HollowNetwork.SONIDO -> drawSpeedLines(vc, m, f, cam, pt);
                    case HollowNetwork.GARGANTA -> drawTearRim(vc, m, f.p.pos(), f.age + pt, f.life, f.p.size(), cam, time);
                    case HollowNetwork.RIFT -> {
                        drawTearRim(vc, m, f.p.pos(), f.age + pt, f.life, f.p.size(), cam, time);
                        drawTearRim(vc, m, f.p.vec(), f.age + pt, f.life, f.p.size(), cam, time);
                    }
                    case HollowNetwork.DEVOUR -> drawSouls(vc, m, left, up, f, cam, pt);
                    case HollowNetwork.RESURRECCION -> drawCocoon(vc, m, left, up, f, cam, time, pt);
                    case HollowNetwork.HIERRO -> drawFlash(vc, m, left, up, f, cam, pt, 1.4F);
                    case HollowNetwork.MASK_FORM -> drawFlash(vc, m, left, up, f, cam, pt, 0.6F);
                    case HollowNetwork.CLAWS -> drawClaws(vc, m, f, cam, pt);
                    default -> { }
                }
            }
            buffers.endBatch(glowT);

            RenderType ringT = HollowRenderTypes.additive(HollowRenderTypes.RING);
            vc = buffers.getBuffer(ringT);
            for (Fx f : FX) {
                switch (f.p.kind()) {
                    case HollowNetwork.PESQUISA -> drawRing(vc, m, f, cam, pt, 1.0F);
                    case HollowNetwork.SHOCKWAVE -> drawRing(vc, m, f, cam, pt, 1.0F);
                    case HollowNetwork.RESURRECCION -> drawRing(vc, m, f, cam, pt, 0.8F);
                    case HollowNetwork.CERO_BEAM, HollowNetwork.OSCURAS_BEAM -> drawMuzzleRings(vc, m, f, cam, pt);
                    case HollowNetwork.RELEASE_CHANNEL -> {
                        Entity ent = level.getEntity(f.p.entity());
                        if (ent != null) {
                            Vec3 c = ent.getPosition(pt).subtract(cam);
                            float t = f.age + pt;
                            HollowRenderTypes.flat(vc, m, (float) c.x, (float) c.y + 0.06F, (float) c.z, 2.2F + 0.6F * Mth.sin(t * 0.3F), t * 0.08F,
                                    HollowRenderTypes.argb(f.p.rgb(), 0.7F));
                        }
                    }
                    default -> { }
                }
            }
            buffers.endBatch(ringT);
        }

        // ---- Pesquisa: marcadores através das paredes
        if (!SENSE.isEmpty()) {
            RenderType xT = HollowRenderTypes.xray(HollowRenderTypes.GLOW);
            VertexConsumer vc = buffers.getBuffer(xT);
            float fade = Mth.clamp((senseUntil - mc.level.getGameTime() - pt) / 20F, 0F, 1F);
            for (Map.Entry<Integer, Integer> me : SENSE.entrySet()) {
                Entity ent = mc.level.getEntity(me.getKey());
                if (ent == null || !ent.isAlive()) continue;
                Vec3 c = ent.getPosition(pt).add(0, ent.getBbHeight() * 0.6, 0).subtract(cam);
                int rgb = switch (me.getValue()) {
                    case 1 -> 0xE01030;
                    case 2 -> 0xF2EEE6;
                    case 3 -> 0x6A0010;
                    default -> 0x50C070;
                };
                float pulse = 0.85F + 0.15F * Mth.sin(time * 0.3F + me.getKey());
                float size = (me.getValue() == 3 ? 0.9F : 0.5F) * pulse * Math.max(0.6F, ent.getBbHeight() * 0.5F);
                HollowRenderTypes.billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, size, size, 0,
                        HollowRenderTypes.argb(rgb, 0.85F * fade));
            }
            buffers.endBatch(xT);
        }
    }

    private static void drawCharge(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx f, Vec3 cam, float time, float pt, boolean grand) {
        Entity ent = level.getEntity(f.p.entity());
        if (ent == null) return;
        float t = f.age + pt;
        float growth = f.p.vec().y > 0 ? (float) f.p.vec().y : f.life - 2F;
        float charge = Mth.clamp(t / Math.max(1F, growth), 0F, 1F);
        Vec3 c = muzzle(ent, pt, f.p.vec().x > 0.5).subtract(cam);
        float x = (float) c.x, y = (float) c.y, z = (float) c.z;
        float base = grand ? 1.3F : 0.75F;
        float r = 0.1F + base * charge * (1F + 0.12F * Mth.sin(time * 2.4F));
        int rgb = f.p.rgb();
        if (dark(rgb)) {
            // Cero Oscuras: o negro vai na passada escura; aqui só a borda rubra e as faíscas
            HollowRenderTypes.billboard(vc, m, left, up, x, y, z, r * 3.2F, r * 3.2F, time * 0.05F, HollowRenderTypes.argb(0x8A0018, 0.5F));
            rgb = 0xB0102A;
        }
        HollowRenderTypes.billboard(vc, m, left, up, x, y, z, r * 2.6F, r * 2.6F, time * 0.05F, HollowRenderTypes.argb(rgb, 0.45F));
        HollowRenderTypes.billboard(vc, m, left, up, x, y, z, r * 1.3F, r * 1.3F, 0, HollowRenderTypes.argb(HollowRenderTypes.mix(rgb, 0xFFFFFF, 0.3F), 0.85F));
        HollowRenderTypes.billboard(vc, m, left, up, x, y, z, r * 0.5F, r * 0.5F, 0, HollowRenderTypes.argb(0xFFFFFF, 1F));
        // faíscas sendo sugadas
        int n = FxQuality.atLeast(MahouConfig.Graphics.HIGH) ? 14 : 7;
        for (int i = 0; i < n; i++) {
            float ph = (time * 0.08F + hash(f.seed, i)) % 1F;
            float dist = (1F - ph) * (1.4F + charge * (grand ? 2.2F : 1.2F));
            float th = hash(f.seed, i + 40) * Mth.TWO_PI, phi = hash(f.seed, i + 80) * Mth.PI;
            HollowRenderTypes.billboard(vc, m, left, up, x + Mth.sin(phi) * Mth.cos(th) * dist, y + Mth.cos(phi) * dist,
                    z + Mth.sin(phi) * Mth.sin(th) * dist, 0.07F, 0.07F, 0, HollowRenderTypes.argb(rgb, ph));
        }
        if (grand) {
            // a gota de sangue que cai no Cero
            float drop = (t % 20F) / 20F;
            HollowRenderTypes.billboard(vc, m, left, up, x, y + 1.2F - drop * 1.2F, z, 0.08F, 0.12F, 0,
                    HollowRenderTypes.argb(0xA00010, 1F - drop * 0.5F));
        }
    }

    private static void drawBeam(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx f, Vec3 cam, float time, float pt) {
        float t = f.age + pt;
        float k = Mth.clamp(1F - t / f.life, 0F, 1F);
        // estica nos 2 primeiros ticks, depois afina
        float grow = Mth.clamp(t / 2F, 0F, 1F);
        Vec3 a = f.p.pos().subtract(cam);
        Vec3 full = f.p.vec().subtract(f.p.pos());
        Vec3 b = f.p.pos().add(full.scale(grow)).subtract(cam);
        float w = f.p.size() * (0.4F + 0.6F * k);
        int rgb = f.p.rgb();
        float ax = (float) a.x, ay = (float) a.y, az = (float) a.z, bx = (float) b.x, by = (float) b.y, bz = (float) b.z;
        HollowRenderTypes.ribbon(vc, m, ax, ay, az, bx, by, bz, w * 1.8F, w * 2.2F, HollowRenderTypes.argb(rgb, 0.35F * k),
                HollowRenderTypes.argb(rgb, 0.3F * k));
        HollowRenderTypes.ribbon(vc, m, ax, ay, az, bx, by, bz, w, w * 1.15F, HollowRenderTypes.argb(rgb, 0.9F * k),
                HollowRenderTypes.argb(rgb, 0.85F * k));
        HollowRenderTypes.ribbon(vc, m, ax, ay, az, bx, by, bz, w * 0.35F, w * 0.4F, HollowRenderTypes.argb(0xFFFFFF, k),
                HollowRenderTypes.argb(0xFFFFFF, 0.9F * k));
        if (FxQuality.atLeast(MahouConfig.Graphics.HIGH)) {
            // espiral escura em volta do núcleo
            Vec3 d = full.normalize();
            Vec3 s1 = d.cross(new Vec3(0, 1, 0));
            if (s1.lengthSqr() < 1.0E-4) s1 = new Vec3(1, 0, 0);
            s1 = s1.normalize();
            Vec3 s2 = d.cross(s1).normalize();
            double len = full.length() * grow;
            int segs = Math.min(40, (int) (len * 1.5));
            for (int i = 0; i < segs; i++) {
                double u = i / (double) segs;
                float ang = (float) (u * len * 1.4 - time * 0.9);
                Vec3 off = s1.scale(Mth.cos(ang) * w * 1.1).add(s2.scale(Mth.sin(ang) * w * 1.1));
                Vec3 at = f.p.pos().add(d.scale(u * len)).add(off).subtract(cam);
                HollowRenderTypes.billboard(vc, m, left, up, (float) at.x, (float) at.y, (float) at.z, w * 0.4F, w * 0.4F, 0,
                        HollowRenderTypes.argb(HollowRenderTypes.mix(rgb, 0xFFFFFF, 0.4F), 0.5F * k));
            }
        }
        // clarão de saída e de impacto
        HollowRenderTypes.billboard(vc, m, left, up, ax, ay, az, w * 3F * k, w * 3F * k, 0, HollowRenderTypes.argb(0xFFFFFF, 0.8F * k));
        float boom = f.p.size() * (2F + 3F * (1F - k));
        HollowRenderTypes.billboard(vc, m, left, up, (float) (f.p.vec().x - cam.x), (float) (f.p.vec().y - cam.y), (float) (f.p.vec().z - cam.z),
                boom, boom, time * 0.1F, HollowRenderTypes.argb(rgb, 0.7F * k));
    }

    /** Cero Oscuras, o corpo negro (mistura normal). */
    private static void drawOscurasDark(VertexConsumer vc, Matrix4f m, Fx f, Vec3 cam, float pt) {
        float t = f.age + pt;
        float k = Mth.clamp(1F - t / f.life, 0F, 1F);
        float grow = Mth.clamp(t / 3F, 0F, 1F);
        Vec3 a = f.p.pos().subtract(cam);
        Vec3 full = f.p.vec().subtract(f.p.pos());
        Vec3 b = f.p.pos().add(full.scale(grow)).subtract(cam);
        float w = f.p.size() * (0.5F + 0.5F * k);
        HollowRenderTypes.ribbon(vc, m, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, w * 1.1F, w * 1.3F,
                HollowRenderTypes.argb(0x000000, 0.92F * k), HollowRenderTypes.argb(0x050006, 0.85F * k));
        HollowRenderTypes.ribbon(vc, m, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, w * 0.6F, w * 0.7F,
                HollowRenderTypes.argb(0x000000, k), HollowRenderTypes.argb(0x000000, k));
    }

    /** Cero Oscuras, a borda na cor do arquétipo (aditivo) e o vazio no fim. */
    private static void drawOscurasRim(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx f, Vec3 cam, float time, float pt) {
        float t = f.age + pt;
        float k = Mth.clamp(1F - t / f.life, 0F, 1F);
        float grow = Mth.clamp(t / 3F, 0F, 1F);
        Vec3 a = f.p.pos().subtract(cam);
        Vec3 full = f.p.vec().subtract(f.p.pos());
        Vec3 b = f.p.pos().add(full.scale(grow)).subtract(cam);
        float w = f.p.size() * (0.5F + 0.5F * k);
        int rgb = f.p.rgb();
        float pulse = 0.75F + 0.25F * Mth.sin(time * 1.3F);
        HollowRenderTypes.ribbon(vc, m, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, w * 1.9F, w * 2.3F,
                HollowRenderTypes.argb(rgb, 0.35F * k * pulse), HollowRenderTypes.argb(rgb, 0.3F * k * pulse));
        if (FxQuality.atLeast(MahouConfig.Graphics.MEDIUM) && full.lengthSqr() > 1.0E-4) {
            // relâmpagos na cor do arquétipo correndo pelo feixe
            Vec3 d = full.normalize();
            Vec3 s1 = d.cross(new Vec3(0, 1, 0));
            if (s1.lengthSqr() < 1.0E-4) s1 = new Vec3(1, 0, 0);
            s1 = s1.normalize();
            double len = full.length() * grow;
            int segs = Math.min(48, (int) (len * 1.2));
            for (int i = 0; i < segs; i++) {
                double u = i / (double) segs;
                float off = (hash(f.seed, i + (int) (time / 2)) - 0.5F) * w * 1.6F;
                Vec3 at = f.p.pos().add(d.scale(u * len)).add(s1.scale(off)).subtract(cam);
                HollowRenderTypes.billboard(vc, m, left, up, (float) at.x, (float) at.y, (float) at.z, w * 0.18F, w * 0.18F, 0,
                        HollowRenderTypes.argb(HollowRenderTypes.mix(rgb, 0xFFFFFF, 0.3F), 0.7F * k));
            }
        }
        float boom = f.p.size() * (2.5F + 3.5F * (1F - k));
        HollowRenderTypes.billboard(vc, m, left, up, (float) (f.p.vec().x - cam.x), (float) (f.p.vec().y - cam.y), (float) (f.p.vec().z - cam.z),
                boom, boom, time * 0.1F, HollowRenderTypes.argb(rgb, 0.5F * k));
    }

    /** A órbita NEGRA na boca/mão (carga do Cero Oscuras). */
    private static void drawDarkOrb(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx f, Vec3 cam, float time, float pt) {
        Entity ent = level.getEntity(f.p.entity());
        if (ent == null) return;
        Vec3 c = muzzle(ent, pt, f.p.vec().x > 0.5).subtract(cam);
        float r = 0.95F * (1F + 0.1F * Mth.sin(time * 2.1F));
        HollowRenderTypes.billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, r * 1.6F, r * 1.6F, time * 0.07F,
                HollowRenderTypes.argb(0x000000, 0.9F));
        HollowRenderTypes.billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, r * 1.1F, r * 1.1F, -time * 0.05F,
                HollowRenderTypes.argb(0x000000, 1F));
    }

    /** Negación: a coluna de luz dourada que desce do céu sobre o Hollow. */
    private static void drawNegacion(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx f, Vec3 cam, float time, float pt) {
        Entity ent = level.getEntity(f.p.entity());
        Vec3 base = ent != null ? ent.getPosition(pt) : f.p.pos();
        float t = f.age + pt;
        float in = Mth.clamp(t / 6F, 0F, 1F), out = Mth.clamp((f.life - t) / 8F, 0F, 1F);
        float k = in * out;
        if (k <= 0) return;
        Vec3 c = base.subtract(cam);
        float h = 40F;
        float y0 = (float) c.y - 1F + (1F - in) * 30F;
        float pulse = 0.85F + 0.15F * Mth.sin(time * 0.4F);
        HollowRenderTypes.pillar(vc, m, (float) c.x, y0, (float) c.z, 1.7F * pulse, h, HollowRenderTypes.argb(0xFFE9A0, 0.32F * k),
                HollowRenderTypes.argb(0xFFE9A0, 0.05F * k));
        HollowRenderTypes.pillar(vc, m, (float) c.x, y0, (float) c.z, 0.7F, h, HollowRenderTypes.argb(0xFFFFF4, 0.6F * k),
                HollowRenderTypes.argb(0xFFFFF4, 0.1F * k));
        HollowRenderTypes.billboard(vc, m, left, up, (float) c.x, (float) c.y + 1F, (float) c.z, 2.2F * pulse, 2.6F * pulse, 0,
                HollowRenderTypes.argb(0xFFF2C0, 0.35F * k));
    }

    /** A coluna da invocação da Resurrección (cresce até o estouro). */
    private static void drawReleaseColumn(VertexConsumer vc, Matrix4f m, Fx f, Vec3 cam, float pt) {
        Entity ent = level.getEntity(f.p.entity());
        if (ent == null) return;
        float t = f.age + pt;
        float u = Mth.clamp(t / f.life, 0F, 1F);
        Vec3 c = ent.getPosition(pt).subtract(cam);
        int rgb = f.p.rgb();
        HollowRenderTypes.pillar(vc, m, (float) c.x, (float) c.y, (float) c.z, 0.6F + 0.9F * u, 3F + 12F * u,
                HollowRenderTypes.argb(rgb, 0.25F + 0.4F * u), HollowRenderTypes.argb(rgb, 0F));
        HollowRenderTypes.pillar(vc, m, (float) c.x, (float) c.y, (float) c.z, 0.25F + 0.3F * u, 4F + 14F * u,
                HollowRenderTypes.argb(0xFFFFFF, 0.2F + 0.4F * u), HollowRenderTypes.argb(0xFFFFFF, 0F));
    }

    private static void drawMuzzleRings(VertexConsumer vc, Matrix4f m, Fx f, Vec3 cam, float pt) {
        // anéis no chão no ponto de impacto (expandem)
        float t = f.age + pt;
        float k = Mth.clamp(1F - t / f.life, 0F, 1F);
        Vec3 c = f.p.vec().subtract(cam);
        float r = f.p.size() * (1.5F + t * 0.5F);
        HollowRenderTypes.flat(vc, m, (float) c.x, (float) c.y, (float) c.z, r, t * 0.1F, HollowRenderTypes.argb(f.p.rgb(), 0.8F * k));
    }

    private static void drawBala(VertexConsumer vc, Matrix4f m, Fx f, Vec3 cam, float pt) {
        float t = f.age + pt;
        float k = Mth.clamp(1F - t / f.life, 0F, 1F);
        Vec3 full = f.p.vec().subtract(f.p.pos());
        float head = Mth.clamp(t / 1.5F, 0F, 1F);
        float tail = Mth.clamp((t - 1F) / 3F, 0F, 1F);
        Vec3 a = f.p.pos().add(full.scale(tail)).subtract(cam);
        Vec3 b = f.p.pos().add(full.scale(head)).subtract(cam);
        HollowRenderTypes.ribbon(vc, m, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, 0.05F, 0.2F,
                HollowRenderTypes.argb(f.p.rgb(), 0.3F * k), HollowRenderTypes.argb(HollowRenderTypes.mix(f.p.rgb(), 0xFFFFFF, 0.5F), k));
    }

    /**
     * w10 — Desgarrón (Pantera): três marcas de garra em pé (meias-luas de ~3 blocos, curvadas para a frente) voando
     * lado a lado, com miolo branco e rastro (o rastro só no preset Alto+). Somem ao chegar no fim (parede/alcance).
     */
    private static void drawClaws(VertexConsumer vc, Matrix4f m, Fx f, Vec3 cam, float pt) {
        Vec3 full = f.p.vec();
        double reach = full.length();
        if (reach < 0.5) return;
        Vec3 dir = full.scale(1.0 / reach);
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        float t = f.age + pt;
        double d = Math.min(reach, t * f.p.size());
        float fade = d >= reach ? Mth.clamp(1F - (t - (float) (reach / f.p.size())) / 5F, 0F, 1F) : 1F;
        if (fade <= 0.01F) return;
        float grow = Mth.clamp(t / 3F, 0.4F, 1F);
        int echoes = FxQuality.atLeast(MahouConfig.Graphics.HIGH) ? 3 : 1;
        int rgb = f.p.rgb();
        int core = HollowRenderTypes.mix(rgb, 0xFFFFFF, 0.75F);
        for (int b = -1; b <= 1; b++) {
            for (int e = 0; e < echoes; e++) {
                Vec3 c = f.p.pos().add(dir.scale(Math.max(0, d - e * 0.9))).add(side.scale(b * 1.1)).subtract(cam);
                float a = fade * (e == 0 ? 1F : 0.45F / e);
                double h = 1.5 * grow * (1.0 - e * 0.1);
                Vec3 prev = null;
                for (int i = 0; i <= 10; i++) {
                    double ang = -1.15 + 2.3 * i / 10;
                    Vec3 p = c.add(0, h * Math.sin(ang), 0).add(dir.scale(h * 0.55 * (Math.cos(ang) - 1)));
                    if (prev != null) {
                        float w = (float) (0.32 * Math.cos(ang * 0.9));
                        HollowRenderTypes.ribbon(vc, m, (float) prev.x, (float) prev.y, (float) prev.z, (float) p.x, (float) p.y, (float) p.z,
                                w, w, HollowRenderTypes.argb(rgb, 0.85F * a), HollowRenderTypes.argb(rgb, 0.85F * a));
                        if (e == 0) {
                            HollowRenderTypes.ribbon(vc, m, (float) prev.x, (float) prev.y, (float) prev.z, (float) p.x, (float) p.y, (float) p.z,
                                    w * 0.35F, w * 0.35F, HollowRenderTypes.argb(core, a), HollowRenderTypes.argb(core, a));
                        }
                    }
                    prev = p;
                }
            }
        }
    }

    private static void drawGhostDark(VertexConsumer vc, Matrix4f m, Fx f, Vec3 cam, float pt) {
        float t = f.age + pt;
        float k = Mth.clamp(1F - t / f.life, 0F, 1F);
        Vec3 c = f.p.pos().subtract(cam);
        HollowRenderTypes.pillar(vc, m, (float) c.x, (float) c.y - 0.1F, (float) c.z, 0.9F, f.p.size() + 0.4F,
                HollowRenderTypes.argb(0x000000, 0.35F * k), HollowRenderTypes.argb(0x000000, 0.0F));
    }

    private static void drawAfterimage(VertexConsumer vc, Matrix4f m, Fx f, Vec3 cam, float pt) {
        float t = f.age + pt;
        float k = Mth.clamp(1F - t / f.life, 0F, 1F);
        Vec3 c = f.p.pos().subtract(cam);
        float h = Math.max(1.6F, f.p.size());
        HollowRenderTypes.pillar(vc, m, (float) c.x, (float) c.y, (float) c.z, h * 0.32F, h, HollowRenderTypes.argb(0x1A0608, 0.7F * k),
                HollowRenderTypes.argb(0x3A1010, 0.7F * k));
    }

    private static void drawSpeedLines(VertexConsumer vc, Matrix4f m, Fx f, Vec3 cam, float pt) {
        float t = f.age + pt;
        float k = Mth.clamp(1F - t / 8F, 0F, 1F);
        if (k <= 0) return;
        Vec3 a0 = f.p.pos(), b0 = f.p.vec();
        for (int i = 0; i < 5; i++) {
            double oy = 0.2 + i * 0.35, ox = (hash(f.seed, i) - 0.5) * 0.6, oz = (hash(f.seed, i + 9) - 0.5) * 0.6;
            Vec3 a = a0.add(ox, oy, oz).subtract(cam), b = b0.add(ox, oy, oz).subtract(cam);
            HollowRenderTypes.ribbon(vc, m, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, 0.01F, 0.05F,
                    HollowRenderTypes.argb(0xD0142C, 0.0F), HollowRenderTypes.argb(0xFFE0E0, 0.6F * k));
        }
    }

    /** O rasgo negro no céu (cresce de uma linha até a boca aberta, fecha no fim). */
    private static void drawTear(VertexConsumer vc, Matrix4f m, Vec3 at, float t, int life, float size, Vec3 cam, float time, boolean dark) {
        float open = Mth.clamp(t / 16F, 0F, 1F) * Mth.clamp((life - t) / 14F, 0F, 1F);
        if (open <= 0) return;
        Vec3 c = at.subtract(cam);
        float h = size * (0.3F + 0.7F * open);
        float w = size * 0.42F * open * open;
        // camadas: o negro do Vazio, mais largo no meio
        for (int i = 0; i < 3; i++) {
            float ww = w * (1F - i * 0.25F), hh = h * (1F - i * 0.12F);
            HollowRenderTypes.pillar(vc, m, (float) c.x, (float) c.y - hh * 0.5F, (float) c.z, ww, hh,
                    HollowRenderTypes.argb(0x000000, 0.8F), HollowRenderTypes.argb(0x000000, 0.8F));
        }
    }

    private static void drawTearRim(VertexConsumer vc, Matrix4f m, Vec3 at, float t, int life, float size, Vec3 cam, float time) {
        float open = Mth.clamp(t / 16F, 0F, 1F) * Mth.clamp((life - t) / 14F, 0F, 1F);
        if (open <= 0) return;
        Vec3 c = at.subtract(cam);
        float h = size * (0.3F + 0.7F * open);
        float w = size * 0.42F * open * open + 0.15F;
        float pulse = 0.7F + 0.3F * Mth.sin(time * 0.5F);
        HollowRenderTypes.pillar(vc, m, (float) c.x, (float) c.y - h * 0.55F, (float) c.z, w * 1.35F, h * 1.1F,
                HollowRenderTypes.argb(Hollow.REIATSU_RGB, 0.55F * pulse), HollowRenderTypes.argb(Hollow.REIATSU_RGB, 0.55F * pulse));
    }

    /** Os dentes nas bordas do rasgo (a "garganta"). */
    private static void drawTeeth(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Vec3 at, float t, int life, float size, Vec3 cam) {
        float open = Mth.clamp(t / 16F, 0F, 1F) * Mth.clamp((life - t) / 14F, 0F, 1F);
        if (open < 0.3F || !FxQuality.atLeast(MahouConfig.Graphics.MEDIUM)) return;
        Vec3 c = at.subtract(cam);
        float h = size * (0.3F + 0.7F * open);
        float w = size * 0.42F * open * open;
        int n = 7;
        for (int i = 0; i < n; i++) {
            float v = (i + 0.5F) / n - 0.5F;
            float bulge = 1F - 4F * v * v;
            float y = (float) c.y + v * h * 0.9F;
            for (int s = -1; s <= 1; s += 2) {
                HollowRenderTypes.billboard(vc, m, left, up, (float) c.x + left.x() * s * w * bulge, y, (float) c.z + left.z() * s * w * bulge,
                        0.12F, 0.18F, s > 0 ? -Mth.HALF_PI : Mth.HALF_PI, HollowRenderTypes.argb(0xF2EEE6, open));
            }
        }
    }

    private static void drawSouls(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx f, Vec3 cam, float pt) {
        Entity ent = level.getEntity(f.p.entity());
        if (ent == null) return;
        float t = f.age + pt;
        Vec3 to = mouth(ent, pt);
        Vec3 from = f.p.pos();
        int n = Math.min(FxQuality.count((int) f.p.size()), 30);
        for (int i = 0; i < n; i++) {
            float delay = hash(f.seed, i) * 8F;
            float u = Mth.clamp((t - delay) / 12F, 0F, 1F);
            if (u <= 0 || u >= 1) continue;
            float e = u * u * (3 - 2 * u);
            // arco: sobe e curva para a boca
            double bend = Math.sin(u * Math.PI) * (1.2 + hash(f.seed, i + 3));
            double sx = (hash(f.seed, i + 7) - 0.5) * 2;
            Vec3 at = from.lerp(to, e).add(sx * bend * 0.5, bend, (hash(f.seed, i + 11) - 0.5) * bend).subtract(cam);
            float s = 0.12F + 0.08F * (1 - u);
            HollowRenderTypes.billboard(vc, m, left, up, (float) at.x, (float) at.y, (float) at.z, s, s, 0,
                    HollowRenderTypes.argb(i % 3 == 0 ? 0xA8E0FF : f.p.rgb(), 0.9F));
        }
    }

    private static void drawCocoon(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx f, Vec3 cam, float time, float pt) {
        float t = f.age + pt;
        float k = Mth.clamp(1F - t / f.life, 0F, 1F);
        Vec3 c = f.p.pos().subtract(cam);
        int rgb = f.p.rgb();
        // a coluna de Reiatsu que sobe e o clarão do estouro
        float h = 6F + f.p.size() * (1F - k);
        HollowRenderTypes.pillar(vc, m, (float) c.x, (float) c.y, (float) c.z, 1.2F + 0.3F * f.p.size() * (1 - k), h,
                HollowRenderTypes.argb(rgb, 0.7F * k), HollowRenderTypes.argb(rgb, 0F));
        HollowRenderTypes.pillar(vc, m, (float) c.x, (float) c.y, (float) c.z, 0.5F, h * 1.2F, HollowRenderTypes.argb(0xFFFFFF, 0.6F * k),
                HollowRenderTypes.argb(0xFFFFFF, 0F));
        float burst = t < 6 ? t / 6F : 1F;
        HollowRenderTypes.billboard(vc, m, left, up, (float) c.x, (float) c.y + 1.2F, (float) c.z, 3F * burst + 1F, 3F * burst + 1F, time * 0.04F,
                HollowRenderTypes.argb(rgb, 0.8F * k));
        if (t < 4) {
            HollowRenderTypes.billboard(vc, m, left, up, (float) c.x, (float) c.y + 1.2F, (float) c.z, 6F, 6F, 0,
                    HollowRenderTypes.argb(0xFFFFFF, 1F - t / 4F));
        }
    }

    private static void drawFlash(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx f, Vec3 cam, float pt, float size) {
        float t = f.age + pt;
        float k = Mth.clamp(1F - t / f.life, 0F, 1F);
        Entity ent = level.getEntity(f.p.entity());
        Vec3 c = (ent != null ? (f.p.kind() == HollowNetwork.MASK_FORM ? ent.getEyePosition(pt) : ent.getPosition(pt).add(0, 1, 0)) : f.p.pos())
                .subtract(cam);
        HollowRenderTypes.billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, size * (1.5F - k * 0.5F), size * (1.8F - k * 0.5F), 0,
                HollowRenderTypes.argb(f.p.rgb(), 0.6F * k));
    }

    /** Estilhaços brancos convergindo para o rosto (a máscara se formando). */
    private static void drawMaskShards(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx f, Vec3 cam, float pt) {
        Entity ent = level.getEntity(f.p.entity());
        if (ent == null) return;
        float t = f.age + pt;
        Vec3 face = ent.getEyePosition(pt).add(ent.getViewVector(pt).scale(0.3));
        int n = FxQuality.atLeast(MahouConfig.Graphics.HIGH) ? 18 : 10;
        for (int i = 0; i < n; i++) {
            float u = Mth.clamp((t - hash(f.seed, i) * 8F) / 12F, 0F, 1F);
            if (u >= 1F) continue;
            double a = hash(f.seed, i + 20) * Math.PI * 2, b = (hash(f.seed, i + 40) - 0.5) * Math.PI;
            double dist = (1 - u) * 1.8;
            Vec3 at = face.add(Math.cos(a) * Math.cos(b) * dist, Math.sin(b) * dist, Math.sin(a) * Math.cos(b) * dist).subtract(cam);
            HollowRenderTypes.billboard(vc, m, left, up, (float) at.x, (float) at.y, (float) at.z, 0.1F, 0.16F, (float) a,
                    HollowRenderTypes.argb(0xF2EEE6, 0.4F + 0.6F * u));
        }
    }

    private static void drawRing(VertexConsumer vc, Matrix4f m, Fx f, Vec3 cam, float pt, float alpha) {
        float t = f.age + pt;
        float u = Mth.clamp(t / f.life, 0F, 1F);
        float k = 1F - u;
        Vec3 c = f.p.pos().subtract(cam);
        float ease = 1F - (1F - u) * (1F - u);
        float r = Math.max(0.5F, f.p.size()) * (0.15F + 0.85F * ease);
        float y = (float) c.y + (f.p.kind() == HollowNetwork.PESQUISA ? -0.9F : 0.08F);
        HollowRenderTypes.flat(vc, m, (float) c.x, y, (float) c.z, r, t * 0.05F, HollowRenderTypes.argb(f.p.rgb(), alpha * k));
        if (FxQuality.atLeast(MahouConfig.Graphics.HIGH)) {
            HollowRenderTypes.flat(vc, m, (float) c.x, y + 0.02F, (float) c.z, r * 0.7F, -t * 0.07F, HollowRenderTypes.argb(0xFFFFFF, 0.4F * alpha * k));
        }
    }

    private static float hash(long seed, int i) {
        long h = seed * 31 + i * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return ((h & 0xFFFFFF) / (float) 0x1000000);
    }

    private HollowFxClient() {}
}
