package com.renanpontes.isekaimahou.feature.equipment.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.onboard.ClientOnboarding;
import com.renanpontes.isekaimahou.core.Progression;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * O Curios abre o inventário de Acessórios com G por padrão, a mesma tecla da magia suprema do núcleo.
 * Abrir a tela solta todas as teclas e interrompe a suprema que estava sendo conjurada.
 * A correção só acontece quando as duas teclas estão de fato em conflito: a do Curios vai para uma tecla livre
 * (ou fica sem tecla, porque o botão no inventário continua funcionando). Se o jogador já mudou uma delas,
 * nada é alterado.
 */
final class CuriosKeyFix {
    private static final String CURIOS_KEY = "key.curios.open.desc";
    /** Teclas que o vanilla e o mod não usam, em ordem de preferência. */
    private static final int[] CANDIDATES = {
            GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M };
    private static boolean checked;

    static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (checked) return; // uma vez por sessão: depois disso a escolha do jogador manda
        checked = true;
        Minecraft mc = Minecraft.getInstance();
        KeyMapping ultimate = CoreKeys.SLOTS[Progression.ULTIMATE_SLOT];
        KeyMapping curios = find(mc.options.keyMappings);
        if (curios == null || curios.isUnbound() || ultimate.isUnbound() || !curios.same(ultimate)) return;

        curios.setKeyModifierAndCode(KeyModifier.NONE, freeKey(mc.options.keyMappings));
        KeyMapping.resetMapping();
        mc.options.save();
        // A troca vale já; o aviso espera a entrada no jogo (classe + tutorial) acabar
        ClientOnboarding.defer(Component.translatable("message.isekai_mahou.equipment.curios_key_moved",
                curios.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.WHITE),
                ultimate.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GOLD));
    }

    private static KeyMapping find(KeyMapping[] all) {
        for (KeyMapping k : all) {
            if (CURIOS_KEY.equals(k.getName())) return k;
        }
        return null;
    }

    private static InputConstants.Key freeKey(KeyMapping[] all) {
        for (int code : CANDIDATES) {
            InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(code);
            boolean used = false;
            for (KeyMapping k : all) {
                if (k.getKey().equals(key)) { used = true; break; }
            }
            if (!used) return key;
        }
        return InputConstants.UNKNOWN;
    }

    private CuriosKeyFix() {}
}
