package com.renanpontes.isekaimahou.feature.creatures_d.entity.ai;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Passeio aéreo: escolhe um ponto livre a até {@code range} blocos, de {@code minAlt} a {@code maxAlt}
 * blocos acima do chão (funciona em cavernas: procura o chão logo abaixo do ponto sorteado).
 */
public class FlyWanderGoal extends Goal {
    private final PathfinderMob mob;
    private final double speed;
    private final int minAlt;
    private final int maxAlt;
    private final int range;
    private BooleanSupplier allowed = () -> true;

    public FlyWanderGoal(PathfinderMob mob, double speed, int minAlt, int maxAlt, int range) {
        this.mob = mob;
        this.speed = speed;
        this.minAlt = minAlt;
        this.maxAlt = Math.max(minAlt, maxAlt);
        this.range = range;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    public FlyWanderGoal onlyWhen(BooleanSupplier s) {
        this.allowed = s;
        return this;
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() == null && allowed.getAsBoolean() && mob.getNavigation().isDone() && mob.getRandom().nextInt(8) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() == null && allowed.getAsBoolean() && mob.getNavigation().isInProgress();
    }

    @Override
    public void start() {
        Vec3 p = pick();
        if (p != null) mob.getNavigation().moveTo(p.x, p.y, p.z, speed);
    }

    private Vec3 pick() {
        Level level = mob.level();
        RandomSource r = mob.getRandom();
        for (int i = 0; i < 12; i++) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(
                    mob.getBlockX() + r.nextInt(range * 2 + 1) - range,
                    mob.getBlockY() + r.nextInt(9) - 4,
                    mob.getBlockZ() + r.nextInt(range * 2 + 1) - range);
            if (!level.isEmptyBlock(m)) continue;
            int down = 0;
            while (down < 32 && m.getY() > level.getMinBuildHeight() && level.isEmptyBlock(m.below())) {
                m.move(Direction.DOWN);
                down++;
            }
            // Não pousa sobre a água
            if (!level.getFluidState(m.below()).isEmpty()) continue;
            int alt = minAlt + r.nextInt(maxAlt - minAlt + 1);
            int climbed = 0;
            while (climbed < alt && level.isEmptyBlock(m.above())) {
                m.move(Direction.UP);
                climbed++;
            }
            return Vec3.atCenterOf(m);
        }
        return null;
    }
}
