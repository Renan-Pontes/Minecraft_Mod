package com.renanpontes.isekaimahou.feature.mounts.client;

import com.renanpontes.isekaimahou.feature.mounts.entity.RideMount;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Contextos de tecla "montado" x "a pé" (v1.0) — consertam o conflito da tecla R.
 *
 * <p>A habilidade da montaria e o Instinto Hollow usam a mesma tecla (R) de propósito: uma só vale montado, a outra só
 * a pé. Antes as duas estavam no contexto "em jogo": a tela de Controles marcava as duas em vermelho (conflito) e o
 * Hollow perdia a técnica em QUALQUER veículo (barco, cavalo...). Com dois contextos que nunca valem ao mesmo tempo,
 * as duas convivem na mesma tecla sem conflito e cada uma só dispara na sua hora.
 */
public enum MountKeyContext implements IKeyConflictContext {
    /** Pilotando uma montaria do mod que tem habilidade. */
    RIDING {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive() && ridingAbilityMount();
        }
    },
    /** Em jogo, mas NÃO pilotando uma montaria com habilidade (teclas de técnica a pé). */
    ON_FOOT {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive() && !ridingAbilityMount();
        }
    };

    /** Conflita com o próprio contexto e com as teclas "em jogo" comuns; RIDING e ON_FOOT não conflitam entre si. */
    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return other == this || other == KeyConflictContext.IN_GAME;
    }

    /** O jogador local está pilotando uma montaria com habilidade na tecla do módulo? */
    public static boolean ridingAbilityMount() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && ridingAbilityMount(mc.player);
    }

    public static boolean ridingAbilityMount(Player player) {
        return player.getControlledVehicle() instanceof RideMount;
    }
}
