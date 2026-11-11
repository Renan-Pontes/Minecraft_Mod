package com.renanpontes.isekaimahou.feature.legends_e.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_e.LeUtil;
import com.renanpontes.isekaimahou.feature.legends_e.entity.BlackGoatStormEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Iä! Cabra Negra (Sombra, lendário, Círculo 8): um vento negro gira em volta do ponto atingido e um selo da Cabra Negra
 * se abre no chão. Depois de 2,5 s, todo inimigo comum (não-chefe) com até 40 de vida (×poder) na área é sacrificada e
 * morre na hora; os outros inimigos sofrem dano sombrio e Corrupção. Para cada 6 sacrifícios (mínimo 1) brota do selo
 * um Filhote Sombrio que luta por você por 60 s.
 */
public class BlackGoatEffect extends EffectGlyph {
    public BlackGoatEffect() {
        super("black_goat", 75);
        element(Element.SHADOW);
        legendary();
        minCircle(8);
    }

    @Override
    public boolean wantsBlocks() { return true; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        BlackGoatStormEntity.spawn(ctx, LeUtil.groundBelow(ctx.level(), target.position().add(0, 0.5, 0), 16), stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        Vec3 p = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.3));
        BlackGoatStormEntity.spawn(ctx, LeUtil.groundBelow(ctx.level(), p.add(0, 0.2, 0), 16), stats);
    }
}
