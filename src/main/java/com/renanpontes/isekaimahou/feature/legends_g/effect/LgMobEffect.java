package com.renanpontes.isekaimahou.feature.legends_g.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Efeitos de status das lendas do legends_g (Magia sem Encantamento, Forma Negra). Só carregam cor e modificadores de
 * atributo; as regras ficam nos ouvintes de {@code LgEvents}.
 */
public class LgMobEffect extends MobEffect {
    public LgMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
