package com.renanpontes.isekaimahou.feature.transform_laws;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

/**
 * Forma <b>Transformação</b> (release 1.0, onda 4): a transformação montada no Grimório, como o Domínio.
 * {@code Transformação [aumentos] → EFEITOS [aumentos] → Leis de Transformação}. Regras em {@link TransformForge}.
 * <ul>
 *   <li>Arcana (o elemento da magia vem dos efeitos e define aura, partículas e cor), Círculo
 *       {@value TransformForge#MIN_CIRCLE}; aprendida pelo caminho Arcano (tier 4) como qualquer glifo neutro;</li>
 *   <li>dura 30 s (Estender/Persistente até 60 s; Encurtar diminui), recarga de 120 s + a do custo, 1,5 s de
 *       conjuração; Expandir dá alcance à aura;</li>
 *   <li>v1.1: um {@link TransformBody corpo} (O Todo-Poderoso, Bankai...) dita a duração e pode pedir recarga maior;</li>
 *   <li>custo: os efeitos contam como pulsos ao longo da duração ({@link SpellCost#pulseCopies}), como no Domínio;</li>
 *   <li>não é ultimate (qualquer tecla) e não aceita ligações (ela não dispara estágios). A versão ultimate é a
 *       {@link Supreme Transformação Suprema} (tecla G), exigida pelos corpos que eram formas ultimate.</li>
 * </ul>
 * Exemplo ("Corpo em Brasa"): Transformação → Estender → Incendiar → Curar → Lei: Corpo de Vidro.
 */
public class TransformationForm extends FormGlyph {
    public TransformationForm() {
        this(TransformForge.FORM, 40, 4);
    }

    protected TransformationForm(String path, int cost, int tier) {
        super(path, cost);
        element(Element.ARCANE);
        tier(tier);
        minCircle(TransformForge.MIN_CIRCLE);
    }

    @Override
    public boolean acceptsTransformLaws() { return true; }

    /** Onda 5: funciona sem efeitos (só aumentos de tempo e/ou leis por cima de uma transformação). */
    @Override
    public boolean requiresEffect() { return false; }

    /** Onda 5 (núcleo novo, FormGlyph.cooldownMult): Acelerar/Retardar no corpo mexem na recarga. */
    @Override
    public float cooldownMult(SpellStats stats) { return TransformForge.cooldownMult(stats); }

    /** Núcleo novo (FormGlyph.allowsLinks): sem ligações depois desta forma. */
    @Override
    public boolean allowsLinks() { return false; }

    @Override
    public int baseCastTicks() { return 30; }

    @Override
    public int baseCooldown() { return TransformForge.BASE_COOLDOWN; }

    @Override
    public float copies(SpellStats stats, int circle) {
        // recarga mais curta = mais usos = pulsos mais caros
        return SpellCost.pulseCopies(TransformForge.duration(stats), 40) * TransformForge.speedCostMult(stats);
    }

    @Override
    public float hintRange() { return -1F; }

    @Override
    public float hintRadius() { return -1F; }

    @Override
    public int hintDuration() { return TransformForge.BASE_DURATION; }

    @Override
    public void cast(SpellContext ctx) {
        ForgeSessions.beginCast(ctx);
    }

    /**
     * v1.1 — <b>Transformação Suprema</b>: a mesma forma, mas ULTIMATE (só na tecla G, gasta a Carga de Ultimate). É onde
     * entram os corpos que eram formas G (O Todo-Poderoso, Bankai, Vollständig, Vasto Lorde, Letzt Stil, Monarca das
     * Sombras, Transcendência). Quem conhece um desses corpos aprende esta forma sozinho ({@link ForgeBodies}).
     */
    public static class Supreme extends TransformationForm {
        public Supreme() {
            super(TransformForge.SUPREME, 60, 5);
        }

        @Override
        public boolean isUltimate() { return true; }
    }
}
