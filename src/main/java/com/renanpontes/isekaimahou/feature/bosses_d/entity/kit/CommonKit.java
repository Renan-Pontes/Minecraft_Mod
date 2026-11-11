package com.renanpontes.isekaimahou.feature.bosses_d.entity.kit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.AvatarMove;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialAvatar;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Golpes de todo Avatar: Impacto (área em volta), Investida (linha), magias de glifo e Invocação. */
public final class CommonKit extends AvatarKit {
    private final AvatarKit main;
    // Estado da investida (um golpe por vez)
    private Vec3 dashFrom = Vec3.ZERO;
    private Vec3 dashDir = new Vec3(0, 0, 1);
    private double dashLen;
    private int dashWarm;
    private boolean dashDone;
    private final Set<UUID> dashHit = new HashSet<>();
    private final List<Vec3> summonSpots = new ArrayList<>();

    CommonKit(TrialAvatar boss, AvatarKit main) {
        super(boss, main.kitElement);
        this.main = main;
    }

    @Override
    public void flavor(LivingEntity e) {
        main.flavor(e);
    }

    @Override
    protected List<AvatarMove> build() {
        List<AvatarMove> l = new ArrayList<>();

        // Impacto: as duas mãos no chão, círculo em volta (corpo a corpo telegrafado)
        l.add(AvatarMove.of("slam", 30, 70, (sl, target, i) -> {
            if (i == 0) {
                // Raio e tempo pensados para dar para sair andando de perto do chefe (≈ 4 blocos em 18 ticks)
                float r = 4.0F + 0.4F * (phase() - 1);
                Vec3 c = boss.position();
                delayed(sl, circle(c, r, 18), hits -> {
                    hitAll(hits, 1.2F, e -> {
                        push(e, c, 0.9, 0.35);
                        flavor(e);
                    });
                    ringFx(sl, c, r * 0.5);
                    ringFx(sl, c, r);
                    burst(sl, c.add(0, 0.4, 0), 40, r * 0.4);
                    sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 2, 0.6, 0.1, 0.6, 0);
                    sound(sl, c, SoundEvents.ANVIL_LAND, 1.2F, 0.55F);
                    sound(sl, c, explodeSound(), 0.8F, 1.2F);
                    shake(sl, c, 16, 0.9F, 12);
                });
            }
        }).anim("slam").range(0, 5.5).weight(14));

        // Investida em linha: linha telegrafada, depois corre por ela; deixa o rastro do elemento
        l.add(AvatarMove.of("dash", 40, 110, (sl, target, i) -> {
            if (i == 0) {
                dashFrom = boss.position();
                dashDir = dirTo(target);
                dashLen = Mth.clamp(boss.distanceTo(target) + 4, 8, 18);
                dashWarm = warm(18);
                dashDone = false;
                dashHit.clear();
                boss.faceDir(dashDir);
                show(sl, line(dashFrom, dashFrom.add(dashDir.scale(dashLen)), boss.getBbWidth() + 1.4F, dashWarm));
                sound(sl, dashFrom, SoundEvents.EVOKER_PREPARE_ATTACK, 1.4F, 0.7F);
            }
            if (i < dashWarm) {
                boss.stopMoving();
                boss.faceDir(dashDir);
                boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
                if (i % 3 == 0) burst(sl, boss.position().add(0, 0.3, 0), 6, 0.6);
                return;
            }
            if (dashDone) {
                boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
                return;
            }
            int steps = 12;
            int k = i - dashWarm;
            Vec3 step = dashDir.scale(dashLen / steps);
            boss.getNavigation().stop();
            boss.move(MoverType.SELF, step);
            boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
            for (LivingEntity e : boss.foesNear(boss.position(), 3.5)) {
                if (!e.getBoundingBox().intersects(boss.getBoundingBox().inflate(0.7)) || !dashHit.add(e.getUUID())) continue;
                boss.hitFoe(e, 1.3F);
                flavor(e);
                Vec3 side = new Vec3(-dashDir.z, 0, dashDir.x);
                if (side.dot(e.position().subtract(boss.position())) < 0) side = side.scale(-1);
                pushDir(e, side.add(dashDir.scale(0.6)), 1.1, 0.4);
            }
            burst(sl, boss.position().add(0, 1.0, 0), 8, 0.5);
            sl.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.2, boss.getZ(), 3, 0.3, 0.05, 0.3, 0.02);
            if (k == 0) sound(sl, boss.position(), SoundEvents.PHANTOM_SWOOP, 1.5F, 0.6F);
            if (k >= steps - 1 || boss.horizontalCollision) {
                dashDone = true;
                main.onDashTrail(sl, dashFrom, boss.position());
                if (boss.horizontalCollision) {
                    sound(sl, boss.position(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 1.2F, 0.6F);
                    shake(sl, boss.position(), 12, 0.7F, 8);
                }
            }
        }).anim("dash").range(5, 20).weight(9).moving().locked());

        // Magia de glifo (fase 1): uma programável mirada, com linha de mira no chão
        l.add(AvatarMove.of("cast", 26, 50, (sl, target, i) -> {
            if (i == 0) aim(sl, target, 12);
            if (i == 12) boss.castSpell(target, false);
            if (i < 12) handFx(sl);
        }).anim("cast").range(3, 40).weight(12).when(t -> boss.phase() == 1));

        // Rajada de magias (fase 2+): duas (três na fase 3) alternando as mãos
        l.add(AvatarMove.of("barrage", 36, 60, (sl, target, i) -> {
            if (i == 0) aim(sl, target, 7);
            if (i == 7) boss.castSpell(target, false);
            if (i == 13) aim(sl, target, 7);
            if (i == 20) boss.castSpell(target, true);
            if (i == 26 && phase() >= 3) boss.castSpell(target, false);
            if (i % 2 == 0) handFx(sl);
        }).anim("barrage").range(3, 40).phase(2).weight(13));

        // Invocação de lacaios do elemento (rank 2+, fase 2+)
        l.add(AvatarMove.of("summon", 40, 520, (sl, target, i) -> {
            if (i == 0) {
                summonSpots.clear();
                int n = 1 + (phase() >= 3 ? 1 : 0) + (rank() >= 5 ? 1 : 0);
                n = Math.min(n, cap() - boss.minionCount());
                for (int k = 0; k < Math.max(1, n); k++) {
                    Vec3 s = randomSpot(boss.position(), 2.5, 5.5);
                    summonSpots.add(s);
                    show(sl, circle(s, 1.3F, 20).noFlash().noRunes());
                }
                announce("summon");
                sound(sl, boss.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1.6F, 0.8F);
            }
            if (i < 20 && i % 2 == 0) {
                for (Vec3 s : summonSpots) column(sl, s, 2.0 * i / 20.0, ElementFx.dust(fxEl(), 1.2F), 3);
            }
            if (i == 20) {
                for (Vec3 s : summonSpots) boss.summonMinion(s, target);
            }
        }).anim("summon").phase(2).weight(6).when(t -> boss.rank() >= 2 && boss.minionCount() < cap()));
        return l;
    }

    private int cap() {
        return Math.min(3, 1 + phase() - 1 + (rank() >= 5 ? 1 : 0));
    }

    /** Linha de mira no chão (curta, sem flash): mostra para onde a magia vai. */
    private void aim(ServerLevel sl, LivingEntity target, int w) {
        Vec3 from = boss.position();
        Vec3 d = dirTo(target);
        double len = Math.min(24, boss.distanceTo(target) + 3);
        Telegraph.show(sl, Telegraph.Shape.line(from.add(d.scale(1.0)), from.add(d.scale(len)), 1.0F).warmup(w).color(col())
                .owner(boss).noFlash().noRunes());
    }

    private void handFx(ServerLevel sl) {
        Vec3 h = boss.handPos();
        sl.sendParticles(ElementFx.dust(fxEl(), 1.1F), h.x, h.y, h.z, 3, 0.12, 0.12, 0.12, 0);
        sl.sendParticles(ElementFx.signature(fxEl()), h.x, h.y, h.z, 1, 0.08, 0.08, 0.08, 0.01);
    }

    @Nullable
    @Override
    protected AvatarMove buildUltimate() {
        return null;
    }
}
