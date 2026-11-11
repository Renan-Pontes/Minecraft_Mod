package com.renanpontes.isekaimahou.glyph.weapon;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Guarda: Resistência I por 5 s (Amplificar: nível maior, até III; Estender: mais tempo). Benéfico.
 * Inverter: "quebra a guarda" do inimigo (Fraqueza). É o efeito da "Postura Firme" inicial das classes Guerreiras.
 */
public class GuardEffect extends EffectGlyph {
    public GuardEffect() {
        super(WeaponGlyphs.GUARD, 8);
        element(Element.ARCANE);
        tier(1);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        ServerLevel level = ctx.level();
        int amp = Mth.clamp(stats.amplify, 0, 2);
        double y = le.getY() + le.getBbHeight() * 0.5;
        if (stats.inverted) {
            le.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stats.duration(100), amp, false, true), ctx.caster());
            level.sendParticles(ParticleTypes.CRIT, le.getX(), y, le.getZ(), 14, 0.3, 0.4, 0.3, 0.2);
            WeaponFx.sound(level, le.position(), SoundEvents.SHIELD_BREAK, 0.7F, 1.3F);
            return;
        }
        le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, stats.duration(100), amp, false, true), ctx.caster());
        int rgb = ctx.color() & 0xFFFFFF;
        double r = le.getBbWidth() * 0.5 + 0.5;
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            level.sendParticles(WeaponFx.dust(rgb, 0.8F), le.getX() + Math.cos(a) * r, y + Math.sin(a * 2) * 0.3,
                    le.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        WeaponFx.sound(level, le.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6F, 1.2F);
    }
}
