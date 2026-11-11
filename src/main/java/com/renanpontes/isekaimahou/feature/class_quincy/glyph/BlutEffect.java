package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Sangue Espiritual (homenagem ao Blut Vene): Reishi corre nas veias do alvo aliado — Resistência (I; II com poder
 * ≥ 1,8) e Absorção por 8 s (Estender aumenta). Inverter (Blut Arterie invertido): veias de Reishi instável nos
 * INIMIGOS — Fraqueza e 2 de dano.
 */
public class BlutEffect extends EffectGlyph {
    public BlutEffect() {
        super("quincy_blut", 9);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(2);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public float baseDamage() {
        return 0F;
    }

    @Override
    public int hintDuration() {
        return 160;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        ServerLevel level = ctx.level();
        if (stats.inverted) {
            ctx.damage(le, 2.0F, stats, null);
            ctx.status(le, MobEffects.WEAKNESS, 160, 0, stats);
        } else {
            int amp = stats.power() >= 1.8F ? 1 : 0;
            ctx.status(le, MobEffects.DAMAGE_RESISTANCE, 160, amp, stats);
            ctx.status(le, MobEffects.ABSORPTION, 160, Math.max(0, Math.round(stats.power()) - 1), stats);
        }
        // veias azuis subindo pelo corpo
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + level.random.nextDouble() * 0.4;
            for (int k = 0; k < 6; k++) {
                double y = le.getBbHeight() * k / 6.0;
                double r = le.getBbWidth() * 0.55;
                level.sendParticles(stats.inverted ? QuincyFx.DEEP : QuincyFx.REISHI_SMALL, le.getX() + Math.cos(a + k * 0.3) * r,
                        le.getY() + y, le.getZ() + Math.sin(a + k * 0.3) * r, 1, 0, 0, 0, 0);
            }
        }
        QuincyFx.sound(level, le.position(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.6F, 1.6F);
    }
}
