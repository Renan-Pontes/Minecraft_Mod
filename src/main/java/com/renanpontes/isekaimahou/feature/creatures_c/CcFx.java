package com.renanpontes.isekaimahou.feature.creatures_c;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

/** Efeitos compartilhados pelas feras (petrificação do basilisco, poeira de pedra da gárgula, brilho das fadas). */
public final class CcFx {
    public static final DustParticleOptions STONE_DUST = new DustParticleOptions(new Vector3f(0.58F, 0.58F, 0.62F), 1.3F);
    public static final DustParticleOptions GAZE_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.86F, 0.25F), 0.9F);
    public static final DustParticleOptions FAIRY_PINK = new DustParticleOptions(new Vector3f(1.0F, 0.62F, 0.8F), 0.7F);
    public static final DustParticleOptions FAIRY_MINT = new DustParticleOptions(new Vector3f(0.6F, 1.0F, 0.86F), 0.7F);
    public static final DustParticleOptions EMBER_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.48F, 0.18F), 0.8F);

    public static BlockParticleOption stoneChips() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
    }

    /**
     * Petrifica um alvo: Atordoado (não anda nem ataca direito) + Lentidão forte que dura um pouco mais,
     * com lascas de pedra e o som de maldição do guardião ancião.
     */
    public static void petrify(ServerLevel level, LivingEntity target, int ticks) {
        target.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, ticks, 0));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks + 60, 3));
        double w = target.getBbWidth() * 0.45, h = target.getBbHeight() * 0.45;
        double cy = target.getY() + target.getBbHeight() * 0.5;
        level.sendParticles(stoneChips(), target.getX(), cy, target.getZ(), 35, w, h, w, 0.12);
        level.sendParticles(STONE_DUST, target.getX(), cy, target.getZ(), 25, w, h, w, 0.0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 1.3F, 0.55F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.45F, 1.5F);
    }

    /** Nuvem de poeira e lascas de pedra (gárgula virando estátua/acordando). */
    public static void stoneBurst(ServerLevel level, LivingEntity e, int count) {
        double w = e.getBbWidth() * 0.5, h = e.getBbHeight() * 0.5;
        double cy = e.getY() + e.getBbHeight() * 0.5;
        level.sendParticles(stoneChips(), e.getX(), cy, e.getZ(), count, w, h, w, 0.1);
        level.sendParticles(STONE_DUST, e.getX(), cy, e.getZ(), count / 2, w, h, w, 0.0);
    }

    private CcFx() {}
}
