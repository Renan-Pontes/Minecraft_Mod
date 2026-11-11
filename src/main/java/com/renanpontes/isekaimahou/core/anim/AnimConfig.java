package com.renanpontes.isekaimahou.core.anim;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configurações das animações de combate (arquivos próprios para não disputar o {@code MahouConfig}):
 * <ul>
 *   <li>{@code config/isekai_mahou-combat-common.toml} — regras da esquiva (servidor manda).</li>
 *   <li>{@code config/isekai_mahou-combat-client.toml} — o que cada jogador quer ver/usar.</li>
 * </ul>
 */
public final class AnimConfig {
    // ---------------- Comum (servidor) ----------------
    private static final ModConfigSpec.Builder COMMON = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue DODGE_ENABLED = COMMON
            .comment("Esquiva (rolamento/passo lateral com invulnerabilidade curta) ligada no servidor",
                    "Dodge (roll/sidestep with short invulnerability) enabled on the server")
            .define("dodgeEnabled", true);
    public static final ModConfigSpec.IntValue DODGE_COOLDOWN = COMMON
            .comment("Recarga da esquiva em ticks (20 = 1 segundo)", "Dodge cooldown in ticks (20 = 1 second)")
            .defineInRange("dodgeCooldownTicks", 24, 5, 200);
    public static final ModConfigSpec.IntValue DODGE_IFRAMES = COMMON
            .comment("Ticks de invulnerabilidade no começo da esquiva (0 = nenhum)",
                    "Invulnerability ticks at the start of a dodge (0 = none)")
            .defineInRange("dodgeInvulnerableTicks", 6, 0, 20);
    public static final ModConfigSpec.DoubleValue DODGE_EXHAUSTION = COMMON
            .comment("Cansaço (fome) gasto por esquiva. 4.0 = 1 ponto de fome", "Exhaustion (hunger) per dodge. 4.0 = 1 hunger point")
            .defineInRange("dodgeExhaustion", 0.8D, 0D, 10D);
    public static final ModConfigSpec COMMON_SPEC = COMMON.build();

    // ---------------- Cliente ----------------
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue COMBAT_ANIMATIONS = CLIENT
            .comment("Animações de combate no estilo Epic Fight (combos de arma, esquiva, guarda)",
                    "Epic-Fight-like combat animations (weapon combos, dodge, guard)")
            .define("combatAnimations", true);
    public static final ModConfigSpec.BooleanValue SPELL_GESTURES = CLIENT
            .comment("Gestos de conjuração por forma de magia (projétil, raio, área, aura, invocação, suprema)",
                    "Spell casting gestures by spell form (bolt, beam, area, aura, summon, ultimate)")
            .define("spellGestures", true);
    public static final ModConfigSpec.BooleanValue FIRST_PERSON_ARMS = CLIENT
            .comment("Mostrar os braços animados também em primeira pessoa (falso = mão normal do Minecraft)",
                    "Show the animated arms in first person too (false = vanilla hand)")
            .define("firstPersonArms", false);
    public static final ModConfigSpec.BooleanValue DOUBLE_TAP_DODGE = CLIENT
            .comment("Esquivar apertando duas vezes rápido Trás/Esquerda/Direita (Frente duas vezes continua sendo correr)",
                    "Dodge by double-tapping Back/Left/Right (double-tap Forward is still sprint)")
            .define("doubleTapDodge", true);
    public static final ModConfigSpec.IntValue DOUBLE_TAP_WINDOW = CLIENT
            .comment("Janela do toque duplo em ticks", "Double-tap window in ticks")
            .defineInRange("doubleTapWindowTicks", 6, 2, 15);
    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    private AnimConfig() {}
}
