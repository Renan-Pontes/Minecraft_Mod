package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Amuleto da Sorte (benéfico, Luz): o alvo fica Sortudo por 15 s (Amplificar sobe o nível, até III) e recupera um
 * pouco de vida. Sortudo = +Sorte nas rolagens do Apostador, +Sorte nos saques e chance de esquivar golpes.
 * Inverter = Azarado no inimigo.
 */
public class LuckyCharmEffect extends EffectGlyph {
    public static final int TICKS = 300;
    public static final float HEAL = 2F;

    public LuckyCharmEffect() {
        super("lucky_charm", 8);
        element(Element.LIGHT);
        origin(MagicPath.GAMBLER);
        tier(1);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public float baseHeal() {
        return HEAL;
    }

    @Override
    public int hintDuration() {
        return TICKS;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        int amp = Math.max(0, Math.min(2, stats.amplify));
        if (stats.inverted) {
            ctx.status(le, GbRegistries.JINXED, TICKS / 2, amp, stats);
            ctx.level().sendParticles(GbFx.dust(0x5A3A78, 1.0F), le.getX(), le.getY() + 1, le.getZ(), 10, 0.3, 0.5, 0.3, 0);
            return;
        }
        ctx.status(le, GbRegistries.LUCKY, TICKS, amp, stats);
        ctx.heal(le, HEAL, stats);
        ctx.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, le.getX(), le.getY() + 1, le.getZ(), 8, 0.35, 0.5, 0.35, 0);
        ctx.level().sendParticles(GbFx.dust(0x6FD88A, 1.0F), le.getX(), le.getY() + 1.2, le.getZ(), 10, 0.3, 0.5, 0.3, 0);
        GbFx.sound(ctx.level(), le.position(), SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7F, 1.8F);
    }
}
