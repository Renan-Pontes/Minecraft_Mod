package com.renanpontes.isekaimahou.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

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
            .comment("Cidades são zonas seguras: monstros não nascem naturalmente dentro (os blocos podem ser destruídos normalmente)")
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
    public enum ChatMode implements TranslatableEnum {
        MINIMAL, NORMAL, ALL;

        @Override
        public Component getTranslatedName() { return enumName("chat_mode", this); }
    }
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
    // ---- v1.3 (desempenho) ----
    /** Preset gráfico do mod: quantidade de partículas, alcance dos efeitos, sombras/brilhos extras. Ver client/FxQuality. */
    public enum Graphics implements TranslatableEnum {
        LOW, MEDIUM, HIGH, ULTRA;

        @Override
        public Component getTranslatedName() { return enumName("graphics", this); }
    }
    public static final ModConfigSpec.EnumValue<Graphics> GRAPHICS = CLIENT
            .comment("Qualidade dos efeitos do mod:",
                    "  LOW    = PC fraco: poucas partículas, efeitos curtos e perto, sem brilhos extras",
                    "  MEDIUM = PC ok",
                    "  HIGH   = padrão",
                    "  ULTRA  = PC forte: tudo no máximo")
            .defineEnum("graphicsPreset", Graphics.HIGH);
    /** "Modo do PC" escolhido na tela de Desempenho (NONE = o jogador ajusta tudo à mão). */
    public enum PcMode implements TranslatableEnum {
        NONE, LOW, OK, GOOD, ULTRA;

        @Override
        public Component getTranslatedName() { return enumName("pc_mode", this); }
    }
    public static final ModConfigSpec.EnumValue<PcMode> PC_MODE = CLIENT
            .comment("Modo do PC escolhido no menu (B) → engrenagem → Desempenho (Fraco/OK/Bom/Ultra).",
                    "  Também controla o alcance do Distant Horizons, se instalado. NONE = suas próprias opções.")
            .defineEnum("pcMode", PcMode.NONE);
    public static final ModConfigSpec.BooleanValue PERF_SUGGESTION_SHOWN = CLIENT
            .comment("A sugestão de Modo do PC (detectada pelo hardware) já foi mostrada uma vez")
            .define("perfSuggestionShown", false);
    // ---- v1.0 RELEASE (tela de opções no menu do Minecraft: Mods → Isekai Mahou → Config, e Opções → Isekai Mahou) ----
    public static final ModConfigSpec.BooleanValue HUD_CARD = CLIENT
            .comment("HUD: mostrar o cartão do canto superior esquerdo (classe, nível e Círculo)")
            .define("hudCard", true);
    public static final ModConfigSpec.BooleanValue HUD_PLACE_NAME = CLIENT
            .comment("HUD: mostrar o nome do lugar atual (cidade, vila...) e o selo de Procurado embaixo do cartão")
            .define("hudPlaceName", true);
    public static final ModConfigSpec.BooleanValue HUD_RESOURCE_BAR = CLIENT
            .comment("HUD: mostrar a barra de Mana/Vigor/Foco (e as suas condições de status ao lado dela)")
            .define("hudResourceBar", true);
    public static final ModConfigSpec.BooleanValue HUD_HEALTH_BAR = CLIENT
            .comment("HUD: mostrar a barra de vida com números do mod. Desligado = voltam os corações do Minecraft")
            .define("hudHealthBar", true);
    public static final ModConfigSpec.BooleanValue HUD_SPELL_BAR = CLIENT
            .comment("HUD: mostrar os slots de magia (Z X C G) e o loadout ao lado da barra de itens")
            .define("hudSpellBar", true);
    public static final ModConfigSpec.BooleanValue HUD_CAST_BAR = CLIENT
            .comment("HUD: mostrar a barra de conjuração e o cântico abaixo da mira")
            .define("hudCastBar", true);
    public static final ModConfigSpec.BooleanValue HUD_LEVEL_BANNERS = CLIENT
            .comment("HUD: mostrar a faixa de \"Subiu de nível\" / \"Novo Círculo\" no topo da tela")
            .define("hudLevelBanners", true);
    public static final ModConfigSpec.IntValue HUD_SCALE = CLIENT
            .comment("Tamanho do HUD do mod, em % (100 = normal). Vale para cartão, barras e slots de magia")
            .defineInRange("hudScalePercent", 100, 70, 130);
    public static final ModConfigSpec.BooleanValue SHOW_HINTS = CLIENT
            .comment("Dicas de primeira vez: uma linha discreta acima da barra de itens, uma por vez, que some sozinha e nunca repete")
            .define("showHints", true);
    public static final ModConfigSpec.BooleanValue LOADING_TIPS = CLIENT
            .comment("Dicas de jogo no menu principal e nas telas de carregamento")
            .define("loadingTips", true);
    public static final ModConfigSpec.BooleanValue GUIDE_TUTORIAL = CLIENT
            .comment("Tutorial do Espírito Guia para personagens novos. Desligado = o tutorial é pulado sozinho")
            .define("guideTutorial", true);
    public static final ModConfigSpec.BooleanValue PEACE_MODE_AUTO = CLIENT
            .comment("Modo Paz automático: liga sozinho ao entrar numa cidade ou em casa. Desligado = só pela tecla")
            .define("peaceModeAuto", true);
    public static final ModConfigSpec.IntValue SCREEN_SHAKE = CLIENT
            .comment("Tremor de tela (chefes, ultimates, impactos), em %. 0 = sem tremor")
            .defineInRange("screenShakePercent", 100, 0, 100);
    // ---- v1.0 RELEASE (costura): o que as ondas 1–2 deixaram sempre ligado ----
    public static final ModConfigSpec.BooleanValue SHOW_PARTY_MARKERS = CLIENT
            .comment("Grupo: mostrar o marcador (losango com nome e distância) dos amigos que estão longe")
            .define("showPartyMarkers", true);
    public static final ModConfigSpec.BooleanValue SHOW_FIGHT_TIMER = CLIENT
            .comment("Chefes: mostrar o cronômetro da luta (e o seu melhor tempo) ao lado da barra do chefe")
            .define("showFightTimer", true);
    public static final ModConfigSpec.BooleanValue SHOW_PLAYER_TITLES = CLIENT
            .comment("Renome: mostrar o título dos outros jogadores acima do nome deles")
            .define("showPlayerTitles", true);
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

    /** Igual a {@link #safe(ModConfigSpec.BooleanValue)} para números inteiros. */
    public static int safe(ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return value.getDefault();
        }
    }

    // =====================================================================================
    //  Leituras prontas para os outros módulos (v1.0 RELEASE)
    // =====================================================================================
    /**
     * Multiplicador do tremor de tela escolhido pelo jogador (0 = desligado, 1 = normal). CLIENTE: multiplique a
     * força de qualquer tremor de câmera por isto. No servidor dedicado devolve sempre 1.
     */
    public static float shakeScale() {
        return Math.max(0, Math.min(100, safe(SCREEN_SHAKE))) / 100F;
    }

    /** Escala do HUD do mod (0.7..1.3). CLIENTE. */
    public static float hudScale() {
        return Math.max(70, Math.min(130, safe(HUD_SCALE))) / 100F;
    }

    /** Dicas de primeira vez ligadas? CLIENTE. */
    public static boolean hintsEnabled() {
        return safe(SHOW_HINTS);
    }

    // ---- Preferências por jogador que o SERVIDOR precisa saber (o cliente manda ao entrar e quando muda) ----
    /** Bit de {@link #setPlayerPrefs}: Modo Paz liga sozinho nas zonas seguras. */
    public static final int PREF_PEACE_AUTO = 1;
    /** Bit de {@link #setPlayerPrefs}: quer o tutorial do Espírito Guia. */
    public static final int PREF_TUTORIAL = 2;
    private static final int PREF_DEFAULT = PREF_PEACE_AUTO | PREF_TUTORIAL;
    private static final Map<UUID, Integer> PLAYER_PREFS = new ConcurrentHashMap<>();

    /** Servidor: guarda as preferências que o cliente do jogador mandou (o módulo ui_hud chama). */
    public static void setPlayerPrefs(UUID player, int bits) {
        if (player != null) PLAYER_PREFS.put(player, bits);
    }

    /** Servidor: o jogador saiu (volta ao padrão na próxima entrada, até o cliente mandar de novo). */
    public static void clearPlayerPrefs(UUID player) {
        if (player != null) PLAYER_PREFS.remove(player);
    }

    private static boolean pref(@Nullable Player player, int bit) {
        Integer bits = player == null ? null : PLAYER_PREFS.get(player.getUUID());
        return ((bits == null ? PREF_DEFAULT : bits) & bit) != 0;
    }

    /** SERVIDOR: este jogador quer o Modo Paz ligando sozinho nas zonas seguras? (padrão: sim) */
    public static boolean peaceAuto(@Nullable Player player) {
        return pref(player, PREF_PEACE_AUTO);
    }

    /** SERVIDOR: este jogador quer o tutorial do Espírito Guia? (padrão: sim) */
    public static boolean wantsTutorial(@Nullable Player player) {
        return pref(player, PREF_TUTORIAL);
    }

    /** CLIENTE: os bits das preferências acima, lidos da config local. */
    public static int localPlayerPrefs() {
        return (safe(PEACE_MODE_AUTO) ? PREF_PEACE_AUTO : 0) | (safe(GUIDE_TUTORIAL) ? PREF_TUTORIAL : 0);
    }

    /** Nome traduzido de um valor de enum na tela de config ({@code isekai_mahou.configuration.enum.<tipo>.<valor>}). */
    private static Component enumName(String type, Enum<?> value) {
        return Component.translatable("isekai_mahou.configuration.enum." + type + "." + value.name().toLowerCase(java.util.Locale.ROOT));
    }

    private MahouConfig() {}
}
