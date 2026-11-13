package com.renanpontes.isekaimahou.feature.combat_feel;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Opções do módulo (arquivos próprios, aparecem em Mods &gt; Isekai Mahou &gt; Config):
 * {@code config/isekai_mahou-feel-common.toml} (regras do mundo) e {@code config/isekai_mahou-feel-client.toml}
 * (cada jogador — é aqui que se desliga a micro-pausa).
 */
public final class FeelConfig {
    // ---------------- Comum (servidor) ----------------
    private static final ModConfigSpec.Builder COMMON = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue FINISHER_ENABLED = COMMON
            .comment("Golpe de misericórdia: inimigo comum quase morto pode ser finalizado com a tecla de usar",
                    "Mercy stroke: a common enemy that is almost dead can be finished with the use key")
            .define("finisherEnabled", true);
    public static final ModConfigSpec.IntValue FINISHER_HEALTH_PERCENT = COMMON
            .comment("Vida (em % da vida máxima) abaixo da qual o inimigo comum pode ser finalizado",
                    "Health (% of max health) below which a common enemy can be finished")
            .defineInRange("finisherHealthPercent", 10, 1, 50);
    public static final ModConfigSpec.BooleanValue COMBO_ENABLED = COMMON
            .comment("Combo de magias: conjurar em sequência rápida soma um contador com um bônus pequeno",
                    "Spell combo: casting in quick succession builds a counter with a small bonus")
            .define("spellComboEnabled", true);
    public static final ModConfigSpec.IntValue COMBO_WINDOW = COMMON
            .comment("Ticks que o combo espera pela próxima magia antes de zerar (20 = 1 s)",
                    "Ticks the combo waits for the next spell before it resets (20 = 1 s)")
            .defineInRange("spellComboWindowTicks", 80, 20, 300);
    public static final ModConfigSpec.DoubleValue COMBO_BONUS = COMMON
            .comment("Bônus de poder mágico por magia do combo depois da primeira, em % (2 = +2% cada)",
                    "Spell power bonus per combo spell after the first, in % (2 = +2% each)")
            .defineInRange("spellComboBonusPercent", 2.0, 0.0, 10.0);
    public static final ModConfigSpec.IntValue COMBO_MAX_STEPS = COMMON
            .comment("Quantas magias do combo contam para o bônus (o contador continua subindo, o bônus para)",
                    "How many combo spells count for the bonus (the counter keeps rising, the bonus stops)")
            .defineInRange("spellComboMaxSteps", 10, 1, 30);
    public static final ModConfigSpec.BooleanValue SMART_CASTERS = COMMON
            .comment("Monstros conjuradores (tag isekai_mahou:smart_casters) desviam, recuam, se curam e pedem ajuda",
                    "Caster monsters (tag isekai_mahou:smart_casters) dodge, back off, heal themselves and call for help")
            .define("smartCasters", true);
    public static final ModConfigSpec COMMON_SPEC = COMMON.build();

    // ---------------- Cliente ----------------
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue HIT_STOP = CLIENT
            .comment("Micro-pausa (só na sua tela) em golpe pesado, crítico, aparo e explosão grande",
                    "Hit-stop (on your screen only) on heavy hits, criticals, parries and big explosions")
            .define("hitStop", true);
    public static final ModConfigSpec.IntValue HIT_STOP_STRENGTH = CLIENT
            .comment("Duração da micro-pausa em % (100 = 2 a 4 quadros)", "Hit-stop length in % (100 = 2 to 4 frames)")
            .defineInRange("hitStopStrength", 100, 25, 200);
    public static final ModConfigSpec.BooleanValue SHOW_COMBO = CLIENT
            .comment("Mostrar o contador \"Combo xN\" ao lado da mira", "Show the \"Combo xN\" counter next to the crosshair")
            .define("showSpellCombo", true);
    public static final ModConfigSpec.BooleanValue FINISHER_PROMPT = CLIENT
            .comment("Mostrar o aviso de finalizar ao mirar num inimigo quase morto",
                    "Show the finish prompt when aiming at an almost dead enemy")
            .define("finisherPrompt", true);
    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    /** Chamado no construtor do mod (init do módulo). */
    static void register() {
        var container = ModLoadingContext.get().getActiveContainer();
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "isekai_mahou-feel-common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "isekai_mahou-feel-client.toml");
    }

    /** Lê uma opção sem quebrar se o arquivo ainda não carregou (usa o padrão). */
    public static boolean on(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return value.getDefault();
        }
    }

    public static int of(ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return value.getDefault();
        }
    }

    public static double of(ModConfigSpec.DoubleValue value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return value.getDefault();
        }
    }

    private FeelConfig() {}
}
