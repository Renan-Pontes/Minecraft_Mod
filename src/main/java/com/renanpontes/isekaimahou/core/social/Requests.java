package com.renanpontes.isekaimahou.core.social;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Pedidos pendentes entre jogadores (convite de grupo, troca, duelo). Vivem 60 s, só na memória do servidor.
 * Quem recebe vê no chat (botões clicáveis), num aviso na tela e na roda de interação (Agachar + scroll).
 */
public final class Requests {
    public static final int LIFETIME_TICKS = 1200;

    /** Tipo do pedido (o byte vai nos pacotes). */
    public enum Kind {
        PARTY("party"), TRADE("trade"), DUEL("duel");

        public final String id;

        Kind(String id) {
            this.id = id;
        }

        public static Kind byIndex(int i) {
            Kind[] v = values();
            return i >= 0 && i < v.length ? v[i] : PARTY;
        }
    }

    /** {@code expires} = tick do servidor em que o pedido vence. */
    public record Request(UUID from, String fromName, UUID to, Kind kind, int expires) {
        public int ticksLeft(int now) {
            return Math.max(0, expires - now);
        }
    }

    private static final List<Request> PENDING = new ArrayList<>();

    /**
     * Registra (ou renova) o pedido e avisa o alvo. Devolve false se o mesmo pedido já estava pendente e foi só renovado
     * (o aviso de chat não se repete).
     */
    static boolean send(ServerPlayer from, ServerPlayer to, Kind kind) {
        int now = from.server.getTickCount();
        boolean fresh = true;
        for (Iterator<Request> it = PENDING.iterator(); it.hasNext(); ) {
            Request r = it.next();
            if (r.from().equals(from.getUUID()) && r.to().equals(to.getUUID()) && r.kind() == kind) {
                it.remove();
                fresh = false;
            }
        }
        PENDING.add(new Request(from.getUUID(), from.getGameProfile().getName(), to.getUUID(), kind, now + LIFETIME_TICKS));
        if (fresh) notifyTarget(from, to, kind);
        SocialSync.markState(to.getUUID());
        SocialSync.markState(from.getUUID());
        return fresh;
    }

    /** Remove e devolve o pedido de {@code from} para {@code to} (null se não existe/venceu). */
    @Nullable
    static Request take(MinecraftServer server, UUID to, UUID from, Kind kind) {
        int now = server.getTickCount();
        for (Iterator<Request> it = PENDING.iterator(); it.hasNext(); ) {
            Request r = it.next();
            if (r.to().equals(to) && r.from().equals(from) && r.kind() == kind) {
                it.remove();
                SocialSync.markState(to);
                SocialSync.markState(from);
                return r.expires() >= now ? r : null;
            }
        }
        return null;
    }

    /** Há pedido de {@code from} para {@code to}? */
    static boolean has(MinecraftServer server, UUID to, UUID from, Kind kind) {
        int now = server.getTickCount();
        for (Request r : PENDING) {
            if (r.to().equals(to) && r.from().equals(from) && r.kind() == kind && r.expires() >= now) return true;
        }
        return false;
    }

    /** O pedido mais recente deste tipo para {@code to}, ou null. */
    @Nullable
    static Request latest(MinecraftServer server, UUID to, Kind kind) {
        int now = server.getTickCount();
        Request best = null;
        for (Request r : PENDING) {
            if (r.to().equals(to) && r.kind() == kind && r.expires() >= now && (best == null || r.expires() > best.expires())) best = r;
        }
        return best;
    }

    /** Pedidos ainda válidos para este jogador (mais novo primeiro). */
    static List<Request> pendingFor(MinecraftServer server, UUID to) {
        int now = server.getTickCount();
        List<Request> out = new ArrayList<>();
        for (Request r : PENDING) if (r.to().equals(to) && r.expires() >= now) out.add(0, r);
        return out;
    }

    /** Some com todos os pedidos que envolvem o jogador (saiu do servidor). */
    static void forget(UUID player) {
        PENDING.removeIf(r -> {
            if (r.from().equals(player) || r.to().equals(player)) {
                SocialSync.markState(r.from());
                SocialSync.markState(r.to());
                return true;
            }
            return false;
        });
    }

    /** Remove pedidos de um tipo feitos por/para alguém (ex.: entrou num grupo → convites antigos caem). */
    static void forget(UUID player, Kind kind) {
        PENDING.removeIf(r -> r.kind() == kind && (r.from().equals(player) || r.to().equals(player)));
    }

    /** A cada segundo: pedidos vencidos somem e o remetente fica sabendo. */
    static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (Iterator<Request> it = PENDING.iterator(); it.hasNext(); ) {
            Request r = it.next();
            if (r.expires() >= now) continue;
            it.remove();
            ServerPlayer from = server.getPlayerList().getPlayer(r.from());
            ServerPlayer to = server.getPlayerList().getPlayer(r.to());
            if (from != null) {
                String targetName = to != null ? to.getGameProfile().getName() : "?";
                from.displayClientMessage(Component.translatable("social.isekai_mahou.request.expired." + r.kind().id, targetName)
                        .withStyle(ChatFormatting.GRAY), true);
                SocialSync.markState(r.from());
            }
            SocialSync.markState(r.to());
        }
    }

    static void clear() {
        PENDING.clear();
    }

    // =====================================================================================
    //  Aviso ao alvo: chat com botões + som
    // =====================================================================================
    private static void notifyTarget(ServerPlayer from, ServerPlayer to, Kind kind) {
        String name = from.getGameProfile().getName();
        String cmd = switch (kind) {
            case PARTY -> "/party";
            case TRADE -> "/trade";
            case DUEL -> "/duel";
        };
        MutableComponent accept = button("social.isekai_mahou.button.accept", ChatFormatting.GREEN, cmd + " accept " + name,
                "social.isekai_mahou.button.accept_hint");
        MutableComponent decline = button("social.isekai_mahou.button.decline", ChatFormatting.RED, cmd + " decline " + name,
                "social.isekai_mahou.button.decline_hint");
        int color = switch (kind) {
            case PARTY -> 0x7CFF8A;
            case TRADE -> 0xFFD86B;
            case DUEL -> 0xFF7A6B;
        };
        to.sendSystemMessage(Component.translatable("social.isekai_mahou.request." + kind.id,
                        Component.literal(name).withStyle(ChatFormatting.BOLD))
                .withColor(color).append(" ").append(accept).append(" ").append(decline));
        to.playNotifySound(kind == Kind.DUEL ? SoundEvents.ANVIL_PLACE : SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS, kind == Kind.DUEL ? 0.4F : 0.8F, kind == Kind.DUEL ? 1.4F : 1.2F);
        from.displayClientMessage(Component.translatable("social.isekai_mahou.request.sent." + kind.id, to.getGameProfile().getName())
                .withColor(color), true);
    }

    static MutableComponent button(String key, ChatFormatting color, String command, String hintKey) {
        return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(hintKey))));
    }

    private Requests() {}
}
