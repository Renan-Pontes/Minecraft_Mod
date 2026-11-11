package com.renanpontes.isekaimahou.feature.mounts.entity.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.mounts.entity.FlyingMount;
import com.renanpontes.isekaimahou.feature.mounts.entity.MountFlight;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Passeio lento pelo ar (Arraia do Céu): escolhe pontos próximos mantendo-se 3–12 blocos acima do chão.
 * Domada, fica baixa (1–3 blocos) e perto, ao alcance da mão para montar ou mandar esperar.
 * Sobre o vazio (ilhas flutuantes) mantém a altitude, sem cair nem subir sem parar, e nunca encosta
 * no teto do mundo.
 */
public class AirWanderGoal<T extends Mob & FlyingMount> extends Goal {
    private final T mount;
    private final double speed;
    @Nullable
    private Vec3 target;
    private int ticks;

    public AirWanderGoal(T mount, double speed) {
        this.mount = mount;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mount.isVehicle() || mount.isLeashed()) return false;
        if (mount instanceof TamableAnimal t && t.isOrderedToSit()) return false;
        if (mount.getRandom().nextInt(reducedTickDelay(40)) != 0) return false;
        target = pick();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && ticks > 0 && !mount.isVehicle() && mount.position().distanceToSqr(target) > 2.25;
    }

    @Override
    public void start() {
        ticks = 200;
        mount.setFlyingMode(true);
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

    @Nullable
    private Vec3 pick() {
        boolean tame = !mount.isWildMount();
        double range = tame ? 14.0 : 24.0;
        double minH = tame ? 1.0 : 3.0;
        double maxH = tame ? 3.0 : 12.0;
        double h = MountFlight.heightAboveGround(mount, 48.0);
        double top = mount.level().getMaxBuildHeight() - 16.0;
        for (int i = 0; i < 8; i++) {
            double dx = (mount.getRandom().nextDouble() - 0.5) * range;
            double dz = (mount.getRandom().nextDouble() - 0.5) * range;
            double dy = (mount.getRandom().nextDouble() - 0.5) * 4.0;
            if (h > 48.0) dy *= 0.5;                               // sobre o vazio: mantém a altitude
            else if (h < minH) dy = (minH - h) + Math.abs(dy) * 0.5;
            else if (h > maxH) dy = -Math.min(h - maxH, 3.0) - Math.abs(dy) * 0.5;
            Vec3 p = mount.position().add(dx, dy, dz);
            if (p.y < mount.level().getMinBuildHeight() + 12 || p.y > top) continue;
            if (mount.level().noCollision(mount, mount.getBoundingBox().move(dx, dy, dz).inflate(0.6))) return p;
        }
        return null;
    }
}
