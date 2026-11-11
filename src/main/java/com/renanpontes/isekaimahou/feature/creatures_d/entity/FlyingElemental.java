package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;

/**
 * Base dos elementais voadores: voo suave sem gravidade (como o allay), navegação aérea,
 * sem dano de queda. A animação em loop é "fly" em movimento e "idle" (pairando) parado.
 */
public abstract class FlyingElemental extends ElementalCreature {
    /**
     * Aceleração por tick = velocidade pedida pela IA (FLYING_SPEED × modificador) × este fator.
     * O allay usa getSpeed() direto porque o FLYING_SPEED dele é 0.1; aqui os valores vão de 0.3 a 0.8
     * e, sem a escala, a velocidade terminal passava de vários blocos por tick (o mob "teleportava").
     * Com 0.045 e o atrito de 0.91: passeio ~5–8 blocos/s, mergulhos ~9–15 blocos/s.
     */
    private static final float FLIGHT_ACCEL = 0.045F;

    protected FlyingElemental(EntityType<? extends FlyingElemental> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected String loopAnimation(boolean moving) {
        return moving ? "fly" : "idle";
    }

    @Override
    protected boolean isAnimMoving(AnimationState<ElementalCreature> state) {
        return getDeltaMovement().lengthSqr() > 4.0E-4;
    }

    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance()) {
            if (isInWater()) {
                moveRelative(0.02F, input);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.8));
            } else {
                // Só a direção vem do controle de voo; a intensidade é linear na velocidade pedida
                Vec3 dir = input.lengthSqr() > 1.0E-7 ? input.normalize() : Vec3.ZERO;
                moveRelative(getSpeed() * FLIGHT_ACCEL, dir);
                // Só cai quando a gravidade é religada (ex.: Sentinela Solar dormindo)
                setDeltaMovement(getDeltaMovement().add(0.0, -getGravity(), 0.0));
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.91));
            }
        }
        calculateEntityAnimation(true);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean onClimbable() {
        return false;
    }
}
