package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.SummonedStoneGolem;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Invocar Golem: um Golem de Pedra aliado sai do chao (45 s; Estender aumenta). Amplificar deixa
 * o golem mais forte (vida e dano). Num inimigo, o golem nasce ao lado dele e ja ataca.
 */
public class SummonGolemEffect extends EffectGlyph {
    public SummonGolemEffect() {
        super("summon_golem", 30);
        element(Element.EARTH);
        tier(3);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!EwUtil.once(ctx, "golem")) return;
        LivingEntity caster = ctx.caster();
        Vec3 pos;
        if (target == caster || caster == null) {
            pos = target.position().add(EwUtil.horizontal(ctx.look()).normalize().scale(2.0));
        } else {
            Vec3 toCaster = EwUtil.horizontal(caster.position().subtract(target.position()));
            pos = target.position().add(toCaster.lengthSqr() > 1.0E-4 ? toCaster.normalize().scale(2.0) : Vec3.ZERO);
        }
        SummonedStoneGolem g = spawn(ctx, pos, stats);
        if (g != null && target instanceof LivingEntity le && target != caster && !ctx.isAlly(le)) g.setTarget(le);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        if (!EwUtil.once(ctx, "golem")) return;
        spawn(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), stats);
    }

    private static SummonedStoneGolem spawn(SpellContext ctx, Vec3 at, SpellStats stats) {
        ServerLevel level = ctx.level();
        BlockPos ground = EwUtil.groundBelow(level, BlockPos.containing(at).above(2), 6);
        Vec3 pos = ground != null ? new Vec3(at.x, ground.getY() + 1.0, at.z) : at;
        // se o lugar estiver apertado, nasce ao lado do conjurador
        if (!level.noCollision(new net.minecraft.world.phys.AABB(pos.x - 0.7, pos.y, pos.z - 0.7, pos.x + 0.7, pos.y + 2.7, pos.z + 0.7))
                && ctx.caster() != null) {
            pos = ctx.caster().position();
        }
        return SummonedStoneGolem.summon(level, ctx.caster(), pos, stats.duration(900), stats.power());
    }
}
