package com.renanpontes.isekaimahou.feature.guild.quest;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.guild.GuildFaction;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.feature.guild.GuildRank;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Uma missão da Guilda, lida de {@code data/<ns>/guild_quests/<id>.json}.
 * <pre>
 * { "rank": "E", "category": "kill",
 *   "objectives": [ {"type": "kill", "entity": "isekai_mahou:goblin", "count": 10} ],
 *   "rewards": {"coins": 6, "xp": 50, "points": 12, "glyph": "random", "items": [{"id": "minecraft:bread", "count": 4}]},
 *   "weight": 10, "repeatable": true, "trial": false, "title": "chave.opcional", "description": "chave.opcional",
 *   "requires": ["isekai_mahou:runic_pedestal"] }
 * </pre>
 * Com {@code "trial": true} é uma PROVA de promoção: {@code rank} é o rank para o qual o jogador sobe.
 * Título/descrição: se não informados, missões do namespace do mod usam {@code guild_quest.isekai_mahou.<id>(.desc)};
 * as de outros datapacks ganham um título automático a partir do 1º objetivo.
 * {@code requires} (opcional): ids de item/bloco/entidade/glifo que precisam existir no jogo (missões de módulos opcionais, como
 * as do Pedestal Rúnico, somem do quadro quando o módulo não está presente).
 * <p>
 * v0.8: {@code "faction"} (padrão {@code "guild"}) diz em qual Quadro de Facção a missão aparece; missões de facção que não
 * são da Guilda são liberadas pelo PATAMAR de reputação ({@code "standing"}: neutral, friendly, honored, revered, exalted —
 * padrão neutral), não pelo rank. {@code rewards.reputation} soma reputação; toda missão da Guilda dá + Guilda pelo rank.
 */
public record GuildQuest(ResourceLocation id, GuildRank rank, String category, List<QuestObjective> objectives, QuestRewards rewards,
                         int weight, boolean repeatable, boolean trial, Optional<String> title, Optional<String> description,
                         List<ResourceLocation> requires, GuildFaction faction, Reputation.Standing standing) {

    public static final int MAX_OBJECTIVES = 4;

    private static final Codec<GuildFaction> FACTION_CODEC = Codec.STRING.comapFlatMap(s -> {
        GuildFaction f = GuildFaction.byId(s);
        return f == null ? DataResult.error(() -> "Faccao de quadro invalida: " + s) : DataResult.success(f);
    }, GuildFaction::id);
    /** Patamar mínimo (só de Desconfiado para cima: abaixo disso a facção não negocia). */
    private static final Codec<Reputation.Standing> STANDING_CODEC = Codec.STRING.comapFlatMap(s -> {
        Reputation.Standing st = Reputation.Standing.byId(s.toLowerCase(java.util.Locale.ROOT));
        return st == null || !st.canTrade() ? DataResult.error(() -> "Patamar invalido: " + s) : DataResult.success(st);
    }, st -> st.id);
    /** Prefixo das chaves de título/descrição das missões do mod. */
    private static final String QUEST_KEY = "guild_quest.isekai_mahou.";

    public static final Codec<GuildQuest> FILE_CODEC = RecordCodecBuilder.<GuildQuest>create(i -> i.group(
            GuildRank.CODEC.fieldOf("rank").forGetter(GuildQuest::rank),
            Codec.STRING.optionalFieldOf("category", "").forGetter(GuildQuest::category),
            QuestObjective.CODEC.listOf().fieldOf("objectives").forGetter(GuildQuest::objectives),
            QuestRewards.CODEC.optionalFieldOf("rewards", QuestRewards.EMPTY).forGetter(GuildQuest::rewards),
            Codec.intRange(1, 1000).optionalFieldOf("weight", 10).forGetter(GuildQuest::weight),
            Codec.BOOL.optionalFieldOf("repeatable", true).forGetter(GuildQuest::repeatable),
            Codec.BOOL.optionalFieldOf("trial", false).forGetter(GuildQuest::trial),
            Codec.STRING.optionalFieldOf("title").forGetter(GuildQuest::title),
            Codec.STRING.optionalFieldOf("description").forGetter(GuildQuest::description),
            ResourceLocation.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(GuildQuest::requires),
            FACTION_CODEC.optionalFieldOf("faction", GuildFaction.GUILD).forGetter(GuildQuest::faction),
            STANDING_CODEC.optionalFieldOf("standing", Reputation.Standing.NEUTRAL).forGetter(GuildQuest::standing)
    ).apply(i, (rank, cat, obj, rew, w, rep, tr, t, d, req, fac, st) ->
            new GuildQuest(IsekaiMahou.id("unnamed"), rank, cat, obj, rew, w, rep, tr, t, d, req, fac, st)))
            .validate(q -> q.objectives.isEmpty() || q.objectives.size() > MAX_OBJECTIVES
                    ? DataResult.error(() -> "a missao precisa de 1 a " + MAX_OBJECTIVES + " objetivos")
                    : q.trial && !q.faction.isGuild() ? DataResult.error(() -> "provas de promocao sao so da Guilda")
                    : DataResult.success(q));

    public GuildQuest withId(ResourceLocation newId) {
        String cat = category.isEmpty() ? defaultCategory() : category.toLowerCase(java.util.Locale.ROOT);
        // A categoria vai para o cliente num campo de até 32 caracteres (QuestView).
        if (cat.length() > 32) cat = cat.substring(0, 32);
        return new GuildQuest(newId, rank, cat, objectives, rewards, weight, repeatable, trial, title, description, requires, faction,
                standing);
    }

    private String defaultCategory() {
        if (trial) return "trial";
        return switch (objectives.get(0).kind()) {
            case KILL -> "kill";
            case GATHER -> "gather";
            case EXPLORE -> "explore";
            case TRIAL_CLEAR -> "chamber";
            default -> "task";
        };
    }

    /** Todos os alvos/itens/lugares existem neste jogo (módulos ausentes deixam a missão de fora). */
    public boolean isAvailable(MinecraftServer server) {
        for (ResourceLocation id : requires) {
            if (!BuiltInRegistries.ITEM.containsKey(id) && !BuiltInRegistries.BLOCK.containsKey(id)
                    && !BuiltInRegistries.ENTITY_TYPE.containsKey(id) && GlyphRegistry.get(id) == null) return false;
        }
        for (QuestObjective o : objectives) if (!o.isAvailable(server)) return false;
        return true;
    }

    public Component titleComponent() {
        if (title.isPresent()) return Component.translatable(title.get());
        if (id.getNamespace().equals(IsekaiMahou.MODID)) return Component.translatable(QUEST_KEY + id.getPath());
        return objectives.get(0).describe();
    }

    public Component descriptionComponent() {
        if (description.isPresent()) return Component.translatable(description.get());
        if (id.getNamespace().equals(IsekaiMahou.MODID)) return Component.translatable(QUEST_KEY + id.getPath() + ".desc");
        return Component.empty();
    }

    /** Missão do quadro da Guilda (rank, pontos, prova)? As outras são de facção (patamar de reputação). */
    public boolean isGuild() {
        return faction.isGuild();
    }

    /**
     * Reputação que a entrega dá: + Guilda pelo rank (5–30) nas missões da Guilda e as do {@code rewards.reputation}
     * (somadas se a mesma facção aparecer nos dois).
     */
    public java.util.Map<String, Integer> reputationRewards() {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        if (isGuild() && !trial) out.put(GuildFaction.GUILD.id(), guildReputation(rank));
        rewards.reputation().forEach((k, v) -> out.merge(k, v, Integer::sum));
        out.values().removeIf(v -> v == 0);
        return out;
    }

    /** + Guilda por missão concluída, pelo rank: F 5 … S 30. */
    public static int guildReputation(GuildRank rank) {
        return 5 + Math.round(rank.ordinal() * 25F / (GuildRank.values().length - 1));
    }

    public boolean hasExplore() {
        for (QuestObjective o : objectives) if (o.kind() == QuestObjective.Kind.EXPLORE) return true;
        return false;
    }
}
