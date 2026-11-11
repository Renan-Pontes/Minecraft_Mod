package com.renanpontes.isekaimahou.feature.bosses_d.entity.kit;

import java.util.ArrayList;
import java.util.List;

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
 * Fogo: Chuva de Meteoros (marcas que perseguem + pontos aleatórios), Erupção (pilares de lava sob os pés),
 * Nova Flamejante (sai do círculo, depois volte para dentro), Sopro Ígneo (cone que fica queimando),
 * rastro de fogo na investida e o golpe máximo Inferno Primordial (anéis em sequência).
 */
public final class FireKit extends AvatarKit {
    private float breathYaw;
    private Vec3 breathOrigin = Vec3.ZERO;
    private int breathWarm;

    FireKit(TrialAvatar boss) {
        super(boss, Element.FIRE);
    }

    @Override
    public void flavor(LivingEntity e) {
        e.igniteForSeconds(3 + rank() / 2F);
    }

    @Override
    protected ParticleOptions fallParticle() {
        return ParticleTypes.LARGE_SMOKE;
    }

    private void meteorImpact(ServerLevel sl, Vec3 at, List<LivingEntity> hits) {
        hitAll(hits, 1.3F, e -> {
            flavor(e);
            push(e, at, 0.5, 0.4);
        });
        sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.6, at.z, 2, 0.4, 0.2, 0.4, 0);
        sl.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 12, 0.8, 0.2, 0.8, 0);
        sl.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 30, 1.2, 0.3, 1.2, 0.05);
        sound(sl, at, explodeSound(), 1.2F, 0.9F + boss.getRandom().nextFloat() * 0.2F);
        shake(sl, at, 10, 0.5F, 8);
    }

    private void eruptionImpact(ServerLevel sl, Vec3 at, List<LivingEntity> hits) {
        hitAll(hits, 1.0F, e -> {
            flavor(e);
            launch(e, 0.9);
        });
        column(sl, at, 5, ParticleTypes.FLAME, 4);
        column(sl, at, 4, ParticleTypes.LAVA, 1);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 3, at.z, 10, 0.5, 1.0, 0.5, 0.02);
        sound(sl, at, SoundEvents.BLAZE_SHOOT, 1.2F, 0.6F);
        sound(sl, at, SoundEvents.LAVA_POP, 1.5F, 0.8F);
    }

    @Override
    protected List<AvatarMove> build() {
        List<AvatarMove> l = new ArrayList<>();
        // 1) Chuva de Meteoros
        l.add(AvatarMove.of("meteor_rain", 56, 190, (sl, target, i) -> {
            if (i == 0) {
                if (phase() >= 2) announce("meteor_rain");
                sound(sl, boss.position(), SoundEvents.BLAZE_AMBIENT, 2.0F, 0.5F);
            }
            if (i < 10) sl.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + 3.2, boss.getZ(), 6, 0.4, 0.2, 0.4, 0.08);
            if (i == 10) {
                int w = warm(42);
                for (LivingEntity f : marks(3 + phase() / 2, 32)) markBlast(sl, f, 2.6F, w, true, (at, hits) -> meteorImpact(sl, at, hits));
                int extra = 1 + phase() + rank() / 3;
                Vec3 around = target.position();
                for (int k = 0; k < extra; k++) {
                    Vec3 s = randomSpot(around, 2, 9);
                    int ww = w + 4 + k * 4;
                    fallFx(sl, () -> s, ww);
                    spotBlast(sl, s, 2.6F, ww, (at, hits) -> meteorImpact(sl, at, hits));
                }
            }
        }).anim("meteor").range(0, 40).weight(10));

        // 2) Erupção: pilares de lava sob os pés
        l.add(AvatarMove.of("eruption", 44, 160, (sl, target, i) -> {
            if (i == 16) {
                int w = warm(28);
                for (LivingEntity f : marks(4, 30)) markBlast(sl, f, 2.2F, w, (at, hits) -> eruptionImpact(sl, at, hits));
                for (int k = 0; k < 1 + phase(); k++) {
                    Vec3 s = randomSpot(boss.position(), 3, 12);
                    spotBlast(sl, s, 2.2F, w + 6, (at, hits) -> eruptionImpact(sl, at, hits));
                }
                sound(sl, boss.position(), SoundEvents.LAVA_AMBIENT, 2.0F, 0.6F);
                shake(sl, boss.position(), 18, 0.7F, 10);
            }
        }).anim("pillar").range(0, 30).weight(9));

        // 3) Nova Flamejante (dentro → fora)
        l.add(insideOutNova("flame_nova", 1.4F, 1.2F));

        // 4) Sopro Ígneo: cone que continua queimando por 1,3 s
        l.add(AvatarMove.of("fire_breath", 56, 150, (sl, target, i) -> {
            if (i == 0) {
                // Mira no alvo (o yRot pode estar na direção em que ele andava de lado, não no jogador)
                breathYaw = yawOf(dirTo(target));
                breathOrigin = boss.position();
                breathWarm = warm(18);
                show(sl, cone(breathOrigin, breathYaw, 70F, 10F, breathWarm).hold(26));
                sound(sl, breathOrigin, SoundEvents.BLAZE_AMBIENT, 1.8F, 0.6F);
            }
            boss.setYRot(breathYaw);
            boss.yBodyRot = breathYaw;
            boss.yHeadRot = breathYaw;
            if (i >= breathWarm && i < breathWarm + 26) {
                breathParticles(sl);
                if ((i - breathWarm) % 5 == 0) {
                    float mult = i == breathWarm ? 0.8F : 0.3F;
                    for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.cone(breathOrigin, breathYaw, 70F, 10F))) {
                        if (!boss.isFoe(e)) continue;
                        boss.hitFoe(e, mult);
                        flavor(e);
                    }
                }
                if ((i - breathWarm) % 4 == 0) sound(sl, breathOrigin, SoundEvents.BLAZE_SHOOT, 1.0F, 0.7F);
            }
        }).anim("breath").range(0, 9).weight(8).locked());
        return l;
    }

    private void breathParticles(ServerLevel sl) {
        double yaw = Math.toRadians(breathYaw);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 mouth = breathOrigin.add(0, 1.7, 0).add(fwd.scale(0.8));
        for (int k = 0; k < 8; k++) {
            Vec3 d = rotateY(fwd, (boss.getRandom().nextDouble() - 0.5) * 70).scale(0.5 + boss.getRandom().nextDouble() * 0.6);
            sl.sendParticles(ParticleTypes.FLAME, mouth.x, mouth.y, mouth.z, 0, d.x, -0.08, d.z, 1.0);
        }
        sl.sendParticles(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, 2, 0.2, 0.2, 0.2, 0.05);
    }

    /** Rastro: linha de fogo que fica 3 s no chão queimando quem pisar. */
    @Override
    public void onDashTrail(ServerLevel sl, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < 4) return;
        show(sl, line(from, to, 2.2F, 4).hold(60).noFlash().noRunes());
        for (int d = 4; d <= 64; d += 10) {
            sched(sl, d, () -> {
                for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.line(from, to, 2.2F))) {
                    if (!boss.isFoe(e)) continue;
                    boss.hitFoe(e, 0.2F);
                    e.igniteForSeconds(2);
                }
                lineFx(sl, from, to, ParticleTypes.FLAME, 0.8);
            });
        }
    }

    @Override
    protected AvatarMove buildUltimate() {
        // Inferno Primordial: quatro anéis em sequência (fique onde já explodiu) + marcas no fim
        return AvatarMove.of("inferno", 90, 600, (sl, target, i) -> {
            if (i == 0) {
                announce("inferno");
                Vec3 c = boss.position();
                for (int k = 0; k < 4; k++) {
                    float in = k * 5F, out = in + 5F;
                    int w = 24 + k * 14;
                    Telegraph.Shape s = k == 0 ? circle(c, out, w) : ring(c, in, out, w);
                    float r = (in + out) / 2;
                    delayed(sl, s, hits -> {
                        hitAll(hits, 1.3F, this::flavor);
                        ringFx(sl, c, r);
                        ringFx(sl, c, out);
                        sound(sl, c, explodeSound(), 1.6F, 0.8F);
                        shake(sl, c, 30, 1.0F, 10);
                    });
                }
                sound(sl, c, SoundEvents.BLAZE_AMBIENT, 3.0F, 0.4F);
            }
            if (i == 66) {
                for (LivingEntity f : marks(4, 30)) markBlast(sl, f, 2.4F, 22, true, (at, hits) -> meteorImpact(sl, at, hits));
            }
            if (i % 3 == 0) sl.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + 1.5, boss.getZ(), 10, 1.5, 1.0, 1.5, 0.06);
        }).anim("channel").ultimate();
    }
}
