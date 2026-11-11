package com.renanpontes.isekaimahou.feature.creatures_e.entity;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Combate das criaturas das dimensoes: escolhe o golpe disponivel e cuida do movimento
 * (perseguir, manter distancia/recuar, ou circular o alvo no ar para as voadoras).
 */
public class CreatureCombatGoal extends Goal {
    private final DimensionalCreature mob;
    private int repath;
    private double orbit;
    private int orbitDir = 1;

    public CreatureCombatGoal(DimensionalCreature mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && mob.canAttack(t);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive() || !mob.canAttack(t)) return false;
        if (t instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return mob.distanceToSqr(t) < 48 * 48;
    }

    @Override
    public void start() {
        repath = 0;
        orbit = mob.getRandom().nextDouble() * Math.PI * 2;
        orbitDir = mob.getRandom().nextBoolean() ? 1 : -1;
        mob.setAggressive(true);
    }

    @Override
    public void stop() {
        mob.endMove();
        mob.setAggressive(false);
        mob.getNavigation().stop();
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
        double gap = mob.gapTo(t);
        boolean sees = mob.getSensing().hasLineOfSight(t);

        CreatureMove cur = mob.current();
        if (cur != null) {
            mob.tickMove(t);
            if (cur.roots && mob.current() == cur) {
                mob.getNavigation().stop();
            } else {
                move(t, gap, sees);
            }
            return;
        }

        if (mob.globalCooldown() <= 0) {
            for (CreatureMove m : mob.moves()) {
                if (!mob.ready(m) || gap < m.minRange || gap > m.maxRange) continue;
                if (m.needsSight && !sees) continue;
                if (!m.condition.test(t)) continue;
                mob.beginMove(m, t);
                if (m.roots) mob.getNavigation().stop();
                return;
            }
        }
        move(t, gap, sees);
    }

    private void move(LivingEntity t, double gap, boolean sees) {
        if (--repath > 0) return;
        double pref = mob.preferredRange();
        if (mob.flies()) {
            flyMove(t, gap, sees, pref);
            return;
        }
        repath = 6 + mob.getRandom().nextInt(6);
        if (gap > pref + 0.5 || !sees) {
            mob.getNavigation().moveTo(t, mob.chaseSpeed());
        } else if (pref > 2.5 && gap < pref - 2.5) {
            // perto demais para quem prefere distancia: recua
            Vec3 away = mob.position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(5);
            Vec3 dest = mob.position().add(away);
            mob.getNavigation().moveTo(dest.x, dest.y, dest.z, mob.chaseSpeed());
        } else {
            mob.getNavigation().stop();
        }
    }

    /** Voadoras: circulam o alvo na distancia preferida, pairando acima dele. */
    private void flyMove(LivingEntity t, double gap, boolean sees, double pref) {
        repath = 8 + mob.getRandom().nextInt(8);
        if (mob.getRandom().nextInt(6) == 0) orbitDir = -orbitDir;
        orbit += orbitDir * (0.5 + mob.getRandom().nextDouble() * 0.5);
        double dist = pref + (t.getBbWidth() + mob.getBbWidth()) * 0.5 + 0.3;
        if (!sees) dist = Math.min(dist, 3.0);
        Vec3 dest = new Vec3(t.getX() + Math.cos(orbit) * dist, t.getY() + mob.hoverHeight(), t.getZ() + Math.sin(orbit) * dist);
        // nao mira dentro de blocos: sobe ate achar ar
        BlockPos p = BlockPos.containing(dest);
        for (int i = 0; i < 4 && !mob.level().getBlockState(p).isAir(); i++) p = p.above();
        dest = new Vec3(dest.x, Math.max(dest.y, p.getY() + 0.2), dest.z);
        if (!mob.getNavigation().moveTo(dest.x, dest.y, dest.z, mob.chaseSpeed())) {
            mob.getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, mob.chaseSpeed());
        }
    }
}
