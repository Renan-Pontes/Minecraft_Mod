package com.renanpontes.isekaimahou.feature.dimensions;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Portais de moldura (estilo Nether): Plano Elemental e Abismo.
 * {@code manaCost} é gasto na ativação; {@code consumesKey} = o item-chave é gasto (oferenda) ou só usado como foco.
 */
public enum PortalKind {
    /** Moldura de pedra elemental; ativada com um Cristal de Mana (foco, não é gasto) + 50 de mana. Círculo 3. */
    ELEMENTAL(DimKind.ELEMENTAL, 3, 50, false,
            () -> DimRegistry.ELEMENTAL_PORTAL_FRAME.get(), () -> DimRegistry.ELEMENTAL_PORTAL.get(),
            () -> CoreRegistries.MANA_CRYSTAL.get()),
    /** Moldura sombria; ativada oferecendo uma Essência de Sombra. Círculo 6. */
    ABYSS(DimKind.ABYSS, 6, 0, true,
            () -> DimRegistry.ABYSS_PORTAL_FRAME.get(), () -> DimRegistry.ABYSS_PORTAL.get(),
            () -> CoreRegistries.ESSENCES.get(Element.SHADOW).get());

    public final DimKind dim;
    public final int minCircle;
    public final int manaCost;
    public final boolean consumesKey;
    private final Supplier<Block> frame;
    private final Supplier<Block> portal;
    private final Supplier<Item> key;

    PortalKind(DimKind dim, int minCircle, int manaCost, boolean consumesKey,
               Supplier<Block> frame, Supplier<Block> portal, Supplier<Item> key) {
        this.dim = dim;
        this.minCircle = minCircle;
        this.manaCost = manaCost;
        this.consumesKey = consumesKey;
        this.frame = frame;
        this.portal = portal;
        this.key = key;
    }

    public Block frame() { return frame.get(); }
    public Block portal() { return portal.get(); }
    public Item key() { return key.get(); }

    /** Marcador de "já abriu este portal uma vez" (cutscene só na primeira vez). */
    public String openedFlag() {
        return "dimensions.portal_opened." + dim.id;
    }
}
