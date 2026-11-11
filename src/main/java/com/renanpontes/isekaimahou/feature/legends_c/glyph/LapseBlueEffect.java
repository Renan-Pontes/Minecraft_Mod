package com.renanpontes.isekaimahou.feature.legends_c.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_c.LcUtil;
import com.renanpontes.isekaimahou.feature.legends_c.entity.BlueOrbEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Azul (Lapso) — ARCANO, t4, C4: abre um ponto de atração violento no alvo (com Si Mesmo, 6 blocos à frente). Puxa
 * tudo, esmaga quem chega ao centro e, com a destruição ligada, devora o terreno em volta. Ver {@link BlueOrbEntity}.
 */
public class LapseBlueEffect extends EffectGlyph {
    public LapseBlueEffect() {
        super("lapse_blue", 26);
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
        if (!LcUtil.budget(ctx.level(), ctx.caster(), "blue", 1)) return;
        Vec3 at;
        if (LcUtil.selfTarget(ctx, target)) {
            Vec3 eye = ctx.eyePos();
            Vec3 to = eye.add(ctx.look().scale(6));
            BlockHitResult bh = ctx.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
            at = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation().subtract(ctx.look().scale(1.0)) : to;
        } else {
            at = LcUtil.center(target);
        }
        BlueOrbEntity.spawn(ctx, stats, at);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        if (LcUtil.selfBlock(ctx, hit)) return;
        if (!LcUtil.budget(ctx.level(), ctx.caster(), "blue", 1)) return;
        Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        BlueOrbEntity.spawn(ctx, stats, hit.getLocation().add(n.scale(1.0)));
    }
}
