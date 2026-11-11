package com.renanpontes.isekaimahou.feature.cities.effect;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/**
 * "Procurado": marca de crime contra o Reino. Os guardas atacam quem tem este efeito. Não tem efeito no corpo — só
 * some com o tempo, pagando a fiança a um guarda ou "limpando a ficha" com o receptador (leite e totens não limpam).
 */
public class WantedEffect extends MobEffect {
    public WantedEffect() {
        super(MobEffectCategory.HARMFUL, 0xC0302A);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        // Sem curas: crime não sai com leite.
    }
}
