package com.renanpontes.isekaimahou.feature.creatures_a.entity.slime;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Controle de movimento "aos pulinhos" (cópia pública do controle do slime vanilla, que é privado do pacote):
 * o slime gira para a direção escolhida e só anda pulando, com intervalo dado pela variedade.
 */
public class SlimeHopControl extends MoveControl {
    private final MahouSlime slime;
    private float yRot;
    private int jumpDelay;
    private boolean aggressive;

    public SlimeHopControl(MahouSlime slime) {
        super(slime);
        this.slime = slime;
        this.yRot = 180.0F * slime.getYRot() / (float) Math.PI;
    }

    public void setDirection(float yRot, boolean aggressive) {
        this.yRot = yRot;
        this.aggressive = aggressive;
    }

    public void setWantedMovement(double speed) {
        this.speedModifier = speed;
        this.operation = Operation.MOVE_TO;
    }

    @Override
    public void tick() {
        mob.setYRot(rotlerp(mob.getYRot(), yRot, 90.0F));
        mob.yHeadRot = mob.getYRot();
        mob.yBodyRot = mob.getYRot();
        if (operation != Operation.MOVE_TO) {
            mob.setZza(0.0F);
            return;
        }
        operation = Operation.WAIT;
        float speed = (float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        if (mob.onGround()) {
            mob.setSpeed(speed);
            if (jumpDelay-- <= 0) {
                jumpDelay = slime.hopDelay();
                if (aggressive) jumpDelay /= 3;
                slime.getJumpControl().jump();
                slime.playHopSound();
            } else {
                slime.xxa = 0.0F;
                slime.zza = 0.0F;
                mob.setSpeed(0.0F);
            }
        } else {
            mob.setSpeed(speed);
        }
    }
}
