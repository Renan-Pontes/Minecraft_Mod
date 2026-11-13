package com.renanpontes.isekaimahou.glyph;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Glifo: a peça básica das magias. Tipos: {@link FormGlyph}, {@link EffectGlyph}, {@link AugmentGlyph},
 * {@link LawGlyph}, {@link TriggerGlyph}.
 *
 * Tradução: "glyph.isekai_mahou.<id>" (nome), "glyph.isekai_mahou.<id>.desc" (descrição curta).
 * Registro: {@link GlyphRegistry#register} durante o init do módulo.
 */
public abstract class Glyph {
    private final ResourceLocation id;
    private final GlyphKind kind;
    private final int cost;
    private Element element = Element.ARCANE;
    private MagicPath origin = null;
    private int minCircle = 1;
    private int tier = 1;
    private boolean legendary = false;
    private boolean hidden = false;

    protected Glyph(String path, GlyphKind kind, int cost) {
        this.id = IsekaiMahou.id(path);
        this.kind = kind;
        this.cost = cost;
    }

    // ---------- Configuração fluente ----------
    /** Elemento do glifo (ARCANE = neutro, usável em qualquer magia). */
    @SuppressWarnings("unchecked")
    public <T extends Glyph> T element(Element e) { this.element = e; return (T) this; }

    /** Glifo de Magia de Origem (Runas, Maldições...): usável em magia de qualquer elemento, se aprendido. */
    @SuppressWarnings("unchecked")
    public <T extends Glyph> T origin(MagicPath p) { this.origin = p; return (T) this; }

    /** Círculo mínimo para usar o glifo numa magia. */
    @SuppressWarnings("unchecked")
    public <T extends Glyph> T minCircle(int c) { this.minCircle = c; return (T) this; }

    /**
     * Tier no caminho do elemento (quando o módulo de Caminhos libera o glifo):
     * 1 = Despertar, 2 = Aprendiz, 3 = Adepto, 4 = Mestre, 5 = Arquimago (lendário).
     * Para glifos ARCANOS (neutros) o tier define o Círculo em que são liberados (1→C1, 2→C2, 3→C3, 4→C5, 5→C8).
     */
    @SuppressWarnings("unchecked")
    public <T extends Glyph> T tier(int t) { this.tier = Math.max(1, Math.min(5, t)); return (T) this; }

    @SuppressWarnings("unchecked")
    public <T extends Glyph> T legendary() { this.legendary = true; this.tier = 5; return (T) this; }

    // ---------- Getters ----------
    public ResourceLocation id() { return id; }
    public GlyphKind kind() { return kind; }
    /** Custo de mana somado ao custo da magia. Aumentos podem ter custo negativo (ex.: Enfraquecer). */
    public int cost() { return cost; }
    public Element element() { return element; }
    public MagicPath origin() { return origin; }
    public int minCircle() { return minCircle; }
    public int tier() { return tier; }
    public boolean isLegendary() { return legendary; }

    /**
     * v1.4: glifo APOSENTADO — continua registrado (magias e mundos antigos carregam e funcionam), mas some das listas
     * do Grimório (paleta e aba Glifos), a não ser que o jogador ainda o use numa magia salva. Ver {@link GlyphRegistry#hide}.
     */
    public boolean isHidden() { return hidden; }

    /** Marca o glifo como aposentado (ver {@link #isHidden()}). */
    @SuppressWarnings("unchecked")
    public <T extends Glyph> T hidden() { this.hidden = true; return (T) this; }

    public String translationKey() { return "glyph." + id.getNamespace() + "." + id.getPath(); }
    public MutableComponent displayName() { return Component.translatable(translationKey()); }
    public MutableComponent description() { return Component.translatable(translationKey() + ".desc"); }

    /** Cor para desenhar o ícone do glifo. */
    public int argb() { return element.argb(); }

    @Override
    public String toString() { return id.toString(); }
}
