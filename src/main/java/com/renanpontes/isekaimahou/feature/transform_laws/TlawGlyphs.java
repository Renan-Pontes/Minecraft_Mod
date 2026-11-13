package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;

import net.minecraft.resources.ResourceLocation;

/**
 * As 13 <b>Leis de Transformação</b> (v1.4). Todas Arcanas (valem para qualquer elemento), tipo LAW, no fim da receita de
 * uma magia de transformação ou escolhidas na aba Votos para a Transformação Racial. Cada uma tem BÔNUS e PREÇO — as
 * regras estão em {@link TlawRules}. As mais fortes pedem Círculos altos; Transcender só no nível 100.
 */
public final class TlawGlyphs {
    public static final String GLASS_BODY = "tlaw_glass_body";
    public static final String PREDATOR_EYES = "tlaw_predator_eyes";
    public static final String BLIND_FURY = "tlaw_blind_fury";
    public static final String BLOODLUST = "tlaw_bloodlust";
    public static final String UNSTABLE_FORM = "tlaw_unstable_form";
    public static final String IRON_ROOT = "tlaw_iron_root";
    public static final String SILENCE_VOW = "tlaw_silence_vow";
    public static final String LAST_BREATH = "tlaw_last_breath";
    public static final String RELENTLESS_RHYTHM = "tlaw_relentless_rhythm";
    public static final String MANA_PACT = "tlaw_mana_pact";
    public static final String GUARDIAN_OATH = "tlaw_guardian_oath";
    public static final String BRIEF_FLAME = "tlaw_brief_flame";
    public static final String TRANSCEND = "tlaw_transcend";

    /** Ordem de exibição (da mais simples à mais forte). */
    public static final List<String> ORDER = List.of(GLASS_BODY, PREDATOR_EYES, BLIND_FURY, BLOODLUST, UNSTABLE_FORM,
            IRON_ROOT, SILENCE_VOW, LAST_BREATH, RELENTLESS_RHYTHM, MANA_PACT, GUARDIAN_OATH, BRIEF_FLAME, TRANSCEND);

    private static final List<TransformLawGlyph> ALL = new ArrayList<>();

    static void register() {
        // Release 1.0: a forma Transformação (montada no Grimório como o Domínio) — regras em TransformForge
        GlyphRegistry.register(new TransformationForm());
        //  id               mana  Círculo  tier  cor
        law(GLASS_BODY,         8, 1, 2, 0x9FE8FF);
        law(PREDATOR_EYES,      8, 1, 2, 0xFFB347);
        law(BLIND_FURY,        12, 2, 3, 0xFF3B3B);
        law(BLOODLUST,         12, 2, 3, 0xB0102A);
        law(UNSTABLE_FORM,     14, 2, 3, 0xC77DFF);
        law(IRON_ROOT,         14, 3, 3, 0x8C8C8C);
        law(SILENCE_VOW,       18, 3, 4, 0xE8E8F0);
        law(LAST_BREATH,       16, 3, 4, 0x7FD8FF);
        law(RELENTLESS_RHYTHM, 16, 4, 4, 0xFF7A1A);
        law(MANA_PACT,         20, 4, 4, 0x4A8CFF);
        law(GUARDIAN_OATH,     20, 5, 5, 0xFFD86B);
        law(BRIEF_FLAME,       18, 5, 5, 0xFF5A1F);
        TransformLawGlyph t = law(TRANSCEND, 60, 8, 5, 0xFFF4C0);
        t.minLevel(100);
        t.legendary();
    }

    private static TransformLawGlyph law(String id, int cost, int minCircle, int tier, int rgb) {
        TransformLawGlyph g = new TransformLawGlyph(id, cost);
        g.element(Element.ARCANE);
        g.tier(tier);
        g.minCircle(minCircle);
        g.rgb(rgb);
        GlyphRegistry.register(g);
        ALL.add(g);
        return g;
    }

    /** Todas as leis deste módulo, na ordem de exibição. */
    public static List<TransformLawGlyph> all() {
        return Collections.unmodifiableList(ALL);
    }

    @Nullable
    public static TransformLawGlyph get(String path) {
        Glyph g = GlyphRegistry.get(IsekaiMahou.id(path));
        return g instanceof TransformLawGlyph t ? t : null;
    }

    @Nullable
    public static TransformLawGlyph get(ResourceLocation id) {
        Glyph g = GlyphRegistry.get(id);
        return g instanceof TransformLawGlyph t ? t : null;
    }

    private TlawGlyphs() {}
}
