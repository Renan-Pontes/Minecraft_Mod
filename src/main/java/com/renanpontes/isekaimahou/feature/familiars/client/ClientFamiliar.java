package com.renanpontes.isekaimahou.feature.familiars.client;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.familiars.FamiliarEntity;
import com.renanpontes.isekaimahou.feature.familiars.FamiliarForm;
import com.renanpontes.isekaimahou.feature.familiars.FamiliarNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Cópia no cliente do estado do Familiar do jogador (chega pelo pacote {@link FamiliarNetwork.State}). */
final class ClientFamiliar {
    static FamiliarNetwork.State state = FamiliarNetwork.State.EMPTY;

    private ClientFamiliar() {}

    static boolean awakened() {
        return state.awakened();
    }

    static FamiliarForm form() {
        FamiliarForm f = FamiliarForm.byId(state.form());
        return f == null ? FamiliarForm.RUNE_WISP : f;
    }

    /** O corpo do meu familiar, se estiver carregado por perto (para o retrato da aba). */
    @Nullable
    static FamiliarEntity body() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof FamiliarEntity f && mc.player.getUUID().equals(f.getOwnerUUID())) return f;
        }
        return null;
    }
}
