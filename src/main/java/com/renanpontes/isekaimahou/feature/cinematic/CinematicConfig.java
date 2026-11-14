package com.renanpontes.isekaimahou.feature.cinematic;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * v1.1 — Opções de cliente das cutscenes coreografadas (aparecem no menu do Minecraft: Opções &gt; Isekai Mahou):
 * {@code config/isekai_mahou-cinematic-client.toml}. As opções gerais (cutscenes ligadas, só as minhas) continuam
 * em {@code MahouConfig}.
 */
public final class CinematicConfig {
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue IMPACT_FRAMES = CLIENT
            .comment("Quadros de impacto: 1–2 quadros de cor invertida no golpe das transformações e ultimates (estilo mangá).",
                    "Desligue se você é sensível a clarões. Também somem com \"Esconder relâmpagos\" (Acessibilidade) ligado.",
                    "Impact frames: 1-2 frames of inverted colour at the burst of transformations and ultimates (manga style).",
                    "Turn off if you are sensitive to flashes. Also hidden when \"Hide Lightning Flashes\" (Accessibility) is on.")
            .define("cinematicImpactFrames", true);
    public static final ModConfigSpec.BooleanValue SPEED_LINES = CLIENT
            .comment("Linhas de velocidade estilo mangá nas cutscenes de transformação e ultimate.",
                    "Manga-style speed lines in transformation and ultimate cutscenes.")
            .define("cinematicSpeedLines", true);
    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    /** Chamado no init do módulo (construtor do mod). */
    static void register() {
        ModLoadingContext.get().getActiveContainer()
                .registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "isekai_mahou-cinematic-client.toml");
    }

    /** Lê sem quebrar se o arquivo ainda não carregou (usa o padrão). */
    public static boolean on(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return value.getDefault();
        }
    }

    private CinematicConfig() {}
}
