package com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Lamina de Vento: corte afiado (dano + leve empurrao). Com Sensivel, corta folhas, mato e teias em volta. */
public class WindBladeEffect extends EffectGlyph {
    public WindBladeEffect() {
        super("wind_blade", 8);
        element(Element.WIND);
        tier(1);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        ServerLevel level = ctx.level();
        ctx.damage(target, 5.5F, stats, null);
        Vec3 look = EwUtil.horizontal(ctx.look());
        if (target != ctx.caster() && look.lengthSqr() > 1.0E-4) EwUtil.push(target, look.normalize().scale(0.35), 0.1);
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1 + Math.max(0, stats.amplify), 0.2, 0.2, 0.2, 0);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.25, 0.25, 0.25, 0.05);
        EwFx.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.4F);
        EwFx.sound(level, at, SoundEvents.BREEZE_SHOOT, 0.5F, 1.6F);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        ServerLevel level = ctx.level();
        BlockPos c = hit.getBlockPos();
        int cut = 0;
        for (BlockPos mpos : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
            BlockPos pos = mpos.immutable();
            BlockState s = level.getBlockState(pos);
            if (!(s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(Blocks.COBWEB))) continue;
            if (!s.getFluidState().isEmpty() || !EwUtil.canBreak(ctx, pos, s)) continue;
            level.destroyBlock(pos, true, ctx.caster());
            cut++;
        }
        Vec3 at = hit.getLocation();
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0);
        if (cut > 0) EwFx.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.2F);
    }
}
