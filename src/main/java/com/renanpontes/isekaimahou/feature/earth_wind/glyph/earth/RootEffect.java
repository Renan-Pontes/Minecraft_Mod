package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwTasks;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Enraizar: raizes saem do chao e prendem o alvo (Enraizado: nao anda nem pula, mas ataca). Voadores sao puxados. */
public class RootEffect extends EffectGlyph {
    public RootEffect() {
        super("root", 15);
        element(Element.EARTH);
        tier(3);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        ServerLevel level = ctx.level();
        int ticks = stats.duration(80);
        ctx.status(le, CoreRegistries.ROOTED, 80, 0, stats);
        ctx.damage(le, 2.0F, stats, null);
        if (!le.onGround()) EwUtil.push(le, Vec3.ZERO, -0.8);
        BlockState roots = Blocks.ROOTED_DIRT.defaultBlockState();
        BlockState hanging = Blocks.HANGING_ROOTS.defaultBlockState();
        EwFx.debris(level, le.position().add(0, 0.2, 0), roots, 24, 0.4);
        EwFx.sound(level, le.position(), SoundEvents.ROOTED_DIRT_PLACE, 1.2F, 0.6F);
        EwFx.sound(level, le.position(), SoundEvents.HANGING_ROOTS_PLACE, 1.0F, 0.7F);
        // raizes se enrolando no alvo enquanto durar
        EwTasks.repeat(ticks, age -> {
            if (!le.isAlive() || !le.hasEffect(CoreRegistries.ROOTED)) return false;
            if (age % 4 != 0) return true;
            double h = Math.min(le.getBbHeight() * 0.7, 0.2 + (age % 24) / 24.0 * le.getBbHeight() * 0.7);
            for (int i = 0; i < 4; i++) {
                double a = age * 0.4 + i * Math.PI / 2;
                double r = le.getBbWidth() * 0.65;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, hanging), le.getX() + Math.cos(a) * r,
                        le.getY() + h, le.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, roots), le.getX(), le.getY() + 0.1, le.getZ(),
                    2, le.getBbWidth() * 0.4, 0, le.getBbWidth() * 0.4, 0);
            return true;
        });
    }
}
