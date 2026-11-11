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
 * Corpo Gelatinoso (Slime): o corpo vira geleia — Regeneração II por 5 s, Resistência por 10 s e um quique para cima.
 * Em aliados também. Com Inverter num inimigo: a geleia o prende no chão (Enraizado e Lentidão forte).
 */
public class JellyBodyEffect extends RaceEffectGlyph {
    public static final int TICKS = 200;
    private static final int JELLY = 0x8FE05A;

    public JellyBodyEffect() {
        super("race_jelly_body", 11, Element.WATER, 2);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        Vec3 c = RaceFx.center(le);
        if (le != ctx.caster() && !ctx.isAlly(le)) {
            Statuses.apply(le, Statuses.Kind.ROOTED, 1, stats.duration(60), ctx.caster(), stats.power());
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(100), 3, false, true, true), ctx.caster());
            level.sendParticles(ParticleTypes.ITEM_SLIME, le.getX(), le.getY() + 0.2, le.getZ(), 24, 0.4, 0.1, 0.4, 0.05);
            RaceFx.sound(level, c, SoundEvents.SLIME_BLOCK_PLACE, 1.0F, 0.6F);
            return;
        }
        le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, stats.duration(100), 1, false, true, true), ctx.caster());
        le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, stats.duration(TICKS), 0, false, true, true), ctx.caster());
        le.setDeltaMovement(le.getDeltaMovement().x, Math.max(le.getDeltaMovement().y, 0.55), le.getDeltaMovement().z);
        le.hurtMarked = true;
        le.resetFallDistance();
        RaceFx.ring(level, le.position().add(0, 0.1, 0), 0.9, 14, ParticleTypes.ITEM_SLIME);
        level.sendParticles(RaceFx.dust(JELLY, 1.5F), c.x, c.y, c.z, 18, 0.4, 0.5, 0.4, 0.01);
        RaceFx.sound(level, c, SoundEvents.SLIME_JUMP, 1.0F, 0.8F);
        RaceFx.sound(level, c, SoundEvents.HONEY_BLOCK_SLIDE, 0.8F, 1.2F);
    }
}
