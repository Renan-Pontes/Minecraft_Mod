package com.renanpontes.isekaimahou.feature.class_enchanter.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchFx;
import com.renanpontes.isekaimahou.feature.class_enchanter.Enchants;
import com.renanpontes.isekaimahou.feature.class_enchanter.entity.SovereignSanctumEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.LivingEntity;

/**
 * SANTUÁRIO DO SOBERANO (forma G do Encantador, ultimate): uma coroa de runas desce e um círculo gigante de inscrição se
 * abre no chão (raio 10; Encantador ×1,25) por 15 s (Encantador +30%). A cada segundo os efeitos pulsam em TODOS lá
 * dentro — encantamentos nos aliados (que empilham pulso a pulso até o teto), maldições/dano nos inimigos — e o
 * santuário é um campo ANTI-MAGIA para os inimigos: magias inimigas não saem lá dentro e projéteis mágicos inimigos se
 * desfazem na borda. Custo por cópia moderado de forma pulsante: 1 + √(pulsos).
 */
public class SovereignForm extends FormGlyph {
    public static final float BASE_RADIUS = 10F;
    public static final int BASE_TICKS = 300;

    public SovereignForm() {
        super("enchant_sovereign", 45);
        element(Element.ARCANE);
        origin(MagicPath.ENCHANTER);
        tier(5);
        minCircle(8);
    }

    @Override
    public int argb() { return 0xFF000000 | EnchFx.GOLD; }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public boolean isPersistent() { return true; }

    @Override
    public int baseCastTicks() { return 30; }

    @Override
    public int baseCooldown() { return 600; }

    @Override
    public float copies(SpellStats stats, int circle) {
        return SpellCost.pulseCopies(stats.duration(BASE_TICKS), 20);
    }

    @Override
    public float hintRadius() { return BASE_RADIUS; }

    @Override
    public int hintDuration() { return BASE_TICKS; }

    @Override
    public float hintRange() { return -1F; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        boolean ench = Enchants.isEnchanter(caster);
        float radius = s.radius(BASE_RADIUS) * (ench ? 1.25F : 1F);
        int ticks = Math.round(s.duration(BASE_TICKS) * (ench ? 1.3F : 1F));
        SovereignSanctumEntity.open(ctx, caster, radius, ticks);
    }
}
