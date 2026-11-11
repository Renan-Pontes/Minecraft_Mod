package com.renanpontes.isekaimahou.feature.afterlife;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config própria do módulo (arquivo {@code isekai_mahou-afterlife.toml}, tipo COMMON): o núcleo tem a dele, então o
 * módulo registra a sua no contêiner do mod durante o init. Os getters nunca lançam (config ainda não carregada = padrão).
 */
public final class AfterlifeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue NEAR_DEATH;
    public static final ModConfigSpec.DoubleValue OVERKILL;
    public static final ModConfigSpec.IntValue CHALLENGE_SECONDS;
    public static final ModConfigSpec.IntValue HEARTBEATS;
    public static final ModConfigSpec.IntValue REAPERS;
    public static final ModConfigSpec.DoubleValue REVIVE_HEALTH;
    public static final ModConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ModConfigSpec.BooleanValue DEATH_CORPSES;
    public static final ModConfigSpec.IntValue LOOT_PROTECTION_MINUTES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Isekai Mahou — À Beira da Morte e Corpos (módulo afterlife).",
                "Com a gamerule keepInventory ligada nada disto acontece (morte vanilla, itens ficam com o jogador).").push("afterlife");
        NEAR_DEATH = b.comment("Liga o estado 'À Beira da Morte': morrer por pouco (sem fogo/condição) vira um desafio para voltar ao corpo.")
                .define("nearDeathEnabled", true);
        OVERKILL = b.comment("Dano excedente máximo (fração da vida máxima) para cair À Beira da Morte em vez de morrer.")
                .defineInRange("overkillThreshold", 0.35, 0.0, 1.0);
        CHALLENGE_SECONDS = b.comment("Tempo (segundos) do desafio de voltar ao corpo.")
                .defineInRange("challengeSeconds", 20, 8, 120);
        HEARTBEATS = b.comment("Batidas do coração no ritmo necessárias dentro do corpo.")
                .defineInRange("heartbeatsRequired", 4, 1, 10);
        REAPERS = b.comment("Quantos Ceifadores caçam a alma durante o desafio.")
                .defineInRange("reaperCount", 3, 0, 8);
        REVIVE_HEALTH = b.comment("Vida ao voltar (fração da vida máxima).")
                .defineInRange("reviveHealth", 0.3, 0.05, 1.0);
        COOLDOWN_SECONDS = b.comment("Depois de voltar, por quantos segundos uma nova morte é morte de verdade (evita repetir sem fim).")
                .defineInRange("nearDeathCooldownSeconds", 60, 0, 3600);
        DEATH_CORPSES = b.comment("Na morte de verdade os itens ficam num Corpo no local (false = itens caem no chão como no vanilla).")
                .define("deathCorpses", true);
        LOOT_PROTECTION_MINUTES = b.comment("Minutos em que só o dono e o grupo dele podem abrir o Corpo (0 = qualquer um, sempre).")
                .defineInRange("corpseLootProtectionMinutes", 20, 0, 10080);
        b.pop();
        SPEC = b.build();
    }

    static void register() {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, SPEC, "isekai_mahou-afterlife.toml");
    }

    public static boolean nearDeath() { return get(NEAR_DEATH, true); }
    public static double overkill() { return get(OVERKILL, 0.35); }
    public static int challengeTicks() { return get(CHALLENGE_SECONDS, 20) * 20; }
    public static int heartbeats() { return get(HEARTBEATS, 4); }
    public static int reapers() { return get(REAPERS, 3); }
    public static double reviveHealth() { return get(REVIVE_HEALTH, 0.3); }
    public static long cooldownTicks() { return get(COOLDOWN_SECONDS, 60) * 20L; }
    public static boolean deathCorpses() { return get(DEATH_CORPSES, true); }
    public static long lootProtectionTicks() { return get(LOOT_PROTECTION_MINUTES, 20) * 1200L; }

    private static boolean get(ModConfigSpec.BooleanValue v, boolean def) {
        try { return v.get(); } catch (RuntimeException e) { return def; }
    }

    private static double get(ModConfigSpec.DoubleValue v, double def) {
        try { return v.get(); } catch (RuntimeException e) { return def; }
    }

    private static int get(ModConfigSpec.IntValue v, int def) {
        try { return v.get(); } catch (RuntimeException e) { return def; }
    }

    private AfterlifeConfig() {}
}
