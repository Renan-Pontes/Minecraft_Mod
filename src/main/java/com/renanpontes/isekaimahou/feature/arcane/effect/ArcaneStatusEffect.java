package com.renanpontes.isekaimahou.feature.arcane.effect;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Efeito de status do módulo Arcano. O comportamento principal vem dos modificadores de atributo
 * (Encolher/Crescer) ou de eventos (Reflexão); aqui fica só a "aura" visual enquanto o efeito dura.
 */
public class ArcaneStatusEffect extends MobEffect {
    public enum Aura { REFLECT, SHRINK, GROWTH }

    private final Aura aura;
    private final DustParticleOptions dust;

    public ArcaneStatusEffect(MobEffectCategory category, int color, Aura aura) {
        super(category, color);
        this.aura = aura;
        this.dust = new DustParticleOptions(new Vector3f(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F,
                (color & 255) / 255F), 0.9F);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % (aura == Aura.REFLECT ? 4 : 10) == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) return true;
        double w = entity.getBbWidth() * 0.5 + 0.45;
        double cy = entity.getY() + entity.getBbHeight() * 0.55;
        switch (aura) {
            case REFLECT -> {
                // Hexágono girando em volta do corpo: deixa claro que projéteis vão ricochetear
                double a0 = entity.tickCount * 0.25;
                for (int i = 0; i < 6; i++) {
                    double a = a0 + i * Math.PI / 3;
                    level.sendParticles(dust, entity.getX() + Math.cos(a) * w, cy + Math.sin(a0 * 2 + i) * 0.25,
                            entity.getZ() + Math.sin(a) * w, 1, 0, 0, 0, 0);
                }
                if (entity.tickCount % 12 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, entity.getX(), cy, entity.getZ(), 1, w * 0.6, 0.3, w * 0.6, 0.0);
                }
            }
            case SHRINK -> level.sendParticles(dust, entity.getX(), cy, entity.getZ(), 2, w * 0.6, entity.getBbHeight() * 0.3, w * 0.6, 0);
            case GROWTH -> {
                level.sendParticles(dust, entity.getX(), entity.getY() + 0.1, entity.getZ(), 3, w, 0.05, w, 0);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), cy, entity.getZ(), 1, w * 0.6, 0.4, w * 0.6, 0);
            }
        }
        return true;
    }
}
