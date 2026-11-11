package com.renanpontes.isekaimahou.feature.mounts.entity.goal;

import java.util.EnumSet;

import com.renanpontes.isekaimahou.feature.mounts.entity.FlyingMount;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Ataque em voo: a criatura sobe, circula acima do alvo e dá rasantes para morder/arranhar.
 * Criaturas com ataque à distância (sopro da Wyvern) podem pairar e usá-lo via
 * {@link FlyingMount#rangedAttackTick}.
 */
public class AerialAttackGoal<T extends Mob & FlyingMount> extends Goal {
    private final T mount;
    private final double speed;
    private final int attackInterval;
    private int attackCooldown;
    private int swoopTimer;

    public AerialAttackGoal(T mount, double speed, int attackInterval) {
        this.mount = mount;
        this.speed = speed;
        this.attackInterval = attackInterval;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mount.getTarget();
        if (t == null || !t.isAlive() || mount.isVehicle() || mount.isLeashed()) return false;
        return !(mount instanceof TamableAnimal ta && ta.isOrderedToSit());
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = mount.getTarget();
        return t != null && t.isAlive() && !mount.isVehicle() && mount.distanceToSqr(t) < 48 * 48
                && !(mount instanceof TamableAnimal ta && ta.isOrderedToSit());
    }

    @Override
    public void start() {
        mount.setFlyingMode(true);
        attackCooldown = 10;
        swoopTimer = 0;
    }

    @Override
    public void stop() {
        mount.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = mount.getTarget();
        if (t == null) return;
        mount.requestFlight();
        mount.getLookControl().setLookAt(t, 30F, 30F);
        if (attackCooldown > 0) attackCooldown--;
        double d2 = mount.distanceToSqr(t);
        if (mount.rangedAttackTick(t, d2)) return;

        // Alterna: circula no alto (4 blocos acima) e mergulha no alvo
        swoopTimer++;
        boolean swooping = swoopTimer % 70 > 30 || d2 < 9.0;
        Vec3 aim = swooping ? t.position().add(0, t.getBbHeight() * 0.5, 0)
                : t.position().add(Math.cos(swoopTimer * 0.1) * 5.0, 4.5, Math.sin(swoopTimer * 0.1) * 5.0);
        mount.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, swooping ? speed * 1.3 : speed);

        double reach = mount.getBbWidth() * 0.5 + t.getBbWidth() * 0.5 + 1.6;
        if (attackCooldown <= 0 && d2 <= reach * reach && mount.hasLineOfSight(t)) {
            attackCooldown = attackInterval;
            mount.doHurtTarget(t);
            swoopTimer = 0; // depois do golpe, volta a subir
        }
    }
}
