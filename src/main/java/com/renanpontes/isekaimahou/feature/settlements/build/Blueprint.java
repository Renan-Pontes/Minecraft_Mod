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
