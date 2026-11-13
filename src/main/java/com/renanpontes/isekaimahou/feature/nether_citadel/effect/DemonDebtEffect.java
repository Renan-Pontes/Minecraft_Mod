package com.renanpontes.isekaimahou.feature.nether_citadel.effect;

import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.EffectCure;

/**
 * Dívida Infernal: a cobrança do pacto. Mais fraco, mana volta devagar, fome; enquanto durar, o altar recusa novos pactos.
 * Leite não cura (dívida com demônio não se lava).
 */
public class DemonDebtEffect extends MobEffect {
    public DemonDebtEffect() {
        super(MobEffectCategory.HARMFUL, 0x3A0A12);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("demon_debt_damage"), -0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addAttributeModifier(CoreRegistries.MANA_REGEN, IsekaiMahou.id("demon_debt_mana"), -0.5, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 60 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player p) p.causeFoodExhaustion(1.5F);
        return true;
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
        cures.clear();
    }
}
