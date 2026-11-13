package com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Impulso: lanca o alvo na direcao em que o conjurador olha. Em Si Mesmo vira uma investida (dash)
 * com protecao contra queda; num inimigo, ele e arremessado para a frente. Inverter = para tras.
 */
public class ImpulseEffect extends EffectGlyph {
    public ImpulseEffect() {
        super("impulse", 10);
        element(Element.WIND);
        tier(2);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        ServerLevel level = ctx.level();
        Vec3 dir = ctx.look().normalize();
        if (stats.inverted) dir = dir.scale(-1);
        double speed = Math.min(4.0, 1.5 * stats.power());
        Vec3 v = dir.scale(speed).add(0, 0.2, 0);
        if (target == ctx.caster()) {
            EwUtil.setVelocity(target, v);
            if (target instanceof LivingEntity le) EwUtil.protectFall(le, 80);
        } else {
            EwUtil.setVelocity(target, v.scale(EwUtil.knockbackFactor(target)));
        }
        Vec3 behind = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(dir.scale(0.6));
        for (int i = 0; i < 8; i++) {
            EwFx.moving(level, Vfx.cloud(), behind, dir.scale(-0.25).add(
                    (level.random.nextDouble() - 0.5) * 0.15, (level.random.nextDouble() - 0.5) * 0.15, (level.random.nextDouble() - 0.5) * 0.15));
        }
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, behind.x, behind.y, behind.z, 1, 0, 0, 0, 0);
        EwFx.sound(level, target.position(), SoundEvents.BREEZE_JUMP, 1.0F, 1.2F);
        EwFx.sound(level, target.position(), SoundEvents.WIND_CHARGE_THROW, 0.8F, 0.9F);
    }
}
