package com.renanpontes.isekaimahou.core.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Efeito de status simples (comportamento por modificadores de atributo ou checado em outros lugares). */
public class MagicEffect extends MobEffect {
    public MagicEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
