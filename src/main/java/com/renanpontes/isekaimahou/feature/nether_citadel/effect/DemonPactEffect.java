package com.renanpontes.isekaimahou.feature.nether_citadel.effect;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Pacto Demoníaco (Altar do Pacto): muito poder por um tempo — dano físico +35%, poder mágico +30%, golpes e passos mais
 * rápidos — pagando com 3 corações de vida máxima a menos e fome constante. Ao acabar vem a Dívida Infernal.
 */
public class DemonPactEffect extends MobEffect {
    public DemonPactEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB0122A);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("demon_pact_damage"), 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addAttributeModifier(CoreRegistries.SPELL_POWER, IsekaiMahou.id("demon_pact_spell"), 0.30, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("demon_pact_speed"), 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("demon_pact_move"), 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addAttributeModifier(Attributes.MAX_HEALTH, IsekaiMahou.id("demon_pact_health"), -6.0, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    /** A fome do pacto: ~1 ponto de comida a cada 20 s. */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player p) p.causeFoodExhaustion(0.2F);
        if (entity.getHealth() > entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
        return true;
    }
}
