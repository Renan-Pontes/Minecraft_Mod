package com.renanpontes.isekaimahou.feature.farm.wand;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Modos da Varinha de Construção (agachar + clique no ar troca). */
public enum WandMode {
    /** Enche toda a caixa marcada. */
    FILL("fill", 0x7FD4FF, true),
    /** Só as 4 paredes da caixa (sem teto e sem chão). */
    WALL("wall", 0xE8A85A, true),
    /** Só a camada de baixo da caixa. */
    FLOOR("floor", 0x9BE07A, true),
    /** Troca todos os blocos do tipo do 1º canto pelo material. */
    SWAP("swap", 0xD98BFF, true),
    /** Copia a caixa (pequena) para a prancheta. */
    COPY("copy", 0xFFE27A, false),
    /** Cola a prancheta onde clicar (gira com você). */
    PASTE("paste", 0x6FF2C0, false),
    /** Desfaz a última construção. */
    UNDO("undo", 0xFF8080, false);

    private static final String KEY = "farm.isekai_mahou.wand.mode.";

    public final String id;
    public final int rgb;
    /** Precisa de material (bloco). */
    public final boolean usesMaterial;

    WandMode(String id, int rgb, boolean usesMaterial) {
        this.id = id;
        this.rgb = rgb;
        this.usesMaterial = usesMaterial;
    }

    public MutableComponent displayName() {
        return Component.translatable(KEY + id).withColor(rgb);
    }

    public MutableComponent hint() {
        return Component.translatable(KEY + id + ".hint");
    }

    public WandMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static WandMode byIndex(int i) {
        WandMode[] v = values();
        return i >= 0 && i < v.length ? v[i] : FILL;
    }
}
