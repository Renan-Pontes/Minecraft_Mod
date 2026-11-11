package com.renanpontes.isekaimahou.feature.trials;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Perigos da arena (a partir da onda 2 e durante o chefe). SEMPRE telegrafados no chão (core Telegraph): o círculo
 * ou a faixa enche até a borda e só então o golpe acontece — dá para ver e sair. Só atingem jogadores de pé.
 * <ul>
 *   <li>Fogo: gêiseres de lava (círculos perto dos jogadores; queimam e jogam para cima).</li>
 *   <li>Água: ondas em linha que atravessam a arena e EMPURRAM (e encharcam).</li>
 *   <li>Terra: pedras caindo (círculos; poeira cai antes; lentidão).</li>
 *   <li>Vento: rajadas em linha que arremessam para o alto (com queda lenta para ninguém morrer da queda).</li>
 *   <li>Luz: pilares sagrados + um círculo VERDE de cura para os jogadores.</li>
 *   <li>Sombra: zonas de escuridão que ficam no chão (cegueira e dano enquanto estiver dentro).</li>
 * </ul>
 */
final class TrialHazards {
    /** Cura proporcional à vida do jogador da v1.0 (base 40 em vez de 20). */
    private static final float HEALTH_SCALE = Progression.BASE_PLAYER_HEALTH / 20F;

    /** Dispara um "lote" de perigos do elemento base {@code base}. */
    static void fire(TrialInstance inst, Element base) {
        List<ServerPlayer> targets = inst.standingPlayers();
        if (targets.isEmpty()) return;
        // Inflação de números (v1.0): os perigos não têm "atacante", então o núcleo não os escala — a mesma razão dos mobs.
        float dmg = (2.5F + inst.rank) * (1F + 0.1F * (targets.size() - 1)) * Progression.MOB_DAMAGE_SCALE;
        switch (base) {
            case FIRE -> geysers(inst, targets, dmg);
            case WATER -> waves(inst, targets, dmg);
            case EARTH -> rocks(inst, targets, dmg);
            case WIND -> gusts(inst, targets, dmg);
            case LIGHT -> pillars(inst, targets, dmg);
            default -> darkness(inst, targets, dmg);
        }
    }

    // ------------------------------------------------------------------------------------------------ fogo
    private static void geysers(TrialInstance inst, List<ServerPlayer> targets, float dmg) {
        ServerLevel level = inst.level;
        RandomSource r = level.random;
        int count = Math.min(6, 1 + targets.size() + inst.rank / 3);
        float radius = 2.0F + inst.rank * 0.1F;
        int warm = Math.max(26, 36 - inst.rank);
        int epoch = inst.epoch;
        for (int k = 0; k < count; k++) {
            Vec3 at = near(inst, targets.get(k % targets.size()).position(), 2.5, r);
            Telegraph.delayed(level, Telegraph.Shape.circle(at, radius).warmup(warm).color(0xFF5A1F).flat().vertical(1.0, 3.5), hits -> {
                if (!inst.hazardActive(epoch)) return;
                for (LivingEntity e : hits) {
                    if (!(e instanceof ServerPlayer p) || !inst.isStanding(p)) continue;
                    p.hurt(level.damageSources().magic(), dmg);
                    p.igniteForSeconds(3);
                    p.push(0, 0.55, 0);
                    p.hurtMarked = true;
                }
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, 24, radius * 0.4, 0.2, radius * 0.4, 0.2);
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 50, radius * 0.3, 0.8, radius * 0.3, 0.12);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.5, at.z, 14, 0.4, 1.0, 0.4, 0.04);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2F, 0.6F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_POP, SoundSource.HOSTILE, 1.5F, 0.8F);
            });
            // borbulhar avisando, além do desenho
            for (int t = 0; t < warm; t += 6) {
                inst.schedule(t, () -> level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.1, at.z, 2, radius * 0.4, 0.05, radius * 0.4, 0));
            }
            level.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.8F);
        }
    }

    // ------------------------------------------------------------------------------------------------ água
    private static void waves(TrialInstance inst, List<ServerPlayer> targets, float dmg) {
        ServerLevel level = inst.level;
        RandomSource r = level.random;
        int count = inst.rank >= 4 ? 2 : 1;
        int epoch = inst.epoch;
        for (int k = 0; k < count; k++) {
            Vec3 through = targets.get(r.nextInt(targets.size())).position();
            double ang = r.nextDouble() * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3[] ends = chord(inst, through, dir);
            Vec3 from = ends[0], to = ends[1];
            Telegraph.delayed(level, Telegraph.Shape.line(from, to, 3.5F).warmup(36).color(0x3FA9FF).flat().vertical(1.0, 3.0), hits -> {
                if (!inst.hazardActive(epoch)) return;
                for (LivingEntity e : hits) {
                    if (!(e instanceof ServerPlayer p) || !inst.isStanding(p)) continue;
                    p.hurt(level.damageSources().magic(), dmg * 0.8F);
                    p.clearFire();
                    p.setDeltaMovement(p.getDeltaMovement().add(dir.x * 1.3, 0.35, dir.z * 1.3));
                    p.hurtMarked = true;
                    p.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 100, 0));
                }
                // a onda "corre" de uma ponta à outra
                for (int s = 0; s <= 6; s++) {
                    double f = s / 6.0;
                    Vec3 at = from.add(to.subtract(from).scale(f));
                    inst.schedule(s, () -> {
                        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.6, at.z, 40, 1.2, 0.5, 1.2, 0.3);
                        level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 0.9, at.z, 12, 1.0, 0.6, 1.0, 0.05);
                        level.sendParticles(ParticleTypes.FALLING_WATER, at.x, at.y + 1.8, at.z, 16, 1.2, 0.3, 1.2, 0);
                    });
                }
                level.playSound(null, through.x, through.y, through.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 1.5F, 0.7F);
                level.playSound(null, through.x, through.y, through.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.2F, 0.6F);
            });
            level.playSound(null, from.x, from.y, from.z, SoundEvents.CONDUIT_AMBIENT_SHORT, SoundSource.HOSTILE, 1.5F, 0.7F);
        }
    }

    // ------------------------------------------------------------------------------------------------ terra
    private static void rocks(TrialInstance inst, List<ServerPlayer> targets, float dmg) {
        ServerLevel level = inst.level;
        RandomSource r = level.random;
        int count = Math.min(7, 2 + targets.size() + inst.rank / 3);
        int warm = 40;
        int epoch = inst.epoch;
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState());
        BlockParticleOption chunk = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState());
        for (int k = 0; k < count; k++) {
            Vec3 at = near(inst, targets.get(k % targets.size()).position(), 3.0, r);
            int delay = k * 4;
            inst.schedule(delay, () -> {
                Telegraph.delayed(level, Telegraph.Shape.circle(at, 1.8F).warmup(warm).color(0xA0743C).flat(), hits -> {
                    if (!inst.hazardActive(epoch)) return;
                    for (LivingEntity e : hits) {
                        if (!(e instanceof ServerPlayer p) || !inst.isStanding(p)) continue;
                        p.hurt(level.damageSources().magic(), dmg * 1.2F);
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                    }
                    level.sendParticles(chunk, at.x, at.y + 0.5, at.z, 50, 0.8, 0.5, 0.8, 0.15);
                    level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.7F, 0.5F);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
                    BossKit.shake(level, at, 12, 0.6F, 8);
                });
                for (int t = 0; t < warm; t += 4) {
                    inst.schedule(t, () -> level.sendParticles(dust, at.x, at.y + 9, at.z, 3, 0.6, 0.2, 0.6, 0));
                }
            });
        }
        level.playSound(null, inst.centerVec().x, inst.floorTop() + 6, inst.centerVec().z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 2.0F, 0.4F);
    }

    // ------------------------------------------------------------------------------------------------ vento
    private static void gusts(TrialInstance inst, List<ServerPlayer> targets, float dmg) {
        ServerLevel level = inst.level;
        RandomSource r = level.random;
        int count = 1 + inst.rank / 3;
        int epoch = inst.epoch;
        for (int k = 0; k < count; k++) {
            Vec3 through = targets.get(k % targets.size()).position();
            double ang = r.nextDouble() * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3[] ends = chord(inst, through, dir);
            Vec3 from = ends[0], to = ends[1];
            int delay = k * 8;
            inst.schedule(delay, () -> {
                Telegraph.delayed(level, Telegraph.Shape.line(from, to, 3.0F).warmup(30).color(0x9FF0D0).flat().vertical(1.0, 3.0), hits -> {
                    if (!inst.hazardActive(epoch)) return;
                    for (LivingEntity e : hits) {
                        if (!(e instanceof ServerPlayer p) || !inst.isStanding(p)) continue;
                        p.hurt(level.damageSources().magic(), dmg * 0.8F);
                        p.setDeltaMovement(dir.x * 0.9, 0.95, dir.z * 0.9);
                        p.hurtMarked = true;
                        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 70, 0, false, true));
                    }
                    for (int s = 0; s <= 8; s++) {
                        Vec3 at = from.add(to.subtract(from).scale(s / 8.0));
                        inst.schedule(s / 2, () -> {
                            level.sendParticles(ParticleTypes.GUST, at.x, at.y + 1.0, at.z, 1, 0.3, 0.3, 0.3, 0);
                            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.6, at.z, 10, 0.8, 0.4, 0.8, 0.1);
                        });
                    }
                    level.playSound(null, through.x, through.y, through.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.5F, 0.8F);
                });
                level.playSound(null, from.x, from.y, from.z, SoundEvents.BREEZE_SHOOT, SoundSource.HOSTILE, 1.2F, 0.6F);
            });
        }
    }

    // ------------------------------------------------------------------------------------------------ luz
    private static void pillars(TrialInstance inst, List<ServerPlayer> targets, float dmg) {
        ServerLevel level = inst.level;
        RandomSource r = level.random;
        int count = Math.min(6, 1 + targets.size() / 2 + inst.rank / 3);
        int epoch = inst.epoch;
        for (int k = 0; k < count; k++) {
            Vec3 at = near(inst, targets.get(k % targets.size()).position(), 2.0, r);
            Telegraph.delayed(level, Telegraph.Shape.circle(at, 1.7F).warmup(32).color(Telegraph.HOLY).flat(), hits -> {
                if (!inst.hazardActive(epoch)) return;
                for (LivingEntity e : hits) {
                    if (!(e instanceof ServerPlayer p) || !inst.isStanding(p)) continue;
                    p.hurt(level.damageSources().magic(), dmg);
                    p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0));
                }
                for (int y = 0; y < 14; y++) level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + y * 0.8, at.z, 4, 0.25, 0.3, 0.25, 0.01);
                level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.0F, 1.6F);
            });
        }
        // Círculo de cura: fique dentro (verde) para recuperar vida.
        Vec3 spot = randomSpot(inst, r, 0.55);
        int ticks = 100;
        Telegraph.safe(level, spot, 2.6F, ticks);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
        float heal = (1.0F + inst.rank * 0.5F) * HEALTH_SCALE;
        for (int t = 20; t <= ticks; t += 20) {
            boolean last = t == ticks;
            inst.schedule(t, () -> {
                if (!inst.hazardActive(epoch)) return;
                Telegraph.Shape zone = Telegraph.Shape.safe(spot, 2.6F);
                for (ServerPlayer p : inst.standingPlayers()) {
                    if (!zone.contains(p)) continue;
                    p.heal(heal);
                    if (last) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0));
                    level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.9, p.getZ(), 2, 0.3, 0.2, 0.3, 0);
                }
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.x, spot.y + 0.3, spot.z, 14, 1.4, 0.2, 1.4, 0);
            });
        }
    }

    // ------------------------------------------------------------------------------------------------ sombra
    private static void darkness(TrialInstance inst, List<ServerPlayer> targets, float dmg) {
        ServerLevel level = inst.level;
        RandomSource r = level.random;
        int count = Math.min(5, 1 + targets.size() / 2 + inst.rank / 4);
        int epoch = inst.epoch;
        int hold = 80;
        float tick = (0.5F + inst.rank * 0.25F) * Progression.MOB_DAMAGE_SCALE;
        for (int k = 0; k < count; k++) {
            Vec3 at = near(inst, targets.get(k % targets.size()).position(), 2.0, r);
            Telegraph.Shape shape = Telegraph.Shape.circle(at, 3.0F).warmup(30).hold(hold).color(0x6A2FB0).flat();
            Telegraph.delayed(level, shape, hits -> {
                if (!inst.hazardActive(epoch)) return;
                for (LivingEntity e : hits) {
                    if (!(e instanceof ServerPlayer p) || !inst.isStanding(p)) continue;
                    p.hurt(level.damageSources().magic(), dmg * 0.7F);
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                }
                level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.4, at.z, 40, 1.4, 0.3, 1.4, 0.02);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 0.8F, 1.4F);
            });
            for (int t = 40; t <= 30 + hold; t += 10) {
                inst.schedule(t, () -> {
                    if (!inst.hazardActive(epoch)) return;
                    Telegraph.Shape zone = Telegraph.Shape.circle(at, 3.0F);
                    for (ServerPlayer p : inst.standingPlayers()) {
                        if (!zone.contains(p)) continue;
                        p.hurt(level.damageSources().magic(), tick);
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0));
                    }
                    level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.3, at.z, 4, 1.3, 0.2, 1.3, 0.02);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 6, 1.3, 0.2, 1.3, 0.01);
                });
            }
        }
        level.playSound(null, inst.centerVec().x, inst.floorTop(), inst.centerVec().z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    // ------------------------------------------------------------------------------------------------ geometria
    /** Ponto perto de {@code around} (desvio aleatório), mantido dentro da arena, na altura do piso. */
    private static Vec3 near(TrialInstance inst, Vec3 around, double spread, RandomSource r) {
        Vec3 p = around.add((r.nextDouble() - 0.5) * 2 * spread, 0, (r.nextDouble() - 0.5) * 2 * spread);
        return inst.clampToArena(p, 2.0);
    }

    /** Ponto aleatório da arena até {@code frac} do raio. */
    private static Vec3 randomSpot(TrialInstance inst, RandomSource r, double frac) {
        double a = r.nextDouble() * Math.PI * 2, d = 3 + r.nextDouble() * (inst.radius * frac - 3);
        Vec3 c = inst.centerVec();
        return new Vec3(c.x + Math.cos(a) * d, inst.floorTop(), c.z + Math.sin(a) * d);
    }

    /** Pontas da corda da arena que passa por {@code p} na direção {@code dir} (de parede a parede). */
    private static Vec3[] chord(TrialInstance inst, Vec3 p, Vec3 dir) {
        Vec3 c = inst.centerVec();
        double rr = inst.radius - 0.8;
        double ox = p.x - c.x, oz = p.z - c.z;
        double b = ox * dir.x + oz * dir.z;
        double cc = ox * ox + oz * oz - rr * rr;
        double disc = Math.max(0, b * b - cc);
        double s1 = -b - Math.sqrt(disc), s2 = -b + Math.sqrt(disc);
        double y = inst.floorTop();
        Vec3 a = new Vec3(p.x + dir.x * s1, y, p.z + dir.z * s1);
        Vec3 e = new Vec3(p.x + dir.x * s2, y, p.z + dir.z * s2);
        if (a.distanceTo(e) < 4) {
            a = new Vec3(c.x - dir.x * rr, y, c.z - dir.z * rr);
            e = new Vec3(c.x + dir.x * rr, y, c.z + dir.z * rr);
        }
        return new Vec3[] {a, e};
    }

    private TrialHazards() {}
}
