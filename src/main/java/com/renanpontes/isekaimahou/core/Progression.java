package com.renanpontes.isekaimahou.core;

import java.util.List;

/**
 * Fórmulas de balanceamento (puras, sem efeitos). A API que MUDA o jogador fica em {@link MagicApi}.
 * v0.7: glifos por magia crescem com o NÍVEL, vida cresce com o nível, glifos Arcanos liberados por nível e ranks de
 * Provação por elemento.
 * <p>
 * v1.0 (docs/V10_DESIGN.md §0.1 e A1.5; tabela em docs/v10/API_spells.md):
 * <ul>
 *   <li>Nível máximo {@link #MAX_LEVEL} = 150; {@link #HIDDEN_MAX_LEVEL} = 200 só depois de matar o Dragão Primordial
 *       (contador {@link #HIDDEN_CAP_COUNTER}) — use {@link #levelCap(MagicData)}; nada mostra 200 antes disso.</li>
 *   <li>Curva de XP bem mais difícil até o 100 e, na release 1.0, ainda mais íngreme depois do 50 e MUITO mais depois
 *       do 100 ({@link #xpToNext}, tabela no jogo com {@code /mahou balance}); Círculo 10 no nível 100
 *       ({@link #levelCap(int)}); os níveis 100–200 dão poder ({@link #highLevelPower}), glifos por magia
 *       ({@link #maxGlyphs(int, int)}) e limite de mana ({@link #levelManaLimitBonus}).</li>
 *   <li>Inflação de números: vida base {@link #BASE_PLAYER_HEALTH}, {@link #DAMAGE_SCALE}, {@link #MOB_HEALTH_SCALE},
 *       {@link #MOB_DAMAGE_SCALE}, {@link #VANILLA_HEALTH_SCALE}, {@link #VANILLA_DAMAGE_SCALE} (aplicados por
 *       {@code core.balance}).</li>
 * </ul>
 */
public final class Progression {
    /** Nível máximo visível (com Círculo 10). */
    public static final int MAX_LEVEL = 150;
    /** Nível máximo escondido: liberado só depois de matar o Dragão Primordial. */
    public static final int HIDDEN_MAX_LEVEL = 200;
    /** Contador (MagicData.counter) que libera o {@link #HIDDEN_MAX_LEVEL}: quem estava na luta do Dragão Primordial. */
    public static final String HIDDEN_CAP_COUNTER = "boss.primordial_dragon";
    /** Nível em que se chega ao Círculo 10 (o resto do jogo, 100→150/200, é o "fim de jogo"). */
    public static final int CIRCLE_TEN_LEVEL = 100;
    public static final int MAX_CIRCLE = 10;
    public static final int MAX_ELEMENT_LEVEL = 30;
    public static final int SLOTS = 4;          // Z, X, C, G
    public static final int ULTIMATE_SLOT = 3;  // G
    public static final int MAX_LOADOUTS = 5;
    public static final int MAX_WHEEL = 5;      // elementos registrados (sem contar o Arcano)
    public static final float MAX_CHARGE = 100.0F;
    /** Limite absoluto de glifos numa magia (o codec de rede aceita até 64). v1.0: 50 (níveis 100–200 dão glifos). */
    public static final int MAX_GLYPHS_HARD = 50;

    // ---------- Inflação de números (§0.1) ----------
    /** Vida base do jogador (o vanilla é 20; o núcleo soma {@link #BASE_HEALTH_BONUS} com um modificador próprio). */
    public static final int BASE_PLAYER_HEALTH = 40;
    /** Quanto o modificador {@code isekai_mahou:base_health} soma à vida base vanilla (20). */
    public static final int BASE_HEALTH_BONUS = BASE_PLAYER_HEALTH - 20;
    /** Todo dano de magia/técnica do mod (aplicado em {@code SpellContext.damage} pela trilha A1b). */
    public static final float DAMAGE_SCALE = 1.5F;
    /** v1.0: curas de magia acompanham a vida dobrada (40 de base) e os mobs com vida ×2. */
    public static final float HEAL_SCALE = 2.0F;
    /** Vida das criaturas do namespace isekai_mahou (modificador de atributo ao entrar no mundo). */
    public static final float MOB_HEALTH_SCALE = 2.0F;
    /** Dano (não-mágico) das criaturas do namespace isekai_mahou. */
    public static final float MOB_DAMAGE_SCALE = 1.5F;
    /** Vida dos mobs HOSTIS do vanilla (se {@code VANILLA_SCALING} estiver ligado). */
    public static final float VANILLA_HEALTH_SCALE = 1.5F;
    /** Dano dos mobs HOSTIS do vanilla (se {@code VANILLA_SCALING} estiver ligado). */
    public static final float VANILLA_DAMAGE_SCALE = 1.25F;
    /** Padrão da opção {@code VANILLA_SCALING} (config da trilha A3; ver {@code core.balance.MobScaling}). */
    public static final boolean VANILLA_SCALING_DEFAULT = true;

    public static final int MEDITATION_DELAY_TICKS = 40;
    public static final float MEDITATION_REGEN_MULT = 5.0F;
    public static final int MEDITATION_XP_INTERVAL = 60;

    /** Nível em que a curva de XP fica mais íngreme (release 1.0: "difícil de upar, ainda mais após lvl 50 e 100"). */
    public static final int STEEP_LEVEL = 50;

    /**
     * XP necessário para passar do nível informado ao próximo. Três trechos, sempre crescentes e sem degrau para baixo:
     * <ul>
     *   <li>1–49: {@code 40 + 18·L + 0,35·L²} (igual à v1.0 interna: 1→50 ≈ 38 mil);</li>
     *   <li>50–99: a mesma base + {@code 60·k + 1,4·k²} com {@code k = L−50} (nível 99 ≈ 11,5 mil; 50→100 ≈ 300 mil,
     *       ~1,8× o que era);</li>
     *   <li>100+: {@code 12000 + 400·k + 4·k²} com {@code k = L−100} (nível 150 = 42 mil, 199 ≈ 91 mil; 100→150 ≈ 1,25
     *       milhão — ~4× o que era — e 150→200 ≈ 3,2 milhões).</li>
     * </ul>
     * Mundos salvos: o nível já alcançado NÃO muda (só o XP que falta para o próximo). Tabela: {@code /mahou balance}.
     */
    public static int xpToNext(int level) {
        int l = Math.max(1, level);
        float base = 40F + 18F * l + 0.35F * l * l;
        if (l < STEEP_LEVEL) return Math.round(base);
        if (l < CIRCLE_TEN_LEVEL) {
            int k = l - STEEP_LEVEL;
            return Math.round(base + 60F * k + 1.4F * k * k);
        }
        int k = l - CIRCLE_TEN_LEVEL;
        return 12000 + 400 * k + 4 * k * k;
    }

    /** XP somado necessário para subir {@code levels} níveis a partir de {@code fromLevel}. */
    public static int xpForLevels(int fromLevel, int levels) {
        long sum = 0;
        for (int l = Math.max(1, fromLevel); l < Math.min(HIDDEN_MAX_LEVEL, fromLevel + levels); l++) sum += xpToNext(l);
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /** XP total acumulado para chegar do nível 1 ao nível informado (para tabelas de balanço; cabe em {@code long}). */
    public static long totalXpTo(int level) {
        long sum = 0;
        for (int l = 1; l < Math.min(HIDDEN_MAX_LEVEL, level); l++) sum += xpToNext(l);
        return sum;
    }

    /** Teto do Círculo (C1..C8 = 10..80, C9 = 100: o Círculo 10 chega no nível 100). */
    private static final int[] CIRCLE_CAP = {10, 20, 30, 40, 50, 60, 70, 80, CIRCLE_TEN_LEVEL};

    /**
     * Nível máximo permitido no Círculo informado (trava até a Ascensão). No Círculo 10 devolve o máximo VISÍVEL
     * ({@link #MAX_LEVEL}); o escondido depende do jogador: use {@link #levelCap(MagicData)}.
     */
    public static int levelCap(int circle) {
        if (circle >= MAX_CIRCLE) return MAX_LEVEL;
        return CIRCLE_CAP[Math.max(0, circle - 1)];
    }

    /**
     * Limite de nível real do jogador: o do Círculo e, no Círculo 10, 150 — ou 200 se ele já ajudou a matar o Dragão
     * Primordial ({@link #HIDDEN_CAP_COUNTER} ≥ 1). Funciona no cliente com {@code ClientMagic.data()}.
     */
    public static int levelCap(MagicData data) {
        if (data == null) return levelCap(1);
        if (data.circle() < MAX_CIRCLE) return levelCap(data.circle());
        return maxLevel(data);
    }

    /** Nível máximo absoluto do jogador (150, ou 200 depois do Dragão Primordial). */
    public static int maxLevel(MagicData data) {
        return hiddenCapUnlocked(data) ? HIDDEN_MAX_LEVEL : MAX_LEVEL;
    }

    /** O jogador já rompeu o limite (níveis 150→200 liberados)? */
    public static boolean hiddenCapUnlocked(MagicData data) {
        return data != null && data.counter(HIDDEN_CAP_COUNTER) >= 1;
    }

    /**
     * Teto que um save pode ter no Círculo informado, SEM olhar os contadores (o carregamento do MagicData usa para não
     * cortar quem já passou do 150): no Círculo 10 é {@link #HIDDEN_MAX_LEVEL}.
     */
    public static int absoluteLevelCap(int circle) {
        return circle >= MAX_CIRCLE ? HIDDEN_MAX_LEVEL : levelCap(circle);
    }

    /** Quanto XP pode ficar guardado no limite do Círculo (o equivalente aos próximos 5 níveis). */
    public static int bankCap(int level) {
        return xpForLevels(level, 5);
    }

    /** Raio (blocos) em que os jogadores dividem o XP de nível de um abate (todos ganham o valor cheio). */
    public static final double KILL_SHARE_RADIUS = 24.0;

    /** XP de nível por abate: com magia max(3, vida/2); outro abate max(2, vida/4). */
    public static int killXp(float maxHealth, boolean spellKill) {
        return spellKill ? Math.max(3, Math.round(maxHealth / 2F)) : Math.max(2, Math.round(maxHealth / 4F));
    }

    /** XP de elemento por abate com magia (só para quem matou, no elemento do último dano). */
    public static int killElementXp(float maxHealth) {
        return Math.max(2, Math.round(maxHealth / 3F));
    }

    public static int maxMana(int level, int circle) {
        return 100 + 8 * (level - 1) + 40 * (circle - 1);
    }

    public static float regenPerTick(int level, int circle) {
        return (2.0F + 0.08F * level + 0.4F * circle) / 20.0F;
    }

    /**
     * Quantos glifos uma magia pode ter: cresce com o nível e com o Círculo (até o 100 igual à v0.8, para os saves
     * antigos não perderem magias) e os níveis 100–200 somam +1 a cada 10 níveis (até {@link #MAX_GLYPHS_HARD}).
     */
    public static int maxGlyphs(int level, int circle) {
        int l = Math.max(1, level);
        int base = 4 + Math.min(l, CIRCLE_TEN_LEVEL) / 4 + Math.max(1, circle);
        int high = Math.max(0, l - CIRCLE_TEN_LEVEL) / 10;
        return Math.min(MAX_GLYPHS_HARD, base + high);
    }

    /**
     * @deprecated use {@link #maxGlyphs(int, int)} (v0.7: depende do nível). Mantido para compatibilidade:
     * assume o nível máximo do Círculo.
     */
    @Deprecated
    public static int maxGlyphs(int circle) {
        return maxGlyphs(levelCap(circle), circle);
    }

    /** Quantos elementos diferentes (sem contar o Arcano) uma magia pode misturar. */
    public static int maxSpellElements(int circle) {
        if (circle >= 7) return 4;
        if (circle >= 4) return 3;
        return 2;
    }

    /**
     * Vida máxima extra pelo nível (v1.0: o dobro da v0.8): +4 a cada 5 níveis até +60 (nível 75); depois do 100,
     * +2 a cada 5 níveis (nível 150: +80; nível 200: +100). Soma-se à vida base {@link #BASE_PLAYER_HEALTH}.
     */
    public static int bonusHealth(int level) {
        int l = Math.max(1, level);
        int early = Math.min(60, (Math.min(l, CIRCLE_TEN_LEVEL) / 5) * 4);
        int late = (Math.max(0, l - CIRCLE_TEN_LEVEL) / 5) * 2;
        return early + late;
    }

    /** Poder Mágico extra dos níveis acima do 100: +0,4% por nível (nível 150: +0,20; nível 200: +0,40). */
    public static double highLevelPower(int level) {
        return Math.max(0, level - CIRCLE_TEN_LEVEL) * 0.004;
    }

    /**
     * Quanto os níveis acima do 100 somam ao LIMITE de mana/vigor/foco (o teto treinável da trilha A1b): +12 por nível
     * (nível 150: +600; nível 200: +1200). 0 até o nível 100.
     */
    public static int levelManaLimitBonus(int level) {
        return Math.max(0, level - CIRCLE_TEN_LEVEL) * 12;
    }

    // ---------- Lições (glifos de origem de outra classe) ----------
    /** XP que o PROFESSOR ganha quando alguém aprende com uma Lição dele (glifo de tier 1..5): 120..360. */
    public static int lessonTeacherXp(int tier) {
        return 60 + 60 * Math.max(1, Math.min(5, tier));
    }

    /**
     * Círculo pedido para aprender um glifo de origem por Lição: o do tier do glifo (1→C1, 2→C2, 3→C3, 4→C5, 5→C8) ou
     * o {@code minCircle} dele (o maior) e, para quem é de OUTRA classe, +1 (custo do multiclasse; máx. 10).
     */
    public static int lessonCircle(int tier, int minCircle, boolean otherClass) {
        int byTier = switch (Math.max(1, Math.min(5, tier))) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 5;
            default -> 8;
        };
        return Math.min(MAX_CIRCLE, Math.max(byTier, Math.max(1, minCircle)) + (otherClass ? 1 : 0));
    }

    /** Essências gastas ao ler uma Lição: 1 (mesma classe) ou 2 (outra classe — custo do multiclasse). */
    public static int lessonEssenceCost(boolean otherClass) {
        return otherClass ? 2 : 1;
    }

    private static final int[] WHEEL_BY_CIRCLE = {1, 2, 2, 2, 3, 3, 4, 4, 5, 5};
    private static final int[] LOADOUTS_BY_CIRCLE = {2, 2, 3, 3, 3, 4, 4, 5, 5, 5};

    /** Quantos elementos (além do Arcano) podem ficar registrados na Roda. */
    public static int wheelSize(int circle) {
        return WHEEL_BY_CIRCLE[Math.max(0, Math.min(9, circle - 1))];
    }

    public static int loadoutsPerElement(int circle) {
        return LOADOUTS_BY_CIRCLE[Math.max(0, Math.min(9, circle - 1))];
    }

    public static boolean ultimatesAllowed(int circle) { return circle >= 4; }
    public static boolean simpleDomainAllowed(int circle) { return circle >= 4; }
    public static boolean domainAllowed(int circle) { return circle >= 6; }

    // ---------- Glifos Arcanos por nível ----------
    private static final int[] ARCANE_UNLOCK_LEVEL = {1, 5, 10, 18, 28};

    /** Nível do jogador em que os glifos Arcanos do tier informado (1..5) são liberados. */
    public static int arcaneUnlockLevel(int tier) {
        return ARCANE_UNLOCK_LEVEL[Math.max(0, Math.min(4, tier - 1))];
    }

    // ---------- Provações (ranks elementais) ----------
    /** Nó do caminho liberado por cada rank de Provação (índice 0 = rank I). */
    public static final List<String> TRIAL_NODES = List.of("apprentice", "adept", "fluidity", "master", "cataclysm", "domain", "archmage");
    public static final int MAX_TRIAL_RANK = 7;
    private static final int[] TRIAL_ELEMENT_LEVEL = {3, 6, 9, 12, 15, 19, 24};
    private static final int[] TRIAL_CIRCLE = {1, 1, 2, 3, 4, 6, 8};

    /** Nível do elemento exigido para entrar na Provação do rank (1..7). */
    public static int trialElementLevel(int rank) {
        return TRIAL_ELEMENT_LEVEL[Math.max(0, Math.min(6, rank - 1))];
    }

    /** Círculo exigido para entrar na Provação do rank (1..7). */
    public static int trialCircle(int rank) {
        return TRIAL_CIRCLE[Math.max(0, Math.min(6, rank - 1))];
    }

    /** Rank de Provação (1..7) que libera o nó informado, ou 0 se o nó não é de Provação. */
    public static int trialRankOfNode(String node) {
        return TRIAL_NODES.indexOf(node) + 1;
    }

    // ---------- Domínio elemental ----------
    public static int elementXpToNext(int elementLevel) {
        return 25 + 15 * elementLevel;
    }

    /** Nível do elemento a partir do XP acumulado (0 = acabou de despertar). */
    public static int elementLevel(int totalXp) {
        int level = 0;
        int remaining = totalXp;
        while (level < MAX_ELEMENT_LEVEL && remaining >= elementXpToNext(level)) {
            remaining -= elementXpToNext(level);
            level++;
        }
        return level;
    }

    /** XP acumulado dentro do nível atual do elemento (para barras de progresso). */
    public static int elementXpIntoLevel(int totalXp) {
        int level = 0;
        int remaining = totalXp;
        while (level < MAX_ELEMENT_LEVEL && remaining >= elementXpToNext(level)) {
            remaining -= elementXpToNext(level);
            level++;
        }
        return remaining;
    }

    private Progression() {}
}
