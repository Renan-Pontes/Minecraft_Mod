package com.renanpontes.isekaimahou.feature.combat_lock;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Opções do módulo (arquivos próprios, aparecem em Mods &gt; Isekai Mahou &gt; Config):
 * {@code config/isekai_mahou-lock-common.toml} (servidor) e {@code config/isekai_mahou-lock-client.toml} (cada jogador).
 */
public final class LockConfig {
    // ---------------- Comum (servidor) ----------------
    private static final ModConfigSpec.Builder COMMON = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue WARNINGS_ENABLED = COMMON
            .comment("Aviso \"!!!\" quando um monstro comum vai dar um golpe forte ou ataca pelas costas",
                    "\"!!!\" warning when a common monster is about to land a heavy hit or attacks from behind")
            .define("threatWarnings", true);
    public static final ModConfigSpec.BooleanValue AIM_ASSIST = COMMON
            .comment("Magias de quem travou um alvo saem exatamente na direção dele",
                    "Spells from a player with a locked target fly exactly towards it")
            .define("lockSpellAim", true);
    public static final ModConfigSpec COMMON_SPEC = COMMON.build();

    // ---------------- Cliente ----------------
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue SHOULDER_CAMERA = CLIENT
            .comment("Com alvo travado em 3ª pessoa, a câmera vai para cima do ombro e enquadra você e o alvo",
                    "With a locked target in third person, the camera moves over the shoulder and frames you and the target")
            .define("lockShoulderCamera", true);
    public static final ModConfigSpec.BooleanValue LEFT_SHOULDER = CLIENT
            .comment("Câmera travada por cima do ombro ESQUERDO (falso = direito)",
                    "Locked camera over the LEFT shoulder (false = right)")
            .define("lockLeftShoulder", false);
    public static final ModConfigSpec.BooleanValue SCROLL_SWITCH = CLIENT
            .comment("Com alvo travado, a roda do mouse troca de alvo (em vez de trocar o item da barra)",
                    "With a locked target, the mouse wheel switches target (instead of the hotbar item)")
            .define("lockScrollSwitch", true);
    public static final ModConfigSpec.BooleanValue SHOW_WARNINGS = CLIENT
            .comment("Mostrar o aviso \"!!!\" de golpe forte / ataque pelas costas", "Show the \"!!!\" heavy hit / back attack warning")
            .define("showThreatWarnings", true);
    public static final ModConfigSpec.BooleanValue WARNING_SOUND = CLIENT
            .comment("Som baixinho junto com o aviso \"!!!\"", "Soft sound with the \"!!!\" warning")
            .define("threatWarningSound", true);
    public static final ModConfigSpec.BooleanValue LOCK_HINT_SEEN = CLIENT
            .comment("A dica única de travar o alvo já apareceu (o jogo marca sozinho)",
                    "The one-time lock-on hint was already shown (set by the game)")
            .define("lockHintSeen", false);
    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    /** Chamado no construtor do mod (init do módulo). */
    static void register() {
        var container = ModLoadingContext.get().getActiveContainer();
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "isekai_mahou-lock-common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "isekai_mahou-lock-client.toml");
    }

    /** Lê uma opção sem quebrar se o arquivo ainda não carregou (usa o padrão). */
    public static boolean on(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return value.getDefault();
        }
    }

    private LockConfig() {}
}
