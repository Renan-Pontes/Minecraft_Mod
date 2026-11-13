package com.renanpontes.isekaimahou.feature.legends_i.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Efeitos de status dos glifos de anime do legends_i (Susanoo, Asas de Papel, Haki da Observação). Só carregam cor e
 * modificadores de atributo; as regras ficam nos ouvintes de {@code LiEvents}.
 */
public class LiMobEffect extends MobEffect {
    public LiMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
