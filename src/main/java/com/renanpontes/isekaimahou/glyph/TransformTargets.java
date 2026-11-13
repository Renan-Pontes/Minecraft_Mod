package com.renanpontes.isekaimahou.glyph;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;

/**
 * v1.4 — Registro do que conta como <b>transformação</b> para as {@link TransformLawGlyph Leis de Transformação}.
 * <p>
 * As transformações do mod quase sempre são EFEITOS usados com a forma Si Mesmo (Kaioken, Forma de Lobo, Forma de Lich,
 * Modo Sábio...) e algumas são FORMAS (Transcendência...). Cada módulo — ou o módulo {@code transform_laws}, em nome
 * deles — registra o id do glifo aqui, com o MobEffect que marca "estou transformado" (a sessão de leis acaba quando o
 * efeito some) e a duração base usada quando não há efeito para vigiar.
 * <pre>TransformTargets.registerEffect("kaioken", IsekaiMahou.id("enchanter_kaioken"), 600);</pre>
 * Formas de "liberação" de outras classes (Bankai, Resurrección...) podem entrar do mesmo jeito, ou chamar
 * {@link TransformLaws#begin} direto.
 */
public final class TransformTargets {
    /**
     * @param glyph     id do glifo (forma ou efeito)
     * @param probe     MobEffect presente enquanto a transformação dura (null = só pelo tempo)
     * @param baseTicks duração base quando não há {@code probe} (Estender/Encurtar da magia ajustam)
     */
    public record Target(ResourceLocation glyph, @Nullable ResourceLocation probe, int baseTicks) {}

    private static final Map<ResourceLocation, Target> FORMS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Target> EFFECTS = new ConcurrentHashMap<>();

    /** Forma que é uma transformação de quem conjura (aceita Leis de Transformação depois dela). */
    public static void registerForm(String glyphPath, @Nullable ResourceLocation probe, int baseTicks) {
        ResourceLocation id = IsekaiMahou.id(glyphPath);
        FORMS.put(id, new Target(id, probe, baseTicks));
    }

    /** Efeito que transforma quem o recebe (qualquer magia com ele no estágio 0 aceita Leis de Transformação). */
    public static void registerEffect(String glyphPath, @Nullable ResourceLocation probe, int baseTicks) {
        ResourceLocation id = IsekaiMahou.id(glyphPath);
        EFFECTS.put(id, new Target(id, probe, baseTicks));
    }

    public static boolean isForm(ResourceLocation id) {
        return FORMS.containsKey(id);
    }

    public static boolean isEffect(ResourceLocation id) {
        return EFFECTS.containsKey(id);
    }

    @Nullable
    public static Target form(ResourceLocation id) {
        return FORMS.get(id);
    }

    @Nullable
    public static Target effect(ResourceLocation id) {
        return EFFECTS.get(id);
    }

    /** O estágio 0 da magia é uma transformação? (forma registrada ou algum efeito registrado). */
    public static boolean isTransformation(@Nullable FormGlyph form, Iterable<ParsedSpell.EffectEntry> effects) {
        if (form != null && form.acceptsTransformLaws()) return true;
        for (ParsedSpell.EffectEntry e : effects) if (isEffect(e.effect().id())) return true;
        return false;
    }

    /** A transformação que a magia faz (a forma tem prioridade; senão o primeiro efeito registrado), ou null. */
    @Nullable
    public static Target of(ParsedSpell spell) {
        Target t = FORMS.get(spell.form().id());
        if (t != null) return t;
        if (spell.form().acceptsTransformLaws()) return new Target(spell.form().id(), null, Math.max(200, spell.form().hintDuration()));
        for (ParsedSpell.EffectEntry e : spell.effects()) {
            t = EFFECTS.get(e.effect().id());
            if (t != null) return t;
        }
        return null;
    }

    /**
     * Quantas Leis de Transformação cabem numa transformação: 1 (Círculos 1–2), 2 (3–5), 3 (6–8), 4 (9–10), +1 no
     * nível 100. Vale para a receita da magia e para as escolhidas na forma racial.
     */
    public static int maxLaws(int level, int circle) {
        int n = circle >= 9 ? 4 : circle >= 6 ? 3 : circle >= 3 ? 2 : 1;
        if (level >= 100) n++;
        return n;
    }

    private TransformTargets() {}
}
