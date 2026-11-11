package com.renanpontes.isekaimahou.feature.fusions_d.glyph;

import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Efeito que cria UMA manifestação por conjuração (buraco negro, flor, boca...): no alvo, no bloco atingido ou — em
 * Si Mesmo — onde você olha. Vários alvos no mesmo tick (formas de área) não multiplicam a manifestação, e o "eco"
 * de Si Mesmo no bloco sob os pés também não.
 */
abstract class FdAreaGlyph extends EffectGlyph {
    /** Alcance da mira quando a magia é lançada em si mesmo. */
    private final double selfRange;

    protected FdAreaGlyph(String id, int cost, double selfRange) {
        super(id, cost);
        this.selfRange = selfRange;
    }

    @Override
    public boolean wantsBlocks() { return true; }

    /** Ticks mínimos entre duas manifestações do mesmo conjurador (0 = sem limite; as com entidade já têm teto). */
    protected int minInterval() { return 0; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!FdUtil.once(ctx, id().getPath()) || !FdUtil.ready(ctx, id().getPath(), minInterval())) return;
        manifest(ctx, FdUtil.areaPoint(ctx, target, selfRange), target, stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        if (!FdUtil.once(ctx, id().getPath()) || !FdUtil.ready(ctx, id().getPath(), minInterval())) return;
        manifest(ctx, hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.3)), null, stats);
    }

    /** Cria a manifestação em {@code at}. {@code target} = entidade atingida (ou null se foi um bloco). */
    protected abstract void manifest(SpellContext ctx, Vec3 at, Entity target, SpellStats stats);
}
