package com.renanpontes.isekaimahou.feature.class_shinigami;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Os 5 arquétipos de Shikai (escolhidos no Mundo Interior, no rank II). Cada um tem o seu espírito (nome próprio), a
 * paisagem do Mundo Interior, o glifo de efeito de origem (a "assinatura" da lâmina), o comando de liberação e a Bankai.
 * Homenagens com nomes próprios:
 * <ul>
 *   <li>{@link #WEIGHT} Peso — cada corte dobra o peso do alvo (Wabisuke);</li>
 *   <li>{@link #PETALS} Pétalas de Lâmina — mil pétalas cortantes (Senbonzakura);</li>
 *   <li>{@link #FROST} Gelo — a dança do gelo (Sode no Shirayuki / Hyōrinmaru);</li>
 *   <li>{@link #FLAME} Chama Ancestral — o fogo mais antigo (Ryūjin Jakka);</li>
 *   <li>{@link #BRUSH} Pincel Branco — tinta que apaga o nome (Shirafude Ichimonji).</li>
 * </ul>
 */
public enum Archetype {
    WEIGHT("weight", "shikai_weight", Element.EARTH, 0x8A93A8, 0x2B2F3A),
    PETALS("petals", "shikai_petals", Element.WIND, 0xFF8FC0, 0x5A1E3C),
    FROST("frost", "shikai_frost", Element.WATER, 0xA8ECFF, 0x2A5A7A),
    FLAME("flame", "shikai_flame", Element.FIRE, 0xFF7A2E, 0x5A140A),
    BRUSH("brush", "shikai_white_brush", Element.SHADOW, 0xF2F2F2, 0x14121A);

    private static final String KEY = "shinigami.isekai_mahou.archetype.";
    private static final String SPIRIT_KEY = "shinigami.isekai_mahou.spirit.";

    public final String id;
    /** Id do glifo de efeito de origem (a assinatura da Shikai). */
    public final String glyph;
    public final Element element;
    public final int rgb;
    /** Cor escura (tinta, sombra, fumaça) para partículas e a paisagem. */
    public final int dark;

    Archetype(String id, String glyph, Element element, int rgb, int dark) {
        this.id = id;
        this.glyph = glyph;
        this.element = element;
        this.rgb = rgb;
        this.dark = dark;
    }

    /** Índice salvo no contador {@code shinigami.archetype} (1..5). */
    public int counterValue() {
        return ordinal() + 1;
    }

    /** "Peso", "Pétalas de Lâmina"... */
    public MutableComponent displayName() {
        return Component.translatable(KEY + id).withColor(rgb);
    }

    public MutableComponent description() {
        return Component.translatable(KEY + id + ".desc");
    }

    /** Passiva da Shikai liberada (o que cada corte com a espada faz). */
    public MutableComponent shikaiPassive() {
        return Component.translatable(KEY + id + ".shikai");
    }

    /** O que a Bankai faz em volta. */
    public MutableComponent bankaiPassive() {
        return Component.translatable(KEY + id + ".bankai");
    }

    /** Nome do espírito da espada (ex.: "Chiribana"). */
    public MutableComponent spiritName() {
        return Component.translatable(SPIRIT_KEY + id).withColor(rgb);
    }

    /** Comando de liberação da Shikai (ex.: "Disperse-se, Chiribana!"). */
    public MutableComponent command() {
        return Component.translatable(KEY + id + ".command", spiritName());
    }

    /** Nome da Bankai (ex.: "Senka Chiribana"). */
    public MutableComponent bankaiName() {
        return Component.translatable(KEY + id + ".bankai_name").withColor(rgb);
    }

    /** Nome da paisagem do Mundo Interior. */
    public MutableComponent landscape() {
        return Component.translatable(KEY + id + ".landscape");
    }

    @Nullable
    public static Archetype byIndex(int i) {
        Archetype[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }

    @Nullable
    public static Archetype byId(String id) {
        for (Archetype a : values()) if (a.id.equalsIgnoreCase(id)) return a;
        return null;
    }
}
