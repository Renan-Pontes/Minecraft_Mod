package com.renanpontes.isekaimahou.feature.mounts.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.feature.mounts.MountsNetwork;
import com.renanpontes.isekaimahou.feature.mounts.entity.FlyingMount;
import com.renanpontes.isekaimahou.feature.mounts.entity.MountFlight;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Entradas do cavaleiro:
 * <ul>
 *   <li>Pulo = subir; Agachar no alto = descer. Enquanto o agachar é usado para descer, ele é "engolido"
 *       (não chega ao servidor), então ninguém desmonta no meio do voo. Pousou e soltou: agachar de novo
 *       desmonta normalmente. Perto do chão (menos de ~1,4 bloco), agachar desmonta.</li>
 *   <li>Tecla do módulo (padrão R) = habilidade da montaria.</li>
 * </ul>
 */
public final class MountInput {
    public static final KeyMapping ABILITY = new KeyMapping("key.isekai_mahou.mount_ability", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CoreKeys.CATEGORY);

    /** O agachar atual começou no alto: continua valendo como "descer" até ser solto. */
    private static boolean sneakLatched;

    static void onMovementInput(MovementInputUpdateEvent e) {
        if (!(e.getEntity() instanceof LocalPlayer player)) return;
        Input in = e.getInput();
        if (!(player.getControlledVehicle() instanceof FlyingMount mount)) {
            sneakLatched = false;
            return;
        }
        boolean high = MountFlight.heightAboveGround(mount.asMob(), 2.0) > 1.4;
        if (!in.shiftKeyDown) sneakLatched = false;
        else if (high) sneakLatched = true;
        boolean descend = in.shiftKeyDown && sneakLatched;
        mount.flight().setInput(in.jumping, descend, player.level().getGameTime());
        if (descend) in.shiftKeyDown = false;
    }

    static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        while (ABILITY.consumeClick()) {
            if (mc.player == null || !(mc.player.getControlledVehicle() instanceof FlyingMount mount)) continue;
            int cd = mount.abilityCooldown();
            if (cd > 0) {
                mc.player.displayClientMessage(Component.translatable("message.isekai_mahou.mount_ability_cooldown",
                        mount.abilityName(), String.format("%.1f", cd / 20.0F)), true);
            } else {
                PacketDistributor.sendToServer(new MountsNetwork.MountAbility());
            }
        }
    }

    private MountInput() {}
}
