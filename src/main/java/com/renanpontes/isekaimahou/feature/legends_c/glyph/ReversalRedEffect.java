package com.renanpontes.isekaimahou.feature.legends_c.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_c.LcUtil;
import com.renanpontes.isekaimahou.feature.legends_c.entity.RedOrbEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Vermelho (Inversão) — ARCANO, t4, C4: repulsão. Com Si Mesmo o ponto vermelho é DISPARADO à frente (voa e explode no
 * primeiro alvo); num alvo/bloco ele aparece ali e detona. A explosão é uma onda vermelha que arremessa tudo longe,
 * com dano alto e uma cratera pequena. Ver {@link RedOrbEntity}.
 */
public class ReversalRedEffect extends EffectGlyph {
    public ReversalRedEffect() {
        super("reversal_red", 26);
        element(Element.ARCANE);
        tier(4);
        minCircle(4);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!LcUtil.budget(ctx.level(), ctx.caster(), "red", 2)) return;
        if (LcUtil.selfTarget(ctx, target)) {
            Vec3 look = ctx.look();
            RedOrbEntity.fire(ctx, stats, ctx.eyePos().add(look.scale(1.2)), look);
        } else {
            RedOrbEntity.place(ctx, stats, LcUtil.center(target));
        }
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        if (LcUtil.selfBlock(ctx, hit)) return;
        if (!LcUtil.budget(ctx.level(), ctx.caster(), "red", 2)) return;
        Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        RedOrbEntity.place(ctx, stats, hit.getLocation().add(n.scale(0.5)));
    }
}
