package com.renanpontes.isekaimahou.core.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.social.AllegianceService;
import com.renanpontes.isekaimahou.core.social.ClientSocialView;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Guerra de cidades — núcleo de dados (v1.0 — A2). O módulo {@code settlements} (tomar/fundar cidades) usa.
 *
 * <ul>
 *   <li><b>Lealdade</b>: cada jogador pertence a no máximo uma cidade (id de lugar do {@link Places}, ex.:
 *       {@code city_120_-340} ou a cidade fundada pelo jogador).</li>
 *   <li><b>Líder</b>: cada cidade pode ter um líder (Senhor da cidade). O grupo (party) do líder luta pela cidade dele.</li>
 *   <li><b>Inimigas</b>: {@link #declareEnemy} põe duas cidades em guerra. Jogadores de lados inimigos podem se ferir
 *       mesmo com o PvP do servidor desligado (só esse par); magias deixam de tratá-los como aliados. NPCs da cidade
 *       inimiga devem ficar hostis/fugir e não negociar — use {@link #isEnemyOf}/{@link #isEnemyAt} na IA/diálogo.</li>
 * </ul>
 * Dados salvos no mundo. Eventos em {@code core.social.SocialEvent} ({@code AllegianceChanged}, {@code WarDeclared},
 * {@code PeaceMade}). Leitura no cliente só para o jogador local ({@link #of}). Ver docs/v10/API_social.md.
 */
public final class Allegiance {

    // =====================================================================================
    //  Lealdade
    // =====================================================================================
    /** Cidade do jogador (id de lugar) ou null. Cliente: só o jogador local. */
    @Nullable
    public static String of(Player player) {
        if (player.level().isClientSide()) {
            if (!player.isLocalPlayer()) return null;
            String s = ClientSocialView.state().allegiance();
            return s.isEmpty() ? null : s;
        }
        MinecraftServer server = player.getServer();
        return server == null ? null : AllegianceService.of(server, player.getUUID());
    }

    /** Muda a cidade do jogador (null = nenhuma). Avisa o jogador e dispara {@code SocialEvent.AllegianceChanged}. */
    public static void set(ServerPlayer player, @Nullable String cityId) {
        AllegianceService.set(player, cityId);
    }

    /** Jogadores (UUID, online ou não) leais à cidade. */
    public static List<UUID> citizens(MinecraftServer server, String cityId) {
        return AllegianceService.citizens(server, cityId);
    }

    // =====================================================================================
    //  Líder
    // =====================================================================================
    /** Define o líder (Senhor) da cidade; null tira. */
    public static void setLeader(MinecraftServer server, String cityId, @Nullable UUID leader) {
        AllegianceService.setLeader(server, cityId, leader);
    }

    @Nullable
    public static UUID leader(MinecraftServer server, String cityId) {
        return AllegianceService.leader(server, cityId);
    }

    public static boolean isLeader(ServerPlayer player, String cityId) {
        return player.getUUID().equals(leader(player.server, cityId));
    }

    // =====================================================================================
    //  Guerra
    // =====================================================================================
    /**
     * {@code cityId} declara {@code targetId} inimiga (guerra mútua). {@code by} = quem declarou (pode ser null).
     * Devolve false se já estavam em guerra ou os ids são inválidos/iguais. Avisa os envolvidos (chamado de guerra).
     */
    public static boolean declareEnemy(MinecraftServer server, String cityId, String targetId, @Nullable ServerPlayer by) {
        return AllegianceService.declare(server, cityId, targetId, by);
    }

    /** Faz as pazes. Devolve false se não estavam em guerra. */
    public static boolean makePeace(MinecraftServer server, String cityA, String cityB) {
        return AllegianceService.peace(server, cityA, cityB);
    }

    public static boolean areEnemies(MinecraftServer server, String cityA, String cityB) {
        return AllegianceService.areEnemies(server, cityA, cityB);
    }

    /** Cidades em guerra com esta. */
    public static Set<String> enemiesOf(MinecraftServer server, String cityId) {
        return AllegianceService.enemiesOf(server, cityId);
    }

    /** Cidades pelas quais o jogador luta: a dele + as dos líderes do grupo dele. */
    public static Set<String> sides(ServerPlayer player) {
        return AllegianceService.sides(player.server, player.getUUID());
    }

    /**
     * Os dois jogadores estão em lados inimigos (PvP liberado entre eles)? Cliente: o jogador local contra outro jogador
     * online (lista sincronizada).
     */
    public static boolean atWar(Player a, Player b) {
        if (a.level().isClientSide()) {
            if (a.isLocalPlayer()) return ClientSocialView.isEnemy(b.getUUID());
            if (b.isLocalPlayer()) return ClientSocialView.isEnemy(a.getUUID());
            return false;
        }
        MinecraftServer server = a.getServer();
        return server != null && AllegianceService.atWar(server, a.getUUID(), b.getUUID());
    }

    /** O jogador luta contra esta cidade? (NPCs dela: hostis/fogem, não negociam) */
    public static boolean isEnemyOf(ServerPlayer player, String cityId) {
        return AllegianceService.isEnemyOf(player.server, player.getUUID(), cityId);
    }

    /** O lugar ({@code Places}) que contém {@code pos} é uma cidade inimiga do jogador? */
    public static boolean isEnemyAt(ServerPlayer player, BlockPos pos) {
        Places.Place place = Places.at(player.serverLevel(), pos);
        return place != null && isEnemyOf(player, place.id());
    }

    /** Nome da cidade (do {@link Places}, em qualquer dimensão; sem lugar = o id). */
    public static Component cityName(MinecraftServer server, String cityId) {
        return AllegianceService.cityName(server, cityId);
    }

    private Allegiance() {}
}
