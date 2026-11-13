package com.renanpontes.isekaimahou.feature.class_hollow.tech;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.class_hollow.Hollow;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * v1.4 — as TÉCNICAS do Hollow (lado comum: o servidor executa, o cliente mostra no HUD/roda/aba). Não são mais glifos de
 * forma para montar magia: são INATAS, prontas para usar, com teclas próprias:
 * <pre>
 *  Tecla "Instinto Hollow" (R)          toque = Bala · segurar = Cero (carrega na boca/mão; soltar dispara)
 *  agachado + R                         toque = Pesquisa · segurar = Gran Rey Cero (Vasto Lorde+)
 *  correndo + R                         Sonído (até 3 passos seguidos)
 *  liberado (Arrancar) + carga máxima   o Cero vira Cero Oscuras
 *  Roda Hollow (H)                      Pesquisa, Regeneração, Hierro, Garganta, Negación, Descorrer, Resurrección
 * </pre>
 * O rank mínimo (0 Hollow · 1 Gillian · 2 Adjuchas · 3 Vasto Lorde · 4 Arrancar) é a única porta: não precisa aprender
 * nada. Os glifos antigos ({@link #legacyGlyph}) continuam registrados e, numa magia salva, executam a mesma técnica.
 */
public enum HollowTech {
    CERO("cero", 0, false, Hollow.CERO, 0xE01030),
    BALA("bala", 0, false, Hollow.BALA, 0xFF5A6A),
    GRAN_REY_CERO("gran_rey_cero", 3, false, Hollow.GRAN_REY_CERO, 0xA00018),
    CERO_OSCURAS("cero_oscuras", 4, false, Hollow.CERO, 0x2A0A2E),
    SONIDO("sonido", 2, false, Hollow.SONIDO, 0x3A1010),
    PESQUISA("pesquisa", 0, true, Hollow.PESQUISA, 0x8A1020),
    REGEN("regen", 0, true, Hollow.REGEN, 0xB01020),
    HIERRO("hierro", 1, true, Hollow.HIERRO, 0xF2EEE6),
    GARGANTA("garganta", 2, true, Hollow.GARGANTA, 0x1A0608),
    NEGACION("negacion", 3, true, Hollow.PESQUISA, 0xFFE9A0),
    DESCORRER("descorrer", 4, true, Hollow.DESCORRER, 0x6A0010),
    RESURRECCION("resurreccion", 4, true, Hollow.RESURRECCION, 0xD0142C);

    // ------------------------------------------------------------------ tempos (ticks) da tecla R
    /** Até isto, soltar R conta como TOQUE (Bala / Pesquisa). */
    public static final int TAP_MAX = 5;
    /** Carga mínima do Cero (abaixo disto, ainda sai uma Bala). */
    public static final int CERO_MIN = 10;
    /** Carga cheia do Cero. */
    public static final int CERO_FULL = 36;
    /** Carga do Cero Oscuras (liberado, Arrancar): segurar até aqui. */
    public static final int OSCURAS_FULL = 44;
    /** Carga do Gran Rey Cero (selado / liberado). */
    public static final int GRAN_REY_MIN = 30, GRAN_REY_MIN_RELEASED = 20;
    /** Segurar mais que isto dispara sozinho. */
    public static final int MAX_HOLD = 100;
    /** Passos de Sonído seguidos antes da recarga. */
    public static final int SONIDO_CHAIN = 3;

    public final String id;
    /** Rank mínimo do Hollow. */
    public final int rank;
    /** Aparece na Roda Hollow (H). */
    public final boolean wheel;
    /** Glifo de forma antigo que dá o contexto da técnica (e que, numa magia salva, a executa). */
    public final String legacyGlyph;
    public final int rgb;

    HollowTech(String id, int rank, boolean wheel, String legacyGlyph, int rgb) {
        this.id = id;
        this.rank = rank;
        this.wheel = wheel;
        this.legacyGlyph = legacyGlyph;
        this.rgb = rgb;
    }

    public ResourceLocation legacyId() {
        return IsekaiMahou.id(legacyGlyph);
    }

    public MutableComponent displayName() {
        return Component.translatable("hollow_tech.isekai_mahou." + id);
    }

    public MutableComponent description() {
        return Component.translatable("hollow_tech.isekai_mahou." + id + ".desc");
    }

    /** Como usar (a tecla), para a aba e a roda. */
    public MutableComponent howTo() {
        return Component.translatable("hollow_tech.isekai_mahou." + id + ".how");
    }

    /** Ícone 32×32 do HUD/roda. */
    public ResourceLocation icon() {
        return IsekaiMahou.id("textures/gui/hollow_tech/" + id + ".png");
    }

    /**
     * Pode usar agora (rank e condição)? {@code released} = a Resurrección está ativa. Não confere recarga nem Vigor.
     * Gran Rey Cero: Vasto Lorde em diante (mais barato e rápido liberado). Cero Oscuras: só Arrancar liberado.
     * Resurrección: precisa ter escolhido o arquétipo.
     */
    public boolean unlocked(MagicData d, boolean released) {
        int r = Hollow.rank(d);
        if (r < rank) return false;
        return switch (this) {
            case CERO_OSCURAS -> released;
            case RESURRECCION -> Hollow.release(d) != null;
            default -> true;
        };
    }

    @Nullable
    public static HollowTech byIndex(int i) {
        HollowTech[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }

    @Nullable
    public static HollowTech byId(String id) {
        for (HollowTech t : values()) if (t.id.equals(id)) return t;
        return null;
    }

    /** As técnicas da Roda Hollow, na ordem (sentido horário a partir do topo). */
    public static HollowTech[] wheelTechs() {
        return new HollowTech[] {PESQUISA, REGEN, HIERRO, GARGANTA, NEGACION, DESCORRER, RESURRECCION};
    }
}
