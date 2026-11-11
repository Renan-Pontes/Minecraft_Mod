package com.renanpontes.isekaimahou.feature.bosses_e.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Perigos "soltos" do Dragão Primordial que vivem alguns segundos e são atualizados pelo próprio dragão (sem
 * entidades próprias): meteoros (um bloco de exibição caindo do céu), tornados, maremotos que correm pela faixa
 * telegrafada e orbes caçadores (anti-trapaça). Tudo some na troca de fase, na morte e quando o dragão sai do mundo.
 * Os blocos de exibição levam a tag {@link #FX_TAG}: se o mundo fechar no meio, somem ao carregar (BeEvents).
 */
final class DragonHazards {
    static final String FX_TAG = "isekai_mahou.dragon_fx";
    private static final int METEOR_FALL = 12;
    static final int MAX_TORNADOS = 4;

    private final PrimordialDragon d;
    private final List<Meteor> meteors = new ArrayList<>();
    private final List<Tornado> tornados = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();
    private final List<Seeker> seekers = new ArrayList<>();

    DragonHazards(PrimordialDragon dragon) {
        this.d = dragon;
    }

    // =====================================================================================
    //  Criação
    // =====================================================================================

    /** Meteoro que cai na forma telegrafada {@code teleId}, chegando junto com o fim do aquecimento. */
    void meteor(int teleId, int warmup, BlockState block, float scale) {
        meteors.add(new Meteor(teleId, Math.max(0, warmup - METEOR_FALL), block, scale));
    }

    void tornado(Vec3 at, int life) {
        if (tornados.size() >= MAX_TORNADOS) return;
        tornados.add(new Tornado(at, life));
    }

    int tornadoCount() {
        return tornados.size();
    }

    /** Maremoto: a frente corre de {@code from} até {@code to} em {@code ticks}, atingindo quem toca (uma vez cada). */
    void wave(Vec3 from, Vec3 to, double width, int ticks) {
        waves.add(new Wave(from, to, width, ticks));
    }

    /** Orbe caçador (alvo escondido / inalcançável): persegue e explode em raio 3. */
    void seeker(Vec3 from, LivingEntity target) {
        if (seekers.size() < 6) seekers.add(new Seeker(from, target));
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    void tick(ServerLevel sl) {
        tickMeteors(sl);
        tickTornados(sl);
        tickWaves(sl);
        tickSeekers(sl);
    }

    void clear() {
        for (Meteor m : meteors) m.discard();
        meteors.clear();
        tornados.clear();
        waves.clear();
        seekers.clear();
    }

    private void tickMeteors(ServerLevel sl) {
        Iterator<Meteor> it = meteors.iterator();
        while (it.hasNext()) {
            Meteor m = it.next();
            if (m.delay > 0) {
                m.delay--;
                continue;
            }
            Vec3 c = Telegraph.centerOf(m.teleId);
            if (c != null) m.last = c;
            if (m.last == null || (!Telegraph.isActive(m.teleId) && m.age > 0)) {
                m.discard();
                it.remove();
                continue;
            }
            if (m.sky == null) {
                m.sky = m.last.add((d.getRandom().nextDouble() - 0.5) * 10, 30, (d.getRandom().nextDouble() - 0.5) * 10);
                m.display = spawnDisplay(sl, m.sky, m.block, m.scale);
                DragonFx.sound(sl, m.last, SoundEvents.BLAZE_SHOOT, 1.6F, 0.4F);
            }
            m.age++;
            double f = Math.min(1.0, m.age / (double) METEOR_FALL);
            Vec3 p = m.sky.lerp(m.last.add(0, 0.5, 0), f);
            if (m.display != null) m.display.teleportTo(p.x, p.y, p.z);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 1, p.z, 3, 0.4, 0.4, 0.4, 0.01);
            sl.sendParticles(ParticleTypes.FLAME, p.x, p.y + 1, p.z, 6, 0.6, 0.6, 0.6, 0.02);
            sl.sendParticles(ParticleTypes.LAVA, p.x, p.y + 1, p.z, 1, 0.3, 0.3, 0.3, 0);
            if (m.age >= METEOR_FALL + 2) {
                m.discard();
                it.remove();
            }
        }
    }

    private void tickTornados(ServerLevel sl) {
        Iterator<Tornado> it = tornados.iterator();
        boolean storm = d.phase() == DragonPhase.STORM;
        while (it.hasNext()) {
            Tornado t = it.next();
            t.age++;
            if (t.age > t.life) {
                sl.sendParticles(ParticleTypes.CLOUD, t.pos.x, t.pos.y + 2, t.pos.z, 30, 1.2, 2, 1.2, 0.08);
                it.remove();
                continue;
            }
            LivingEntity near = null;
            double best = 24 * 24;
            for (LivingEntity e : d.enemiesNear(t.pos, 24)) {
                double dd = e.distanceToSqr(t.pos);
                if (dd < best) {
                    best = dd;
                    near = e;
                }
            }
            if (near != null) {
                Vec3 dir = near.position().subtract(t.pos).multiply(1, 0, 1);
                if (dir.lengthSqr() > 0.01) t.pos = t.pos.add(dir.normalize().scale(0.13));
            }
            Vec3 g = d.groundAt(t.pos.x, t.pos.y, t.pos.z, 3, 6);
            if (g != null) t.pos = g;
            if (t.age % 2 == 0) {
                double a = t.age * 0.6;
                for (int i = 0; i < 6; i++) {
                    double y = i * 1.2;
                    double r = 0.6 + y * 0.35;
                    double aa = a + i * 1.1;
                    sl.sendParticles(ParticleTypes.CLOUD, t.pos.x + Math.cos(aa) * r, t.pos.y + y, t.pos.z + Math.sin(aa) * r, 1, 0, 0, 0, 0);
                    if (storm && i % 2 == 0) {
                        sl.sendParticles(DragonFx.dust(DragonFx.STORM, 1.3F), t.pos.x + Math.cos(aa + 3) * r, t.pos.y + y,
                                t.pos.z + Math.sin(aa + 3) * r, 1, 0, 0, 0, 0);
                    }
                }
            }
            if (t.age % 20 == 0) DragonFx.sound(sl, t.pos, SoundEvents.ELYTRA_FLYING, 0.8F, 0.6F);
            for (LivingEntity e : d.enemiesNear(t.pos.add(0, 2, 0), 6)) {
                Vec3 pull = t.pos.subtract(e.position()).multiply(1, 0, 1);
                double dist = pull.length();
                if (dist > 2.2) {
                    Vec3 v = pull.normalize().scale(0.07);
                    e.setDeltaMovement(e.getDeltaMovement().add(v.x, 0.01, v.z));
                    e.hurtMarked = true;
                } else if (t.age % 10 == 0) {
                    e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 0.75, e.getDeltaMovement().z * 0.3);
                    e.hurtMarked = true;
                    if (e.hurt(d.spellSource(null), d.spellPower() * 0.3F)) {
                        Statuses.apply(e, Statuses.Kind.WET, 1, 100, d, 1F);
                        if (storm) Statuses.apply(e, Statuses.Kind.SHOCKED, 1, 80, d, 1F);
                    }
                }
            }
        }
    }

    private void tickWaves(ServerLevel sl) {
        Iterator<Wave> it = waves.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            w.age++;
            double f = Math.min(1.0, w.age / (double) w.ticks);
            Vec3 front = w.from.lerp(w.to, f);
            Vec3 dir = w.to.subtract(w.from).multiply(1, 0, 1).normalize();
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            int pts = DragonFx.reduced() ? 6 : 12;
            for (int i = 0; i <= pts; i++) {
                double s = (i / (double) pts - 0.5) * w.width;
                Vec3 p = front.add(side.scale(s));
                sl.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 1.0, p.z, 3, 0.3, 0.8, 0.3, 0.1);
                sl.sendParticles(DragonFx.dust(i % 2 == 0 ? DragonFx.WATER : DragonFx.ICE, 2.2F), p.x, p.y + 1.8, p.z, 1, 0.2, 0.6, 0.2, 0);
                if (i % 3 == 0) sl.sendParticles(ParticleTypes.BUBBLE_POP, p.x, p.y + 2.6, p.z, 2, 0.3, 0.2, 0.3, 0.05);
            }
            if (w.age % 6 == 0) DragonFx.sound(sl, front, SoundEvents.GENERIC_SPLASH, 1.6F, 0.6F);
            for (LivingEntity e : d.enemiesNear(front, w.width / 2 + 2)) {
                Vec3 rel = e.position().subtract(front);
                double along = rel.dot(dir);
                double across = Math.abs(rel.dot(side));
                if (Math.abs(along) > 1.6 || across > w.width / 2 + e.getBbWidth() / 2 || e.getY() - front.y > 3.5) continue;
                if (!w.hit.add(e.getUUID())) continue;
                if (e.hurt(d.spellSource(null), d.spellPower() * 0.8F)) {
                    Statuses.apply(e, Statuses.Kind.WET, 1, 160, d, 1F);
                    Statuses.apply(e, Statuses.Kind.CHILLED, 2, 120, d, 1F);
                }
                e.setDeltaMovement(dir.x * 1.3, 0.45, dir.z * 1.3);
                e.hurtMarked = true;
            }
            if (w.age >= w.ticks) it.remove();
        }
    }

    private void tickSeekers(ServerLevel sl) {
        Iterator<Seeker> it = seekers.iterator();
        while (it.hasNext()) {
            Seeker s = it.next();
            s.age++;
            LivingEntity t = s.target;
            if (s.age > 140 || t == null || !t.isAlive() || t.level() != sl) {
                it.remove();
                continue;
            }
            Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(s.pos);
            if (aim.lengthSqr() < 1.4 * 1.4) {
                burst(sl, s.pos);
                it.remove();
                continue;
            }
            s.vel = s.vel.scale(0.82).add(aim.normalize().scale(0.16));
            if (s.vel.length() > 0.75) s.vel = s.vel.normalize().scale(0.75);
            Vec3 next = s.pos.add(s.vel);
            HitResult hit = sl.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, d));
            if (hit.getType() != HitResult.Type.MISS) {
                burst(sl, hit.getLocation());
                it.remove();
                continue;
            }
            s.pos = next;
            int rgb = d.phase().rgb;
            sl.sendParticles(DragonFx.dust(rgb, 2.0F), s.pos.x, s.pos.y, s.pos.z, 3, 0.15, 0.15, 0.15, 0);
            sl.sendParticles(ParticleTypes.END_ROD, s.pos.x, s.pos.y, s.pos.z, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /** Explosão do orbe caçador: 3 blocos, atravessa coberturas finas (é o castigo de quem se esconde). */
    private void burst(ServerLevel sl, Vec3 at) {
        sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0);
        sl.sendParticles(DragonFx.dust(d.phase().rgb, 2.5F), at.x, at.y, at.z, 30, 1.2, 1.2, 1.2, 0);
        DragonFx.sound(sl, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.3F);
        for (LivingEntity e : d.enemiesNear(at, 3.2)) {
            if (e.hurt(d.spellSource(null), d.spellPower() * 0.65F)) d.applyPhaseStatus(e, 1);
        }
    }

    // =====================================================================================
    //  Tipos
    // =====================================================================================

    private static final class Meteor {
        final int teleId;
        final BlockState block;
        final float scale;
        int delay;
        int age;
        @Nullable Vec3 sky;
        @Nullable Vec3 last;
        @Nullable Entity display;

        Meteor(int teleId, int delay, BlockState block, float scale) {
            this.teleId = teleId;
            this.delay = delay;
            this.block = block;
            this.scale = scale;
        }

        void discard() {
            if (display != null) display.discard();
            display = null;
        }
    }

    private static final class Tornado {
        Vec3 pos;
        final int life;
        int age;

        Tornado(Vec3 pos, int life) {
            this.pos = pos;
            this.life = life;
        }
    }

    private static final class Wave {
        final Vec3 from;
        final Vec3 to;
        final double width;
        final int ticks;
        final Set<UUID> hit = new HashSet<>();
        int age;

        Wave(Vec3 from, Vec3 to, double width, int ticks) {
            this.from = from;
            this.to = to;
            this.width = width;
            this.ticks = Math.max(1, ticks);
        }
    }

    private static final class Seeker {
        Vec3 pos;
        Vec3 vel = Vec3.ZERO;
        final LivingEntity target;
        int age;

        Seeker(Vec3 pos, LivingEntity target) {
            this.pos = pos;
            this.target = target;
        }
    }

    // =====================================================================================
    //  Bloco de exibição (vanilla) para os meteoros
    // =====================================================================================

    /** Bloco de exibição girado e aumentado, sempre aceso, que se move suave (teleport_duration). */
    @Nullable
    static Entity spawnDisplay(ServerLevel sl, Vec3 at, BlockState block, float scale) {
        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:block_display");
        t.put("block_state", NbtUtils.writeBlockState(block));
        float a = sl.random.nextFloat() * Mth.TWO_PI;
        float b = sl.random.nextFloat() * Mth.TWO_PI;
        // Quaternion de uma rotação qualquer (meteoro tombado), normalizado.
        float qx = Mth.sin(a / 2) * Mth.cos(b / 2);
        float qy = Mth.sin(b / 2) * Mth.cos(a / 2);
        float qz = Mth.sin(a / 2) * Mth.sin(b / 2);
        float qw = Mth.cos(a / 2) * Mth.cos(b / 2);
        float len = Mth.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", floats(qx / len, qy / len, qz / len, qw / len));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tr.put("translation", floats(-scale / 2, -scale / 2, -scale / 2));
        tr.put("scale", floats(scale, scale, scale));
        t.put("transformation", tr);
        t.putInt("teleport_duration", 2);
        CompoundTag br = new CompoundTag();
        br.putInt("sky", 15);
        br.putInt("block", 15);
        t.put("brightness", br);
        t.putFloat("view_range", 2.0F);
        Entity e = EntityType.create(t, sl).orElse(null);
        if (e == null) return null;
        e.moveTo(at.x, at.y, at.z, 0F, 0F);
        e.addTag(FX_TAG);
        sl.addFreshEntity(e);
        return e;
    }

    private static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) l.add(FloatTag.valueOf(f));
        return l;
    }
}
