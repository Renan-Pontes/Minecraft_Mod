package com.renanpontes.isekaimahou.feature.domain;

import java.util.function.IntPredicate;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.feature.domain.entity.DomainDomeEntity;
import com.renanpontes.isekaimahou.feature.domain.glyph.CataclysmForm;
import com.renanpontes.isekaimahou.feature.domain.glyph.DomainExpansionForm;
import com.renanpontes.isekaimahou.feature.domain.glyph.SimpleDomainForm;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.LawGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;

/**
 * Glifos do módulo: as formas Cataclismo, Expansão de Domínio, Domínio Simples e as Leis de Domínio.
 * Todos são ARCANOS (neutros): servem em magia de qualquer elemento.
 */
public final class DomainGlyphs {
    // Ids das Leis (lidas por DomainExpansionForm via ParsedSpell.hasLaw)
    public static final String SURE_HIT = "law_sure_hit";
    public static final String NO_ESCAPE = "law_no_escape";
    public static final String TERRITORY = "law_territory";
    public static final String NULLIFY = "law_nullify";
    public static final String SLOW_TIME = "law_slow_time";
    public static final String GRAVITY = "law_gravity";
    public static final String LANDSCAPE = "law_landscape";
    public static final String OPEN_BARRIER = "law_open_barrier";
    public static final String ABSOLUTE = "law_absolute";
    /** v0.8: Lei Ruína (destruição progressiva + cratera ao fechar). A regra roda por {@link RuinLaw} (registro do núcleo). */
    public static final String RUIN = RuinLaw.ID;
    /** Lei do legends_c que força o domínio a ser aberto e com raio ×1,5 (Santuário Maligno). */
    public static final String SHRINE = "law_shrine";

    public static void register() {
        GlyphRegistry.register(new CataclysmForm());
        GlyphRegistry.register(new DomainExpansionForm());
        GlyphRegistry.register(new SimpleDomainForm());

        // Leis: liberadas junto com a Expansão de Domínio; Domínio Absoluto só no último Círculo
        int domain = firstCircle(Progression::domainAllowed);
        law(SURE_HIT, 30, domain);
        law(NO_ESCAPE, 20, domain);
        law(TERRITORY, 25, domain);
        law(NULLIFY, 30, domain);
        law(SLOW_TIME, 20, domain);
        law(GRAVITY, 20, domain);
        law(LANDSCAPE, 15, domain);
        law(OPEN_BARRIER, 15, domain);
        law(ABSOLUTE, 60, 10);
        law(RUIN, 45, Math.max(domain, 6));
        Domains.registerLaw(IsekaiMahou.id(RUIN), new RuinLaw());
    }

    /** Primeiro Círculo (1–10) em que a regra de progressão libera algo (ex.: {@code Progression::domainAllowed}). */
    public static int firstCircle(IntPredicate allowed) {
        for (int c = 1; c <= 10; c++) if (allowed.test(c)) return c;
        return 10;
    }

    private static void law(String id, int cost, int minCircle) {
        LawGlyph g = new LawGlyph(id, cost);
        g.element(Element.ARCANE);
        g.tier(5);
        g.minCircle(minCircle);
        GlyphRegistry.register(g);
    }

    /** Bits de lei que o domo guarda (e sincroniza com o cliente para o visual). */
    public static int lawFlags(ParsedSpell spell) {
        int f = 0;
        if (spell.hasLaw(SURE_HIT)) f |= DomainDomeEntity.F_SURE_HIT;
        if (spell.hasLaw(NO_ESCAPE)) f |= DomainDomeEntity.F_NO_ESCAPE;
        if (spell.hasLaw(TERRITORY)) f |= DomainDomeEntity.F_TERRITORY;
        if (spell.hasLaw(NULLIFY)) f |= DomainDomeEntity.F_NULLIFY;
        if (spell.hasLaw(SLOW_TIME)) f |= DomainDomeEntity.F_SLOW;
        if (spell.hasLaw(GRAVITY)) f |= DomainDomeEntity.F_GRAVITY;
        if (spell.hasLaw(LANDSCAPE)) f |= DomainDomeEntity.F_LANDSCAPE;
        if (spell.hasLaw(OPEN_BARRIER) || spell.hasLaw(SHRINE)) f |= DomainDomeEntity.F_OPEN;
        if (spell.hasLaw(RUIN)) f |= DomainDomeEntity.F_RUIN;
        if (spell.hasLaw(ABSOLUTE)) f |= DomainDomeEntity.F_ABSOLUTE;
        return f;
    }

    private DomainGlyphs() {}
}
