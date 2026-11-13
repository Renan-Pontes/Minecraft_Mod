package com.renanpontes.isekaimahou.feature.settlements.build;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Planta de um prédio (ou da muralha) de um estilo. O molde é o .nbt {@code structure/settlements/<estilo>_<chave>}
 * gerado por {@code tools/gen/settlements.py} (entrada sempre no lado +Z do molde).
 *
 * @param key       chave comum aos estilos (house_small, market, ...) — maravilhas/castelo só existem num estilo
 * @param minLevel  nível da cidade que libera a planta
 * @param points    prosperidade ganha ao terminar (sobe o nível da cidade)
 * @param maxCount  quantas vezes dá para construir
 * @param fx        o que o prédio pronto dá à cidade
 */
public record Blueprint(String key, SettlementStyle style, int minLevel, int points, int maxCount, Effects fx) {

    /**
     * Efeitos de um prédio pronto.
     *
     * @param residents  vagas de moradores
     * @param guards     vagas de guardas
     * @param merchants  vagas de mercadores
     * @param builders   construtores extras
     * @param speedPct   velocidade de construção extra (%)
     * @param affinity   afinidade do povo por dia
     * @param taxPct     impostos extras (%)
     * @param militia    vagas de milícia
     * @param guardPower guardas mais fortes (castelo/esfinge)
     * @param wonder     id da maravilha ("" = não é)
     */
    public record Effects(int residents, int guards, int merchants, int builders, int speedPct, int affinity, int taxPct,
                          int militia, boolean guardPower, String wonder) {
        public static Effects none() {
            return new Effects(0, 0, 0, 0, 0, 0, 0, 0, false, "");
        }
    }

    public static final String PLAZA = "plaza";
    public static final String WALL = "wall";
    public static final String TOWN_HALL = "town_hall";

    public boolean isWall() {
        return WALL.equals(key);
    }

    public boolean isPlaza() {
        return PLAZA.equals(key);
    }

    /** Ocupa um terreno (célula) da cidade? (a praça fica no centro; a muralha, na borda) */
    public boolean usesCell() {
        return !isWall() && !isPlaza();
    }

    public boolean isWonder() {
        return !fx.wonder().isEmpty();
    }

    public ResourceLocation template() {
        return template(style, key);
    }

    /** Molde de uma variante (0 = o molde base; as outras têm o sufixo {@code _<n>}). */
    public ResourceLocation template(int variant) {
        return variant <= 0 ? template(style, key) : template(style, key + "_" + variant);
    }

    /** Geração do visual dos prédios: 0 = moldes de antes da onda 8; 1 = visual novo ({@link LookBuildings}). */
    public static final int LOOK_OLD = 0, LOOK_NEW = 1;

    /**
     * Molde de um prédio num NÍVEL para uma OBRA NOVA (construir, melhorar, reformar): onda 8 — o visual novo, em que
     * cada nível é outro prédio ({@link LookBuildings}). Para o prédio que JÁ está de pé use
     * {@link #template(int, int, int)} com a geração guardada no lote ({@code Settlement.bLooks}).
     */
    public ResourceLocation template(int variant, int level) {
        return template(variant, level, LOOK_NEW);
    }

    /** Molde do prédio na geração de visual pedida (mundos antigos: o que está de pé continua no molde em que foi erguido). */
    public ResourceLocation template(int variant, int level, int look) {
        if (look >= LOOK_NEW && usesCell() && LookBuildings.supports(key)) return LookBuildings.id(style, key, level, variant);
        return templateOld(variant, level);
    }

    /**
     * Molde ANTIGO de um prédio num nível (v1.4): plantas procedurais crescem com o nível; as de molde .nbt ganham os
     * enfeites do nível em volta ({@link ProcBuildings#ornaments}).
     */
    public ResourceLocation templateOld(int variant, int level) {
        if (ProcBuildings.handles(key)) return Templates.procId(style, key, level);
        ResourceLocation base;
        if (Blueprints.CITY_LOTS.contains(key)) {
            String palette = switch (style) {
                case DESERT -> "desert";
                case NORDIC -> "snowy";
                case KINGDOM -> "plains";
                // onda 5: os estilos novos não têm paleta de cidade de reino — o molde do lote é gerado para eles
                default -> null;
            };
            base = palette == null ? template(style, "lot_" + key) : IsekaiMahou.id("city/" + palette + "/" + key);
        } else {
            base = template(variant);
        }
        return isWall() || isPlaza() ? base : Templates.leveledId(base, style, level);
    }

    public static ResourceLocation template(SettlementStyle style, String piece) {
        return IsekaiMahou.id("settlements/" + style.id + "_" + piece);
    }

    public String langKey() {
        return "settlements.isekai_mahou.bp." + style.id + "." + key;
    }

    public MutableComponent displayName() {
        return Component.translatable(langKey());
    }

    public MutableComponent description() {
        return Component.translatable(langKey() + ".desc");
    }
}
