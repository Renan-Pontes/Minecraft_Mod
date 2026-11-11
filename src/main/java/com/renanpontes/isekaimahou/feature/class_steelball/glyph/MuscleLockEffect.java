package com.renanpontes.isekaimahou.feature.class_steelball.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Contração Muscular (efeito de origem, tier 2): o giro faz os músculos do alvo se contraírem — ele fica Enraizado
 * (não anda) por 2 s e com Fraqueza; dano leve (2). Estender alonga; chefes resistem (regra das condições).
 * Inverter: a contração vai para a pele — o alvo fica Lento e Fraco por mais tempo, sem prender (útil em chefes).
 */
public class MuscleLockEffect extends EffectGlyph {
    public static final float DAMAGE = 2F;
    public static final int LOCK_TICKS = 40;

    public MuscleLockEffect() {
        super("muscle_lock", 12);
        origin(MagicPath.STEELBALL);
        element(Element.EARTH);
        tier(2);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return LOCK_TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        ctx.damage(target, DAMAGE, stats, null);
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        int ticks = stats.duration(LOCK_TICKS);
        if (stats.inverted) {
            ctx.status(le, MobEffects.MOVEMENT_SLOWDOWN, LOCK_TICKS * 3, 1 + Math.max(0, stats.amplify), stats);
            ctx.status(le, MobEffects.WEAKNESS, LOCK_TICKS * 3, 0, stats);
        } else {
            Statuses.apply(le, Statuses.Kind.ROOTED, 1, ticks, ctx.caster(), stats.power() * ctx.powerMult());
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks + 20, Math.max(0, stats.amplify)), ctx.caster());
        }
        SbFx.spinRing(ctx.level(), le.position().add(0, 0.1, 0), SbFx.STEEL, le.getBbWidth() * 0.8 + 0.3, 12);
        SbFx.spinRing(ctx.level(), le.position().add(0, le.getBbHeight() * 0.6, 0), SbFx.STEEL, le.getBbWidth() * 0.6 + 0.2, 10);
        SbFx.sound(ctx.level(), le.position(), SoundEvents.CHAIN_PLACE, 0.6F, 0.8F);
    }
}
