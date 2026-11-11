package com.renanpontes.isekaimahou.feature.hueco_mundo.entity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Base dos voadores (Vazio Alado, Fada Travessa): sem gravidade, circula o alvo a uma altura e distância fixas e passeia
 * perto de casa sem alvo. Movimento pelo {@link FlyingMoveControl} (barato: um ponto desejado por vez).
 */
public abstract class HmFlyer extends HmMob {
    @Nullable
    private BlockPos home;
    private int wanderCd;
    protected float orbitAngle;

    protected HmFlyer(EntityType<? extends HmFlyer> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Altura acima do alvo e raio da órbita. */
    protected abstract double orbitHeight();

    protected abstract double orbitRadius();

    protected double flySpeed() {
        return 1.0;
    }

    @Override
    protected String baseAnimation(boolean moving) {
        return "fly";
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (home == null) home = blockPosition();
        LivingEntity t = liveTarget();
        if (t != null) {
            orbitAngle += 0.05F;
            Vec3 want = t.position().add(Math.cos(orbitAngle) * orbitRadius(), orbitHeight(), Math.sin(orbitAngle) * orbitRadius());
            moveToward(want, flySpeed());
            getLookControl().setLookAt(t, 30F, 30F);
            combatTick(t);
        } else if (--wanderCd <= 0) {
            wanderCd = 60 + random.nextInt(60);
            int x = home.getX() + random.nextInt(25) - 12, z = home.getZ() + random.nextInt(25) - 12;
            int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            moveToward(new Vec3(x + 0.5, ground + 4 + random.nextInt(5), z + 0.5), flySpeed() * 0.6);
        }
    }

    protected void moveToward(Vec3 p, double speed) {
        getMoveControl().setWantedPosition(p.x, p.y, p.z, speed);
    }

    /** Ataques (chamado todo tick com alvo). */
    protected abstract void combatTick(LivingEntity target);

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {}
}
