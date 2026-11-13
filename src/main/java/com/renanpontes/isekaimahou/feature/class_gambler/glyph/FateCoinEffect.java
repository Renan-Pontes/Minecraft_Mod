package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFxNet;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Cara ou Coroa: uma moeda de energia gira sobre o alvo. CARA = 9 de dano de Luz (os dois lados da moeda: Luz e
 * Sombra, as afinidades do Apostador). COROA = 4 de dano de Sombra e o alvo fica Azarado por 6 s (recebe +15% de dano,
 * erra golpes e rola pior). A Sorte de quem conjura aumenta a chance de Cara (até 80%). Inverter = sempre Coroa.
 */
public class FateCoinEffect extends EffectGlyph {
    public static final float HEADS = 9F;
    public static final float TAILS = 4F;
    public static final int JINX_TICKS = 120;

    public FateCoinEffect() {
        super("fate_coin", 12);
        origin(MagicPath.GAMBLER);
        tier(1);
    }

    @Override
    public float baseDamage() {
        return (HEADS + TAILS) / 2F;
    }

    @Override
    public int hintDuration() {
        return JINX_TICKS;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        int luck = ctx.caster() != null ? LuckService.luck(ctx.caster()) : 0;
        float heads = Math.min(0.8F, 0.5F + 0.03F * luck);
        boolean isHeads = !stats.inverted && ctx.random().nextFloat() < heads;
        Vec3 top = target.position().add(0, target.getBbHeight() + 0.6, 0);
        GbFxNet.at(ctx.level(), GbFxNet.FATE_COIN, top, Vec3.ZERO, 1F, GbFx.GOLD, target.getId(), isHeads ? 1 : 0);
        if (isHeads) {
            ctx.damage(target, HEADS, stats, null, Element.LIGHT);
            GbFx.sound(ctx.level(), top, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.6F);
        } else {
            ctx.damage(target, TAILS, stats, null, Element.SHADOW);
            if (target instanceof LivingEntity le) {
                int amp = Math.max(0, Math.min(2, stats.amplify));
                ctx.status(le, GbRegistries.JINXED, JINX_TICKS, amp, stats);
            }
            GbFx.sound(ctx.level(), top, SoundEvents.NOTE_BLOCK_BASS.value(), 0.8F, 0.6F);
        }
    }
}
