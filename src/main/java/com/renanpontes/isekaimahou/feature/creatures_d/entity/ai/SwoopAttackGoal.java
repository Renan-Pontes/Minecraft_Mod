package com.renanpontes.isekaimahou.feature.creatures_d.entity.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Ataque de mergulho dos voadores: vai direto no alvo (sem caminho), golpeia ao encostar e depois
 * recua para o alto por alguns ticks antes do próximo mergulho — dá ao jogador uma janela para revidar.
 */
public class SwoopAttackGoal extends Goal {
    private final Mob mob;
    private final double speed;
    private final int retreatTicks;
    private int retreat;
    private int attackCooldown;
    private Vec3 retreatPos = Vec3.ZERO;

    public SwoopAttackGoal(Mob mob, double speed, int retreatTicks) {
        this.mob = mob;
        this.speed = speed;
        this.retreatTicks = retreatTicks;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && mob.distanceToSqr(t) < 48 * 48;
    }

    @Override
    public void stop() {
        retreat = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        if (t == null) return;
        mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (attackCooldown > 0) attackCooldown--;
        if (retreat > 0) {
            retreat--;
            mob.getMoveControl().setWantedPosition(retreatPos.x, retreatPos.y, retreatPos.z, speed);
            return;
        }
        Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0);
        mob.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, speed * 1.2);
        if (attackCooldown <= 0 && mob.getBoundingBox().inflate(0.45).intersects(t.getBoundingBox())) {
            mob.doHurtTarget(t);
            attackCooldown = 20;
            retreat = retreatTicks;
            Vec3 away = mob.position().subtract(t.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(mob.getRandom().nextGaussian(), 0, mob.getRandom().nextGaussian());
            Vec3 dest = mob.position().add(away.normalize().scale(6)).add(0, 4, 0);
            // Recuo para dentro de um bloco (cavernas): só sobe
            retreatPos = mob.level().isEmptyBlock(BlockPos.containing(dest)) ? dest : mob.position().add(0, 3, 0);
        }
    }
}
