package com.renanpontes.isekaimahou.feature.bosses_d.entity.kit;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.AvatarMove;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialAvatar;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Água: Onda em cone (empurra), Gêiser sob os jogadores (lança para cima; 2ª onda na fase 2+), Prisão de Bolha
 * (marca que prende), Redemoinho (puxa para o centro: corra para fora!) e o golpe máximo Dilúvio
 * (só os círculos verdes ficam secos).
 */
public final class WaterKit extends AvatarKit {
    private float waveYaw;
    private Vec3 pool = Vec3.ZERO;

    WaterKit(TrialAvatar boss) {
        super(boss, Element.WATER);
    }

    @Override
    public void flavor(LivingEntity e) {
        status(e, CoreRegistries.SOAKED, 120, 0);
    }

    @Override
    protected ParticleOptions fallParticle() {
        return ParticleTypes.FALLING_WATER;
    }

    private void geyserImpact(ServerLevel sl, Vec3 at, List<LivingEntity> hits) {
        hitAll(hits, 0.95F, e -> {
            flavor(e);
            launch(e, 1.05);
        });
        column(sl, at, 5, ParticleTypes.SPLASH, 4);
        column(sl, at, 5, ParticleTypes.BUBBLE_COLUMN_UP, 2);
        sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 4.5, at.z, 8, 0.6, 0.4, 0.6, 0.02);
        sound(sl, at, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.4F, 0.7F);
        sound(sl, at, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.5F, 0.8F);
    }

    @Override
    protected List<AvatarMove> build() {
        List<AvatarMove> l = new ArrayList<>();
        // 1) Onda em cone
        l.add(AvatarMove.of("tidal_wave", 34, 110, (sl, target, i) -> {
            if (i == 0) {
                // Mira no alvo (o yRot pode estar na direção em que ele andava de lado, não no jogador)
                waveYaw = yawOf(dirTo(target));
                Vec3 o = boss.position();
                delayed(sl, cone(o, waveYaw, 80F, 13F, warm(20)), hits -> {
                    hitAll(hits, 1.1F, e -> {
                        flavor(e);
                        push(e, o, 1.3, 0.35);
                    });
                    sound(sl, o, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2.0F, 0.5F);
                    for (int k = 1; k <= 8; k++) {
                        double r = k * 1.6;
                        sched(sl, k, () -> waveArc(sl, o, waveYaw, r));
                    }
                });
                sound(sl, o, SoundEvents.AMBIENT_UNDERWATER_ENTER, 1.5F, 0.8F);
            }
            boss.setYRot(waveYaw);
            boss.yBodyRot = waveYaw;
            boss.yHeadRot = waveYaw;
        }).anim("wave").range(0, 14).weight(12).locked());

        // 2) Gêiser sob os pés (e uma 2ª onda na fase 2+)
        l.add(AvatarMove.of("geyser", 46, 140, (sl, target, i) -> {
            if (i == 16 || (i == 30 && phase() >= 2)) {
                int w = warm(i == 16 ? 28 : 22);
                for (LivingEntity f : marks(4, 30)) markBlast(sl, f, 2.2F, w, (at, hits) -> geyserImpact(sl, at, hits));
                if (i == 16) {
                    for (int k = 0; k < 1 + phase(); k++) {
                        Vec3 s = randomSpot(boss.position(), 3, 12);
                        spotBlast(sl, s, 2.2F, w + 4, (at, hits) -> geyserImpact(sl, at, hits));
                    }
                }
                sound(sl, boss.position(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 2.0F, 0.6F);
            }
        }).anim("pillar").range(0, 30).weight(10));

        // 3) Prisão de Bolha
        l.add(prison("bubble_prison", 0.6F, e -> {
            e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, 0.25, 0));
            e.hurtMarked = true;
        }));

        // 4) Redemoinho: puxa para o centro; o centro explode
        l.add(AvatarMove.of("whirlpool", 60, 240, (sl, target, i) -> {
            if (i == 0) {
                pool = boss.position();
                announce("whirlpool");
                show(sl, ring(pool, 4F, 14F, warm(44)).noFlash().noRunes().color(Telegraph.FROST));
                Vec3 c = pool;
                delayed(sl, circle(c, 4F, warm(44)), hits -> {
                    hitAll(hits, 1.8F, e -> {
                        flavor(e);
                        launch(e, 0.8);
                    });
                    column(sl, c, 6, ParticleTypes.SPLASH, 4);
                    burst(sl, c.add(0, 1, 0), 60, 2.0);
                    sound(sl, c, SoundEvents.GENERIC_SPLASH, 2.0F, 0.5F);
                    shake(sl, c, 20, 1.0F, 12);
                });
            }
            if (i < warm(44)) {
                for (LivingEntity e : boss.foesNear(pool, 14)) {
                    Vec3 d = pool.subtract(e.position()).multiply(1, 0, 1);
                    double len = d.length();
                    if (len < 1.5) continue;
                    Vec3 u = d.scale(1 / len);
                    // Puxão moderado + giro: andando para fora dá para escapar (o servidor reenvia a velocidade
                    // todo tick, então o puxão "come" parte da caminhada: 0,035 ainda deixa ~1 bloco/s andando)
                    e.push(u.x * 0.035 - u.z * 0.016, 0, u.z * 0.035 + u.x * 0.016);
                    e.hurtMarked = true;
                }
                if (i % 2 == 0) {
                    double a = i * 0.4;
                    for (int k = 0; k < 6; k++) {
                        double ang = a + k * Math.PI / 3;
                        double r = 3 + (k % 3) * 4;
                        sl.sendParticles(ParticleTypes.SPLASH, pool.x + Math.cos(ang) * r, pool.y + 0.2, pool.z + Math.sin(ang) * r,
                                2, 0.1, 0.05, 0.1, 0.0);
                        sl.sendParticles(ParticleTypes.BUBBLE_POP, pool.x + Math.cos(ang) * r, pool.y + 0.3, pool.z + Math.sin(ang) * r,
                                1, 0.1, 0.05, 0.1, 0.0);
                    }
                }
                if (i % 10 == 0) sound(sl, pool, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 1.6F, 0.7F);
            }
        }).anim("vortex").range(0, 16).phase(2).weight(7));
        return l;
    }

    private void waveArc(ServerLevel sl, Vec3 o, float yaw, double r) {
        double y = Math.toRadians(yaw);
        for (int k = -6; k <= 6; k++) {
            double a = y + Math.toRadians(k * 6.5);
            double x = o.x - Math.sin(a) * r, z = o.z + Math.cos(a) * r;
            sl.sendParticles(ParticleTypes.SPLASH, x, o.y + 0.4, z, 3, 0.2, 0.2, 0.2, 0.05);
            if (k % 2 == 0) sl.sendParticles(ParticleTypes.BUBBLE_POP, x, o.y + 0.8, z, 1, 0.1, 0.2, 0.1, 0.02);
        }
    }

    /** Rastro: poça que encharca quem passar por ela. */
    @Override
    public void onDashTrail(ServerLevel sl, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < 4) return;
        show(sl, line(from, to, 2.2F, 4).hold(50).noFlash().noRunes());
        for (int d = 4; d <= 54; d += 10) {
            sched(sl, d, () -> {
                for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.line(from, to, 2.2F))) {
                    if (!boss.isFoe(e)) continue;
                    flavor(e);
                    status(e, net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 1);
                }
                lineFx(sl, from, to, ParticleTypes.SPLASH, 0.7);
            });
        }
    }

    @Override
    protected AvatarMove buildUltimate() {
        return safeZones("deluge", 2.2F);
    }
}
