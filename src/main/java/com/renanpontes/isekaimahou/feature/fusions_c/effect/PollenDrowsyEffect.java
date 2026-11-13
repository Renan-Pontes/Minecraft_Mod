package com.renanpontes.isekaimahou.feature.fusions_c.effect;

import com.renanpontes.isekaimahou.IsekaiMahou;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Sonolência (Tempestade de Pólen): −15% de velocidade e −10% de velocidade de ataque por nível; pálpebras pesadas
 * (partículas de pólen sobre a cabeça). No nível III a vítima adormece (regra no PollenCloudEntity).
 */
public class PollenDrowsyEffect extends MobEffect {
    public PollenDrowsyEffect() {
        super(MobEffectCategory.HARMFUL, 0xFFE066);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.pollen_drowsy"), -0.15,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.pollen_drowsy"), -0.10,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (e.level() instanceof ServerLevel sl) {
            sl.sendParticles(Vfx.sporeFall(), e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(),
                    1 + amplifier, 0.25, 0.05, 0.25, 0);
        }
        return true;
    }
}
