package com.renanpontes.isekaimahou.feature.legends_i.client;

import com.renanpontes.isekaimahou.feature.legends_i.LiRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Asas de Papel (Konan): o movimento do jogador é do CLIENTE, então o planeio é feito aqui. No ar e caindo, a queda
 * fica lenta (máx. 0,12 bloco/tick) e o jogador desliza para onde olha (até ~0,5 bloco/tick). Agachar fecha as asas
 * (cai normal). Na água, voando no criativo ou de élitro, nada muda. O servidor zera o dano de queda enquanto o efeito durar.
 */
final class PaperGlide {
    private static final double MAX_FALL = -0.12;
    private static final double GLIDE_SPEED = 0.5;

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || !p.hasEffect(LiRegistry.PAPER_WINGS)) return;
        if (p.onGround() || p.isInWater() || p.isInLava() || p.getAbilities().flying || p.isFallFlying() || p.isPassenger()
                || p.isShiftKeyDown()) return;
        Vec3 v = p.getDeltaMovement();
        if (v.y >= -0.05) return;
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        double hx = v.x, hz = v.z;
        if (flat.lengthSqr() > 1.0E-4) {
            flat = flat.normalize();
            hx += flat.x * 0.045;
            hz += flat.z * 0.045;
            double h = Math.sqrt(hx * hx + hz * hz);
            if (h > GLIDE_SPEED) {
                hx *= GLIDE_SPEED / h;
                hz *= GLIDE_SPEED / h;
            }
        }
        p.setDeltaMovement(hx, Math.max(v.y, MAX_FALL), hz);
        p.fallDistance = 0F;
    }

    private PaperGlide() {}
}
