package com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.SummonedSylph;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Invocar Silfide: uma silfide aliada aparece ao lado do conjurador (45 s). Ela da Velocidade a voce e aos
 * aliados perto e corta os inimigos com laminas de vento. Num inimigo, ela ja surge atacando-o.
 */
public class SummonSylphEffect extends EffectGlyph {
    public SummonSylphEffect() {
        super("summon_sylph", 30);
        element(Element.WIND);
        tier(3);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!EwUtil.once(ctx, "sylph")) return;
        LivingEntity caster = ctx.caster();
        LivingEntity enemy = target instanceof LivingEntity le && target != caster && !ctx.isAlly(le) ? le : null;
        Vec3 pos = caster != null ? caster.position().add(0, caster.getBbHeight() + 0.3, 0).add(EwUtil.horizontal(ctx.look()).scale(-1))
                : target.position().add(0, 1.5, 0);
        SummonedSylph.summon(ctx, pos, stats.duration(900), stats.power(), enemy);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        if (!EwUtil.once(ctx, "sylph")) return;
        Vec3 pos = Vec3.atCenterOf(hit.getBlockPos().relative(hit.getDirection())).add(0, 0.5, 0);
        SummonedSylph.summon(ctx, pos, stats.duration(900), stats.power(), null);
    }
}
