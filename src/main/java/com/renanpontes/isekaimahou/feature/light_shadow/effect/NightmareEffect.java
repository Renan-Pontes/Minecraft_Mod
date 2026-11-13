package com.renanpontes.isekaimahou.feature.light_shadow.effect;

import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Pesadelo: a cada 1,5s, sussurros e sons assustadores (só a vítima ouve, se for jogador) e dano psíquico.
 * O pavor (Amedrontado) é aplicado pelo glifo com a mesma duração — adicionar efeitos aqui, durante o tick dos
 * efeitos, quebraria a iteração do LivingEntity.tickEffects.
 */
public class NightmareEffect extends MobEffect {
    public NightmareEffect() {
        super(MobEffectCategory.HARMFUL, 0x2B0F3F);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 30 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel sl)) return true;
        e.hurt(e.damageSources().magic(), 1.0F + 0.5F * amplifier);
        SoundEvent[] pool = {SoundEvents.WARDEN_HEARTBEAT, SoundEvents.AMBIENT_CAVE.value(), SoundEvents.GHAST_SCREAM,
                SoundEvents.WARDEN_NEARBY_CLOSER, SoundEvents.VEX_AMBIENT, SoundEvents.PHANTOM_AMBIENT};
        SoundEvent s = pool[sl.random.nextInt(pool.length)];
        float pitch = 0.5F + sl.random.nextFloat() * 0.4F;
        if (e instanceof ServerPlayer sp) sp.playNotifySound(s, SoundSource.HOSTILE, 0.9F, pitch);
        else sl.playSound(null, e.getX(), e.getY(), e.getZ(), s, SoundSource.HOSTILE, 0.5F, pitch);
        sl.sendParticles(Vfx.sculkSoul(), e.getX(), e.getEyeY() + 0.2, e.getZ(), 3, 0.3, 0.2, 0.3, 0.02);
        sl.sendParticles(LsFx.dust(LsFx.DEEP_VIOLET, 1.3F), e.getX(), e.getEyeY(), e.getZ(), 10, 0.4, 0.3, 0.4, 0);
        return true;
    }
}
