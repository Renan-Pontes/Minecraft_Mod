package com.renanpontes.isekaimahou.glyph.weapon;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.LivingEntity;

/**
 * Golpe: por 6 s (Estender: +50% cada), o PRÓXIMO golpe corpo a corpo do conjurador aplica os efeitos no alvo
 * atingido. Dividir = +1 golpe por cópia. A arma brilha na cor do elemento enquanto o Golpe está carregado.
 * Conjurar de novo substitui o Golpe anterior. Ao Acertar dispara no alvo do golpe (ex.: Golpe → Ao Acertar → Explosão).
 */
public class StrikeForm extends FormGlyph {
    /** Duração base do Golpe carregado (ticks). */
    public static final int BASE_TICKS = 120;

    public StrikeForm() {
        super(WeaponGlyphs.STRIKE, 4);
        element(Element.ARCANE);
        tier(1);
    }

    @Override
    public int baseCooldown() { return 20; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive()) return;
        SpellStats s = ctx.formStats();
        WeaponStrikes.arm(caster, ctx, s.count(), s.duration(BASE_TICKS));
    }
}
