package com.renanpontes.isekaimahou.feature.mounts.entity.goal;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.mounts.entity.FlyingMount;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Apito de Montaria: a montaria chamada levanta voo e vai até o dono, pousando ao lado dele.
 * Desiste depois de 30 s ou se o dono sumir / mudar de dimensão.
 */
public class AnswerCallGoal<T extends Mob & FlyingMount> extends Goal {
    private final T mount;
    private final double speed;
    @Nullable
    private Player caller;
    private int ticks;

    public AnswerCallGoal(T mount, double speed) {
        this.mount = mount;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        UUID id = mount.caller();
        if (id == null || mount.isVehicle()) return false;
        if (mount.isLeashed()) { // presa na guia: não arrebenta a guia para atender
            mount.clearCall();
            return false;
        }
        caller = mount.level().getPlayerByUUID(id);
        if (caller == null || caller.isSpectator()) {
            mount.clearCall();
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return caller != null && caller.isAlive() && caller.level() == mount.level() && ticks > 0 && !mount.isVehicle()
                && !mount.isLeashed() && mount.caller() != null;
    }

    @Override
    public void start() {
        ticks = 600;
        mount.setFlyingMode(true);
    }

    @Override
    public void stop() {
        caller = null;
        mount.clearCall();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (caller == null) return;
        ticks--;
        mount.requestFlight();
        mount.getLookControl().setLookAt(caller, 20F, 30F);
        // Pousa um pouco à frente do jogador (não em cima dele)
        Vec3 front = caller.getLookAngle().multiply(1, 0, 1);
        if (front.lengthSqr() < 1.0E-4) front = new Vec3(0, 0, 1);
        Vec3 aim = caller.position().add(front.normalize().scale(2.5)).add(0, 0.5, 0);
        double h = Math.sqrt(mount.distanceToSqr(aim.x, mount.getY(), aim.z));
        if (h > 3.0) aim = aim.add(0, Math.min(6.0, h * 0.25), 0); // de longe chega por cima
        mount.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, speed);
        if (mount.distanceToSqr(aim) < 2.0) ticks = 0;
    }
}
