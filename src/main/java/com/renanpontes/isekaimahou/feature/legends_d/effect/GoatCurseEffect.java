package com.renanpontes.isekaimahou.feature.legends_d.effect;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Maldição da Cabra (jogadores): não ataca nem conjura (eventos no {@code Goatify}), pula alto e BERRA de tempos em
 * tempos. Os outros jogadores o veem como uma cabra (cliente).
 */
public class GoatCurseEffect extends MobEffect {
    public GoatCurseEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 35 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (e.level() instanceof ServerLevel sl) {
            sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GOAT_SCREAMING_AMBIENT, SoundSource.PLAYERS,
                    1.1F, 0.9F + sl.random.nextFloat() * 0.3F);
            sl.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + e.getBbHeight() + 0.4, e.getZ(), 1, 0.2, 0.1, 0.2, 0.6);
            sl.sendParticles(Vfx.poof(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 3, 0.25, 0.25, 0.25, 0.01);
        }
        return true;
    }
}
