package com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Rajada: um sopro forte que joga o alvo longe (Inverter = puxa para voce). Dano leve. */
public class GustEffect extends EffectGlyph {
    public GustEffect() {
        super("gust", 8);
        element(Element.WIND);
        tier(1);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        ServerLevel level = ctx.level();
        LivingEntity caster = ctx.caster();
        Vec3 dir = caster != null && target != caster ? EwUtil.horizontal(target.position().subtract(caster.position()))
                : EwUtil.horizontal(ctx.look());
        if (dir.lengthSqr() < 1.0E-4) dir = EwUtil.horizontal(ctx.look());
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();
        if (stats.inverted) dir = dir.scale(-1);
        double strength = Math.min(4.0, 1.7 * stats.power());
        EwUtil.push(target, dir.scale(strength), 0.45);
        if (target != caster) ctx.damage(target, 1.0F, stats, null);
        EwFx.gustAt(level, target);
        if (caster != null && target != caster) {
            EwFx.windLine(level, caster.getEyePosition().add(ctx.look().scale(0.8)), target.position().add(0, target.getBbHeight() * 0.5, 0));
        }
        EwFx.sound(level, target.position(), SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 1.0F);
    }
}
