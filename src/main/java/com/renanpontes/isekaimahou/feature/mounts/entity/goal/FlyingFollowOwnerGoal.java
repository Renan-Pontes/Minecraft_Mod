package com.renanpontes.isekaimahou.feature.mounts.entity.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.mounts.entity.AbstractSkyMount;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Segue o dono andando ou voando: se o dono estiver no ar (élitro, outra montaria) ou bem acima/longe,
 * levanta voo e vai direto até ele; perto e no chão, anda. Muito longe, teleporta (como os pets vanilla).
 * (O FollowOwnerGoal vanilla guarda a navegação no construtor e não funciona com a troca chão/ar.)
 */
public class FlyingFollowOwnerGoal extends Goal {
    /** Além desta distância tenta teleportar mesmo voando. */
    private static final double FAR = 40.0;

    private final AbstractSkyMount mount;
    private final double speed;
    private final float startDistance;
    private final float stopDistance;
    @Nullable
    private LivingEntity owner;
    private int recalc;
    private int teleportDelay;

    public FlyingFollowOwnerGoal(AbstractSkyMount mount, double speed, float startDistance, float stopDistance) {
        this.mount = mount;
        this.speed = speed;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity o = mount.getOwner();
        if (o == null || o.isSpectator() || mount.unableToMoveToOwner() || mount.isVehicle()) return false;
        if (mount.distanceToSqr(o) < startDistance * startDistance) return false;
        owner = o;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (owner == null || !owner.isAlive() || mount.isVehicle() || mount.unableToMoveToOwner()) return false;
        return mount.distanceToSqr(owner) > stopDistance * stopDistance;
    }

    @Override
    public void start() {
        recalc = 0;
        teleportDelay = 0;
    }

    @Override
    public void stop() {
        owner = null;
        mount.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (owner == null) return;
        mount.getLookControl().setLookAt(owner, 10F, mount.getMaxHeadXRot());
        if (mount.shouldTryTeleportToOwner() && !needsFlight()) {
            mount.tryToTeleportToOwner();
            return;
        }
        // Muito para trás (dono rápido demais, preso atrás de um morro): tenta aparecer ao lado do dono.
        // Só dá certo se houver chão livre perto dele; com o dono no ar ela continua voando atrás.
        if (mount.distanceToSqr(owner) > FAR * FAR && --teleportDelay <= 0) {
            teleportDelay = 20;
            mount.tryToTeleportToOwner();
            if (mount.distanceToSqr(owner) <= FAR * FAR) return;
        }
        if (needsFlight() || mount.isFlyingMode()) {
            mount.setFlyingMode(true);
            mount.requestFlight();
            Vec3 aim = owner.position().add(0, owner.onGround() ? 1.0 : 2.5, 0);
            mount.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, speed * 1.2);
            // Chegou perto de um dono no chão: deixa de pedir voo para planar e pousar ao lado
            if (owner.onGround() && mount.distanceToSqr(owner) < 36.0) mount.setFlyingMode(mount.alwaysFlying());
        } else if (--recalc <= 0) {
            recalc = adjustedTickDelay(10);
            mount.getNavigation().moveTo(owner, speed);
        }
    }

    private boolean needsFlight() {
        if (mount.alwaysFlying()) return true;
        if (owner == null) return false;
        return !owner.onGround() && !owner.isInWater() || owner.getY() - mount.getY() > 4.0 || mount.distanceToSqr(owner) > 24 * 24;
    }
}
