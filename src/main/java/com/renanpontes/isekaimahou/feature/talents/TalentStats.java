package com.renanpontes.isekaimahou.feature.talents;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Soma das PASSIVAS ({@code "stat"} no JSON) que os talentos e a especialização do jogador dão agora. Calculada quando os
 * talentos mudam (não a cada golpe) e lida pelo {@link TalentCombat}. Valores são frações (0.05 = 5%), exceto as chaves
 * de {@link #FLAT}, que são quantidades (vida, recurso, segundos).
 * <p>
 * <b>Números</b> (dano): {@code all_damage} · {@code melee_damage} · {@code ranged_damage} · {@code summon_damage} ·
 * {@code spell_damage_all} · {@code spell_damage.<elemento base>} · {@code boss_damage} · {@code near_damage} ·
 * {@code far_damage} · {@code afflicted_damage} · {@code high_mana_power} · {@code full_health_power} ·
 * {@code low_health_power} · {@code execute} · {@code first_strike} · {@code backstab} · {@code night_power} ·
 * {@code day_power} · {@code undead_damage} · {@code aim_power} · {@code run_power} · {@code crit_chance} ·
 * {@code crit_damage} · {@code lucky}.
 * <p>
 * <b>Mecânicas</b> (os nós notáveis, as escolhas, os nós finais e as assinaturas das especializações):
 * {@code combo} (golpes seguidos no mesmo alvo) · {@code kill_fury} (dano depois de abater) · {@code crit_fury} ·
 * {@code parry_power} · {@code revenge} · {@code dodge_power} · {@code surge} (por magia conjurada) · {@code ult_power}
 * (depois da suprema) · {@code summon_mark} · {@code reckless} · {@code wild} · {@code fifth_hit} · {@code echo} ·
 * {@code cull} · {@code kill_burst} · {@code plague} · {@code crit_mana} · {@code crit_heal} · {@code crit_cdr} ·
 * {@code kill_cdr} · {@code charge_on_kill} · {@code cast_refund} · {@code heal_on_cast} · {@code cast_ward} ·
 * {@code guard_fortify} · {@code guard_reflect} · {@code parry_heal} · {@code parry_mana} · {@code summon_bond} ·
 * {@code summon_lifesteal} · {@code cheat_death} · {@code barrier} · {@code on_struck.<efeito>}.
 * <p>
 * <b>Defesa e sustento</b>: {@code damage_resist} · {@code magic_resist} · {@code projectile_resist} ·
 * {@code melee_resist} · {@code fall_resist} · {@code fire_resist} · {@code low_health_resist} ·
 * {@code full_health_resist} · {@code boss_resist} · {@code dodge} · {@code mana_shield} · {@code thorns} ·
 * {@code second_wind} · {@code regen_health} · {@code healing_received} · {@code lifesteal} · {@code mana_on_hit} ·
 * {@code mana_on_kill} · {@code heal_on_kill} · {@code kill_speed} ·
 * {@code on_hit.<burn|poison|slowness|weakness|wither|bleed|chill|shock>}.
 * <p>
 * Os tetos de cada uma ficam no {@link TalentCombat}; a lista para a tela e o comando, em {@link #CAPS}.
 */
public final class TalentStats {
    public static final TalentStats EMPTY = new TalentStats(Map.of());

    public static final String RANGED_DAMAGE = "ranged_damage", SUMMON_DAMAGE = "summon_damage", CRIT_CHANCE = "crit_chance",
            CRIT_DAMAGE = "crit_damage", LUCKY = "lucky", LIFESTEAL = "lifesteal", MANA_ON_HIT = "mana_on_hit",
            MANA_ON_KILL = "mana_on_kill", HEAL_ON_KILL = "heal_on_kill", KILL_SPEED = "kill_speed",
            DAMAGE_RESIST = "damage_resist", MAGIC_RESIST = "magic_resist", PROJECTILE_RESIST = "projectile_resist",
            MELEE_RESIST = "melee_resist", FALL_RESIST = "fall_resist", FIRE_RESIST = "fire_resist", DODGE = "dodge",
            MANA_SHIELD = "mana_shield", THORNS = "thorns", SECOND_WIND = "second_wind", REGEN_HEALTH = "regen_health",
            HEALING_RECEIVED = "healing_received", LOW_HEALTH_POWER = "low_health_power", EXECUTE = "execute",
            FIRST_STRIKE = "first_strike", BACKSTAB = "backstab", NIGHT_POWER = "night_power", DAY_POWER = "day_power",
            UNDEAD_DAMAGE = "undead_damage";
    // release 1.0 — números
    public static final String ALL_DAMAGE = "all_damage", MELEE_DAMAGE = "melee_damage", SPELL_DAMAGE_ALL = "spell_damage_all",
            BOSS_DAMAGE = "boss_damage", NEAR_DAMAGE = "near_damage", FAR_DAMAGE = "far_damage",
            AFFLICTED_DAMAGE = "afflicted_damage", HIGH_MANA_POWER = "high_mana_power",
            FULL_HEALTH_POWER = "full_health_power", AIM_POWER = "aim_power", RUN_POWER = "run_power",
            LOW_HEALTH_RESIST = "low_health_resist", FULL_HEALTH_RESIST = "full_health_resist", BOSS_RESIST = "boss_resist";
    // release 1.0 — mecânicas
    public static final String COMBO = "combo", KILL_FURY = "kill_fury", CRIT_FURY = "crit_fury", PARRY_POWER = "parry_power",
            REVENGE = "revenge", DODGE_POWER = "dodge_power", SURGE = "surge", ULT_POWER = "ult_power",
            SUMMON_MARK = "summon_mark", RECKLESS = "reckless", WILD = "wild", FIFTH_HIT = "fifth_hit", ECHO = "echo",
            CULL = "cull", KILL_BURST = "kill_burst", PLAGUE = "plague", CRIT_MANA = "crit_mana", CRIT_HEAL = "crit_heal",
            CRIT_CDR = "crit_cdr", KILL_CDR = "kill_cdr", CHARGE_ON_KILL = "charge_on_kill", CAST_REFUND = "cast_refund",
            HEAL_ON_CAST = "heal_on_cast", CAST_WARD = "cast_ward", GUARD_FORTIFY = "guard_fortify",
            GUARD_REFLECT = "guard_reflect", PARRY_HEAL = "parry_heal", PARRY_MANA = "parry_mana",
            SUMMON_BOND = "summon_bond", SUMMON_LIFESTEAL = "summon_lifesteal", CHEAT_DEATH = "cheat_death",
            BARRIER = "barrier";
    public static final String SPELL_DAMAGE_PREFIX = "spell_damage.", ON_HIT_PREFIX = "on_hit.", ON_STRUCK_PREFIX = "on_struck.";

    /** Passivas que são quantidade (mana, vida, segundos) e não porcentagem — a tela mostra sem "%". */
    public static final Set<String> FLAT = Set.of(MANA_ON_HIT, MANA_ON_KILL, HEAL_ON_KILL, KILL_SPEED, SECOND_WIND, REGEN_HEALTH,
            CRIT_MANA, CRIT_HEAL, CRIT_CDR, KILL_CDR, CHARGE_ON_KILL, HEAL_ON_CAST, PARRY_HEAL, PARRY_MANA, BARRIER);

    /** Passivas cujo texto lê melhor sem o sinal de "+" ("abaixo de 10% de vida", "adianta em 2 s"). */
    public static final Set<String> PLAIN = Set.of(CULL, KILL_BURST, PLAGUE, CHEAT_DEATH, GUARD_REFLECT, SUMMON_LIFESTEAL,
            CRIT_CDR, KILL_CDR, BARRIER, WILD);

    /**
     * Teto de cada passiva que tem teto próprio (o que passar disso não faz efeito). A soma de TODAS as resistências
     * tem ainda o teto {@link TalentCombat#MAX_RESIST} e a de todos os bônus de dano, {@link TalentCombat#MAX_BONUS}.
     */
    public static final Map<String, Double> CAPS = Map.ofEntries(
            Map.entry(DODGE, TalentCombat.MAX_DODGE), Map.entry(CRIT_CHANCE, TalentCombat.MAX_CRIT),
            Map.entry(CRIT_DAMAGE, TalentCombat.MAX_CRIT_DAMAGE), Map.entry(LUCKY, TalentCombat.MAX_LUCKY),
            Map.entry(ECHO, TalentCombat.MAX_ECHO), Map.entry(CAST_REFUND, TalentCombat.MAX_REFUND),
            Map.entry(CULL, TalentCombat.MAX_CULL), Map.entry(MANA_SHIELD, TalentCombat.MAX_MANA_SHIELD),
            Map.entry(CRIT_CDR, TalentCombat.MAX_CRIT_CDR), Map.entry(KILL_CDR, TalentCombat.MAX_KILL_CDR),
            Map.entry(CHARGE_ON_KILL, TalentCombat.MAX_KILL_CHARGE), Map.entry(BARRIER, TalentCombat.MAX_BARRIER),
            Map.entry(RECKLESS, TalentCombat.MAX_RECKLESS), Map.entry(WILD, TalentCombat.MAX_WILD),
            Map.entry(PLAGUE, 1.0), Map.entry(CHEAT_DEATH, TalentCombat.MAX_CHEAT_HEAL));

    /** Todas as chaves sem prefixo que o {@link TalentCombat} entende (para o comando que confere as árvores). */
    public static final Set<String> KNOWN = Set.of(RANGED_DAMAGE, SUMMON_DAMAGE, CRIT_CHANCE, CRIT_DAMAGE, LUCKY, LIFESTEAL,
            MANA_ON_HIT, MANA_ON_KILL, HEAL_ON_KILL, KILL_SPEED, DAMAGE_RESIST, MAGIC_RESIST, PROJECTILE_RESIST, MELEE_RESIST,
            FALL_RESIST, FIRE_RESIST, DODGE, MANA_SHIELD, THORNS, SECOND_WIND, REGEN_HEALTH, HEALING_RECEIVED, LOW_HEALTH_POWER,
            EXECUTE, FIRST_STRIKE, BACKSTAB, NIGHT_POWER, DAY_POWER, UNDEAD_DAMAGE, ALL_DAMAGE, MELEE_DAMAGE, SPELL_DAMAGE_ALL,
            BOSS_DAMAGE, NEAR_DAMAGE, FAR_DAMAGE, AFFLICTED_DAMAGE, HIGH_MANA_POWER, FULL_HEALTH_POWER, AIM_POWER, RUN_POWER,
            LOW_HEALTH_RESIST, FULL_HEALTH_RESIST, BOSS_RESIST, COMBO, KILL_FURY, CRIT_FURY, PARRY_POWER, REVENGE, DODGE_POWER,
            SURGE, ULT_POWER, SUMMON_MARK, RECKLESS, WILD, FIFTH_HIT, ECHO, CULL, KILL_BURST, PLAGUE, CRIT_MANA, CRIT_HEAL,
            CRIT_CDR, KILL_CDR, CHARGE_ON_KILL, CAST_REFUND, HEAL_ON_CAST, CAST_WARD, GUARD_FORTIFY, GUARD_REFLECT, PARRY_HEAL,
            PARRY_MANA, SUMMON_BOND, SUMMON_LIFESTEAL, CHEAT_DEATH, BARRIER);
    /** Efeitos aceitos depois de {@link #ON_HIT_PREFIX} e {@link #ON_STRUCK_PREFIX}. */
    public static final Set<String> ON_HIT_KINDS = Set.of("burn", "poison", "slowness", "weakness", "wither", "bleed", "chill", "shock");

    /** A chave de passiva existe? (as com prefixo conferem o que vem depois dele) */
    public static boolean known(String key) {
        if (KNOWN.contains(key)) return true;
        if (key.startsWith(ON_HIT_PREFIX)) return ON_HIT_KINDS.contains(key.substring(ON_HIT_PREFIX.length()));
        if (key.startsWith(ON_STRUCK_PREFIX)) return ON_HIT_KINDS.contains(key.substring(ON_STRUCK_PREFIX.length()));
        if (key.startsWith(SPELL_DAMAGE_PREFIX)) {
            return com.renanpontes.isekaimahou.core.Element.byId(key.substring(SPELL_DAMAGE_PREFIX.length())) != null;
        }
        return false;
    }

    private final Map<String, Double> values;

    private TalentStats(Map<String, Double> values) {
        this.values = values;
    }

    public static TalentStats of(Map<String, Double> values) {
        return values.isEmpty() ? EMPTY : new TalentStats(Collections.unmodifiableMap(new HashMap<>(values)));
    }

    public double get(String key) {
        Double v = values.get(key);
        return v == null ? 0.0 : v;
    }

    /** Valor já limitado ao teto próprio da passiva (se tiver). */
    public double capped(String key) {
        Double cap = CAPS.get(key);
        double v = get(key);
        return cap == null ? v : Math.min(cap, v);
    }

    public boolean isEmpty() { return values.isEmpty(); }

    public Map<String, Double> all() { return values; }
}
