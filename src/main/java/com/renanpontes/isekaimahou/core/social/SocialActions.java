package com.renanpontes.isekaimahou.core.social;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Party.Result;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Pacotes C→S da roda de interação, das respostas a pedidos e da aba Grupo, com validação e mensagem de resultado. */
final class SocialActions {

    static void onWheel(ServerPlayer player, UUID targetId, int action) {
        ServerPlayer target = player.server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            report(player, Result.OFFLINE, "?");
            return;
        }
        String name = target.getGameProfile().getName();
        if (target == player) {
            report(player, Result.SELF, name);
            return;
        }
        // A roda só abre olhando para o jogador: perto e no mesmo mundo (folga para quem se mexeu)
        if (target.level() != player.level() || target.distanceTo(player) > Party.INTERACT_RANGE + 4 || target.isSpectator()) {
            report(player, Result.TOO_FAR, name);
            return;
        }
        Result r = switch (action) {
            case SocialPayloads.ACTION_PARTY -> {
                if (Requests.has(player.server, player.getUUID(), targetId, Requests.Kind.PARTY)) {
                    yield PartyService.accept(player, targetId);
                }
                yield PartyService.invite(player, target);
            }
            case SocialPayloads.ACTION_TRADE -> {
                if (Requests.has(player.server, player.getUUID(), targetId, Requests.Kind.TRADE)) {
                    yield TradeService.accept(player, targetId);
                }
                yield TradeService.request(player, target);
            }
            case SocialPayloads.ACTION_DUEL -> {
                if (Requests.has(player.server, player.getUUID(), targetId, Requests.Kind.DUEL)) {
                    yield DuelService.accept(player, targetId);
                }
                yield DuelService.request(player, target);
            }
            case SocialPayloads.ACTION_PROFILE -> {
                ProfileService.show(player, target);
                yield Result.OK;
            }
            default -> Result.NOT_ALLOWED;
        };
        if (!r.ok()) report(player, r, name);
    }

    static void onReply(ServerPlayer player, UUID from, Requests.Kind kind, boolean accept) {
        Result r = switch (kind) {
            case PARTY -> accept ? PartyService.accept(player, from) : PartyService.decline(player, from);
            case TRADE -> accept ? TradeService.accept(player, from) : TradeService.decline(player, from);
            case DUEL -> accept ? DuelService.accept(player, from) : DuelService.decline(player, from);
        };
        if (!r.ok()) {
            ServerPlayer other = player.server.getPlayerList().getPlayer(from);
            report(player, r, other != null ? other.getGameProfile().getName() : "?");
        }
    }

    static void onPartyOp(ServerPlayer player, int op, UUID target) {
        Result r = switch (op) {
            case SocialPayloads.OP_LEAVE -> PartyService.leave(player);
            case SocialPayloads.OP_KICK -> PartyService.kick(player, target);
            case SocialPayloads.OP_PROMOTE -> PartyService.promote(player, target);
            case SocialPayloads.OP_DISBAND -> PartyService.disband(player);
            case SocialPayloads.OP_CHAT_TOGGLE -> {
                if (PartyService.partyOf(player) == null) yield Result.NOT_IN_PARTY;
                boolean on = PartyService.toggleChat(player);
                player.displayClientMessage(Component.translatable(on ? "social.isekai_mahou.party.chat_on"
                        : "social.isekai_mahou.party.chat_off").withColor(PartyService.PARTY_COLOR), true);
                yield Result.OK;
            }
            case SocialPayloads.OP_PROFILE -> {
                ServerPlayer t = player.server.getPlayerList().getPlayer(target);
                if (t == null) yield Result.OFFLINE;
                ProfileService.show(player, t);
                yield Result.OK;
            }
            default -> Result.NOT_ALLOWED;
        };
        if (!r.ok()) report(player, r, "");
    }

    /** Mensagem de falha na barra de ação + som de "não". */
    static void report(ServerPlayer player, Result r, String name) {
        player.displayClientMessage(r.message(name).withStyle(ChatFormatting.RED), true);
        player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5F, 1.1F);
    }

    private SocialActions() {}
}
