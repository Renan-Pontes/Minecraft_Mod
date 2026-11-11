package com.renanpontes.isekaimahou.feature.mounts.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Controle de voo das montarias selvagens: em vez do "zigue-zague a 45°" do FlyingMoveControl do vanilla,
 * puxa a velocidade suavemente na direção exata do alvo (desacelerando perto dele) e vira o corpo para lá.
 * Os objetivos de voo miram direto no ponto (sem caminho), então um raio curto à frente detecta encostas,
 * árvores e paredes e a criatura sobe por cima delas em vez de ficar empurrando o obstáculo.
 * Sem alvo, as que não pairam ({@code hovers = false}) voltam a ter gravidade e planam até o chão.
 */
public class MountFlyMoveControl extends MoveControl {
    /** Quantos blocos além da borda da caixa o raio "olha" à frente. */
    private static final double LOOK_AHEAD = 2.5;

    private final float maxTurn;
    private final boolean hovers;

    public MountFlyMoveControl(Mob mob, float maxTurn, boolean hovers) {
        super(mob);
        this.maxTurn = maxTurn;
        this.hovers = hovers;
    }

    @Override
    public void tick() {
        if (operation == Operation.MOVE_TO) {
            operation = Operation.WAIT;
            mob.setNoGravity(true);
            double dx = wantedX - mob.getX();
            double dy = wantedY - mob.getY();
            double dz = wantedZ - mob.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            mob.setZza(0.0F);
            mob.setYya(0.0F);
            if (dist < 0.3) {
                mob.setDeltaMovement(mob.getDeltaMovement().scale(0.6));
                return;
            }
            double speed = speedModifier * mob.getAttributeValue(Attributes.FLYING_SPEED);
            double s = Math.min(speed, dist * 0.3);
            Vec3 desired = new Vec3(dx, dy, dz).scale(s / dist);
            double h = Math.sqrt(dx * dx + dz * dz);
            if (h > 0.5 && (mob.horizontalCollision || blockedAhead(dx / h, dz / h, h))) {
                // Obstáculo no caminho: freia na horizontal e ganha altura até passar por cima
                desired = new Vec3(desired.x * 0.35, Math.max(desired.y, Math.max(0.25, speed * 0.7)), desired.z * 0.35);
            }
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(v.add(desired.subtract(v).scale(0.15)));
            if (h > 0.4) {
                float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
                mob.setYRot(rotlerp(mob.getYRot(), yaw, maxTurn));
                mob.yBodyRot = mob.getYRot();
            }
            float pitch = (float) -(Mth.atan2(dy, h) * Mth.RAD_TO_DEG);
            mob.setXRot(rotlerp(mob.getXRot(), Mth.clamp(pitch, -40F, 40F), 8.0F));
        } else {
            if (hovers) {
                Vec3 v = mob.getDeltaMovement();
                mob.setDeltaMovement(v.x * 0.85, v.y * 0.8, v.z * 0.85);
            } else {
                mob.setNoGravity(false);
            }
            mob.setZza(0.0F);
            mob.setYya(0.0F);
        }
    }

    /**
     * Há bloco sólido logo à frente, na altura do corpo, mais perto que o próprio destino?
     * (Se o destino está antes da parede — pousar ao lado do dono, morder o alvo — não desvia.)
     */
    private boolean blockedAhead(double nx, double nz, double distToTarget) {
        double reach = mob.getBbWidth() * 0.5 + LOOK_AHEAD;
        double len = Math.min(reach, distToTarget - 0.5);
        if (len <= 0.2) return false;
        Vec3 from = mob.position().add(0.0, mob.getBbHeight() * 0.5, 0.0);
        Vec3 to = from.add(nx * len, 0.0, nz * len);
        BlockHitResult hit = mob.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return hit.getType() != HitResult.Type.MISS;
    }
}
