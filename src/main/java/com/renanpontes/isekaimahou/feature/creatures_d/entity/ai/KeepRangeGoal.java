package com.renanpontes.isekaimahou.feature.creatures_d.entity.ai;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Atiradores: mantêm o alvo entre {@code min} e {@code max} blocos, circulando em volta dele
 * (e {@code height} blocos acima, para os voadores). Usa a navegação, então desvia de obstáculos.
 */
public class KeepRangeGoal extends Goal {
    private final PathfinderMob mob;
    private final double speed;
    private final double min;
    private final double max;
    private final double height;
    private int repath;
    private int strafeDir = 1;

    public KeepRangeGoal(PathfinderMob mob, double speed, double min, double max, double height) {
        this.mob = mob;
        this.speed = speed;
        this.min = min;
        this.max = max;
        this.height = height;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive();
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        if (t == null) return;
        mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (--repath > 0) return;
        repath = 10 + mob.getRandom().nextInt(6);
        if (mob.getRandom().nextInt(5) == 0) strafeDir = -strafeDir;
        double d = mob.distanceTo(t);
        Vec3 away = mob.position().subtract(t.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
        away = away.normalize();
        double want;
        if (d < min) {
            want = min + 2;
        } else if (d > max) {
            want = max - 1;
        } else {
            // Já na faixa: anda de lado em volta do alvo
            double ang = Math.toRadians(30 * strafeDir);
            away = new Vec3(away.x * Math.cos(ang) - away.z * Math.sin(ang), 0, away.x * Math.sin(ang) + away.z * Math.cos(ang));
            want = d;
        }
        Vec3 dest = t.position().add(away.scale(want)).add(0, height, 0);
        mob.getNavigation().moveTo(dest.x, dest.y, dest.z, speed);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
