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
 * v1.0: Dividir chama um segundo golem (no maximo {@link SummonedStoneGolem#MAX_PER_OWNER} por dono).
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
        SummonedStoneGolem first = SummonedStoneGolem.summon(level, ctx.caster(), pos, stats.duration(900), stats.power());
        // Dividir: os golens extras ficam ao lado do primeiro (ele não é desfeito: keep = quantos já nasceram agora)
        int n = Math.min(SummonedStoneGolem.MAX_PER_OWNER, stats.count());
        for (int i = 1; i < n; i++) {
            Vec3 side = pos.add(EwUtil.horizontal(ctx.look()).normalize().yRot((float) Math.PI / 2).scale(2.2 * i));
            BlockPos g2 = EwUtil.groundBelow(level, BlockPos.containing(side).above(2), 6);
            Vec3 p2 = g2 != null ? new Vec3(side.x, g2.getY() + 1.0, side.z) : pos;
            if (!level.noCollision(new net.minecraft.world.phys.AABB(p2.x - 0.7, p2.y, p2.z - 0.7, p2.x + 0.7, p2.y + 2.7, p2.z + 0.7))) p2 = pos;
            SummonedStoneGolem.summon(level, ctx.caster(), p2, stats.duration(900), stats.power(), i);
        }
        return first;
    }
}
