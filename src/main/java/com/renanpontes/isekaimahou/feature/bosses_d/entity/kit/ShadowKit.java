package com.renanpontes.isekaimahou.feature.bosses_d.entity.kit;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.AvatarMove;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialAvatar;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Sombra: Correntes em linha (puxam para o chefe), Passo Sombrio (some e reaparece ATRÁS do alvo com um golpe em
 * cone), Zonas de Escuridão (ficam no chão), Explosão Umbral (anel em volta), rastro de vazio e o golpe máximo
 * Eclipse (escuridão + três marcas seguidas em cada jogador).
 */
public final class ShadowKit extends AvatarKit {
    private LivingEntity stepTarget;

    ShadowKit(TrialAvatar boss) {
        super(boss, Element.SHADOW);
    }

    @Override
    public void flavor(LivingEntity e) {
        status(e, CoreRegistries.CORRUPTED, 60, 0);
    }

    @Override
    protected ParticleOptions fallParticle() {
        return ParticleTypes.SQUID_INK;
    }

    private void voidBurst(ServerLevel sl, Vec3 at) {
        sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.5, at.z, 20, 0.8, 0.4, 0.8, 0.05);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.8, at.z, 30, 0.6, 0.6, 0.6, 0.1);
        sl.sendParticles(ElementFx.dust(fxEl(), 1.6F), at.x, at.y + 0.6, at.z, 16, 0.7, 0.5, 0.7, 0);
    }

    @Override
    protected List<AvatarMove> build() {
        List<AvatarMove> l = new ArrayList<>();
        // 1) Correntes Sombrias
        l.add(AvatarMove.of("shadow_chains", 44, 150, (sl, target, i) -> {
            if (i == 6) {
                int w = warm(16);
                for (LivingEntity f : marks(1 + phase(), 22)) {
                    Vec3 from = boss.position();
                    Vec3 d = f.position().subtract(from).multiply(1, 0, 1);
                    if (d.lengthSqr() < 1.0E-3) continue;
                    double len = d.length() + 3;
                    Vec3 u = d.normalize();
                    Vec3 to = from.add(u.scale(len));
                    delayed(sl, line(from.add(u), to, 1.6F, w), hits -> {
                        hitAll(hits, 0.8F, e -> {
                            flavor(e);
                            pushDir(e, u.scale(-1), 1.1, 0.25);
                            status(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 2);
                            status(e, MobEffects.DARKNESS, 40, 0);
                        });
                        lineFx(sl, from.add(0, 1.2, 0), to.add(0, 1.2, 0), ParticleTypes.SQUID_INK, 0.7);
                        lineFx(sl, from.add(0, 1.2, 0), to.add(0, 1.2, 0), ElementFx.dust(fxEl(), 1.1F), 0.5);
                        sound(sl, to, SoundEvents.CHAIN_BREAK, 1.6F, 0.6F);
                    });
                }
                sound(sl, boss.position(), SoundEvents.CHAIN_PLACE, 2.0F, 0.5F);
            }
        }).anim("chains").range(3, 22).weight(11));

        // 2) Zonas de Escuridão
        l.add(AvatarMove.of("dark_zones", 40, 180, (sl, target, i) -> {
            if (i == 4) {
                List<Vec3> spots = new ArrayList<>();
                for (LivingEntity f : marks(phase() + 1, 30)) spots.add(f.position());
                for (int k = 0; k < 2; k++) spots.add(randomSpot(target.position(), 3, 9));
                int w = warm(22);
                for (Vec3 s : spots) {
                    delayed(sl, circle(s, 3.0F, w).hold(100), hits -> {
                        hitAll(hits, 0.7F, e -> status(e, MobEffects.DARKNESS, 80, 0));
                        voidBurst(sl, s);
                        sound(sl, s, SoundEvents.SCULK_CATALYST_BLOOM, 1.4F, 0.6F);
                    });
                    for (int t = w + 20; t <= w + 100; t += 20) {
                        sched(sl, t, () -> {
                            for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(s, 3.0F))) {
                                if (!boss.isFoe(e)) continue;
                                boss.hitFoe(e, 0.3F);
                                status(e, MobEffects.WEAKNESS, 60, 0);
                            }
                            sl.sendParticles(ParticleTypes.SQUID_INK, s.x, s.y + 0.3, s.z, 8, 1.5, 0.2, 1.5, 0.01);
                            sl.sendParticles(ParticleTypes.SMOKE, s.x, s.y + 0.6, s.z, 6, 1.4, 0.4, 1.4, 0.01);
                        });
                    }
                }
            }
        }).anim("nova").range(0, 30).weight(8));

        // 3) Passo Sombrio: marca o alvo, some e reaparece atrás dele com um golpe em cone
        l.add(AvatarMove.of("shadow_step", 34, 130, (sl, target, i) -> {
            if (i == 0) {
                stepTarget = target;
                show(sl, follow(target, 2.5F, 12).noFlash().noRunes());
                sound(sl, boss.position(), SoundEvents.ENDERMAN_STARE, 1.2F, 0.8F);
            }
            if (i == 8) {
                boss.setInvisible(true);
                voidBurst(sl, boss.position());
                sound(sl, boss.position(), SoundEvents.ENDERMAN_TELEPORT, 1.4F, 0.6F);
            }
            if (i == 11) {
                LivingEntity t = stepTarget != null && stepTarget.isAlive() ? stepTarget : target;
                Vec3 look = t.getLookAngle().multiply(1, 0, 1);
                look = look.lengthSqr() < 1.0E-4 ? dirTo(t).scale(-1) : look.normalize();
                Vec3 behind = boss.groundAt(t.position().subtract(look.scale(2.6)));
                boolean moved = false;
                if (behind != null && boss.inArena(behind)
                        && sl.noCollision(boss, boss.getDimensions(boss.getPose()).makeBoundingBox(behind.x, behind.y, behind.z))) {
                    boss.teleportTo(behind.x, behind.y, behind.z);
                    moved = true;
                }
                if (!moved) BossKit.blinkNear(boss, t, 2.6);
                boss.setInvisible(false);
                boss.faceTarget(t);
                voidBurst(sl, boss.position());
                sound(sl, boss.position(), SoundEvents.ENDERMAN_TELEPORT, 1.4F, 0.9F);
                Vec3 o = boss.position();
                float yaw = yawOf(dirTo(t));
                boss.setYRot(yaw);
                boss.yBodyRot = yaw;
                // Aparece atrás: 14 ticks para dar um passo à frente (sai do alcance de 4,2 blocos)
                delayed(sl, cone(o, yaw, 110F, 4.2F, 14).noRunes(), hits -> {
                    hitAll(hits, 1.4F, e -> {
                        flavor(e);
                        push(e, o, 0.8, 0.3);
                    });
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK, o.x, o.y + 1.2, o.z, 3, 1.2, 0.3, 1.2, 0);
                    sound(sl, o, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
                });
            }
        }).anim("blink").range(4, 30).weight(9).locked().onEnd(() -> boss.setInvisible(false)));

        // 4) Explosão Umbral: anel em volta do chefe (o miolo é seguro)
        l.add(AvatarMove.of("umbral_burst", 36, 140, (sl, target, i) -> {
            if (i == 0) {
                Vec3 c = boss.position();
                delayed(sl, ring(c, 2.5F, 9F, warm(22)), hits -> {
                    hitAll(hits, 1.1F, e -> {
                        flavor(e);
                        push(e, c, 0.8, 0.3);
                    });
                    for (double r = 3; r <= 9; r += 2) ringFx(sl, c, r);
                    voidBurst(sl, c);
                    sound(sl, c, SoundEvents.WARDEN_SONIC_BOOM, 0.8F, 1.4F);
                    shake(sl, c, 16, 0.9F, 10);
                });
            }
            if (i < 20 && i % 2 == 0) sl.sendParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1.4, boss.getZ(), 6, 1.0, 1.0, 1.0, 0.2);
        }).anim("slam").range(0, 9).weight(9));
        return l;
    }

    /** Rastro: fenda de vazio que explode logo depois. */
    @Override
    public void onDashTrail(ServerLevel sl, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < 4) return;
        delayed(sl, line(from, to, 2.2F, 12), hits -> {
            hitAll(hits, 0.6F, e -> status(e, MobEffects.WEAKNESS, 60, 0));
            lineFx(sl, from, to, ParticleTypes.SQUID_INK, 0.6);
            sound(sl, to, SoundEvents.SCULK_CATALYST_BLOOM, 1.2F, 0.8F);
        });
    }

    @Override
    protected AvatarMove buildUltimate() {
        // Eclipse: escuridão em todos e três marcas seguidas em cada jogador
        return AvatarMove.of("eclipse", 96, 600, (sl, target, i) -> {
            if (i == 0) {
                announce("eclipse");
                for (ServerPlayer p : BossKit.partyAround(boss, 40)) status(p, MobEffects.DARKNESS, 140, 0);
                sound(sl, boss.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 2.0F, 0.6F);
            }
            if (i == 6 || i == 28 || i == 50) {
                for (LivingEntity f : marks(6, 40)) {
                    markBlast(sl, f, 2.4F, 28, (at, hits) -> {
                        hitAll(hits, 1.0F, this::flavor);
                        voidBurst(sl, at);
                        sound(sl, at, SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.5F);
                    });
                }
            }
            if (i % 3 == 0) sl.sendParticles(ParticleTypes.SQUID_INK, boss.getX(), boss.getY() + 1.5, boss.getZ(), 8, 2.0, 1.0, 2.0, 0.02);
        }).anim("channel").ultimate();
    }
}
