package com.renanpontes.isekaimahou.feature.class_gambler.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Efeitos do Apostador (Jackpot, Sortudo, Azarado). O comportamento fica nos serviços que olham o efeito
 * ({@code LuckService}, {@code GamblerPassives}, {@code JackpotService}); aqui só o ícone, a cor e os atributos.
 */
public class GamblerEffect extends MobEffect {
    public GamblerEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
