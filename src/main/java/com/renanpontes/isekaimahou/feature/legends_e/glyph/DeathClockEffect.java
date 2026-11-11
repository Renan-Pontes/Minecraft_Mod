package com.renanpontes.isekaimahou.feature.legends_e.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_e.LeUtil;
import com.renanpontes.isekaimahou.feature.legends_e.entity.DeathClockEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * O Fim de Toda Vida (Sombra, lendário, Círculo 9): um relógio gigante de 12 marcas surge no céu sobre a área e o
 * ponteiro avança uma marca por segundo, com sinos cada vez mais graves. Quando completa a volta (12 s), todo inimigo
 * comum que AINDA estiver na área morre; chefes e jogadores perdem 35% da vida máxima. Quem foge da sombra do relógio
 * escapa. Um relógio por área (vários alvos juntos dividem o mesmo).
 */
public class DeathClockEffect extends EffectGlyph {
    public DeathClockEffect() {
        super("death_clock", 80);
        element(Element.SHADOW);
        legendary();
        minCircle(9);
    }

    @Override
    public boolean wantsBlocks() { return true; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        DeathClockEntity.spawn(ctx, LeUtil.groundBelow(ctx.level(), target.position().add(0, 0.5, 0), 24), stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        Vec3 p = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.3));
        DeathClockEntity.spawn(ctx, LeUtil.groundBelow(ctx.level(), p.add(0, 0.2, 0), 24), stats);
    }
}
