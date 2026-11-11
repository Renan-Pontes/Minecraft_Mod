package com.renanpontes.isekaimahou.core.api;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.social.DuelService;
import com.renanpontes.isekaimahou.core.social.PartyService;
import com.renanpontes.isekaimahou.core.social.ProfileService;
import com.renanpontes.isekaimahou.core.social.SocialRules;
import com.renanpontes.isekaimahou.core.social.TradeService;
import com.renanpontes.isekaimahou.core.social.ClientSocialView;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Grupos (party) e interações entre jogadores (v1.0 — A2): convite/aceitar/sair/expulsar/promover, até
 * {@link #MAX_MEMBERS} membros, sem dano entre membros (golpes, flechas, magias, mascotes), XP de abate dividido com o
 * grupo, HUD de grupo e chat de grupo ({@code /p}). Também: troca segura de itens, duelo e perfil.
 *
 * <p>No jogo: agachar e girar o scroll olhando para outro jogador abre a roda de interação (Convidar, Trocar, Duelar,
 * Ver perfil). Comandos: {@code /party}, {@code /p}, {@code /trade}, {@code /duel}. Ver docs/v10/API_social.md.
 *
 * <p>Leitura funciona nos dois lados: no cliente só para o jogador local (dados do HUD de grupo). Escrita: servidor.
 */
public final class Party {
    public static final int MAX_MEMBERS = 8;
    /** Distância máxima (blocos) para interagir pela roda / trocar / chamar para duelo. */
    public static final double INTERACT_RANGE = 10.0;

    /** Resultado das ações. {@link #message} = texto pronto (chave {@code social.isekai_mahou.result.<id>}). */
    public enum Result {
        OK("ok"),
        SELF("self"),
        OFFLINE("offline"),
        TOO_FAR("too_far"),
        TARGET_IN_PARTY("target_in_party"),
        ALREADY_MEMBER("already_member"),
        ALREADY_IN_PARTY("already_in_party"),
        NOT_LEADER("not_leader"),
        PARTY_FULL("party_full"),
        NO_REQUEST("no_request"),
        NOT_IN_PARTY("not_in_party"),
        NOT_MEMBER("not_member"),
        BUSY("busy"),
        TARGET_BUSY("target_busy"),
        DEAD("dead"),
        NOT_ALLOWED("not_allowed");

        public final String id;

        Result(String id) {
            this.id = id;
        }

        public boolean ok() {
            return this == OK;
        }

        public MutableComponent message(Object... args) {
            return Component.translatable("social.isekai_mahou.result." + id, args);
        }
    }

    // =====================================================================================
    //  Leitura
    // =====================================================================================
    /** Está num grupo? (cliente: jogador local) */
    public static boolean inParty(Player player) {
        if (player.level().isClientSide()) return player.isLocalPlayer() && ClientSocialView.inParty();
        return PartyService.partyOf(player) != null;
    }

    /** Os dois estão no mesmo grupo? (cliente: o jogador local e alguém do grupo dele) */
    public static boolean sameParty(Player a, Player b) {
        if (a == b) return true;
        if (a.level().isClientSide()) {
            if (a.isLocalPlayer()) return ClientSocialView.isMember(b.getUUID());
            if (b.isLocalPlayer()) return ClientSocialView.isMember(a.getUUID());
            return ClientSocialView.isMember(a.getUUID()) && ClientSocialView.isMember(b.getUUID());
        }
        return PartyService.sameParty(a.getServer(), a.getUUID(), b.getUUID());
    }

    /** Membros (UUIDs, inclusive o próprio e os offline), na ordem de entrada. Vazio sem grupo. */
    public static List<UUID> members(Player player) {
        if (player.level().isClientSide()) return player.isLocalPlayer() ? ClientSocialView.members() : List.of();
        return PartyService.members(player);
    }

    /** Membros online (servidor), inclusive o próprio. Sem grupo: só o próprio. */
    public static List<ServerPlayer> onlineMembers(ServerPlayer player) {
        return PartyService.onlineMembers(player);
    }

    /** Líder do grupo, ou null sem grupo. */
    @Nullable
    public static UUID leader(Player player) {
        if (player.level().isClientSide()) return player.isLocalPlayer() ? ClientSocialView.leader() : null;
        return PartyService.leader(player);
    }

    public static boolean isLeader(Player player) {
        UUID l = leader(player);
        return l != null && l.equals(player.getUUID());
    }

    /**
     * "Esta entidade é amiga desta outra" pelas regras sociais (grupo, duelo, guerra, facções aliadas), servidor.
     * TRUE = aliada (magias de cura afetam, dano não), FALSE = inimiga mesmo com PvP desligado (duelo/guerra),
     * null = sem opinião (use as regras normais). É o que o {@code SpellContext.isAlly} consulta.
     */
    @Nullable
    public static Boolean allyOverride(@Nullable Entity source, @Nullable Entity target) {
        return SocialRules.allyOverride(source, target);
    }

    // =====================================================================================
    //  Escrita (servidor)
    // =====================================================================================
    /** Convida {@code to} (precisa ser líder, ou estar sem grupo — o grupo nasce quando o convite é aceito). */
    public static Result invite(ServerPlayer from, ServerPlayer to) {
        return PartyService.invite(from, to);
    }

    /** Aceita o convite de {@code from} (null = o mais recente). */
    public static Result accept(ServerPlayer player, @Nullable UUID from) {
        return PartyService.accept(player, from);
    }

    public static Result decline(ServerPlayer player, @Nullable UUID from) {
        return PartyService.decline(player, from);
    }

    /** Sai do grupo (o líder passa a liderança ao próximo; grupo de 1 se desfaz). */
    public static Result leave(ServerPlayer player) {
        return PartyService.leave(player);
    }

    public static Result kick(ServerPlayer leader, UUID target) {
        return PartyService.kick(leader, target);
    }

    public static Result promote(ServerPlayer leader, UUID target) {
        return PartyService.promote(leader, target);
    }

    public static Result disband(ServerPlayer leader) {
        return PartyService.disband(leader);
    }

    /** Mensagem no chat de grupo (como {@code /p}). */
    public static Result chat(ServerPlayer from, String message) {
        return PartyService.chat(from, message);
    }

    // ---------------------------------------------------------------- troca, duelo, perfil
    /** Pede uma troca (ou aceita, se {@code to} já tinha pedido). Os dois a até {@link #INTERACT_RANGE} blocos. */
    public static Result trade(ServerPlayer from, ServerPlayer to) {
        return TradeService.request(from, to);
    }

    public static boolean isTrading(Player player) {
        return TradeService.isTrading(player.getUUID());
    }

    /** Desafia para duelo (ou aceita, se {@code to} já tinha desafiado). */
    public static Result duel(ServerPlayer from, ServerPlayer to) {
        return DuelService.request(from, to);
    }

    /** Os dois estão duelando entre si (servidor; cliente: o jogador local contra {@code b})? */
    public static boolean dueling(Player a, Player b) {
        if (a.level().isClientSide()) return a.isLocalPlayer() && b.getUUID().equals(ClientSocialView.duelOpponent());
        return DuelService.dueling(a.getUUID(), b.getUUID());
    }

    /** Abre o perfil de {@code target} na tela de {@code viewer}. */
    public static void showProfile(ServerPlayer viewer, ServerPlayer target) {
        ProfileService.show(viewer, target);
    }

    /**
     * Linha extra no perfil (ex.: o módulo de raças mostra "Raça: Elfo"). Chamada no servidor ao montar o perfil; devolva
     * null para não mostrar nada. Registre no init do módulo.
     */
    public static void addProfileLine(Function<ServerPlayer, Component> line) {
        ProfileService.addLine(line);
    }

    private Party() {}
}
