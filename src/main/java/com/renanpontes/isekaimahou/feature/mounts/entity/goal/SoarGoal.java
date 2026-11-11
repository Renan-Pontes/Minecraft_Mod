package com.renanpontes.isekaimahou.feature.mounts.entity.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.mounts.entity.FlyingMount;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Montaria SELVAGEM levanta voo de vez em quando e circula no alto (12–24 blocos acima do chão)
 * por um tempo; quando o objetivo termina ela plana de volta e pousa sozinha.
 */
public class SoarGoal<T extends Mob & FlyingMount> extends Goal {
    private final T mount;
    private final int chance;
    private Vec3 center = Vec3.ZERO;
    @Nullable
    private Vec3 waypoint;
    private double radius;
    private float angle;
    private int ticksLeft;
    private int retarget;

    /** @param chance 1 em {@code chance} por tick de começar a voar. */
    public SoarGoal(T mount, int chance) {
        this.mount = mount;
        this.chance = chance;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mount.isWildMount() || mount.isVehicle() || mount.isLeashed() || mount.getTarget() != null) return false;
        if (mount.isBaby() || mount.caller() != null) return false;
        return mount.getRandom().nextInt(reducedTickDelay(chance)) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return ticksLeft > 0 && !mount.isVehicle() && mount.getTarget() == null && !mount.isLeashed() && mount.caller() == null;
    }

    @Override
    public void start() {
        ticksLeft = 240 + mount.getRandom().nextInt(300);
        radius = 8 + mount.getRandom().nextInt(10);
        angle = mount.getRandom().nextFloat() * 360F;
        int ground = mount.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mount.getBlockX(), mount.getBlockZ());
        double y = Math.max(ground, mount.getY()) + 12 + mount.getRandom().nextInt(12);
        y = Math.min(y, mount.level().getMaxBuildHeight() - 8);
        center = new Vec3(mount.getX() + mount.getRandom().nextInt(17) - 8, y, mount.getZ() + mount.getRandom().nextInt(17) - 8);
        mount.setFlyingMode(true);
        waypoint = null;
        retarget = 0;
    }

    @Override
    public void stop() {
        waypoint = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticksLeft--;
        mount.requestFlight();
        if (waypoint == null || --retarget <= 0 || mount.position().distanceToSqr(waypoint) < 9.0) nextWaypoint();
        if (waypoint != null) {
            mount.getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, 1.0);
            mount.getLookControl().setLookAt(waypoint.x, waypoint.y, waypoint.z, 10F, 30F);
        }
    }

    /** Próximo ponto do círculo (com pequena variação de altura); pula pontos dentro de blocos. */
    private void nextWaypoint() {
        retarget = 80;
        for (int i = 0; i < 6; i++) {
            angle += 40F + mount.getRandom().nextFloat() * 25F;
            double r = radius + mount.getRandom().nextGaussian() * 2.0;
            Vec3 p = center.add(Mth.cos(angle * Mth.DEG_TO_RAD) * r, mount.getRandom().nextGaussian() * 2.5,
                    Mth.sin(angle * Mth.DEG_TO_RAD) * r);
            if (mount.level().noCollision(mount, mount.getBoundingBox().move(p.subtract(mount.position())).inflate(0.5))) {
                waypoint = p;
                return;
            }
        }
        // Sem espaço livre no céu (dentro de montanhas, teto da dimensão): desiste e pousa
        ticksLeft = 0;
    }
}
