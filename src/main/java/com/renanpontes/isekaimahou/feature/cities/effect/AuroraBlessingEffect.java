package com.renanpontes.isekaimahou.feature.cities.effect;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * "Bênção da Aurora" (serviço do sacerdote, 10 min): +4 de vida máxima, +2 de armadura, +1 de sorte e uma cura suave
 * de 1 ponto a cada 5 s, com um brilho dourado.
 */
public class AuroraBlessingEffect extends MobEffect {
    public AuroraBlessingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFE9A8);
        addAttributeModifier(Attributes.MAX_HEALTH, IsekaiMahou.id("aurora_blessing_health"), 4.0, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("aurora_blessing_armor"), 2.0, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.LUCK, IsekaiMahou.id("aurora_blessing_luck"), 1.0, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 100 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.getHealth() < entity.getMaxHealth()) entity.heal(1.0F + amplifier);
        if (entity.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                    4, 0.35, 0.5, 0.35, 0.01);
        }
        return true;
    }
}
