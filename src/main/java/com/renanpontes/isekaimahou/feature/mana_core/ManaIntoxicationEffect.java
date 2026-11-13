package com.renanpontes.isekaimahou.feature.mana_core;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Intoxicação por Mana: o corpo não aguenta o mana acima do limite. Dano mágico periódico que NUNCA leva abaixo de
 * 1 de vida (para em 1 — mas aí qualquer golpe mata). Nível = sobrecarga / 300. Do nível 6 em diante, náusea.
 * <ul>
 *   <li>Dano: 2% da vida máxima × (1 + 0,5 por nível acima do 1º), no mínimo 1;</li>
 *   <li>intervalo: 3 s no nível I, mais rápido a cada nível (mínimo 1 s).</li>
 * </ul>
 * Leite não cura: só baixar a sobrecarga (gastar, depositar num Núcleo ou esperar escoar).
 */
public class ManaIntoxicationEffect extends MobEffect {
    /** Quem está levando o dano da Intoxicação agora (o {@link #clampDamage} segura o dano em "vida − 1"). */
    @Nullable private static LivingEntity hurting;

    public ManaIntoxicationEffect() {
        super(MobEffectCategory.HARMFUL, Overcharge.RGB, ParticleTypes.WITCH);
    }

    public static int interval(int amplifier) {
        return Math.max(20, 60 - amplifier * 6);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (e.level().isClientSide) return true;
        if (amplifier >= 5 && e.tickCount % 40 == 0) {
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false, false));
        }
        if (e.tickCount % interval(amplifier) != 0) return true;
        float hp = e.getHealth();
        if (hp <= 1.0F) return true;
        float dmg = Math.max(1.0F, e.getMaxHealth() * 0.02F * (1F + amplifier * 0.5F));
        dmg = Math.min(dmg, hp - 1.0F);
        if (dmg <= 0.01F) return true;
        hurting = e;
        try {
            e.hurt(e.damageSources().magic(), dmg);
        } finally {
            hurting = null;
        }
        return true;
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        // Sem curas: leite e totens não tiram o mana de dentro do corpo.
    }

    /** Última palavra no dano (prioridade mínima): a Intoxicação nunca passa de "vida − 1". */
    static void clampDamage(LivingDamageEvent.Pre e) {
        if (hurting == null || e.getEntity() != hurting) return;
        float allowed = Math.max(0F, e.getEntity().getHealth() - 1.0F);
        if (e.getNewDamage() > allowed) e.setNewDamage(allowed);
    }
}
