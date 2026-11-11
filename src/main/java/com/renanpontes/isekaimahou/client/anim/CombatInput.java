package com.renanpontes.isekaimahou.client.anim;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.ux.MeditationPose;
import com.renanpontes.isekaimahou.core.anim.AnimConfig;
import com.renanpontes.isekaimahou.core.anim.AnimPayloads;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Entrada do combate no cliente:
 * <ul>
 *   <li><b>Combo</b>: cada clique de ataque com arma toca o próximo golpe do combo da família da arma
 *       ({@link WeaponFamily}); o combo volta ao 1º golpe depois de ~1 s sem atacar. Toca na hora e avisa o servidor.</li>
 *   <li><b>Esquiva</b>: toque duplo em Trás/Esquerda/Direita (Frente duas vezes é correr no Minecraft) ou a tecla
 *       "Esquivar" (sem tecla por padrão) + direção. Dá um impulso curto no chão; o servidor dá a invulnerabilidade.</li>
 * </ul>
 */
final class CombatInput {
    static final KeyMapping DODGE_KEY = new KeyMapping("key.isekai_mahou.dodge", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CoreKeys.CATEGORY);

    private static final int COMBO_RESET = 20;
    private static final double SPEED_SIDE = 1.2, SPEED_BACK = 1.0;

    // ---- combo
    private static int combo;
    private static long lastSwing = Long.MIN_VALUE / 2;
    private static WeaponFamily lastFamily;
    // ---- esquiva
    private static final boolean[] WAS_DOWN = new boolean[4];
    private static final long[] LAST_TAP = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
    private static long nextDodge = Long.MIN_VALUE / 2;
    private static long lastHungryMsg = Long.MIN_VALUE / 2;

    static void reset() {
        combo = 0;
        lastSwing = Long.MIN_VALUE / 2;
        lastFamily = null;
        nextDodge = Long.MIN_VALUE / 2;
        for (int i = 0; i < 4; i++) {
            WAS_DOWN[i] = false;
            LAST_TAP[i] = Long.MIN_VALUE / 2;
        }
    }

    // =====================================================================================
    //  Combo
    // =====================================================================================
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!AnimConfig.COMBAT_ANIMATIONS.get()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p.isSpectator()) return;
        WeaponFamily family = WeaponFamily.of(p.getMainHandItem());
        if (family == null) return;
        HitResult hit = mc.hitResult;
        // Socar bloco é minerar (madeira, terra...): sem combo. Com arma, o golpe anima mesmo acertando grama alta.
        if (family == WeaponFamily.FIST && hit != null && hit.getType() == HitResult.Type.BLOCK) return;
        long now = mc.level.getGameTime();
        if (now - lastSwing < family.minGap) return;
        if (now - lastSwing > COMBO_RESET || family != lastFamily) combo = 0;
        String id = family.combo[combo % family.combo.length];
        if (!MahouAnimsClient.play(p, id)) return;
        combo++;
        lastSwing = now;
        lastFamily = family;
        PacketDistributor.sendToServer(new AnimPayloads.Melee(id));
    }

    // =====================================================================================
    //  Esquiva
    // =====================================================================================
    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        long now = mc.level.getGameTime();
        while (DODGE_KEY.consumeClick()) {
            if (mc.screen == null) tryDodge(mc, p, inputDir(p), now);
        }
        if (mc.screen != null || !AnimConfig.DOUBLE_TAP_DODGE.get()) {
            for (int i = 0; i < 4; i++) WAS_DOWN[i] = false;
            return;
        }
        int window = AnimConfig.DOUBLE_TAP_WINDOW.get();
        // 0 = frente (só pela tecla), 1 = trás, 2 = esquerda, 3 = direita
        for (int i = 1; i < 4; i++) {
            KeyMapping key = i == 1 ? mc.options.keyDown : i == 2 ? mc.options.keyLeft : mc.options.keyRight;
            boolean down = key.isDown();
            if (down && !WAS_DOWN[i]) {
                if (now - LAST_TAP[i] <= window) {
                    LAST_TAP[i] = Long.MIN_VALUE / 2;
                    tryDodge(mc, p, (byte) i, now);
                } else {
                    LAST_TAP[i] = now;
                }
            }
            WAS_DOWN[i] = down;
        }
    }

    /** Direção pelo que o jogador está apertando; parado = para trás (passo de recuo). */
    private static byte inputDir(LocalPlayer p) {
        float left = p.input.leftImpulse, fwd = p.input.forwardImpulse;
        if (Math.abs(left) > Math.abs(fwd)) return left > 0 ? MahouAnims.DIR_LEFT : MahouAnims.DIR_RIGHT;
        if (fwd > 0) return MahouAnims.DIR_FORWARD;
        return MahouAnims.DIR_BACK;
    }

    private static void tryDodge(Minecraft mc, LocalPlayer p, byte dir, long now) {
        if (!MahouAnimsClient.serverDodge || now < nextDodge) return;
        if (p.isSpectator() || p.isPassenger() || p.isSleeping() || p.isFallFlying() || p.getAbilities().flying
                || p.isInWater() || !p.onGround() || p.isDeadOrDying() || MeditationPose.isMeditating(p)) {
            return;
        }
        if (!p.getAbilities().instabuild && p.getFoodData().getFoodLevel() <= 6) {
            if (now - lastHungryMsg > 40) {
                lastHungryMsg = now;
                p.displayClientMessage(Component.translatable("message.isekai_mahou.anim.dodge_hungry"), true);
            }
            return;
        }
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);   // frente
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);  // direita
        double dx, dz, speed;
        String anim;
        switch (dir) {
            case MahouAnims.DIR_FORWARD -> { dx = fx; dz = fz; speed = SPEED_SIDE; anim = MahouAnims.DODGE_F; }
            case MahouAnims.DIR_LEFT -> { dx = -rx; dz = -rz; speed = SPEED_SIDE; anim = MahouAnims.DODGE_L; }
            case MahouAnims.DIR_RIGHT -> { dx = rx; dz = rz; speed = SPEED_SIDE; anim = MahouAnims.DODGE_R; }
            default -> { dx = -fx; dz = -fz; speed = SPEED_BACK; anim = MahouAnims.DODGE_B; }
        }
        Vec3 v = p.getDeltaMovement();
        // Só horizontal (no chão o atrito freia em ~2-3 blocos); mantém o vertical
        p.setDeltaMovement(dx * speed, v.y, dz * speed);
        p.hasImpulse = true;
        nextDodge = now + MahouAnimsClient.serverDodgeCooldown;
        MahouAnimsClient.play(p, anim);
        PacketDistributor.sendToServer(new AnimPayloads.Dodge(dir));
    }

    private CombatInput() {}
}
