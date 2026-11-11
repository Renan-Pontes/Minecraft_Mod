package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.StalagmiteSpike;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Estalagmite: um espinho de pedra brota do chao sob o alvo, fere, joga para cima e deixa lento. */
public class StalagmiteEffect extends EffectGlyph {
    public StalagmiteEffect() {
        super("stalagmite", 12);
        element(Element.EARTH);
        tier(2);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        ServerLevel level = ctx.level();
        BlockPos ground = EwUtil.groundUnder(target, 4);
        float dmg = 7.0F;
        if (ground != null) {
            spike(level, ground, target.getX(), target.getZ(), stats);
        } else {
            dmg *= 0.6F; // no ar o espinho mal alcanca
        }
        ctx.damage(target, dmg, stats, null);
        EwUtil.push(target, Vec3.ZERO, 0.55 + 0.1 * Math.max(0, stats.amplify));
        if (target instanceof LivingEntity le) ctx.status(le, MobEffects.MOVEMENT_SLOWDOWN, 40, 1, stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        ServerLevel level = ctx.level();
        BlockPos ground = hit.getDirection() == Direction.UP ? hit.getBlockPos()
                : EwUtil.groundBelow(level, hit.getBlockPos().relative(hit.getDirection()), 4);
        if (ground == null || !EwUtil.once(ctx, "spike@" + ground.asLong())) return;
        spike(level, ground, ground.getX() + 0.5, ground.getZ() + 0.5, stats);
        // fere quem estiver pisando exatamente ali
        Vec3 top = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
        for (LivingEntity e : ctx.enemiesAround(top, 1.3)) {
            ctx.damage(e, 7.0F, stats, null);
            EwUtil.push(e, Vec3.ZERO, 0.55);
        }
    }

    private static void spike(ServerLevel level, BlockPos ground, double x, double z, SpellStats stats) {
        float scale = 1.0F + 0.2F * Math.max(0, stats.amplify) + 0.15F * Math.max(0, stats.aoe);
        StalagmiteSpike.spawn(level, new Vec3(x, ground.getY() + 1.0, z), scale, EwFx.groundState(level, ground));
        Vec3 at = new Vec3(x, ground.getY() + 1.0, z);
        EwFx.sound(level, at, SoundEvents.POINTED_DRIPSTONE_LAND, 1.2F, 0.6F);
        EwFx.sound(level, at, SoundEvents.DRIPSTONE_BLOCK_BREAK, 1.0F, 0.7F);
    }
}
