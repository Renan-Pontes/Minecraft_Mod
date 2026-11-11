package com.renanpontes.isekaimahou.core.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.reputation.FactionRegistry;
import com.renanpontes.isekaimahou.core.reputation.ReputationEvents;
import com.renanpontes.isekaimahou.core.reputation.ReputationService;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Reputação com as facções do mundo (Guilda, Reino, Academia, Igreja, Submundo, Povo Selvagem, Horda...).
 *
 * <ul>
 *   <li>Valor de {@link #MIN} a {@link #MAX} por facção, guardado no contador {@code rep.<id>} do {@link MagicData}
 *       (salvo com o jogador e sincronizado com o cliente — {@link #get} funciona nos dois lados).</li>
 *   <li>Facções vêm de {@code data/<ns>/factions/<id>.json} (recarregam com /reload e são enviadas ao cliente).</li>
 *   <li>Membros: tag de entidade {@code <ns>:faction/<id>} ou a interface {@link FactionMember}.</li>
 *   <li>{@link #add} aplica respingo em rivais/aliados, dispara {@code ReputationChangedEvent}, avisa o cliente e entrega
 *       as recompensas de patamar (uma vez cada).</li>
 * </ul>
 * Ver {@code docs/v08/API_reputation.md}.
 */
public final class Reputation {
    public static final int MIN = -1000;
    public static final int MAX = 1000;
    /** Devolva isto em {@link FactionMember#factionId()} para dizer "esta entidade não é de facção nenhuma" (ignora a tag). */
    public static final String NO_FACTION = "";

    /** Patamares de reputação, do pior ao melhor. {@link #min} = primeiro valor do patamar. */
    public enum Standing {
        ENEMY("enemy", MIN, 0xD03A2C, 1.5F),
        HOSTILE("hostile", -600, 0xF0643C, 1.5F),
        UNFRIENDLY("unfriendly", -300, 0xF0A043, 1.25F),
        NEUTRAL("neutral", -49, 0xD8D2C0, 1.0F),
        FRIENDLY("friendly", 100, 0x7BD86A, 0.95F),
        HONORED("honored", 300, 0x3FD1AE, 0.9F),
        REVERED("revered", 600, 0x6FA8FF, 0.8F),
        EXALTED("exalted", 900, 0xFFD86B, 0.7F);

        private static final Standing[] VALUES = values();

        public final String id;
        /** Primeiro valor do patamar (inclusivo). */
        public final int min;
        /** Cor RGB do patamar (texto, barras). */
        public final int rgb;
        private final float price;

        Standing(String id, int min, int rgb, float price) {
            this.id = id;
            this.min = min;
            this.rgb = rgb;
            this.price = price;
        }

        /** Último valor do patamar (inclusivo). */
        public int max() {
            Standing n = next();
            return n == null ? MAX : n.min - 1;
        }

        /** Multiplicador de preço dos membros. Inimigo/Hostil não negociam: confira {@link #canTrade()} antes. */
        public float priceMultiplier() { return price; }

        public boolean canTrade() { return ordinal() >= UNFRIENDLY.ordinal(); }

        public boolean atLeast(Standing other) { return ordinal() >= other.ordinal(); }

        @Nullable public Standing next() { return ordinal() + 1 < VALUES.length ? VALUES[ordinal() + 1] : null; }

        @Nullable public Standing previous() { return ordinal() > 0 ? VALUES[ordinal() - 1] : null; }

        public int argb() { return 0xFF000000 | rgb; }

        /** Nome traduzido ("Amigável"), já colorido. */
        public MutableComponent displayName() {
            return Component.translatable("reputation.isekai_mahou.standing." + id).withColor(rgb);
        }

        public static Standing of(int value) {
            for (int i = VALUES.length - 1; i > 0; i--) {
                if (value >= VALUES[i].min) return VALUES[i];
            }
            return ENEMY;
        }

        @Nullable public static Standing byId(@Nullable String id) {
            if (id == null) return null;
            String s = id.toLowerCase(Locale.ROOT);
            for (Standing st : VALUES) if (st.id.equals(s)) return st;
            return null;
        }

        /** Patamares que dão recompensa/título (Amigável em diante). */
        public static List<Standing> rewardTiers() {
            return List.of(FRIENDLY, HONORED, REVERED, EXALTED);
        }
    }

    /**
     * Entidades que decidem a própria facção (ex.: mercadores pela profissão). Tem prioridade sobre a tag.
     * Retorne {@code null} para usar a tag, ou {@link #NO_FACTION} para "nenhuma facção".
     * Precisa funcionar no cliente também (use dados sincronizados) se a facção aparecer na interface.
     */
    public interface FactionMember {
        @Nullable String factionId();
    }

    /** Um item de recompensa (id em texto: itens de módulos ausentes são ignorados). */
    public record ItemReward(ResourceLocation id, int count) {
        /** O stack, ou vazio se o item não existe neste jogo. */
        public ItemStack stack() {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, Math.min(count, item.getDefaultMaxStackSize() * 4)));
        }
    }

    /**
     * Recompensa ao alcançar um patamar pela 1ª vez.
     * @param legend {@code "random"}, um id de lenda ({@link Legends}) ou null
     * @param title  chave de tradução do título desbloqueado, ou null
     */
    public record TierRewards(int xp, List<ItemReward> items, int glyphScrolls, int glyphTier, @Nullable String legend,
                              @Nullable String title) {
        public static final TierRewards NONE = new TierRewards(0, List.of(), 0, 1, null, null);

        public boolean isEmpty() {
            return xp <= 0 && items.isEmpty() && glyphScrolls <= 0 && legend == null && title == null;
        }
    }

    /**
     * Definição de uma facção (lida do JSON). Mapas são imutáveis.
     * @param rivals     facção → peso (1 = respingo −50% dos ganhos; 0,5 = −25%)
     * @param allies     facção → peso (1 = +25% de qualquer mudança)
     * @param killPenalties  penalidade por tipo de entidade (sobrepõe {@code killPenalty})
     * @param killRewards    ao matar um membro DESTA facção, ganhe reputação nestas (limitado por {@code killRewardDailyCap}/dia cada)
     * @param counterRewards contador do MagicData (ex.: "trial.clear") → reputação nesta facção a cada +1
     * @param neutralWhenFriendly membros hostis por natureza ficam neutros com patamar ≥ Amigável
     * @param guards     tipos de entidade (sem IA própria de facção) que atacam quem está Hostil/Inimigo com esta facção
     * @param traders    tipos com trocas vanilla (ex.: aldeão) que se recusam a negociar com quem está Hostil/Inimigo
     */
    public record Faction(String id, int color, ResourceLocation icon, int order, Map<String, Float> rivals, Map<String, Float> allies,
                          int killPenalty, Map<ResourceLocation, Integer> killPenalties, Map<String, Integer> killRewards,
                          int killRewardDailyCap, Map<String, Integer> counterRewards, boolean neutralWhenFriendly,
                          Set<ResourceLocation> guards, Set<ResourceLocation> traders, Map<Standing, TierRewards> rewards,
                          TagKey<EntityType<?>> memberTag) {

        /** "Guilda dos Aventureiros" (tradução {@code faction.isekai_mahou.<id>}). */
        public MutableComponent name() {
            return Component.translatableWithFallback("faction.isekai_mahou." + id, id);
        }

        /** Nome na cor da facção. */
        public MutableComponent coloredName() {
            return name().withColor(color);
        }

        public MutableComponent description() {
            return Component.translatableWithFallback("faction.isekai_mahou." + id + ".desc", "");
        }

        /** Vantagens do patamar ({@code faction.isekai_mahou.<id>.perk.<patamar>}). */
        public MutableComponent perk(Standing standing) {
            return Component.translatableWithFallback("faction.isekai_mahou." + id + ".perk." + standing.id, "");
        }

        public int argb() { return 0xFF000000 | color; }

        /** Emblema 32×32 da interface ({@code textures/gui/faction/<id>.png}). */
        public ResourceLocation emblem() {
            ResourceLocation rl = ResourceLocation.tryBuild(IsekaiMahou.MODID, "textures/gui/faction/" + id + ".png");
            return rl != null ? rl : IsekaiMahou.id("textures/gui/faction/unknown.png");
        }

        /** Ícone em item (toasts, listas). */
        public ItemStack iconStack() {
            Item item = BuiltInRegistries.ITEM.getOptional(icon).orElse(Items.AIR);
            return new ItemStack(item == Items.AIR ? Items.PAPER : item);
        }

        /** Penalidade (negativa) por matar um membro deste tipo. */
        public int killPenalty(EntityType<?> type) {
            Integer v = killPenalties.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
            return v != null ? v : killPenalty;
        }

        public TierRewards rewards(Standing standing) {
            TierRewards r = rewards.get(standing);
            return r != null ? r : TierRewards.NONE;
        }

        /** Chave de tradução do título do patamar, ou null. */
        @Nullable public String title(Standing standing) {
            return rewards(standing).title();
        }

        public boolean isRival(String other) { return rivals.containsKey(other); }
        public boolean isAlly(String other) { return allies.containsKey(other); }
    }

    // =====================================================================================
    //  Leitura (cliente e servidor)
    // =====================================================================================
    /** Nome do contador do MagicData que guarda o valor ({@code rep.<id>}). */
    public static String counterKey(String faction) {
        return "rep." + faction;
    }

    /** Flag gravada quando o patamar é alcançado pela 1ª vez (recompensa entregue): {@code rep.<id>.<patamar>}. */
    public static String reachedFlag(String faction, Standing standing) {
        return "rep." + faction + "." + standing.id;
    }

    /** Valor atual (−1000..1000). No cliente funciona para o jogador local (outros jogadores: 0). */
    public static int get(Player player, String faction) {
        MagicData d = data(player);
        if (d == null || faction == null) return 0;
        return clamp(d.counter(counterKey(faction)));
    }

    public static Standing standing(Player player, String faction) {
        return Standing.of(get(player, faction));
    }

    /** Já alcançou este patamar alguma vez (e recebeu a recompensa)? */
    public static boolean reached(Player player, String faction, Standing standing) {
        MagicData d = data(player);
        return d != null && d.hasFlag(reachedFlag(faction, standing));
    }

    /** Multiplicador de preço dos membros da facção (Inimigo/Hostil: confira {@link #canTrade} — eles não negociam). */
    public static float priceMultiplier(Player player, String faction) {
        return standing(player, faction).priceMultiplier();
    }

    public static boolean canTrade(Player player, String faction) {
        return standing(player, faction).canTrade();
    }

    /** Patamar Hostil ou Inimigo: guardas e membros atacam. */
    public static boolean isHostile(Player player, String faction) {
        return !standing(player, faction).atLeast(Standing.UNFRIENDLY);
    }

    /** Patamar Amigável ou melhor. */
    public static boolean isFriendly(Player player, String faction) {
        return standing(player, faction).atLeast(Standing.FRIENDLY);
    }

    /** Facções carregadas, na ordem de exibição (lado atual: servidor ou cópia sincronizada no cliente). */
    public static Collection<Faction> factions() {
        return FactionRegistry.current().sorted();
    }

    @Nullable
    public static Faction faction(@Nullable String id) {
        return id == null ? null : FactionRegistry.current().get(id);
    }

    /** Facção da entidade: {@link FactionMember} primeiro, depois as tags {@code faction/<id>}. Funciona no cliente. */
    @Nullable
    public static String factionOf(@Nullable Entity entity) {
        return entity == null ? null : FactionRegistry.current().factionOf(entity);
    }

    /** A facção com o maior valor (desempate: ordem de exibição); null se não há facções. */
    @Nullable
    public static Faction best(Player player) {
        Faction best = null;
        int bestValue = Integer.MIN_VALUE;
        for (Faction f : factions()) {
            int v = get(player, f.id());
            if (v > bestValue) {
                best = f;
                bestValue = v;
            }
        }
        return best;
    }

    // =====================================================================================
    //  Títulos
    // =====================================================================================
    /** Chaves de tradução dos títulos desbloqueados (ordem estável: facções, depois os extras). */
    public static List<String> titles(Player player) {
        MagicData d = data(player);
        List<String> out = new ArrayList<>();
        if (d == null) return out;
        for (Faction f : factions()) {
            for (Standing s : Standing.rewardTiers()) {
                String t = f.title(s);
                if (t != null && d.hasFlag(ReputationService.TITLE_FLAG + t) && !out.contains(t)) out.add(t);
            }
        }
        List<String> extra = new ArrayList<>();
        for (String flag : d.flags()) {
            if (flag.startsWith(ReputationService.TITLE_FLAG)) {
                String t = flag.substring(ReputationService.TITLE_FLAG.length());
                if (!t.isEmpty() && !out.contains(t)) extra.add(t);
            }
        }
        extra.sort(String::compareTo);
        out.addAll(extra);
        return out;
    }

    public static boolean hasTitle(Player player, String titleKey) {
        MagicData d = data(player);
        return d != null && d.hasFlag(ReputationService.TITLE_FLAG + titleKey);
    }

    /** Chave do título em uso, ou null. */
    @Nullable
    public static String selectedTitleKey(Player player) {
        MagicData d = data(player);
        if (d == null) return null;
        for (String flag : d.flags()) {
            if (flag.startsWith(ReputationService.TITLE_SELECTED_FLAG)) {
                String t = flag.substring(ReputationService.TITLE_SELECTED_FLAG.length());
                if (!t.isEmpty() && d.hasFlag(ReputationService.TITLE_FLAG + t)) return t;
            }
        }
        return null;
    }

    /** Título em uso, traduzido (dourado), ou null. */
    @Nullable
    public static MutableComponent selectedTitle(Player player) {
        String key = selectedTitleKey(player);
        return key == null ? null : Component.translatable(key).withColor(Rewards.GOLD);
    }

    // =====================================================================================
    //  Escrita (servidor)
    // =====================================================================================
    /**
     * Soma {@code amount} (pode ser negativo) e devolve o novo valor. Faz respingo nos rivais/aliados, dispara o evento,
     * mostra o aviso no cliente e entrega recompensas de patamar. {@code reason} aparece no aviso (pode ser null).
     */
    public static int add(ServerPlayer player, String faction, int amount, @Nullable Component reason) {
        return ReputationService.add(player, faction, amount, reason);
    }

    /** Define o valor (sem respingo). Recompensas de patamar ainda são entregues ao subir. */
    public static void set(ServerPlayer player, String faction, int value) {
        ReputationService.set(player, faction, value, null);
    }

    /**
     * Como {@link #add}, mas limitado a {@code dailyCap} pontos por dia de jogo para a chave {@code source}
     * (ex.: "cities.help_guard"). Só para ganhos positivos. Devolve quanto foi realmente somado.
     */
    public static int addDaily(ServerPlayer player, String faction, int amount, String source, int dailyCap, @Nullable Component reason) {
        int granted = ReputationService.takeDaily(player, faction + "." + source, amount, dailyCap);
        if (granted > 0) add(player, faction, granted, reason);
        return granted;
    }

    /** Desbloqueia um título (chave de tradução). Mostra mensagem se for novo. */
    public static void grantTitle(ServerPlayer player, String titleKey) {
        ReputationService.grantTitle(player, titleKey, true);
    }

    /** Usa um título desbloqueado (null = nenhum). Devolve false se o título não estiver desbloqueado. */
    public static boolean selectTitle(ServerPlayer player, @Nullable String titleKey) {
        return ReputationService.selectTitle(player, titleKey);
    }

    /** O jogador atacou membros desta facção nos últimos 30 s? (vingança; servidor) */
    public static boolean recentlyAttacked(Player player, String faction) {
        return ReputationEvents.recentlyAttacked(player, faction);
    }

    // =====================================================================================
    //  Facções aliadas (v1.0 — A2)
    // =====================================================================================
    /** Máximo de facções aliadas por jogador. */
    public static final int MAX_ALLIED = 3;

    /** Resultado de {@link #setAllied}. */
    public enum AllyResult {
        OK("ok"), UNKNOWN_FACTION("unknown"), LOW_STANDING("low_standing"), LIMIT("limit"), UNCHANGED("unchanged");

        public final String id;

        AllyResult(String id) {
            this.id = id;
        }

        public boolean ok() { return this == OK; }

        /** Texto pronto ({@code reputation.isekai_mahou.allied.result.<id>}). */
        public MutableComponent message() {
            return Component.translatable("reputation.isekai_mahou.allied.result." + id, MAX_ALLIED);
        }
    }

    /**
     * A facção é aliada do jogador? Membros de facção aliada não recebem dano dele (golpes, flechas, magias, mascotes —
     * {@code SpellContext.isAlly} considera), não o escolhem como alvo e ajudam nas brigas. Cliente: jogador local.
     */
    public static boolean isAllied(Player player, @Nullable String faction) {
        MagicData d = data(player);
        return d != null && faction != null && !faction.isEmpty() && d.hasFlag(ReputationService.ALLIED_FLAG + faction);
    }

    /** Ids das facções aliadas do jogador (ordem de exibição). Cliente: jogador local. */
    public static List<String> allied(Player player) {
        return ReputationService.allied(data(player));
    }

    /**
     * Escolhe (ou desfaz) uma facção aliada. Regras: no máximo {@link #MAX_ALLIED}; só com patamar ≥ Neutro (cair abaixo
     * de Neutro desfaz a aliança sozinho, com aviso). Servidor; a aba Reputação tem o botão.
     */
    public static AllyResult setAllied(ServerPlayer player, String faction, boolean allied) {
        return ReputationService.setAllied(player, faction, allied);
    }

    // =====================================================================================
    //  Interno
    // =====================================================================================
    public static int clamp(int v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    @Nullable
    private static MagicData data(@Nullable Player player) {
        if (player == null) return null;
        if (player.level().isClientSide()) return player.isLocalPlayer() ? ClientMagic.data() : null;
        return MagicApi.get(player);
    }

    private Reputation() {}
}
