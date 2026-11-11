package com.renanpontes.isekaimahou.feature.mounts.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Contrato comum das montarias voadoras do módulo (Grifo, Arraia do Céu, Pégaso, Wyvern).
 * O Pégaso estende {@code AbstractHorse} e as outras {@code TamableAnimal}; por isso a parte
 * compartilhada (voo, habilidade, chamado do apito, troca de navegação) é exposta por esta interface,
 * usada pelos objetivos de IA, pelo pacote da tecla de habilidade e pelo cliente (HUD/renderer).
 */
public interface FlyingMount {
    /** A própria entidade. */
    Mob asMob();

    /** Estado de voo (entradas do cavaleiro, inclinação visual, estado no ar). */
    MountFlight flight();

    // ---------------- Navegação: modo chão x modo ar ----------------

    /** Troca a navegação/controle de movimento entre chão e ar (só no servidor). */
    void setFlyingMode(boolean flying);

    boolean isFlyingMode();

    /** Objetivos que precisam continuar voando chamam isto a cada tick (senão a montaria plana e pousa). */
    void requestFlight();

    /** Selvagem (sem dono)? Os objetivos de passeio aéreo só valem para criaturas selvagens. */
    boolean isWildMount();

    // ---------------- Dono, sela, chamado ----------------

    boolean isMountOwnedBy(Player player);

    boolean isMountSaddled();

    /** Apito de Montaria: voa até o jogador. */
    void answerCall(Player caller);

    @Nullable
    UUID caller();

    void clearCall();

    // ---------------- Habilidade especial (tecla do módulo) ----------------

    /** Id da habilidade (tradução {@code ability.isekai_mahou.<id>}). */
    String abilityId();

    default Component abilityName() {
        return Component.translatable(abilityTranslationKey(abilityId()));
    }

    static String abilityTranslationKey(String id) {
        return "ability." + com.renanpontes.isekaimahou.IsekaiMahou.MODID + "." + id;
    }

    /** Ticks até poder usar de novo (sincronizado). */
    int abilityCooldown();

    int abilityCooldownMax();

    /** Ticks restantes do efeito ativo (sincronizado; o cliente usa para a física do impulso). */
    int abilityActive();

    /** Servidor: o cavaleiro apertou a tecla da habilidade. */
    boolean tryAbility(ServerPlayer rider);

    /** Multiplicador de velocidade de voo agora (habilidades de impulso). */
    default double flightSpeedMultiplier() {
        return 1.0;
    }

    /** A habilidade ativa força um mergulho para a frente, seguindo o olhar (Investida do Grifo)? */
    default boolean isDashing() {
        return false;
    }

    /** IA de ataque aéreo: devolve true se neste tick a criatura usou um ataque à distância (e deve pairar). */
    default boolean rangedAttackTick(LivingEntity target, double distSqr) {
        return false;
    }
}
