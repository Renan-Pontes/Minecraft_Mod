package com.renanpontes.isekaimahou.glyph;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Efeito: o que acontece com cada alvo atingido pela forma.
 * {@code stats} contém os aumentos colocados LOGO DEPOIS deste efeito (Amplificar, Estender duração...).
 * Use os helpers do contexto: {@code ctx.damage(...)}, {@code ctx.heal(...)}, {@code ctx.status(...)}, {@code ElementFx}.
 */
public abstract class EffectGlyph extends Glyph {

    protected EffectGlyph(String path, int cost) {
        super(path, GlyphKind.EFFECT, cost);
    }

    /** Aplicado em uma entidade atingida. */
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {}

    /** Aplicado em um bloco atingido (só se a forma acertar blocos ou tiver o aumento Sensível). */
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {}

    /**
     * Efeito "benéfico" (cura, escudo, bênção...): por padrão afeta aliados e o próprio conjurador
     * quando a forma é Si Mesmo/área; efeitos não benéficos ignoram aliados por padrão.
     */
    public boolean isBeneficial() { return false; }

    /** Se true, este efeito também é aplicado em blocos mesmo sem o aumento Sensível (ex.: Quebrar, Colher, Erguer Pilar). */
    public boolean wantsBlocks() { return false; }

    // =====================================================================================
    //  v1.0 — números para o detalhamento ({@link ParsedSpell#breakdown()})
    // =====================================================================================

    /**
     * Dano base por acerto (o número passado a {@code ctx.damage}, antes de poder/aumentos e do
     * {@code Progression.DAMAGE_SCALE}). 0 = sem dano direto. Padrão: {@link GlyphHints}. Glifos novos devem sobrescrever.
     */
    public float baseDamage() { return Math.max(0F, GlyphHints.getOrNone(id()).damage()); }

    /** Cura base por acerto. 0 = não cura. Padrão: {@link GlyphHints}. */
    public float baseHeal() { return Math.max(0F, GlyphHints.getOrNone(id()).heal()); }

    /** Duração base em ticks (status, zonas...). &lt; 0 = instantâneo. Padrão: {@link GlyphHints}. */
    public int hintDuration() { return GlyphHints.getOrNone(id()).duration(); }

    /** Raio base em blocos (efeitos de área). &lt; 0 = alvo único. Padrão: {@link GlyphHints}. */
    public float hintRadius() { return GlyphHints.getOrNone(id()).radius(); }
}
