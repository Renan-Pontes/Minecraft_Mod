package com.renanpontes.isekaimahou.core.api;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.onboard.TravelService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Viagem rápida (v0.9) entre lugares JÁ VISITADOS do {@link Places}: pelas Pedras de Teleporte (módulo travel,
 * {@code fromStone = true}, mais barata e sem recarga) ou pela aba "Viagem" do menu B ({@code fromStone = false},
 * custo ×3 e recarga de 10 min). Paga com o recurso da classe (Mana/Vigor/Foco — o mesmo valor de mana).
 *
 * <ul>
 *   <li>Descoberta: flag {@code visit.place.<id>} (gravada pelo núcleo ao entrar no lugar) ou {@code travel.<id>}
 *       ({@link #discover}, ex. ao tocar uma pedra).</li>
 *   <li>Hubs das dimensões do mod (Plano Elemental, Abismo, Santuário Celestial, Fenda do Crepúsculo) entram como
 *       destino depois da 1ª visita à dimensão. O módulo dimensions registra cada hub no {@link Places} com id
 *       {@code hub_<dimensão>} (ex. {@code hub_elemental_plane}) e tipo {@link #HUB_TYPE}, centro = ponto de chegada;
 *       sem o registro, a chegada é o centro (0, superfície, 0).</li>
 *   <li>Entre dimensões só se viaja para hubs (de qualquer dimensão para um hub já visitado).</li>
 *   <li>Chegada: a pedra registrada para o lugar ({@link #registerStone}), ou um lugar do tipo {@link #WAYSTONE_TYPE}
 *       dentro da área do lugar, ou o centro; sempre ajustada a um chão seguro por perto.</li>
 *   <li>Custo: pedra = 10 + 1 por 50 blocos (máx. 120; outra dimensão conta como 2000 blocos); menu = ×3. Nunca mais
 *       que o máximo do recurso do jogador. Criativo: grátis e sem recarga.</li>
 * </ul>
 * Cliente: {@code client.onboard.ClientTravel} (lista recebida, pedidos de viagem). Servidor: chame aqui.
 */
public final class Travel {
    /** Tipo de lugar dos hubs das dimensões. */
    public static final String HUB_TYPE = "dimension_hub";
    /** Tipo de lugar das Pedras de Teleporte soltas (módulo travel). */
    public static final String WAYSTONE_TYPE = "waystone";
    /** Recarga da viagem pelo menu (10 min). */
    public static final int MENU_COOLDOWN_TICKS = 10 * 60 * 20;
    /** Distância máxima entre o jogador e a pedra para valer como viagem "de pedra". */
    public static final double STONE_REACH = 8.0;

    /** Resultado de uma tentativa de viagem. */
    public enum TravelResult {
        OK,
        /** Lugar não existe (removido) ou tipo que não aceita viagem. */
        UNKNOWN_PLACE,
        /** O jogador ainda não visitou o lugar. */
        NOT_DISCOVERED,
        /** Já está no lugar. */
        ALREADY_HERE,
        /** Outra dimensão e o destino não é um hub. */
        OTHER_DIMENSION,
        /** Recurso (Mana/Vigor/Foco) insuficiente. */
        NO_RESOURCE,
        /** Recarga da viagem pelo menu. */
        COOLDOWN,
        /** Ferido por um inimigo nos últimos 5 s. */
        IN_COMBAT,
        /** Não pode viajar agora (sem classe, morto, numa Provação...). */
        BLOCKED,
        /** Nenhum chão seguro perto do destino. */
        NO_SAFE_SPOT;

        public boolean success() {
            return this == OK;
        }

        /** Texto curto para o jogador ({@code travel.isekai_mahou.result.<nome>}). */
        public Component message() {
            return Component.translatable("travel.isekai_mahou.result." + name().toLowerCase(Locale.ROOT));
        }

        public static TravelResult byOrdinal(int i) {
            TravelResult[] v = values();
            return i >= 0 && i < v.length ? v[i] : UNKNOWN_PLACE;
        }
    }

    /** O jogador conhece o lugar (flag {@code visit.place.<id>} ou {@code travel.<id>}; hubs: visitou a dimensão). */
    public static boolean discovered(Player p, String placeId) {
        return TravelService.discovered(p, placeId);
    }

    /** Grava a descoberta ({@code travel.<id>}) e avisa "Novo destino de viagem: <nome>" se for nova. */
    public static void discover(ServerPlayer p, String placeId) {
        TravelService.discover(p, placeId, true);
    }

    /** Lugares conhecidos que aceitam viagem (todas as dimensões, hubs incluídos), mais perto primeiro. */
    public static List<Places.Place> destinations(ServerPlayer p) {
        return TravelService.destinations(p);
    }

    /** Custo no recurso da classe (0 no criativo). */
    public static int cost(ServerPlayer p, Places.Place to, boolean fromStone) {
        return TravelService.cost(p, to, fromStone);
    }

    /** Confere se a viagem é possível agora, sem pagar nem teleportar. */
    public static TravelResult check(ServerPlayer p, Places.Place to, boolean fromStone) {
        return TravelService.check(p, to, fromStone);
    }

    /**
     * Viaja: confere, paga o recurso, efeito de partida, teleporta (para um chão seguro) e efeito de chegada; pelo
     * menu, liga a recarga. O jogador recebe a mensagem do resultado na barra de ação quando falha.
     */
    public static TravelResult travel(ServerPlayer p, String placeId, boolean fromStone) {
        return TravelService.travel(p, placeId, fromStone);
    }

    /** Ticks que faltam para poder viajar pelo menu (0 = pronto). */
    public static int menuCooldown(ServerPlayer p) {
        return TravelService.menuCooldown(p);
    }

    /** Zera a recarga do menu. */
    public static void resetCooldown(ServerPlayer p) {
        TravelService.resetCooldown(p);
    }

    /**
     * Manda a lista de destinos ao cliente (e abre a sessão de viagem). {@code stone} = posição da Pedra de Teleporte
     * usada (viagem de pedra) ou null (menu). O módulo travel abre a tela dele ao receber
     * ({@code ClientTravel.onMenu}).
     */
    public static void openMenu(ServerPlayer p, @Nullable BlockPos stone) {
        TravelService.openMenu(p, stone);
    }

    /** Registra a posição da Pedra de Teleporte de um lugar (vira o ponto de chegada dele). */
    public static void registerStone(ServerLevel level, String placeId, BlockPos pos) {
        TravelService.registerStone(level, placeId, pos);
    }

    /** Esquece qualquer pedra registrada nesta posição (pedra quebrada). */
    public static void unregisterStone(ServerLevel level, BlockPos pos) {
        TravelService.unregisterStone(level, pos);
    }

    /** Pedra registrada para o lugar, ou null. */
    @Nullable
    public static GlobalPos stone(MinecraftServer server, String placeId) {
        return TravelService.stone(server, placeId);
    }

    /** Lugares deste tipo nunca viram destino (ex. áreas temporárias). Já excluídos: hunting_ground, trial, arena. */
    public static void excludeType(String type) {
        TravelService.excludeType(type);
    }

    /** O lugar é um hub de dimensão? */
    public static boolean isHub(Places.Place place) {
        return TravelService.isHub(place);
    }

    private Travel() {}
}
