package com.renanpontes.isekaimahou.feature.earth_wind.effect;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Efeito de status simples do modulo (comportamento por atributos ou tratado nos eventos do modulo). */
public class EwMobEffect extends MobEffect {
    public EwMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    public EwMobEffect(MobEffectCategory category, int color, ParticleOptions particle) {
        super(category, color, particle);
    }
}
