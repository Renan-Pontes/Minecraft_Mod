package com.renanpontes.isekaimahou.feature.flat_lands.effect;

import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.EffectCure;

/**
 * Sorte do Omikuji (dura 1 dia do jogo). Os bônus/penalidades são LEVES e crescem com o nível (amplificador):
 * <ul>
 *   <li>Bênção da Sakura: +1 de sorte, +2% de velocidade e +5% de regeneração de mana por nível; pétalas às vezes;</li>
 *   <li>Má Sorte: −1 de sorte e −3% de velocidade por nível.</li>
 * </ul>
 * Leite não tira nenhum dos dois: a bênção é do dia todo, e a má sorte só sai amarrando o papel no santuário.
 */
public class SakuraFortuneEffect extends MobEffect {
    private final boolean blessing;

    public SakuraFortuneEffect(boolean blessing) {
        super(blessing ? MobEffectCategory.BENEFICIAL : MobEffectCategory.HARMFUL, blessing ? 0xFFB7D5 : 0x5C4A72);
        this.blessing = blessing;
        String p = blessing ? "sakura_blessing_" : "sakura_misfortune_";
        if (blessing) {
            addAttributeModifier(Attributes.LUCK, IsekaiMahou.id(p + "luck"), 1.0, AttributeModifier.Operation.ADD_VALUE);
            addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(p + "speed"), 0.02,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(CoreRegistries.MANA_REGEN, IsekaiMahou.id(p + "mana_regen"), 0.05,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            addAttributeModifier(Attributes.LUCK, IsekaiMahou.id(p + "luck"), -1.0, AttributeModifier.Operation.ADD_VALUE);
            addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(p + "speed"), -0.03,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 60 == 0;
    }

    /** Só visual: algumas pétalas (bênção) ou fumacinha (má sorte) em volta de quem tem o efeito. */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel sl && entity.getRandom().nextInt(3) == 0) {
            sl.sendParticles(blessing ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.SMOKE, entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.8, entity.getZ(), blessing ? 2 + amplifier : 2, 0.4, 0.3, 0.4, 0.0);
        }
        return true;
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        // Sem curas (leite/totem não tiram).
    }
}
