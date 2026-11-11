package com.renanpontes.isekaimahou.feature.legends_f.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.feature.legends_f.necro.MonarchState;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Monarca das Sombras (forma ULTIMATE do Necromante, slot G; homenagem a Solo Leveling): por 30 s (Estender aumenta,
 * até 60 s) TODO o exército sai da sombra de graça — até quem estava se refazendo —, os soldados ficam ×1,5 mais
 * fortes e resistentes, você ganha a armadura de sombras do Monarca, inimigos por perto sentem medo, os efeitos da
 * receita pulsam a cada 2 s nos inimigos a até 8 blocos e toda criatura que morrer a até 24 blocos é erguida na hora.
 */
public class MonarchForm extends FormGlyph {
    public MonarchForm() {
        super("necro_monarch", 110);
        element(Element.SHADOW);
        origin(MagicPath.NECROMANCER);
        legendary();
        minCircle(8);
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public int baseCastTicks() { return 20; }

    @Override
    public int baseCooldown() { return 900; }

    /** Os efeitos pulsam a cada 2 s durante a forma: cópias moderadas (1 + √pulsos), como os Domínios. */
    @Override
    public float copies(SpellStats stats, int circle) {
        int ticks = Mth.clamp(stats.duration(600), 300, 1200);
        return 1F + (float) Math.sqrt(ticks / 40.0);
    }

    @Override
    public float hintRadius() { return (float) MonarchState.PULSE_RADIUS; }

    @Override
    public int hintDuration() { return 600; }

    @Override
    public float hintRange() { return 0F; }

    @Override
    public void cast(SpellContext ctx) {
        if (!(ctx.caster() instanceof ServerPlayer sp) || LfUtil.isFake(sp) || !sp.isAlive() || sp.level() != ctx.level()) return;
        if (MonarchState.active(sp)) return;
        int ticks = Mth.clamp(ctx.formStats().duration(600), 300, 1200);
        MonarchState.start(sp, ctx, ticks);
    }
}
