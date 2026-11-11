package com.renanpontes.isekaimahou.feature.bosses_d.entity.kit;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
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
 * Luz: Lanças do Céu (círculos sob os jogadores + aleatórios), Zona Sagrada (círculos que ficam queimando),
 * Feixe Giratório (2–3 feixes horizontais que giram: chegue perto e acompanhe a brecha), rastro de luz que
 * explode e o golpe máximo Julgamento (tabuleiro de pilares em duas ondas).
 */
public final class LightKit extends AvatarKit {

    LightKit(TrialAvatar boss) {
        super(boss, Element.LIGHT);
    }

    @Override
    public void flavor(LivingEntity e) {
        status(e, CoreRegistries.EXPOSED, 100, 0);
    }

    @Override
    protected ParticleOptions fallParticle() {
        return ParticleTypes.END_ROD;
    }

    private void lanceImpact(ServerLevel sl, Vec3 at, List<LivingEntity> hits, float mult) {
        hitAll(hits, mult, this::flavor);
        column(sl, at, 12, ParticleTypes.END_ROD, 2);
        sl.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        burst(sl, at.add(0, 0.6, 0), 20, 0.8);
        sound(sl, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 1.6F);
        sound(sl, at, SoundEvents.TRIDENT_THUNDER.value(), 0.5F, 1.8F);
    }

    @Override
    protected List<AvatarMove> build() {
        List<AvatarMove> l = new ArrayList<>();
        // 1) Lanças do Céu
        l.add(AvatarMove.of("sky_lances", 50, 150, (sl, target, i) -> {
            if (i == 8) {
                int w = warm(32);
                for (LivingEntity f : marks(4, 36)) markBlast(sl, f, 2.2F, w, true, (at, hits) -> lanceImpact(sl, at, hits, 1.2F));
                for (int k = 0; k < 2 + phase(); k++) {
                    Vec3 s = randomSpot(target.position(), 2, 8);
                    int ww = w - 2 + k * 5;
                    fallFx(sl, () -> s, ww);
                    spotBlast(sl, s, 2.2F, ww, (at, hits) -> lanceImpact(sl, at, hits, 1.2F));
                }
                sound(sl, boss.position(), SoundEvents.BEACON_POWER_SELECT, 2.0F, 1.5F);
            }
        }).anim("meteor").range(0, 36).weight(11));

        // 2) Zona Sagrada: círculos que ficam no chão queimando
        l.add(AvatarMove.of("holy_zone", 40, 200, (sl, target, i) -> {
            if (i == 4) {
                List<Vec3> spots = new ArrayList<>();
                for (LivingEntity f : marks(phase() + 1, 24)) spots.add(f.position());
                spots.add(randomSpot(boss.position(), 4, 10));
                int w = warm(22);
                for (Vec3 s : spots) {
                    delayed(sl, circle(s, 3.2F, w).hold(100), hits -> {
                        hitAll(hits, 0.8F, this::flavor);
                        column(sl, s, 5, ParticleTypes.END_ROD, 2);
                        sound(sl, s, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
                    });
                    for (int t = w + 10; t <= w + 100; t += 10) {
                        sched(sl, t, () -> {
                            for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(s, 3.2F))) {
                                if (boss.isFoe(e)) boss.hitFoe(e, 0.25F);
                            }
                            sl.sendParticles(ParticleTypes.END_ROD, s.x, s.y + 0.3, s.z, 6, 1.6, 0.2, 1.6, 0.03);
                            sl.sendParticles(ParticleTypes.GLOW, s.x, s.y + 0.6, s.z, 4, 1.4, 0.4, 1.4, 0.01);
                        });
                    }
                }
                announce("holy_zone");
            }
        }).anim("nova").range(0, 24).weight(8));

        // 3) Feixe Giratório
        l.add(rotatingBeams("radiant_beam"));

        // 4) Clarão: círculo em volta do chefe que cega por 2 s
        l.add(AvatarMove.of("radiant_flash", 30, 120, (sl, target, i) -> {
            if (i == 0) {
                Vec3 c = boss.position();
                delayed(sl, circle(c, 5.0F, 22), hits -> {
                    hitAll(hits, 0.9F, e -> {
                        flavor(e);
                        status(e, net.minecraft.world.effect.MobEffects.BLINDNESS, 40, 0);
                        push(e, c, 0.7, 0.3);
                    });
                    sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.5, c.z, 2, 0.5, 0.5, 0.5, 0);
                    ringFx(sl, c, 3.0);
                    ringFx(sl, c, 5.0);
                    sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.8F);
                    shake(sl, c, 14, 0.7F, 8);
                });
                sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 1.4F, 1.8F);
            }
            if (i < 22 && i % 2 == 0) {
                sl.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + 1.9, boss.getZ(), 4, 0.3, 0.3, 0.3, 0.05);
            }
        }).anim("slam").range(0, 6).weight(9));
        return l;
    }

    /** Rastro: a trilha de luz explode logo depois (linha telegrafada). */
    @Override
    public void onDashTrail(ServerLevel sl, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < 4) return;
        delayed(sl, line(from, to, 2.2F, 12), hits -> {
            hitAll(hits, 0.7F, this::flavor);
            lineFx(sl, from, to, ParticleTypes.END_ROD, 0.6);
            sound(sl, to, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 1.2F);
        });
    }

    @Override
    protected AvatarMove buildUltimate() {
        // Julgamento: tabuleiro de pilares de luz — primeiro as casas "pares", depois as "ímpares". O raio 3,2 com
        // passo 4,5 cobre os cantos das casas (não há "fresta" segura nas duas ondas) e deixa o centro das casas da
        // outra cor seguro com ~1 bloco de folga. A borda da arena também é atingida nas duas ondas: entre no tabuleiro!
        return AvatarMove.of("judgment", 90, 600, (sl, target, i) -> {
            if (i == 0) {
                announce("judgment");
                Vec3 c = boss.home() != null && BossKit.arenaRadius(boss) > 0 ? boss.home() : boss.position();
                double step = 4.5;
                float cell = 3.2F;
                double gridR = 17.0;
                for (int gx = -4; gx <= 4; gx++) {
                    for (int gz = -4; gz <= 4; gz++) {
                        double x = c.x + gx * step, z = c.z + gz * step;
                        if (Math.hypot(x - c.x, z - c.z) > gridR) continue;
                        Vec3 p = boss.groundAt(new Vec3(x, c.y, z));
                        if (p == null || !boss.inArena(p)) continue;
                        boolean even = ((gx + gz) & 1) == 0;
                        int w = even ? 36 : 60;
                        delayed(sl, circle(p, cell, w).noRunes(), hits -> lanceImpact(sl, p, hits, 1.4F));
                    }
                }
                for (int w : new int[] {36, 60}) {
                    delayed(sl, ring(c, 15.5F, 30F, w).noRunes(), hits -> {
                        hitAll(hits, 1.0F, this::flavor);
                        ringFx(sl, c, 16);
                        ringFx(sl, c, 20);
                        sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 1.4F);
                    });
                }
                sound(sl, c, SoundEvents.BEACON_ACTIVATE, 3.0F, 0.7F);
            }
            if (i % 3 == 0) sl.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + 4.5, boss.getZ(), 6, 2.0, 0.5, 2.0, 0.02);
        }).anim("channel").ultimate();
    }
}
