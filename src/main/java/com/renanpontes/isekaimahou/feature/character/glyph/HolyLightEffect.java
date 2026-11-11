package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Luz Sagrada (Anjo): um raio de luz do céu. Num inimigo: dano de luz (o DOBRO em mortos-vivos) e Brilho, e o anjo se
 * cura com 15% do dano causado; em você (Si Mesmo): cura e apaga o fogo.
 */
public class HolyLightEffect extends RaceEffectGlyph {
    public static final float DAMAGE = 5.0F;
    public static final float HEAL = 4.0F;
    private static final int GOLD = 0xFFE08A, WHITE = 0xFFF8E0;

    public HolyLightEffect() {
        super("race_holy_light", 10, Element.LIGHT, 1);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public float baseHeal() { return HEAL; }

    @Override
    public int hintDuration() { return 100; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        Vec3 base = le.position();
        // Coluna de luz descendo do céu
        for (int i = 0; i < 10; i++) {
            level.sendParticles(ParticleTypes.END_ROD, base.x, base.y + i * 0.5, base.z, 1, 0.04, 0.04, 0.04, 0);
        }
        level.sendParticles(RaceFx.dust(GOLD, 1.4F), base.x, base.y + le.getBbHeight() * 0.5, base.z, 12, 0.3, 0.5, 0.3, 0);
        if (le == ctx.caster() || ctx.isAlly(le)) {
            ctx.heal(le, HEAL, stats);
            le.clearFire();
            RaceFx.ring(level, base.add(0, 0.1, 0), 0.8, 12, RaceFx.dust(WHITE, 0.9F));
            RaceFx.sound(level, base, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.6F);
            return;
        }
        boolean undead = le.getType().is(EntityTypeTags.UNDEAD);
        float dealt = ctx.damage(le, DAMAGE * (undead ? 2F : 1F), stats, null);
        le.addEffect(new MobEffectInstance(MobEffects.GLOWING, stats.duration(100), 0, false, false, true), ctx.caster());
        if (dealt > 0 && ctx.caster() != null && ctx.caster().isAlive()) ctx.caster().heal(dealt * 0.15F);
        if (undead) level.sendParticles(ParticleTypes.FLASH, base.x, base.y + 1.0, base.z, 1, 0, 0, 0, 0);
        RaceFx.sound(level, base, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 1.8F);
        RaceFx.sound(level, base, SoundEvents.BEACON_ACTIVATE, 0.4F, 2.0F);
    }
}
