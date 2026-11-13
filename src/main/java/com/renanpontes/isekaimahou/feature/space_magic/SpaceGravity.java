package com.renanpontes.isekaimahou.feature.space_magic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Gravidade (servidor): três efeitos que duram alguns ticks, atualizados no fim de cada tick do servidor.
 * <ul>
 *   <li><b>Suspensão</b> — o alvo flutua ~3 blocos acima de onde estava, quase sem conseguir se mexer;</li>
 *   <li><b>Esmagar</b> (Inverter) — o alvo despenca e leva dano ao bater no chão (no próprio conjurador: "pisão" que
 *       atinge quem está em volta, sem dano de queda para você);</li>
 *   <li><b>Poço</b> (em bloco) — puxa (ou, com Inverter, repele) os inimigos em volta do ponto.</li>
 * </ul>
 * Tudo com limite de quantidade; nada varre o mundo.
 */
public final class SpaceGravity {
    private static final int MAX_EACH = 64;

    private record Lift(ServerLevel level, UUID id, double holdY, long until, boolean boss) {}

    private record Slam(ServerLevel level, UUID id, long until, SpellContext ctx, SpellStats stats, double startY, boolean self) {}

    private record Well(ServerLevel level, Vec3 center, double radius, long until, SpellContext ctx, boolean repel) {}

    private static final List<Lift> LIFTS = new ArrayList<>();
    private static final List<Slam> SLAMS = new ArrayList<>();
    private static final List<Well> WELLS = new ArrayList<>();

    static boolean isBoss(Entity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES) || e instanceof LivingEntity le && le.getMaxHealth() >= 200F;
    }

    /** Suspende o alvo no ar por {@code ticks}. Chefes sobem pouco e por menos tempo. */
    public static void lift(ServerLevel level, Entity target, int ticks, long now) {
        if (LIFTS.size() >= MAX_EACH) return;
        boolean boss = isBoss(target);
        LIFTS.removeIf(l -> l.id().equals(target.getUUID()));
        double h = boss ? 1.2 : 3.0;
        LIFTS.add(new Lift(level, target.getUUID(), target.getY() + h, now + (boss ? ticks / 3 : ticks), boss));
        target.setDeltaMovement(target.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 0.55, 0));
        target.hurtMarked = true;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 0.2, target.getZ(), 24, 0.5, 0.1, 0.5, 0.04);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    /** Joga o alvo contra o chão (ou o próprio conjurador, num pisão). */
    public static void slam(SpellContext ctx, Entity target, SpellStats stats, long now, boolean self) {
        if (SLAMS.size() >= MAX_EACH) return;
        ServerLevel level = ctx.level();
        LIFTS.removeIf(l -> l.id().equals(target.getUUID()));
        SLAMS.removeIf(s -> s.id().equals(target.getUUID()));
        SLAMS.add(new Slam(level, target.getUUID(), now + 60, ctx, stats, target.getY(), self));
        double down = isBoss(target) && !self ? -0.8 : -2.2;
        target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, down, 0));
        target.hurtMarked = true;
        if (!self && target instanceof LivingEntity le) {
            ctx.status(le, MobEffects.MOVEMENT_SLOWDOWN, 60, 2, stats);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.5F, 1.8F);
    }

    /** Poço gravitacional num ponto. */
    public static void well(SpellContext ctx, Vec3 center, double radius, int ticks, long now, boolean repel) {
        if (WELLS.size() >= 16) WELLS.remove(0);
        WELLS.add(new Well(ctx.level(), center, radius, now + ticks, ctx, repel));
        ctx.level().playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.5F);
    }

    static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        tickLifts(now);
        tickSlams(now);
        tickWells(now);
    }

    private static void tickLifts(long now) {
        for (Iterator<Lift> it = LIFTS.iterator(); it.hasNext(); ) {
            Lift l = it.next();
            Entity e = l.level().getEntity(l.id());
            if (e == null || !e.isAlive() || now > l.until()) {
                if (e instanceof LivingEntity le && e.isAlive()) le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0));
                it.remove();
                continue;
            }
            Vec3 v = e.getDeltaMovement();
            double dy = l.holdY() - e.getY();
            e.setDeltaMovement(v.x * 0.55, Mth.clamp(dy * 0.25, -0.3, 0.35), v.z * 0.55);
            e.hurtMarked = true;
            e.resetFallDistance();
            if (e instanceof Mob m && !l.boss()) m.getNavigation().stop();
            if (now % 3 == 0) {
                l.level().sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() - 0.1, e.getZ(), 3, e.getBbWidth() * 0.4, 0.05,
                        e.getBbWidth() * 0.4, 0.01);
            }
        }
    }

    private static void tickSlams(long now) {
        for (Iterator<Slam> it = SLAMS.iterator(); it.hasNext(); ) {
            Slam s = it.next();
            Entity e = s.level().getEntity(s.id());
            if (e == null || !e.isAlive() || now > s.until()) {
                it.remove();
                continue;
            }
            if (!e.onGround()) {
                if (s.self()) e.resetFallDistance();
                continue;
            }
            it.remove();
            double drop = Math.max(0, s.startY() - e.getY());
            ServerLevel level = s.level();
            level.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 0.1, e.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 0.1, e.getZ(), 40, 1.2, 0.1, 1.2, 0.08);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.8F);
            float dmg = (float) (4.0 + Math.min(10.0, drop * 1.2));
            if (s.self()) {
                e.resetFallDistance();
                for (LivingEntity t : s.ctx().enemiesAround(e.position(), 3.5)) {
                    s.ctx().damage(t, dmg, s.stats(), null);
                    t.setDeltaMovement(t.getDeltaMovement().add(0, 0.5, 0));
                    t.hurtMarked = true;
                }
            } else {
                s.ctx().damage(e, dmg, s.stats(), null);
            }
        }
    }

    private static void tickWells(long now) {
        for (Iterator<Well> it = WELLS.iterator(); it.hasNext(); ) {
            Well w = it.next();
            if (now > w.until()) {
                w.level().sendParticles(ParticleTypes.REVERSE_PORTAL, w.center().x, w.center().y + 0.5, w.center().z, 30, 0.6, 0.4, 0.6, 0.1);
                it.remove();
                continue;
            }
            for (LivingEntity t : w.ctx().enemiesAround(w.center(), w.radius())) {
                Vec3 d = w.center().add(0, 0.5, 0).subtract(t.position());
                double len = d.length();
                if (len < 0.8 && !w.repel()) continue;
                double f = (isBoss(t) ? 0.03 : 0.11) * (w.repel() ? -1.6 : 1.0);
                Vec3 pull = d.scale(f / Math.max(0.5, len));
                t.setDeltaMovement(t.getDeltaMovement().scale(0.8).add(pull.x, Math.max(-0.05, pull.y) + (w.repel() ? 0.03 : 0), pull.z));
                t.hurtMarked = true;
            }
            if (now % 2 == 0) spiral(w, now);
        }
    }

    /** Espiral de partículas girando em volta do poço. */
    private static void spiral(Well w, long now) {
        int arms = 3;
        for (int a = 0; a < arms; a++) {
            double ang = now * 0.35 + a * Math.PI * 2 / arms;
            double r = w.radius() * (0.35 + 0.6 * ((now % 20) / 20.0));
            if (w.repel()) r = w.radius() - r + 0.4;
            double x = w.center().x + Math.cos(ang) * r, z = w.center().z + Math.sin(ang) * r;
            w.level().sendParticles(ParticleTypes.REVERSE_PORTAL, x, w.center().y + 0.3, z, 1, 0, 0, 0, 0);
            w.level().sendParticles(ParticleTypes.PORTAL, x, w.center().y + 0.6, z, 1, 0, 0, 0, 0);
        }
        w.level().sendParticles(ParticleTypes.DRAGON_BREATH, w.center().x, w.center().y + 0.3, w.center().z, 2, 0.2, 0.1, 0.2, 0.01);
    }

    static void clear() {
        LIFTS.clear();
        SLAMS.clear();
        WELLS.clear();
    }

    private SpaceGravity() {}
}
