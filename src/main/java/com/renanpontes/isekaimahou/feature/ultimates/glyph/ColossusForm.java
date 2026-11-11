package com.renanpontes.isekaimahou.feature.ultimates.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.ultimates.UltFx;
import com.renanpontes.isekaimahou.feature.ultimates.entity.SpectralColossusEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Colosso (ultimate, slot G): um gigante espectral de ~12 blocos, translúcido na cor do elemento, surge do chão em
 * volta de quem conjura e o acompanha por 30 s (Estender aumenta, até 60 s): cortes em arco largos e pisões que
 * aplicam os efeitos da receita, e o escudo bloqueia projéteis que vêm de frente. Só um Colosso por conjurador.
 */
public class ColossusForm extends FormGlyph {
    public ColossusForm() {
        super("colossus", 110);
        element(Element.ARCANE);
        tier(4);
        minCircle(6);
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public int baseCastTicks() { return 25; }

    @Override
    public int baseCooldown() { return 600; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !UltFx.casterReady(ctx)) return;
        int ticks = Mth.clamp(ctx.formStats().duration(600), 300, 1200);
        SpectralColossusEntity.summon(ctx, caster, ticks);
    }
}
