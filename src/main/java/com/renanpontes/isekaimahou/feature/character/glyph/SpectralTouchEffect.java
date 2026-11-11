package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Toque Espectral (Fantasma): a mão fria de um espírito atravessa o alvo — dano de sombra, Gelado e Fraqueza; 25% do
 * dano causado volta como vida para quem conjura (a alma roubada).
 */
public class SpectralTouchEffect extends RaceEffectGlyph {
    public static final float DAMAGE = 6.0F;
    public static final int TICKS = 80;

    public SpectralTouchEffect() {
        super("race_spectral_touch", 10, Element.SHADOW, 1);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        if (le == ctx.caster() || ctx.isAlly(le)) return;
        ServerLevel level = ctx.level();
        Vec3 c = RaceFx.center(le);
        float dealt = ctx.damage(le, DAMAGE, stats, null);
        Statuses.apply(le, Statuses.Kind.CHILLED, 1, stats.duration(TICKS), ctx.caster(), stats.power());
        le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stats.duration(TICKS), 0, false, true, true), ctx.caster());
        if (dealt > 0 && ctx.caster() != null && ctx.caster().isAlive()) {
            ctx.caster().heal(dealt * 0.25F);
            RaceFx.line(level, c, RaceFx.center(ctx.caster()), ParticleTypes.SOUL);
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 8, 0.25, 0.35, 0.25, 0.03);
        level.sendParticles(RaceFx.dust(0x9FD8E8, 1.1F), c.x, c.y, c.z, 14, 0.3, 0.4, 0.3, 0.01);
        RaceFx.sound(level, c, SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.7F);
        RaceFx.sound(level, c, SoundEvents.VEX_CHARGE, 0.5F, 0.6F);
    }
}
