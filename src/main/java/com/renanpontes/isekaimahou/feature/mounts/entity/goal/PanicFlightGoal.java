package com.renanpontes.isekaimahou.feature.mounts.entity.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.mounts.entity.FlyingMount;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Criatura selvagem ferida (ou pegando fogo) foge VOANDO para longe e para cima do agressor. */
public class PanicFlightGoal<T extends Mob & FlyingMount> extends Goal {
    private final T mount;
    private final double speed;
    @Nullable
    private Vec3 target;
    private int ticks;

    public PanicFlightGoal(T mount, double speed) {
        this.mount = mount;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!mount.isWildMount() || mount.isVehicle() || mount.isLeashed()) return false;
        boolean hurt = mount.getLastHurtByMob() != null && mount.tickCount - mount.getLastHurtByMobTimestamp() < 60;
        if (!hurt && !mount.isOnFire()) return false;
        LivingEntity from = mount.getLastHurtByMob();
        Vec3 away = from != null ? mount.position().subtract(from.position()).multiply(1, 0, 1) : Vec3.ZERO;
        if (away.lengthSqr() < 1.0E-3) away = new Vec3(mount.getRandom().nextDouble() - 0.5, 0, mount.getRandom().nextDouble() - 0.5);
        Vec3 p = mount.position().add(away.normalize().scale(18.0)).add(0, 9 + mount.getRandom().nextInt(6), 0);
        if (p.y > mount.level().getMaxBuildHeight() - 6) p = new Vec3(p.x, mount.level().getMaxBuildHeight() - 6, p.z);
        target = p;
        return true;
    }

    @Override
    public void start() {
        ticks = 120;
        mount.setFlyingMode(true);
        mount.setDeltaMovement(mount.getDeltaMovement().add(0, 0.5, 0)); // salto de decolagem
    }

    @Override
    public boolean canContinueToUse() {
        return ticks > 0 && target != null && !mount.isVehicle() && mount.position().distanceToSqr(target) > 4.0;
    }

    @Override
    public void stop() {
        target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticks--;
        mount.requestFlight();
        if (target != null) mount.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed);
    }
}
