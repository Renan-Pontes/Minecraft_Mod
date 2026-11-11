package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import software.bernie.geckolib.animation.AnimationState;

/**
 * Base dos elementais aquáticos: nadam como golfinhos (movimento suave, navegação só na água),
 * se debatem e secam fora d'água (a chuva os mantém vivos). Loop "swim" em movimento, "idle" parado.
 */
public abstract class SwimmingElemental extends ElementalCreature {
    protected SwimmingElemental(EntityType<? extends SwimmingElemental> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    protected String loopAnimation(boolean moving) {
        return moving ? "swim" : "idle";
    }

    @Override
    protected boolean isAnimMoving(AnimationState<ElementalCreature> state) {
        return isInWater() && getDeltaMovement().lengthSqr() > 2.5E-4;
    }

    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance() && isInWater()) {
            moveRelative(getSpeed(), input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
            if (getTarget() == null) setDeltaMovement(getDeltaMovement().add(0.0, -0.005, 0.0));
            calculateEntityAnimation(true);
        } else {
            super.travel(input);
        }
    }

    /** Dano por segundo fora da água (sem chuva). */
    protected float dryDamage() {
        return 1.0F;
    }

    @Override
    public void aiStep() {
        // Fora d'água: se debate tentando voltar (como os peixes)
        if (!isInWater() && onGround() && verticalCollision) {
            setDeltaMovement(getDeltaMovement().add((random.nextFloat() * 2 - 1) * 0.05, 0.4, (random.nextFloat() * 2 - 1) * 0.05));
            setOnGround(false);
            hasImpulse = true;
        }
        super.aiStep();
        if (!level().isClientSide && isAlive() && !isInWaterRainOrBubble() && tickCount % 20 == 0) {
            hurt(damageSources().dryOut(), dryDamage());
        }
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** Nadadores não são arrastados pela correnteza. */
    @Override
    public boolean isPushedByFluid(FluidType type) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
