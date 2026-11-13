package com.renanpontes.isekaimahou.feature.journal.story;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Opções da campanha — gosto de quem joga, então é arquivo de cliente e aparece no menu do Minecraft
 * (Opções &gt; Isekai Mahou, ou Mods &gt; Isekai Mahou &gt; Config): {@code config/isekai_mahou-story-client.toml}.
 * O cliente avisa o servidor (pacote {@code journal_story_prefs}) e é o servidor que deixa de mandar as falas.
 */
public final class StoryConfig {
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue CAMPAIGN = CLIENT
            .comment("Campanha: o Eco (Espírito Guia) conta a história do mundo nos marcos da sua jornada. Desligada, nada é perdido: tudo fica no Diário.",
                    "Campaign: the Echo (Guide Spirit) tells the world's story at the milestones of your journey. When off nothing is lost: it all stays in the Journal.")
            .define("storyCampaign", true);
    public static final ModConfigSpec.BooleanValue SCENES = CLIENT
            .comment("Cutscenes curtas nos marcos da campanha (começo de cada Ato e grandes viradas). Desligadas, a narração vira uma fala.",
                    "Short cutscenes at campaign milestones (start of each Act and big turns). When off, the narration becomes a spoken line.")
            .define("storyMilestoneCutscenes", true);
    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    /** Chamado no construtor do mod (init do módulo). */
    public static void register() {
        ModLoadingContext.get().getActiveContainer()
                .registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "isekai_mahou-story-client.toml");
    }

    /** Lê uma opção sem quebrar se o arquivo ainda não carregou (usa o padrão). */
    public static boolean on(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return value.getDefault();
        }
    }

    private StoryConfig() {}
}
