package com.renanpontes.isekaimahou.feature.automation.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Base dos glifos utilitários: o efeito trabalha numa ÁREA em volta de onde a forma acertou — o bloco atingido
 * (Toque no chão, projétil, o tampo do pedestal) ou a criatura atingida. Assim qualquer forma serve:
 * Si Mesmo (em volta de você), Toque, Projétil, Nova, Zona...
 */
public abstract class AreaEffect extends EffectGlyph {
    protected AreaEffect(String path, int cost) {
        super(path, cost);
    }

    /** Mexe em blocos sem precisar do aumento Sensível. */
    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        onTarget(ctx, target, stats);
        BlockPos center = target.blockPosition();
        if (AutoArea.claimArea(ctx, id().getPath(), center, 1)) work(ctx, stats, center, target.position(), target);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        BlockPos center = hit.getBlockPos();
        if (AutoArea.claimArea(ctx, id().getPath(), center, 1)) work(ctx, stats, center, hit.getLocation(), null);
    }

    /** Extra só para a criatura atingida (ex.: Forja Arcana queima, Chamar Chuva encharca). */
    protected void onTarget(SpellContext ctx, Entity target, SpellStats stats) {}

    /**
     * O trabalho na área.
     * @param center bloco central (bloco atingido ou os pés da criatura)
     * @param at     ponto exato do acerto
     * @param hit    criatura atingida (null = bloco)
     */
    protected abstract void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit);
}
