package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwRegistries;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Peso: o alvo fica pesado por 8 s — lento, sem pular, cai mais rapido e sofre mais dano de queda.
 * Quem estiver no ar e arremessado ao chao. Com Inverter vira "Leveza" (queda lenta + pulo alto).
 */
public class WeightEffect extends EffectGlyph {
    public WeightEffect() {
        super("weight", 22);
        element(Element.EARTH);
        tier(4);
        minCircle(3);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        ServerLevel level = ctx.level();
        Vec3 top = le.position().add(0, le.getBbHeight() + 0.3, 0);
        if (stats.inverted) {
            ctx.status(le, MobEffects.SLOW_FALLING, 200, 0, stats);
            ctx.status(le, MobEffects.JUMP, 200, 1, stats);
            level.sendParticles(ParticleTypes.CLOUD, le.getX(), le.getY() + 0.2, le.getZ(), 14, 0.4, 0.1, 0.4, 0.03);
            EwFx.sound(level, top, SoundEvents.BREEZE_JUMP, 0.8F, 1.4F);
            return;
        }
        int amp = Math.max(0, Math.min(2, stats.amplify));
        ctx.status(le, EwRegistries.WEIGHTED, 160, amp, stats);
        if (!le.onGround()) {
            Vec3 dm = le.getDeltaMovement();
            EwUtil.setVelocity(le, new Vec3(dm.x * 0.3, -1.4, dm.z * 0.3));
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState()),
                top.x, top.y, top.z, 24, le.getBbWidth() * 0.5, 0.2, le.getBbWidth() * 0.5, 0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()),
                le.getX(), le.getY() + 0.1, le.getZ(), 16, le.getBbWidth() * 0.5, 0.05, le.getBbWidth() * 0.5, 0.1);
        EwFx.sound(level, top, SoundEvents.ANVIL_LAND, 0.6F, 0.6F);
        EwFx.sound(level, top, SoundEvents.CHAIN_PLACE, 1.0F, 0.5F);
    }
}
