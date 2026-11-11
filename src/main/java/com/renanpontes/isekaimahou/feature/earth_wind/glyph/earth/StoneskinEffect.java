package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwRegistries;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Pele de Pedra (benefico): armadura, robustez, Resistencia e resistencia a empurrao por 20 s. Inverter: pele quebradica. */
public class StoneskinEffect extends EffectGlyph {
    public StoneskinEffect() {
        super("stoneskin", 14);
        element(Element.EARTH);
        tier(2);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        ServerLevel level = ctx.level();
        if (stats.inverted && !ctx.isAlly(le)) {
            // Inverter: "pele quebradica" — a pedra racha, o alvo fica Exposto (sofre mais dano) e pesado
            ctx.status(le, CoreRegistries.EXPOSED, 160, 0, stats);
            ctx.status(le, MobEffects.MOVEMENT_SLOWDOWN, 80, 0, stats);
            Vec3 at = le.position().add(0, le.getBbHeight() * 0.5, 0);
            EwFx.debris(level, at, Blocks.GRAVEL.defaultBlockState(), 24, le.getBbWidth() * 0.5);
            EwFx.sound(level, at, SoundEvents.DEEPSLATE_BREAK, 1.0F, 1.2F);
            return;
        }
        int amp = Math.max(0, Math.min(2, stats.amplify));
        ctx.status(le, EwRegistries.STONESKIN, 400, amp, stats);
        ctx.status(le, MobEffects.DAMAGE_RESISTANCE, 400, 0, stats);
        Vec3 at = le.position().add(0, le.getBbHeight() * 0.5, 0);
        EwFx.debris(level, at, Blocks.STONE.defaultBlockState(), 20, le.getBbWidth() * 0.5);
        EwFx.debris(level, at, Blocks.TUFF.defaultBlockState(), 10, le.getBbWidth() * 0.5);
        EwFx.sound(level, at, SoundEvents.DEEPSLATE_BRICKS_PLACE, 1.0F, 0.6F);
        EwFx.sound(level, at, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8F, 0.6F);
    }
}
