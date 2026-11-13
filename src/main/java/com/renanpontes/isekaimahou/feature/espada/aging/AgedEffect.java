package com.renanpontes.isekaimahou.feature.espada.aging;

import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.EffectCure;

/**
 * "Envelhecido" ({@code isekai_mahou:aged}) — o aspecto da Velhice de Barragan. Cada acúmulo (amplificador + 1, até
 * {@link Aging#MAX_STACKS}) deixa o corpo mais velho: mais lento, mais fraco, golpes e mineração mais lentos e vida
 * máxima menor (modificadores temporários de atributo, somem com o efeito). Jogadores sentem fome mais rápido e a
 * armadura apodrece aos poucos. Leite NÃO cura: some devagar (um acúmulo de cada vez) ou na hora com a
 * Restauração de Reishi.
 */
public class AgedEffect extends MobEffect {
    public AgedEffect() {
        super(MobEffectCategory.HARMFUL, 0xA89068);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("aged_speed"), -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("aged_damage"), -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("aged_attack_speed"), -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, IsekaiMahou.id("aged_mining"), -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.MAX_HEALTH, IsekaiMahou.id("aged_health"), -1.5, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("aged_jump"), -0.03, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 40 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        int stacks = amplifier + 1;
        // Vida máxima menor: a vida atual não pode ficar acima dela.
        if (entity.getHealth() > entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
        if (entity instanceof Player p && !p.getAbilities().invulnerable) {
            p.causeFoodExhaustion(0.5F * stacks);
            // A armadura apodrece com o tempo (a cada ~6 s, mais forte com mais acúmulos).
            if (stacks >= 3 && entity.tickCount % 120 < 40) Aging.rotArmor(p, Math.max(1, stacks / 3));
        }
        return true;
    }

    /** Sem curas padrão: leite e mel não tiram a velhice. */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
    }
}
