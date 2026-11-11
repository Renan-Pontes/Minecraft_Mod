package com.renanpontes.isekaimahou.glyph;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Versões de uma magia no mesmo slot, escolhidas pelo movimento do jogador ao apertar a tecla.
 * Cada gatilho (menos NORMAL) precisa do glifo de gatilho correspondente aprendido
 * (ex.: isekai_mahou:trigger_sneak), liberado no nó "Fluidez" dos caminhos.
 */
public enum MoveTrigger {
    NORMAL("normal"),
    SNEAK("sneak"),
    SPRINT("sprint"),
    AIR("air"),
    WATER("water");

    public final String id;

    MoveTrigger(String id) {
        this.id = id;
    }

    /** Glifo que libera este gatilho (null para NORMAL). */
    public ResourceLocation glyphId() {
        return this == NORMAL ? null : IsekaiMahou.id("trigger_" + id);
    }

    public MutableComponent displayName() {
        return Component.translatable("trigger.isekai_mahou." + id);
    }

    /** O que o jogador está fazendo agora (prioridade: água > ar > correndo > agachado > normal). */
    public static MoveTrigger fromPlayer(Player p) {
        if (p.isInWater()) return WATER;
        if (!p.onGround() && !p.getAbilities().flying && !p.isPassenger()) return AIR;
        if (p.isSprinting()) return SPRINT;
        if (p.isShiftKeyDown()) return SNEAK;
        return NORMAL;
    }

    public static MoveTrigger byIndex(int i) {
        MoveTrigger[] v = values();
        return i >= 0 && i < v.length ? v[i] : NORMAL;
    }
}
