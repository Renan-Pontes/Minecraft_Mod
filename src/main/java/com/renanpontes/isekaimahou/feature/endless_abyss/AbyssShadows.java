package com.renanpontes.isekaimahou.feature.endless_abyss;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Lutas das Sombras: a versão sombria dos chefes que o jogador JÁ venceu (contador {@code boss.<id>} ≥ 1, mantido pelo
 * núcleo). A Sombra é a própria entidade do chefe (referida por id em texto), mais forte, com nome "Sombra de …",
 * contorno roxo e corpo escurecido no cliente — o cliente reconhece pelo time do placar {@link #TEAM}, que é
 * sincronizado sozinho pelo vanilla.
 * <p>
 * A Sombra não paga a recompensa normal do chefe (nem conta como abate dele): a recompensa é a do Abismo.
 */
public final class AbyssShadows {
    /** Time do placar das Sombras (o cliente escurece quem está nele; cor do contorno = roxo escuro). */
    public static final String TEAM = "im_abyss_shadow";
    /** persistentData: esta entidade é uma Sombra do Abismo. */
    public static final String SHADOW_KEY = "isekai_mahou.abyss_shadow";

    /**
     * Chefes que não viram Sombra: dependem de arena/estrutura própria ou são grandes demais para a arena do Abismo.
     */
    private static final Set<String> EXCLUDED = Set.of("trial_avatar", "primordial_dragon", "depths_worm_king", "star_devourer");

    /** O chefe (path do id, namespace do mod) pode virar Sombra? */
    public static boolean valid(String path) {
        return type(path) != null;
    }

    @Nullable
    static EntityType<?> type(String path) {
        if (path == null || EXCLUDED.contains(path)) return null;
        ResourceLocation id = ResourceLocation.tryBuild(IsekaiMahou.MODID, path);
        if (id == null) return null;
        Optional<EntityType<?>> t = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        return t.isPresent() && t.get().is(BossKit.BOSSES_TAG) ? t.get() : null;
    }

    /** Chefes que o jogador já venceu e que podem virar Sombra (paths, em ordem alfabética). */
    public static List<String> defeated(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : MagicApi.get(p).counters().entrySet()) {
            if (!e.getKey().startsWith("boss.") || e.getValue() <= 0) continue;
            String path = e.getKey().substring(5);
            if (valid(path)) out.add(path);
        }
        out.sort(String::compareTo);
        return out;
    }

    /** Todos os chefes que podem virar Sombra (para o comando de teste). */
    public static List<String> all() {
        List<String> out = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (IsekaiMahou.MODID.equals(id.getNamespace()) && valid(id.getPath())) out.add(id.getPath());
        }
        out.sort(String::compareTo);
        return out;
    }

    /** Nome mostrado de um chefe (o da entidade). */
    public static Component bossName(String path) {
        EntityType<?> t = type(path);
        return t != null ? t.getDescription() : Component.literal(path);
    }

    /** Cria a Sombra (ainda fora do mundo). Null se o tipo não existir ou não for um Mob. */
    @Nullable
    static Mob create(ServerLevel level, String path) {
        EntityType<?> t = type(path);
        if (t == null) return null;
        Entity e = t.create(level);
        if (e instanceof Mob mob) return mob;
        if (e != null) e.discard();
        return null;
    }

    /**
     * Marca a entidade como Sombra: nome, contorno, time (visual escuro no cliente) e "já recompensado" para o núcleo
     * não pagar XP/contador de chefe. {@code baseName} = nome do chefe ou da criatura original.
     */
    static void mark(Mob mob, Component baseName, boolean champion) {
        mob.getPersistentData().putBoolean(SHADOW_KEY, true);
        mob.getPersistentData().putBoolean(BossRewards.REWARDED_KEY, true);
        mob.setCustomName(Component.translatable(champion ? "entity.isekai_mahou.endless_abyss.champion"
                : "entity.isekai_mahou.endless_abyss.shadow_of", baseName).withStyle(ChatFormatting.DARK_PURPLE));
        mob.setGlowingTag(true);
        if (mob.level() instanceof ServerLevel sl) {
            Scoreboard sb = sl.getServer().getScoreboard();
            PlayerTeam team = sb.getPlayerTeam(TEAM);
            if (team == null) {
                team = sb.addPlayerTeam(TEAM);
                team.setColor(ChatFormatting.DARK_PURPLE);
                team.setAllowFriendlyFire(true);
            }
            sb.addPlayerToTeam(mob.getScoreboardName(), team);
        }
    }

    /** Tira a entidade do time (o placar não fica acumulando nomes de Sombras mortas). */
    static void unmark(MinecraftServer server, String scoreboardName) {
        Scoreboard sb = server.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(TEAM);
        if (team != null && team.getPlayers().contains(scoreboardName)) sb.removePlayerFromTeam(scoreboardName, team);
    }

    public static boolean isShadow(Entity e) {
        return e.getPersistentData().getBoolean(SHADOW_KEY);
    }

    private AbyssShadows() {}
}
