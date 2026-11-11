package com.renanpontes.isekaimahou.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Configurações (arquivo config/isekai_mahou-common.toml e -client.toml). */
public final class MahouConfig {
    // ---------------- Comum (servidor) ----------------
    private static final ModConfigSpec.Builder COMMON = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue ANNOUNCE_RADIUS = COMMON
            .comment("Raio (blocos) em que os jogadores veem o nome das magias fortes e os cânticos")
            .defineInRange("announceRadius", 32, 0, 256);
    public static final ModConfigSpec.IntValue MAX_PERSISTENT_SPELLS = COMMON
            .comment("Máximo de magias persistentes (zonas, torres, runas...) por jogador ao mesmo tempo")
            .defineInRange("maxPersistentSpells", 6, 1, 64);
    public static final ModConfigSpec.BooleanValue DOMAIN_GRIEFING = COMMON
            .comment("Domínios podem trocar blocos temporariamente (Paisagem Elemental). Os blocos voltam ao normal.")
            .define("domainLandscape", true);
    public static final ModConfigSpec.BooleanValue SPELL_BLOCK_DESTRUCTION = COMMON
            .comment("Magias lendárias podem destruir blocos de verdade (crateras, apagar blocos). Também respeita a regra mobGriefing.")
            .define("spellBlockDestruction", true);
    public static final ModConfigSpec.IntValue TRIAL_MAX_INSTANCES = COMMON
            .comment("Máximo de Câmaras de Provação ativas ao mesmo tempo no servidor")
            .defineInRange("trialMaxInstances", 8, 1, 64);
    public static final ModConfigSpec.IntValue PEDESTAL_MAX_PER_CHUNK = COMMON
            .comment("Máximo de Pedestais Rúnicos por chunk (0 = proibidos)")
            .defineInRange("pedestalMaxPerChunk", 4, 0, 64);
    // ---- v0.8 ----
    public static final ModConfigSpec.IntValue DESTRUCTION_BLOCKS_PER_TICK = COMMON
            .comment("Destruição de terreno por magias: máximo de blocos alterados por tick no servidor inteiro (fila)")
            .defineInRange("destructionBlocksPerTick", 3000, 100, 50000);
    public static final ModConfigSpec.BooleanValue DESTRUCTION_BLOCK_ENTITIES = COMMON
            .comment("Magias destrutivas também podem apagar blocos com inventário/dados (baús, fornalhas...). Falso = esses blocos ficam intactos.")
            .define("destructionBlockEntities", false);
    public static final ModConfigSpec.BooleanValue STATUS_ON_PLAYERS = COMMON
            .comment("Condições de status (Molhado, Em Chamas, Gelado...) também afetam jogadores")
            .define("statusOnPlayers", true);
    public static final ModConfigSpec.BooleanValue CITY_PROTECTION = COMMON
            .comment("Cidades são zonas seguras: magias não destroem blocos lá e monstros não nascem naturalmente dentro")
            .define("cityProtection", true);
    // ---- v1.0 ----
    public static final ModConfigSpec.BooleanValue BLOCK_MAP_TELEPORT = COMMON
            .comment("Bloqueia /tp, /teleport e o teleporte por waypoints/mapa (Xaero, JourneyMap) para jogadores fora do criativo/espectador.",
                    "Viagem rápida só pelas Pedras de Teleporte e locais descobertos.")
            .define("blockMapTeleport", true);
    public static final ModConfigSpec.BooleanValue VANILLA_SCALING = COMMON
            .comment("Mobs do Minecraft (vanilla) ficam mais fortes para acompanhar o jogador do mod (vida e dano multiplicados abaixo)")
            .define("vanillaScaling", true);
    public static final ModConfigSpec.DoubleValue VANILLA_HEALTH_MULT = COMMON
            .comment("Multiplicador de vida dos mobs vanilla quando vanillaScaling está ligado")
            .defineInRange("vanillaHealthMultiplier", 1.5D, 0.1D, 20D);
    public static final ModConfigSpec.DoubleValue VANILLA_DAMAGE_MULT = COMMON
            .comment("Multiplicador de dano dos mobs vanilla quando vanillaScaling está ligado")
            .defineInRange("vanillaDamageMultiplier", 1.25D, 0.1D, 20D);
    public static final ModConfigSpec COMMON_SPEC = COMMON.build();

    // ---------------- Cliente ----------------
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue CUTSCENES = CLIENT
            .comment("Mostrar cutscenes (ultimates, chefes, rituais)")
            .define("cutscenes", true);
    public static final ModConfigSpec.BooleanValue OWN_CUTSCENES_ONLY = CLIENT
            .comment("Mostrar só as cutscenes das SUAS magias e de chefes (ignora as de outros jogadores)")
            .define("ownCutscenesOnly", false);
    public static final ModConfigSpec.BooleanValue OTHERS_ANNOUNCEMENTS = CLIENT
            .comment("Mostrar o nome das magias de outros jogadores/mobs")
            .define("othersAnnouncements", true);
    public static final ModConfigSpec.BooleanValue REDUCED_PARTICLES = CLIENT
            .comment("Reduzir partículas de magia (melhora o FPS)")
            .define("reducedParticles", false);
    public static final ModConfigSpec.BooleanValue SHOW_OBJECTIVES = CLIENT
            .comment("Mostrar o rastreador de objetivos no canto superior direito (a tecla J alterna; o tutorial liga)")
            .define("showObjectives", false);
    public static final ModConfigSpec.BooleanValue SHOW_TARGET_INFO = CLIENT
            .comment("Mostrar o Olho Analítico: nome, vida, armadura e fraquezas do alvo que você está olhando")
            .define("showTargetInfo", true);
    public static final ModConfigSpec.BooleanValue FLOATING_HEALTH_BARS = CLIENT
            .comment("Mostrar barrinhas de vida flutuantes sobre mobs feridos")
            .define("floatingHealthBars", true);
    // ---- v0.8 ----
    public static final ModConfigSpec.BooleanValue SHOW_STATUS_ICONS = CLIENT
            .comment("Mostrar ícones de condições de status (Molhado, Em Chamas...) sobre os mobs")
            .define("showStatusIcons", true);
    public static final ModConfigSpec.BooleanValue SHOW_REACTION_TEXT = CLIENT
            .comment("Mostrar o nome das reações elementais (ELETROCUTAR!, DERRETER!...) sobre o alvo")
            .define("showReactionText", true);
    public static final ModConfigSpec.BooleanValue SHOW_STACK = CLIENT
            .comment("Mostrar a Pilha: magias e golpes inimigos sendo conjurados por perto (para responder com Contramágica)")
            .define("showStack", true);
    public static final ModConfigSpec.BooleanValue REPUTATION_TOASTS = CLIENT
            .comment("Mostrar avisos de ganho/perda de reputação")
            .define("reputationToasts", true);
    // ---- v1.0 ----
    public static final ModConfigSpec.BooleanValue CUSTOM_TITLE_SCREEN = CLIENT
            .comment("Usar o menu principal do Isekai Mahou (céu mágico, runas). Falso = menu clássico do Minecraft")
            .define("customTitleScreen", true);
    public static final ModConfigSpec.BooleanValue SHOW_ARENA_BOUNDS = CLIENT
            .comment("Mostrar a borda das arenas (Provações, chefes) e o aviso \"Limite da arena: N blocos\" ao se aproximar")
            .define("showArenaBounds", true);
    public static final ModConfigSpec.BooleanValue LAYOUT_AVOID_MINIMAP = CLIENT
            .comment("O HUD do mod desvia do minimapa (Xaero/JourneyMap) no canto superior direito")
            .define("layoutAvoidMinimap", true);
    // ---- v1.1 (menos poluição na tela) ----
    /** Quanto do mod vai para o CHAT: o resto fica no registro "Avisos" do menu (B) e numa linha curta acima da hotbar. */
    public enum ChatMode { MINIMAL, NORMAL, ALL }
    public static final ModConfigSpec.EnumValue<ChatMode> CHAT_MESSAGES = CLIENT
            .comment("Mensagens do mod no chat:",
                    "  MINIMAL = só o essencial (erros/recusas, convites, falas de NPC, subir de nível/Círculo, chefe derrotado, respostas a comandos);",
                    "            o resto vai para a aba \"Avisos\" do menu (B) e aparece rapidinho acima da hotbar",
                    "  NORMAL  = quase tudo no chat; só dicas, progresso e recompensas vão para os Avisos",
                    "  ALL     = tudo no chat, como antes")
            .defineEnum("chatMessages", ChatMode.MINIMAL);
    public static final ModConfigSpec.BooleanValue NOTICE_ACTION_BAR = CLIENT
            .comment("Avisos que saem do chat aparecem numa linha curta acima da hotbar (no máximo um a cada ~1,5 s, sem repetir)")
            .define("noticeActionBar", true);
    public static final ModConfigSpec.BooleanValue PROGRESSIVE_MENU = CLIENT
            .comment("Menu (B) com revelação progressiva: as abas aparecem quando fazem sentido (\"Nova aba!\"). Falso = todas as abas desde o começo")
            .define("progressiveMenu", true);
    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    /** Lê um valor sem quebrar se a config ainda não carregou (antes do arquivo ser lido, ou no menu principal): devolve o padrão. */
    public static boolean safe(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return value.getDefault();
        }
    }

    /** Igual a {@link #safe(ModConfigSpec.BooleanValue)} para números com vírgula. */
    public static double safe(ModConfigSpec.DoubleValue value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return value.getDefault();
        }
    }

    /** Igual a {@link #safe(ModConfigSpec.BooleanValue)} para enums. */
    public static <T extends Enum<T>> T safe(ModConfigSpec.EnumValue<T> value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return value.getDefault();
        }
    }

    private MahouConfig() {}
}
