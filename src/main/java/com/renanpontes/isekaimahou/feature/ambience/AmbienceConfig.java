package com.renanpontes.isekaimahou.feature.ambience;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Opções do módulo (arquivo próprio de cada jogador, aparece em Mods &gt; Isekai Mahou &gt; Config):
 * {@code config/isekai_mahou-ambience-client.toml}. Tudo aqui é gosto de quem ouve/vê, então é só de cliente.
 */
public final class AmbienceConfig {
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue AMBIENT_MUSIC = CLIENT
            .comment("Música ambiente baixa (dark fantasy) em masmorras, torres, cidades, Abismo...",
                    "Quiet dark fantasy ambient music in dungeons, towers, cities, the Abyss...")
            .define("ambientMusic", true);
    public static final ModConfigSpec.IntValue AMBIENT_VOLUME = CLIENT
            .comment("Volume da música ambiente, de 0 a 100 (o controle \"Música\" do jogo também vale)",
                    "Ambient music volume, 0 to 100 (the game's \"Music\" slider also applies)")
            .defineInRange("ambientMusicVolume", 45, 0, 100);
    public static final ModConfigSpec.BooleanValue SPELL_SOUNDS = CLIENT
            .comment("Sons próprios das magias (por elemento e tamanho) ao conjurar e ao acertar",
                    "Custom spell sounds (by element and size) on cast and on hit")
            .define("spellSounds", true);
    public static final ModConfigSpec.IntValue SPELL_VOLUME = CLIENT
            .comment("Volume dos sons das magias, de 0 a 100", "Spell sound volume, 0 to 100")
            .defineInRange("spellSoundVolume", 70, 0, 100);
    public static final ModConfigSpec.BooleanValue GLOW = CLIENT
            .comment("Cristais, runas e essências brilham no escuro (vale depois de recarregar os recursos: F3+T)",
                    "Crystals, runes and essences glow in the dark (applies after a resource reload: F3+T)")
            .define("glowInDark", true);
    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    /** Chamado no construtor do mod (init do módulo). */
    static void register() {
        ModLoadingContext.get().getActiveContainer()
                .registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "isekai_mahou-ambience-client.toml");
    }

    /** Lê uma opção sem quebrar se o arquivo ainda não carregou (usa o padrão). */
    public static boolean on(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return value.getDefault();
        }
    }

    /** Volume 0..1 de uma opção 0..100 (padrão se o arquivo ainda não carregou). */
    public static float volume(ModConfigSpec.IntValue value) {
        try {
            return Math.max(0, Math.min(100, value.get())) / 100F;
        } catch (RuntimeException e) {
            return value.getDefault() / 100F;
        }
    }

    private AmbienceConfig() {}
}
