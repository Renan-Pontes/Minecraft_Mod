package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
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
 * Presa Venenosa (Aracnídeo): a mordida injeta veneno — dano, Veneno II e Fraqueza. Num alvo que JÁ está envenenado,
 * +40% de dano (o veneno se acumula). Em você (Si Mesmo): suga o próprio veneno (tira Veneno e Murchar).
 */
public class VenomFangEffect extends RaceEffectGlyph {
    public static final float DAMAGE = 7.0F;
    public static final int TICKS = 120;
    private static final int VENOM = 0x7AC83A, PURPLE = 0x9A6AD8;

    public VenomFangEffect() {
        super("race_venom_fang", 11, Element.NATURE, 2);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        Vec3 c = RaceFx.center(le);
        if (le == ctx.caster() || ctx.isAlly(le)) {
            le.removeEffect(MobEffects.POISON);
            le.removeEffect(MobEffects.WITHER);
            RaceFx.dustBurst(level, c, VENOM, 12, 0.3);
            RaceFx.sound(level, c, SoundEvents.HONEY_DRINK, 0.8F, 1.2F);
            return;
        }
        boolean poisoned = le.hasEffect(MobEffects.POISON);
        ctx.damage(le, DAMAGE * (poisoned ? 1.4F : 1F), stats, null);
        le.addEffect(new MobEffectInstance(MobEffects.POISON, stats.duration(TICKS), 1, false, true, true), ctx.caster());
        le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stats.duration(TICKS), 0, false, true, true), ctx.caster());
        // Duas presas cravando + gotas de veneno
        for (int s = -1; s <= 1; s += 2) {
            Vec3 a = c.add(s * 0.25, 0.5, 0);
            RaceFx.line(level, a, c.add(s * 0.1, 0, 0), RaceFx.dust(0xF0F0E0, 0.6F));
        }
        level.sendParticles(RaceFx.dust(VENOM, 1.2F), c.x, c.y, c.z, 16, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(RaceFx.dust(PURPLE, 0.9F), c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.ITEM_SLIME, c.x, c.y - 0.2, c.z, 6, 0.2, 0.2, 0.2, 0.02);
        if (poisoned) level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.2);
        RaceFx.sound(level, c, SoundEvents.SPIDER_HURT, 0.8F, 1.3F);
        RaceFx.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 0.6F, 1.2F);
    }
}
