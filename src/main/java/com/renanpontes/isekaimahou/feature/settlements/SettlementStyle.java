package com.renanpontes.isekaimahou.feature.settlements;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Os 3 estilos de cidade (docs/V10_DESIGN.md §0/§3 "settlements"):
 * <ul>
 *   <li><b>Reino</b> — muralha de pedra alta, guardas com mais armadura, ritmo normal;</li>
 *   <li><b>Desértico</b> — muros simples de arenito e MARAVILHAS (Obelisco, Pirâmide, Esfinge) com bônus fortes;
 *       constrói um pouco mais devagar (as maravilhas compensam);</li>
 *   <li><b>Nórdico</b> — paliçada de madeira, rústico, constrói rápido e sobe de nível com menos prosperidade.</li>
 * </ul>
 * As cidades conquistadas herdam o estilo do reino (Aldória = Reino, Sahra = Desértico, Nordheim = Nórdico).
 */
public enum SettlementStyle {
    KINGDOM("kingdom", 0x4F7FE8, 1.0F, 1.0F, 4, 4),
    DESERT("desert", 0xE8A440, 0.9F, 1.0F, 4, 0),
    NORDIC("nordic", 0x8ED0F0, 1.4F, 0.65F, 3, 0);

    public final String id;
    /** Cor do estilo (letreiro do lugar, cristal do coração, interface). */
    public final int color;
    /** Multiplicador da velocidade dos construtores. */
    public final float buildSpeed;
    /** Multiplicador da prosperidade exigida para subir de nível (Nórdico: fácil de upar). */
    public final float levelCost;
    /** Altura da abertura do portão (a grade que fecha). */
    public final int gateHeight;
    /** Armadura extra dos guardas (muralha de pedra do Reino). */
    public final int guardArmor;

    SettlementStyle(String id, int color, float buildSpeed, float levelCost, int gateHeight, int guardArmor) {
        this.id = id;
        this.color = color;
        this.buildSpeed = buildSpeed;
        this.levelCost = levelCost;
        this.gateHeight = gateHeight;
        this.guardArmor = guardArmor;
    }

    public MutableComponent displayName() {
        return SettlementsLang.tr("style." + id).withColor(color);
    }

    public MutableComponent description() {
        return SettlementsLang.tr("style." + id + ".desc");
    }

    public MutableComponent bonus() {
        return SettlementsLang.tr("style." + id + ".bonus");
    }

    /** Bloco que preenche o chão por baixo dos prédios (fundação). */
    public BlockState foundation() {
        return switch (this) {
            case DESERT -> Blocks.SANDSTONE.defaultBlockState();
            case NORDIC -> Blocks.COBBLESTONE.defaultBlockState();
            default -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    /** Grade que fecha o portão. */
    public BlockState portcullis() {
        return this == NORDIC ? Blocks.SPRUCE_FENCE.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState();
    }

    @Nullable
    public static SettlementStyle byId(@Nullable String id) {
        for (SettlementStyle s : values()) if (s.id.equals(id)) return s;
        return null;
    }

    /** Estilo de uma cidade de reino conquistada, pela facção do lugar. */
    public static SettlementStyle forFaction(@Nullable String faction) {
        if ("kingdom_sahra".equals(faction)) return DESERT;
        if ("kingdom_nordheim".equals(faction)) return NORDIC;
        return KINGDOM;
    }
}
