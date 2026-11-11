package com.renanpontes.isekaimahou.feature.legends_e.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_e.LeUtil;
import com.renanpontes.isekaimahou.feature.legends_e.entity.FallenPillarEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Queda Final (Luz, lendário, Círculo 7): um círculo se acende no chão e no céu, e um pilar descomunal de luz azul-branca
 * desce do firmamento. Quem está dentro é vaporizado (dano altíssimo que fura armadura), o terreno vira um poço de vidro
 * (cilindro VITRIFY + ERASE, se a destruição estiver ligada) e uma onda de calor varre os arredores.
 * Vários alvos atingidos juntos pela mesma magia dividem o mesmo pilar (um por região).
 */
public class FallenDownEffect extends EffectGlyph {
    public FallenDownEffect() {
        super("fallen_down", 70);
        element(Element.LIGHT);
        legendary();
        minCircle(7);
    }

    @Override
    public boolean wantsBlocks() { return true; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        Vec3 ground = LeUtil.groundBelow(ctx.level(), target.position().add(0, 0.5, 0), 24);
        FallenPillarEntity.spawn(ctx, ground, stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        Vec3 at = hit.getLocation();
        Vec3 ground = hit.getDirection() == Direction.UP ? at
                : LeUtil.groundBelow(ctx.level(), at.add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.4)), 24);
        FallenPillarEntity.spawn(ctx, ground, stats);
    }
}
