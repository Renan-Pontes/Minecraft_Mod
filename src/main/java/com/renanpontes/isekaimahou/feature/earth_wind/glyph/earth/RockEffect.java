package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Rocha: pedrada pesada — dano fisico alto e empurrao. */
public class RockEffect extends EffectGlyph {
    public RockEffect() {
        super("rock", 9);
        element(Element.EARTH);
        tier(1);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        ServerLevel level = ctx.level();
        ctx.damage(target, 7.5F, stats, null);
        Vec3 from = ctx.caster() != null ? ctx.caster().position() : target.position().subtract(ctx.look());
        Vec3 dir = EwUtil.horizontal(target.position().subtract(from));
        if (dir.lengthSqr() < 1.0E-4) dir = EwUtil.horizontal(ctx.look());
        if (dir.lengthSqr() > 1.0E-4 && target != ctx.caster()) EwUtil.push(target, dir.normalize().scale(0.9 * stats.power()), 0.3);
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
        EwFx.debris(level, at, Blocks.STONE.defaultBlockState(), 26, 0.35);
        EwFx.debris(level, at, Blocks.COBBLESTONE.defaultBlockState(), 10, 0.25);
        EwFx.sound(level, at, SoundEvents.STONE_BREAK, 1.2F, 0.6F);
        EwFx.sound(level, at, SoundEvents.ANVIL_LAND, 0.35F, 0.5F);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        // so com Sensivel: a pedra se despedaca no bloco
        Vec3 at = hit.getLocation();
        EwFx.debris(ctx.level(), at, Blocks.STONE.defaultBlockState(), 20, 0.3);
        EwFx.sound(ctx.level(), at, SoundEvents.STONE_BREAK, 1.0F, 0.7F);
    }
}
