package com.renanpontes.isekaimahou.feature.ascension;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

/**
 * Regra de jogo {@code isekaiPrivateAltars} (padrão DESLIGADA). v1.0: a Ascensão acontece nos Altares Sagrados
 * prontos (Igrejas e Academias das cidades, Santuários de Ascensão). Um servidor que queira os altares caseiros da
 * v0.7–v0.9 (Núcleo + pedestais + Pedras Rúnicas montados pelo jogador) liga com
 * {@code /gamerule isekaiPrivateAltars true}. Operadores também consagram um Núcleo com {@code /mahou ascension public}.
 */
public final class AscensionRules {
    public static final String RULE_NAME = "isekaiPrivateAltars";

    @Nullable
    private static volatile GameRules.Key<GameRules.BooleanValue> rule;

    /** Registra a regra UMA vez (setup comum, na thread principal — o mapa de regras não é seguro em paralelo). */
    static synchronized void register() {
        if (rule == null) rule = GameRules.register(RULE_NAME, GameRules.Category.MISC, GameRules.BooleanValue.create(false));
    }

    /** Altares caseiros (não consagrados) podem fazer a Ascensão neste mundo? */
    public static boolean privateAltars(ServerLevel level) {
        GameRules.Key<GameRules.BooleanValue> k = rule;
        return k != null && level.getGameRules().getBoolean(k);
    }

    private AscensionRules() {}
}
