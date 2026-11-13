package com.renanpontes.isekaimahou.feature.settlements;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Os estilos de cidade do jogador. Os 3 de origem (docs/V10_DESIGN.md §0/§3 "settlements"):
 * <ul>
 *   <li><b>Reino</b> — muralha de pedra alta, guardas com mais armadura, ritmo normal;</li>
 *   <li><b>Desértico</b> — muros simples de arenito e MARAVILHAS (Obelisco, Pirâmide, Esfinge) com bônus fortes;
 *       constrói um pouco mais devagar (as maravilhas compensam);</li>
 *   <li><b>Nórdico</b> — paliçada de madeira, rústico, constrói rápido e sobe de nível com menos prosperidade; gasta
 *       mais combustível.</li>
 * </ul>
 * E os 4 da onda 5 ("adicionar mais estilos além dos 3"), cada um com bônus e ponto fraco:
 * <ul>
 *   <li><b>Oriental</b> — telhados curvos, madeira escura e vermelho, cerejeiras e portão torii: povo mais feliz e mais
 *       impostos, mas as obras caprichadas demoram;</li>
 *   <li><b>Élfico</b> — madeira clara, folhas e luzes suaves: colhe mais e quase não gasta combustível, mas a cidade
 *       demora a subir de degrau e os guardas não usam armadura;</li>
 *   <li><b>Sombrio</b> — pedra escura, telhados pontudos e lanternas de alma: guardas pesados e muita segurança, mas o
 *       povo é mais triste;</li>
 *   <li><b>Anão</b> — pedra, cobre e forja: minas rendem o dobro e as obras andam rápido, mas a lavoura é fraca e as
 *       forjas comem combustível.</li>
 * </ul>
 * As cidades conquistadas herdam o estilo do reino (Aldória = Reino, Sahra = Desértico, Nordheim = Nórdico) — os
 * estilos novos são só para cidades de jogador.
 * <p><b>O id é o que vai no mundo salvo</b>: só ACRESCENTE valores (no fim), nunca renomeie.
 */
public enum SettlementStyle {
    KINGDOM("kingdom", 0x4F7FE8, 1.0F, 1.0F, 4, 4),
    DESERT("desert", 0xE8A440, 0.9F, 1.0F, 4, 0),
    NORDIC("nordic", 0x8ED0F0, 1.4F, 0.65F, 3, 0),
    ORIENTAL("oriental", 0xE2574C, 0.85F, 1.0F, 4, 2),
    ELVEN("elven", 0x74D98A, 1.0F, 1.25F, 4, 0),
    GOTHIC("gothic", 0xA07BE0, 1.0F, 1.0F, 4, 6),
    DWARVEN("dwarven", 0xE0903C, 1.2F, 1.1F, 4, 4);

    static {
        // personalidade de cada estilo (o que não cabe nos 4 números de origem)
        NORDIC.fuelMult = 1.5F;
        ORIENTAL.happiness = 1;
        ORIENTAL.taxPct = 15;
        ELVEN.foodMult = 1.3F;
        ELVEN.fuelMult = 0.5F;
        GOTHIC.happiness = -1;
        GOTHIC.securityPct = 30;
        DWARVEN.foodMult = 0.75F;
        DWARVEN.fuelMult = 1.25F;
        DWARVEN.mineMult = 2.0F;
    }

    public final String id;
    /** Cor do estilo (letreiro do lugar, cristal do coração, interface). */
    public final int color;
    /** Multiplicador da velocidade dos construtores. */
    public final float buildSpeed;
    /** Multiplicador da prosperidade exigida para subir de nível (Nórdico: fácil de upar; Élfico: demora). */
    public final float levelCost;
    /** Altura da abertura do portão das muralhas antigas (.nbt, grau 0). */
    public final int gateHeight;
    /** Armadura extra dos guardas. */
    public final int guardArmor;
    /** Produção de comida e gasto de combustível da cidade (×). */
    private float foodMult = 1F, fuelMult = 1F;
    /** Produção das minas no depósito da cidade (×). */
    private float mineMult = 1F;
    /** Impostos extras (%), segurança extra (%) e felicidade por dia (pode ser negativa). */
    private int taxPct, securityPct, happiness;

    SettlementStyle(String id, int color, float buildSpeed, float levelCost, int gateHeight, int guardArmor) {
        this.id = id;
        this.color = color;
        this.buildSpeed = buildSpeed;
        this.levelCost = levelCost;
        this.gateHeight = gateHeight;
        this.guardArmor = guardArmor;
    }

    public float foodMult() {
        return foodMult;
    }

    public float fuelMult() {
        return fuelMult;
    }

    public float mineMult() {
        return mineMult;
    }

    public int taxPct() {
        return taxPct;
    }

    public int securityPct() {
        return securityPct;
    }

    /** Felicidade por dia que o jeito da cidade dá (ou tira). */
    public int happiness() {
        return happiness;
    }

    /** Estilos que o jogador pode escolher ao fundar/reformar (todos; a ordem é a da tela). */
    public static List<SettlementStyle> playable() {
        return List.of(values());
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
            case NORDIC, ELVEN -> Blocks.COBBLESTONE.defaultBlockState();
            case GOTHIC -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            case DWARVEN -> Blocks.DEEPSLATE_BRICKS.defaultBlockState();
            default -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    /** Grade que fecha o portão. */
    public BlockState portcullis() {
        return switch (this) {
            case NORDIC -> Blocks.SPRUCE_FENCE.defaultBlockState();
            case ORIENTAL -> Blocks.DARK_OAK_FENCE.defaultBlockState();
            case ELVEN -> Blocks.BIRCH_FENCE.defaultBlockState();
            default -> Blocks.IRON_BARS.defaultBlockState();
        };
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
