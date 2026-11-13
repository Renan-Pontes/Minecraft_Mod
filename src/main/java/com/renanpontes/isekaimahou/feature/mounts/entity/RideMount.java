package com.renanpontes.isekaimahou.feature.mounts.entity;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Contrato mínimo de TODA montaria do módulo (voadora ou de chão): dono, sela, chamado do apito e a
 * habilidade da tecla do módulo. O que é só de voo fica em {@link FlyingMount}.
 * Usado pelo pacote da tecla, pelo HUD, pelo apito e pelas dicas.
 */
public interface RideMount {
    /** A própria entidade. */
    Mob asMob();

    boolean isMountOwnedBy(Player player);

    boolean isMountSaddled();

    /** Selvagem (sem dono)? */
    boolean isWildMount();

    /** Apito de Montaria: vem até o jogador. */
    void answerCall(Player caller);

    /** Id da habilidade (tradução {@code ability.isekai_mahou.<id>}). */
    String abilityId();

    default Component abilityName() {
        return Component.translatable("ability." + IsekaiMahou.MODID + "." + abilityId());
    }

    /** Ticks até poder usar de novo (sincronizado). */
    int abilityCooldown();

    int abilityCooldownMax();

    /** Ticks restantes do efeito ativo (sincronizado). */
    int abilityActive();

    /** Servidor: o cavaleiro apertou a tecla da habilidade. */
    boolean tryAbility(ServerPlayer rider);

    /**
     * Como domar esta espécie (texto curto para a dica de primeira vez):
     * tradução {@code hint.isekai_mahou.mounts.tame.<id da entidade>}.
     */
    default Component tameHint() {
        return Component.translatable("hint.isekai_mahou.mounts.tame."
                + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(asMob().getType()).getPath());
    }
}
