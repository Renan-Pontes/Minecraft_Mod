package com.renanpontes.isekaimahou.feature.settlements;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Opções das cidades (onda 6, city_pace) — arquivo próprio {@code config/isekai_mahou-city-common.toml}, que aparece em
 * Mods &gt; Isekai Mahou &gt; Config. Vale a do servidor (toda a conta das obras roda nele).
 */
public final class CityPaceConfig {
    private static final ModConfigSpec.Builder COMMON = new ModConfigSpec.Builder();
    /** "Ritmo da cidade": multiplica a DURAÇÃO das obras em dias (1 = normal; 0,5 = metade do tempo; 2 = o dobro). */
    public static final ModConfigSpec.DoubleValue PACE = COMMON
            .comment("Ritmo da cidade: multiplica a duração das obras em dias do jogo (1 = normal; 0.5 = metade do tempo; 2 = o dobro)",
                    "City pace: multiplies how many in-game days city works take (1 = normal; 0.5 = half the time; 2 = twice as long)")
            .defineInRange("cityPace", 1.0, 0.05, 10.0);
    public static final ModConfigSpec COMMON_SPEC = COMMON.build();

    /** Chamado no construtor do mod (init do módulo). */
    static void register() {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "isekai_mahou-city-common.toml");
    }

    /** Lê o ritmo sem quebrar se o arquivo ainda não carregou (usa o padrão). */
    public static float pace() {
        try {
            return (float) Math.max(0.05, Math.min(10.0, PACE.get()));
        } catch (RuntimeException e) {
            return 1F;
        }
    }

    /** Muda o ritmo em jogo (comando de teste) e grava no arquivo. Devolve false se a config ainda não carregou. */
    static boolean setPace(double value) {
        try {
            PACE.set(Math.max(0.05, Math.min(10.0, value)));
            PACE.save();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private CityPaceConfig() {}
}
