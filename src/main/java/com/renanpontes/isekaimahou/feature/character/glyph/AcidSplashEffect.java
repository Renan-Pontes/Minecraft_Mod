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
 * Gosma Ácida (Slime): uma bolha de gosma corrosiva — dano, Exposto (o ácido come a armadura), Molhado e Lentidão
 * (gruda). Em aliados não faz nada.
 */
public class AcidSplashEffect extends RaceEffectGlyph {
    public static final float DAMAGE = 5.0F;
    public static final int TICKS = 100;
    private static final int ACID = 0x8FE05A;

    public AcidSplashEffect() {
        super("race_acid_splash", 9, Element.WATER, 1);
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
        ctx.damage(le, DAMAGE, stats, null);
        Statuses.apply(le, Statuses.Kind.EXPOSED, 1, stats.duration(TICKS), ctx.caster(), stats.power());
        Statuses.apply(le, Statuses.Kind.WET, 1, stats.duration(TICKS), ctx.caster(), stats.power());
        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(60), 1, false, true, true), ctx.caster());
        level.sendParticles(ParticleTypes.ITEM_SLIME, c.x, c.y, c.z, 20, 0.35, 0.4, 0.35, 0.08);
        level.sendParticles(RaceFx.dust(ACID, 1.3F), c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.01);
        level.sendParticles(ParticleTypes.SMOKE, c.x, c.y + 0.3, c.z, 4, 0.2, 0.2, 0.2, 0.01);
        RaceFx.sound(level, c, SoundEvents.SLIME_SQUISH, 1.0F, 1.1F);
        RaceFx.sound(level, c, SoundEvents.FIRE_EXTINGUISH, 0.4F, 1.6F);
    }
}
