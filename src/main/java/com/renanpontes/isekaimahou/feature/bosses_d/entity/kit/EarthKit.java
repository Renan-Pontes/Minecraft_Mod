package com.renanpontes.isekaimahou.feature.bosses_d.entity.kit;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.AvatarMove;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialAvatar;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Terra: Pilares em linha (1/3/5 linhas em leque por fase), Terremoto em anel (PULE para desviar), Chuva de
 * Pedras, Espinhos sob os pés, onda de choque no fim da investida e o golpe máximo Cataclismo (três anéis
 * pulaveis + pedras).
 */
public final class EarthKit extends AvatarKit {
    private static final ParticleOptions STONE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
    private static final ParticleOptions DIRT = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState());
    private static final ParticleOptions PILLAR = new BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.STONE.defaultBlockState());

    EarthKit(TrialAvatar boss) {
        super(boss, Element.EARTH);
    }

    @Override
    public void flavor(LivingEntity e) {
        status(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
    }

    @Override
    protected ParticleOptions fallParticle() {
        return STONE;
    }

    private void pillarFx(ServerLevel sl, Vec3 at, double h) {
        column(sl, at, h, STONE, 4);
        sl.sendParticles(PILLAR, at.x, at.y + 0.2, at.z, 14, 0.5, 0.1, 0.5, 0.2);
        sl.sendParticles(DIRT, at.x, at.y + 0.5, at.z, 10, 0.6, 0.4, 0.6, 0.15);
    }

    /** Tremor que só pega quem está com os pés no chão (pule!). */
    private void quakeRing(ServerLevel sl, Vec3 c, float in, float out, int w, float mult) {
        delayed(sl, ring(c, in, out, w).vertical(0.5, 1.0), hits -> {
            hitAll(hits, mult, e -> {
                flavor(e);
                launch(e, 0.3);
            });
            for (double r = in + 1; r <= out; r += 2.5) ringFx(sl, c, r);
            for (int k = 0; k < 24; k++) {
                double a = k * Math.PI / 12;
                double r = (in + out) / 2;
                sl.sendParticles(STONE, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 3, 0.4, 0.1, 0.4, 0.1);
            }
            sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.5F);
            shake(sl, c, out + 10, 1.4F, 14);
        });
    }

    @Override
    protected List<AvatarMove> build() {
        List<AvatarMove> l = new ArrayList<>();
        // 1) Pilares em linha
        l.add(AvatarMove.of("pillar_line", 44, 120, (sl, target, i) -> {
            if (i == 16) {
                int n = phase() == 1 ? 1 : phase() == 2 ? 3 : 5;
                Vec3 d0 = dirTo(target);
                int w = warm(18);
                for (int k = 0; k < n; k++) {
                    Vec3 d = rotateY(d0, (k - (n - 1) / 2.0) * 22);
                    Vec3 from = boss.position().add(d.scale(1.5));
                    Vec3 to = from.add(d.scale(18));
                    delayed(sl, line(from, to, 2.4F, w), hits -> {
                        hitAll(hits, 1.1F, e -> {
                            flavor(e);
                            launch(e, 0.8);
                        });
                        for (int s = 0; s <= 6; s++) {
                            Vec3 p = from.add(d.scale(s * 3));
                            sched(sl, s + 1, () -> {
                                pillarFx(sl, p, 3.0);
                                sound(sl, p, SoundEvents.POINTED_DRIPSTONE_LAND, 1.0F, 0.6F);
                            });
                        }
                    });
                }
                sound(sl, boss.position(), SoundEvents.STONE_BREAK, 2.0F, 0.5F);
                shake(sl, boss.position(), 16, 0.6F, 8);
            }
        }).anim("pillar").range(3, 24).weight(12));

        // 2) Terremoto em anel (pular desvia)
        l.add(AvatarMove.of("quake", 40, 130, (sl, target, i) -> {
            if (i == 0) {
                Vec3 c = boss.position();
                announce("quake");
                quakeRing(sl, c, 2F, 11F, warm(24), 1.2F);
                if (phase() >= 3) quakeRing(sl, c, 11F, 16F, warm(36), 1.0F);
            }
            if (i < 16 && i % 3 == 0) sl.sendParticles(DIRT, boss.getX(), boss.getY() + 0.2, boss.getZ(), 10, 1.5, 0.1, 1.5, 0.1);
        }).anim("slam").range(0, 11).weight(10));

        // 3) Chuva de Pedras
        l.add(AvatarMove.of("rockfall", 56, 170, (sl, target, i) -> {
            if (i == 8) {
                List<LivingEntity> foes = marks(4, 30);
                if (foes.isEmpty()) foes = List.of(target);
                int n = 5 + 2 * phase() + rank() / 2;
                for (int k = 0; k < n; k++) {
                    LivingEntity f = foes.get(k % foes.size());
                    Vec3 s = randomSpot(f.position(), 0, 5);
                    int w = warm(26) + k * 3;
                    fallFx(sl, () -> s, w);
                    spotBlast(sl, s, 2.0F, w, (at, hits) -> {
                        hitAll(hits, 1.0F, this::flavor);
                        sl.sendParticles(STONE, at.x, at.y + 0.4, at.z, 30, 0.7, 0.3, 0.7, 0.15);
                        sl.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.3, at.z, 6, 0.5, 0.1, 0.5, 0.02);
                        sound(sl, at, SoundEvents.STONE_BREAK, 1.4F, 0.6F);
                        sound(sl, at, SoundEvents.ANVIL_LAND, 0.5F, 0.5F);
                    });
                }
                sound(sl, boss.position(), SoundEvents.ROOTED_DIRT_BREAK, 2.0F, 0.5F);
            }
        }).anim("meteor").range(0, 30).weight(9));

        // 4) Espinhos sob os pés
        l.add(AvatarMove.of("spikes", 40, 150, (sl, target, i) -> {
            if (i == 16) {
                for (LivingEntity f : marks(4, 26)) {
                    markBlast(sl, f, 1.6F, warm(24), (at, hits) -> {
                        hitAll(hits, 1.1F, e -> {
                            flavor(e);
                            launch(e, 1.0);
                        });
                        pillarFx(sl, at, 2.5);
                        sound(sl, at, SoundEvents.POINTED_DRIPSTONE_LAND, 1.4F, 0.8F);
                        sound(sl, at, SoundEvents.STONE_BREAK, 1.0F, 1.2F);
                    });
                }
            }
        }).anim("pillar").range(0, 26).weight(9));
        return l;
    }

    /** Fim da investida: pequena onda de choque pulável. */
    @Override
    public void onDashTrail(ServerLevel sl, Vec3 from, Vec3 to) {
        quakeRing(sl, to, 1F, 5F, 12, 0.8F);
    }

    @Override
    protected AvatarMove buildUltimate() {
        return AvatarMove.of("cataclysm", 90, 600, (sl, target, i) -> {
            // Três tremores seguidos: cada anel só aparece quando o anterior estoura (ritmo de "pula, pula, pula")
            if (i == 0) {
                announce("cataclysm");
                quakeRing(sl, boss.position(), 2F, 22F, 30, 1.5F);
            }
            if (i == 30 || i == 52) quakeRing(sl, boss.position(), 2F, 22F, 22, 1.5F);
            if (i == 10 || i == 40) {
                for (LivingEntity f : marks(4, 30)) {
                    Vec3 s = randomSpot(f.position(), 1, 4);
                    fallFx(sl, () -> s, 26);
                    spotBlast(sl, s, 2.2F, 26, (at, hits) -> {
                        hitAll(hits, 1.0F, this::flavor);
                        sl.sendParticles(STONE, at.x, at.y + 0.4, at.z, 30, 0.7, 0.3, 0.7, 0.15);
                        sound(sl, at, SoundEvents.STONE_BREAK, 1.4F, 0.5F);
                    });
                }
            }
            if (i % 4 == 0) sl.sendParticles(DIRT, boss.getX(), boss.getY() + 0.3, boss.getZ(), 12, 3.0, 0.2, 3.0, 0.1);
        }).anim("channel").ultimate();
    }
}
