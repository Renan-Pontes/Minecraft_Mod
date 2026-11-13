package com.renanpontes.isekaimahou.feature.cities.effect;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * "Bem Descansado" (estalagem, release 1.0): dormiu numa cama de verdade. Bônus pequeno e passageiro — +15% de
 * regeneração de mana e 1 ponto de vida a cada 10 s. Não dá poder: só recompensa passar pela cidade antes de sair.
 */
public class WellRestedEffect extends MobEffect {
    public WellRestedEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8FD0FF);
        addAttributeModifier(CoreRegistries.MANA_REGEN, IsekaiMahou.id("effect.well_rested"), 0.15,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 200 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.getHealth() < entity.getMaxHealth()) entity.heal(1.0F);
        return true;
    }
}
