package com.renanpontes.isekaimahou.feature.arcane;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Telecinese: segura entidades (mobs, itens, orbes de XP) no ponto para onde o conjurador olha.
 * Rodado no tick do servidor. Termina quando o tempo acaba, quando o conjurador agacha (solta)
 * ou se alguém sai do mundo. O alvo sai com o embalo atual: virar a câmera rápido "arremessa".
 */
public final class Telekinesis {
    private record Hold(UUID caster, UUID target, ResourceKey<Level> dim, long start, long until, double distance, int color) {}

    /** Agachar só solta depois deste tempo (a magia pode ter sido conjurada na versão "agachado" do slot). */
    private static final int SNEAK_GRACE = 10;

    private static final List<Hold> HOLDS = new ArrayList<>();
    private static final int MAX_PER_CASTER = 16;

    /** Começa a segurar {@code target}. */
    public static void hold(LivingEntity caster, Entity target, int ticks, int color) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        HOLDS.removeIf(h -> h.target.equals(target.getUUID()));
        long mine = HOLDS.stream().filter(h -> h.caster.equals(caster.getUUID())).count();
        if (mine >= MAX_PER_CASTER) return;
        double dist = Math.max(2.5, Math.min(10.0, caster.getEyePosition().distanceTo(target.position())));
        long now = level.getGameTime();
        HOLDS.add(new Hold(caster.getUUID(), target.getUUID(), level.dimension(), now, now + ticks, dist, color));
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL,
                caster.getSoundSource(), 0.5F, 1.6F);
    }

    /** Inverter: arremessa o alvo na direção do olhar na hora. */
    public static void fling(LivingEntity caster, Entity target, float power) {
        Vec3 dir = caster.getLookAngle();
        double strength = (target instanceof LivingEntity ? 1.6 : 1.1) * Math.min(2.5F, power);
        target.setDeltaMovement(dir.x * strength, Math.max(0.25, dir.y * strength + 0.25), dir.z * strength);
        target.hurtMarked = true;
        if (caster.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    8, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                    caster.getSoundSource(), 0.8F, 0.8F);
        }
    }

    public static boolean isHeld(Entity e) {
        for (Hold h : HOLDS) if (h.target.equals(e.getUUID())) return true;
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (HOLDS.isEmpty()) return;
        Iterator<Hold> it = HOLDS.iterator();
        while (it.hasNext()) {
            Hold h = it.next();
            ServerLevel level = server.getLevel(h.dim);
            Entity c = level == null ? null : level.getEntity(h.caster);
            Entity t = level == null ? null : level.getEntity(h.target);
            if (!(c instanceof LivingEntity caster) || t == null || !caster.isAlive() || !t.isAlive()
                    || level.getGameTime() > h.until
                    || (caster instanceof Player p && p.isShiftKeyDown() && level.getGameTime() - h.start > SNEAK_GRACE)
                    || t.distanceToSqr(caster) > 48 * 48) {
                if (t != null && t.isAlive()) {
                    t.resetFallDistance();
                    if (level != null) level.sendParticles(ParticleTypes.REVERSE_PORTAL, t.getX(), t.getY() + t.getBbHeight() / 2,
                            t.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                }
                it.remove();
                continue;
            }
            Vec3 desired = caster.getEyePosition().add(caster.getLookAngle().scale(h.distance)).subtract(0, t.getBbHeight() / 2, 0);
            Vec3 v = desired.subtract(t.position()).scale(0.3);
            double max = t instanceof LivingEntity ? 1.2 : 1.6;
            if (v.length() > max) v = v.normalize().scale(max);
            t.setDeltaMovement(v);
            t.hurtMarked = true;
            t.resetFallDistance();
            if (t instanceof Mob m) m.getNavigation().stop();

            if (level.getGameTime() % 3 == 0) {
                DustParticleOptions dust = ArcaneUtil.dust(h.color & 0xFFFFFF, 0.8F);
                Vec3 mid = ArcaneUtil.center(t);
                level.sendParticles(dust, mid.x, mid.y, mid.z, 3, t.getBbWidth() * 0.4, t.getBbHeight() * 0.3, t.getBbWidth() * 0.4, 0);
                Vec3 hand = caster.getEyePosition().add(caster.getLookAngle().scale(0.8)).subtract(0, 0.35, 0);
                Vec3 d = mid.subtract(hand);
                for (int i = 1; i <= 4; i++) {
                    Vec3 q = hand.add(d.scale(i / 5.0));
                    level.sendParticles(dust, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
                }
            }
        }
    }

    public static void clear() {
        HOLDS.clear();
    }

    private Telekinesis() {}
}
