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
            .defineInRange("dodgeInvulnerableTicks", 8, 0, 20);
    public static final ModConfigSpec.DoubleValue DODGE_EXHAUSTION = COMMON
            .comment("Cansaço (fome) gasto por esquiva. 4.0 = 1 ponto de fome", "Exhaustion (hunger) per dodge. 4.0 = 1 hunger point")
            .defineInRange("dodgeExhaustion", 0.8D, 0D, 10D);
    public static final ModConfigSpec.BooleanValue FEEL_ENABLED = COMMON
            .comment("Tranco ao levar golpe pesado, queda ao ser arremessado, rolamento/aterrissagem após quedas e pose de vitória",
                    "Flinch on heavy hits, knockdown on huge knockback, landing roll after big falls and victory pose")
            .define("feelAnimations", true);
    public static final ModConfigSpec.DoubleValue FLINCH_MIN_DAMAGE = COMMON
            .comment("Dano mínimo (depois da armadura) para o jogador dar o tranco", "Minimum damage (after armor) for a flinch")
            .defineInRange("flinchMinDamage", 6.0D, 1.0D, 100.0D);
    public static final ModConfigSpec.DoubleValue KNOCKDOWN_MIN_STRENGTH = COMMON
            .comment("Força mínima do empurrão para derrubar o jogador (0,4 = golpe comum; 1,5 = Repulsão II correndo)",
                    "Minimum knockback strength to knock the player down (0.4 = normal hit)")
            .defineInRange("knockdownMinStrength", 1.25D, 0.5D, 10.0D);
    // ---- v1.0 (release): bloqueio com aparo
    public static final ModConfigSpec.BooleanValue GUARD_ENABLED = COMMON
            .comment("Bloqueio com arma ou cajado (segurar a tecla) e aparo no tempo certo ligados no servidor",
                    "Weapon/staff block (hold the key) and timed parry enabled on the server")
            .define("guardEnabled", true);
    public static final ModConfigSpec.DoubleValue GUARD_REDUCTION = COMMON
            .comment("Parte do dano que o bloqueio segura (0,5 = metade). Magia segura menos (60% disso)",
                    "Share of the damage a block absorbs (0.5 = half). Magic absorbs less (60% of it)")
            .defineInRange("guardDamageReduction", 0.5D, 0D, 0.9D);
    public static final ModConfigSpec.IntValue PARRY_WINDOW = COMMON
            .comment("Janela do aparo em ticks depois de apertar o bloqueio (6 = 0,3 s; 0 = sem aparo)",
                    "Parry window in ticks after pressing block (6 = 0.3 s; 0 = no parry)")
            .defineInRange("parryWindowTicks", 6, 0, 12);
    public static final ModConfigSpec.IntValue PARRY_STUN = COMMON
            .comment("Ticks de atordoamento do atacante aparado (chefes levam metade)",
                    "Stun ticks on a parried attacker (bosses take half)")
            .defineInRange("parryStunTicks", 30, 0, 100);
    public static final ModConfigSpec.DoubleValue GUARD_EXHAUSTION = COMMON
            .comment("Cansaço (fome) por golpe bloqueado, mais 10% do dano. 4.0 = 1 ponto de fome",
                    "Exhaustion (hunger) per blocked hit, plus 10% of the damage. 4.0 = 1 hunger point")
            .defineInRange("guardExhaustion", 0.6D, 0D, 10D);
    public static final ModConfigSpec.DoubleValue GUARD_BREAK_DAMAGE = COMMON
            .comment("Golpe (antes da redução) a partir do qual o bloqueio QUEBRA por 2 s — golpe forte pede aparo ou esquiva",
                    "Hit (before reduction) that BREAKS the block for 2 s — heavy hits ask for a parry or a dodge")
            .defineInRange("guardBreakDamage", 14.0D, 1.0D, 1000.0D);
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
    public static final ModConfigSpec.BooleanValue FEEL_ANIMATIONS = CLIENT
            .comment("Ver as animações de sensação: tranco, queda e levantar, rolamento ao cair, investida correndo, vitória",
                    "Show feel animations: flinch, knockdown + get-up, landing roll, sprint lunge, victory pose")
            .define("feelAnimationsClient", true);
    public static final ModConfigSpec.BooleanValue WEAPON_DRAW = CLIENT
            .comment("Floreio ao sacar uma arma de classe (trocar para ela na barra)",
                    "Flourish when switching to a class weapon on the hotbar")
            .define("weaponDrawFlourish", true);
    public static final ModConfigSpec.BooleanValue EMOTES = CLIENT
            .comment("Mostrar emotes (seus e dos outros jogadores)", "Show emotes (yours and other players')")
            .define("emotes", true);
    public static final ModConfigSpec.BooleanValue ANIM_CUES = CLIENT
            .comment("Poeira/som de impacto nas animações (aterrissagem, queda, pancada no chão) — segue o preset gráfico",
                    "Dust/sound impact cues on animations (landing, knockdown, ground slam) — follows the graphics preset")
            .define("animationImpactCues", true);
    public static final ModConfigSpec.BooleanValue PARRY_FLASH = CLIENT
            .comment("Clarão curto nas bordas da tela ao aparar um golpe", "Short flash on the screen edges when you parry a hit")
            .define("parryFlash", true);
    public static final ModConfigSpec.BooleanValue GUARD_HINT_SEEN = CLIENT
            .comment("A dica única do bloqueio já apareceu (o jogo marca sozinho)", "The one-time block hint was already shown (set by the game)")
            .define("guardHintSeen", false);
    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    private AnimConfig() {}
}
