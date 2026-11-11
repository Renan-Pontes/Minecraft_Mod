package com.renanpontes.isekaimahou.feature.creatures_e.entity;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

/**
 * Passeio das voadoras sem alvo: escolhe pontos proximos, sempre a poucos blocos do chao/ilha mais proximo
 * e abaixo de qualquer teto. A busca e feita pela coluna de blocos (e nao pelo heightmap) porque no Abismo
 * o heightmap aponta para cima do teto da caverna, e nas ilhas flutuantes pode apontar para outra ilha.
 */
public class SkyWanderGoal extends Goal {
    private static final int NO_FLOOR = Integer.MIN_VALUE;
    private final DimensionalCreature mob;

    public SkyWanderGoal(DimensionalCreature mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() == null && mob.getNavigation().isDone() && mob.getRandom().nextInt(25) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() == null && mob.getNavigation().isInProgress();
    }

    @Override
    public void start() {
        var rnd = mob.getRandom();
        Level level = mob.level();
        for (int attempt = 0; attempt < 10; attempt++) {
            double x = mob.getX() + (rnd.nextDouble() - 0.5) * 24;
            double z = mob.getZ() + (rnd.nextDouble() - 0.5) * 24;
            BlockPos.MutableBlockPos p = BlockPos.containing(x, mob.getY() + (rnd.nextDouble() - 0.5) * 6, z).mutable();
            // ponto de partida dentro de um bloco: sobe um pouco ate achar ar
            for (int i = 0; i < 6 && solid(level, p); i++) p.move(0, 1, 0);
            if (solid(level, p)) continue;

            int floor = NO_FLOOR;
            for (int i = 0; i < 24 && p.getY() > level.getMinBuildHeight(); i++) {
                p.move(0, -1, 0);
                if (solid(level, p)) {
                    floor = p.getY() + 1;
                    break;
                }
            }
            double y;
            if (floor == NO_FLOOR) {
                // sobre o vazio entre ilhas: mantem a altura atual
                y = mob.getY() + (rnd.nextDouble() - 0.5) * 3;
            } else {
                y = floor + 2.0 + rnd.nextDouble() * 7.0;
                // teto (cavernas, beirais de ilhas): fica abaixo dele com folga para o corpo
                for (int h = floor; h <= Mth.floor(y + mob.getBbHeight()) + 1; h++) {
                    p.setY(h);
                    if (solid(level, p)) {
                        y = Math.min(y, h - mob.getBbHeight() - 0.4);
                        break;
                    }
                }
                if (y < floor + 0.8) continue;
            }
            if (level.isOutsideBuildHeight(Mth.floor(y)) || level.isOutsideBuildHeight(Mth.floor(y + mob.getBbHeight()))) continue;
            if (mob.getNavigation().moveTo(x, y, z, 0.8)) return;
        }
    }

    /** Bloco que serve de chao/teto (colisao ou liquido). */
    private static boolean solid(Level level, BlockPos p) {
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty();
    }
}
