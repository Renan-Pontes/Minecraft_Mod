package com.renanpontes.isekaimahou.client.anim;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.ux.MeditationPose;
import com.renanpontes.isekaimahou.core.anim.AnimConfig;
import com.renanpontes.isekaimahou.core.anim.AnimPayloads;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 *   <li><b>Investida</b> (v1.3): o primeiro golpe correndo vira uma estocada longa (no lugar do golpe do combo).</li>
 *   <li><b>Saque</b> (v1.3): trocar na barra para uma arma de classe (do mod) faz um floreio curto.</li>
 *   <li><b>Emotes</b> (v1.3): a tecla "Emotes" (sem tecla por padrão) abre a roda.</li>
 * </ul>
 */
final class CombatInput {
    static final KeyMapping DODGE_KEY = new KeyMapping("key.isekai_mahou.dodge", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CoreKeys.CATEGORY);

    private static final int COMBO_RESET = 20;
    /** Velocidade (blocos/tick) mantida durante o impulso da esquiva: ~6 blocos para os lados/frente, ~5,5 para trás. */
    private static final double SPEED_SIDE = 0.95, SPEED_BACK = 0.85;
    /** Ticks em que o impulso é mantido (o atrito do chão não "come" a esquiva). */
    private static final int DASH_TICKS = 5;
    private static int dashTicks;
    private static double dashX, dashZ;

    // ---- combo
    private static int combo;
    private static long lastSwing = Long.MIN_VALUE / 2;
    private static WeaponFamily lastFamily;
    // ---- esquiva
    private static final boolean[] WAS_DOWN = new boolean[4];
    private static final long[] LAST_TAP = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
    private static long nextDodge = Long.MIN_VALUE / 2;
    private static long lastHungryMsg = Long.MIN_VALUE / 2;
    // ---- investida / saque
    private static final int LUNGE_GAP = 20, DRAW_GAP = 30;
    private static long lastLunge = Long.MIN_VALUE / 2;
    private static long lastDraw = Long.MIN_VALUE / 2;
    @Nullable
    private static Item lastHeld;
    private static boolean heldKnown;

    static void reset() {
        combo = 0;
        lastSwing = Long.MIN_VALUE / 2;
        lastFamily = null;
        nextDodge = Long.MIN_VALUE / 2;
        dashTicks = 0;
        lastLunge = Long.MIN_VALUE / 2;
        lastDraw = Long.MIN_VALUE / 2;
        lastHeld = null;
        heldKnown = false;
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
        // Correndo no chão: o primeiro golpe vira investida (o combo recomeça depois dela)
        if (AnimConfig.FEEL_ANIMATIONS.get() && p.isSprinting() && p.onGround() && family != WeaponFamily.FIST
                && now - lastLunge >= LUNGE_GAP && MahouAnimsClient.play(p, MahouAnims.SPRINT_LUNGE)) {
            lastLunge = now;
            lastSwing = now;
            lastFamily = family;
            combo = 0;
            PacketDistributor.sendToServer(new AnimPayloads.Melee(MahouAnims.SPRINT_LUNGE));
            return;
        }
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
        if (dashTicks > 0) {
            dashTicks--;
            if (p.isInWater() || p.isPassenger() || p.isFallFlying() || p.horizontalCollision) dashTicks = 0;
            else {
                Vec3 dv = p.getDeltaMovement();
                p.setDeltaMovement(dashX, Math.min(dv.y, 0.1), dashZ);
                p.hasImpulse = true;
            }
        }
        while (DODGE_KEY.consumeClick()) {
            if (mc.screen == null) tryDodge(mc, p, inputDir(p), now);
        }
        while (EmoteWheelScreen.KEY.consumeClick()) {
            if (mc.screen == null && !p.isSpectator()) mc.setScreen(new EmoteWheelScreen());
        }
        weaponDraw(mc, p, now);
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

    // =====================================================================================
    //  Saque da arma
    // =====================================================================================
    /** Trocou (fora de telas) para uma arma do mod → floreio de saque; arma pesada/lança vai ao ombro. */
    private static void weaponDraw(Minecraft mc, LocalPlayer p, long now) {
        ItemStack stack = p.getMainHandItem();
        Item held = stack.isEmpty() ? null : stack.getItem();
        if (!heldKnown) {
            // Primeiro tick no mundo: só lembra o que já estava na mão
            heldKnown = true;
            lastHeld = held;
            return;
        }
        if (held == lastHeld) return;
        lastHeld = held;
        if (held == null || mc.screen != null || now - lastDraw < DRAW_GAP || !AnimConfig.WEAPON_DRAW.get()) return;
        if (!IsekaiMahou.MODID.equals(BuiltInRegistries.ITEM.getKey(held).getNamespace())) return;
        WeaponFamily family = WeaponFamily.of(stack);
        if (family == null || family == WeaponFamily.FIST) return;
        String id = family == WeaponFamily.HEAVY || family == WeaponFamily.SPEAR ? MahouAnims.DRAW_HEAVY : MahouAnims.DRAW_BLADE;
        if (!MahouAnimsClient.play(p, id)) return;
        lastDraw = now;
        PacketDistributor.sendToServer(new AnimPayloads.Melee(id));
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
        if (com.renanpontes.isekaimahou.client.ux.PeaceClient.isOn()) return; // v1.6: Modo Paz trava a esquiva
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
        dashX = dx * speed;
        dashZ = dz * speed;
        dashTicks = DASH_TICKS;
        nextDodge = now + MahouAnimsClient.serverDodgeCooldown;
        MahouAnimsClient.play(p, anim);
        PacketDistributor.sendToServer(new AnimPayloads.Dodge(dir));
    }

    private CombatInput() {}
}
