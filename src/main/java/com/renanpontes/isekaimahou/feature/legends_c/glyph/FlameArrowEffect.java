package com.renanpontes.isekaimahou.feature.legends_c.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_c.LcUtil;
import com.renanpontes.isekaimahou.feature.legends_c.entity.FugaArrowEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Fuga — FOGO lendário, C6: um arco de chamas se forma na sua mão e dispara uma flecha flamejante que queima o chão
 * pelo caminho e explode numa COLUNA DE FOGO com cratera. Com Si Mesmo, atira para onde você olha; num alvo, mira nele.
 * Ver {@link FugaArrowEntity}.
 */
public class FlameArrowEffect extends EffectGlyph {
    public FlameArrowEffect() {
        super("flame_arrow", 60);
        element(Element.FIRE);
        legendary();
        minCircle(6);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (LcUtil.selfTarget(ctx, target) || ctx.isAlly(target)) {
            FugaArrowEntity.spawn(ctx, stats, null, null);
        } else {
            FugaArrowEntity.spawn(ctx, stats, target instanceof LivingEntity le ? le : null, LcUtil.center(target));
        }
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        if (LcUtil.selfBlock(ctx, hit)) return;
        FugaArrowEntity.spawn(ctx, stats, null, hit.getLocation());
    }
}
