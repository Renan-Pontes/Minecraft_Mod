package com.renanpontes.isekaimahou.feature.potions.effect;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.potions.PotionKind;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Fluxo Vital (+nível de vida por segundo) e Fluxo Arcano (+nível% do recurso da classe por segundo).
 * Aplica a cada 10 ticks metade do valor por segundo — proporcional à vida base 40 da v1.0.
 */
public class FlowEffect extends MobEffect {
    private final PotionKind kind;

    public FlowEffect(PotionKind kind, int color) {
        super(MobEffectCategory.BENEFICIAL, color);
        this.kind = kind;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        float perSecond = amplifier + 1;
        if (kind == PotionKind.HEALTH) {
            if (entity.getHealth() < entity.getMaxHealth()) entity.heal(perSecond * 0.5F);
        } else if (entity instanceof Player player) {
            MagicData d = MagicApi.get(player);
            if (d.mana() < d.maxMana()) d.addMana(d.maxMana() * perSecond * 0.005F);
        }
        return true;
    }
}
