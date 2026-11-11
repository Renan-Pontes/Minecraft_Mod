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
 * Égide Divina (Anjo): asas de luz fecham em volta do alvo — Absorção (mais forte com Amplificar), Resistência por 6 s
 * e apaga o fogo. Em aliados também. Com Inverter num inimigo: a luz o expõe (Brilho, Exposto 2 e Fraqueza).
 */
public class DivineShieldEffect extends RaceEffectGlyph {
    public static final int TICKS = 200;
    private static final int GOLD = 0xFFE08A, WHITE = 0xFFF8E0;

    public DivineShieldEffect() {
        super("race_divine_shield", 12, Element.LIGHT, 2);
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
            le.addEffect(new MobEffectInstance(MobEffects.GLOWING, stats.duration(TICKS), 0, false, false, true), ctx.caster());
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stats.duration(120), 0, false, true, true), ctx.caster());
            Statuses.apply(le, Statuses.Kind.EXPOSED, 2, stats.duration(TICKS), ctx.caster(), stats.power());
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 16, 0.3, 0.5, 0.3, 0.05);
            RaceFx.sound(level, c, SoundEvents.BEACON_DEACTIVATE, 0.7F, 1.6F);
            return;
        }
        int amp = stats.power() >= 1.8F ? 2 : stats.power() >= 1.3F ? 1 : 0;
        le.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, stats.duration(TICKS), amp + 1, false, true, true), ctx.caster());
        le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, stats.duration(120), 0, false, true, true), ctx.caster());
        le.clearFire();
        // Duas asas de luz se fechando (arcos de pó dourado) + anel no chão
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * i / 9.0;
                Vec3 p = c.add(Math.cos(a) * 0.9 * s, Math.sin(a) * 0.9 - 0.2, -0.3);
                level.sendParticles(RaceFx.dust(i % 2 == 0 ? GOLD : WHITE, 1.1F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        RaceFx.ring(level, le.position().add(0, 0.1, 0), 1.1, 16, ParticleTypes.END_ROD);
        RaceFx.sound(level, c, SoundEvents.ARMOR_EQUIP_GOLD.value(), 1.0F, 1.2F);
        RaceFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
    }
}
