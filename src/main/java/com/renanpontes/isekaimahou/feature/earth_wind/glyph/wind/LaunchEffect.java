package com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Arremessar: uma coluna de vento joga o alvo para o alto (e a queda faz o resto). Em Si Mesmo e um
 * super pulo com protecao de queda. Inverter = joga o alvo contra o chao.
 */
public class LaunchEffect extends EffectGlyph {
    public LaunchEffect() {
        super("launch", 14);
        element(Element.WIND);
        tier(3);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        ServerLevel level = ctx.level();
        boolean self = target == ctx.caster();
        double k = self ? 1.0 : EwUtil.knockbackFactor(target);
        Vec3 dm = target.getDeltaMovement();
        if (stats.inverted) {
            EwUtil.setVelocity(target, new Vec3(dm.x * 0.2, -2.2 * k, dm.z * 0.2));
        } else {
            double up = Math.min(3.0, 1.35 * stats.power()) * Math.max(0.5, k);
            EwUtil.setVelocity(target, new Vec3(dm.x * 0.3, up, dm.z * 0.3));
        }
        if (self && target instanceof LivingEntity le) EwUtil.protectFall(le, 140);
        else ctx.damage(target, 3.0F, stats, null);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, target.getX(), target.getY(), target.getZ(), 1, 0, 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            EwFx.windSpiral(level, target.position().add(0, -0.5 + i * 0.6, 0), 0.6 + i * 0.15, 0.6, 6, i * 1.3);
        }
        EwFx.sound(level, target.position(), SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 0.8F);
        EwFx.sound(level, target.position(), SoundEvents.BREEZE_JUMP, 0.8F, 0.8F);
    }
}
