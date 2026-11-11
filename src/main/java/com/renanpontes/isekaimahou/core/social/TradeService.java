package com.renanpontes.isekaimahou.core.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Party.Result;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Troca segura entre dois jogadores: cada um põe itens na própria oferta, os dois confirmam, e a troca acontece de uma
 * vez só no servidor. Qualquer mudança nas ofertas desconfirma os dois e trava a confirmação por 2 s (anti-golpe).
 * Fechar a tela, se afastar, morrer ou sair do jogo cancela: cada um recebe de volta o que ofereceu.
 */
public final class TradeService {
    /** Distância máxima durante a troca. */
    public static final double MAX_DISTANCE = 12.0;
    public static final int LOCK_TICKS = 40;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    /** Uma troca em andamento. */
    public static final class Session {
        private enum Stage { OPEN, DONE, CANCELLED }

        final UUID a, b;
        final String nameA, nameB;
        final MinecraftServer server;
        private final SimpleContainer offerA = new SimpleContainer(TradeMenu.OFFER_SLOTS);
        private final SimpleContainer offerB = new SimpleContainer(TradeMenu.OFFER_SLOTS);
        private boolean confA, confB;
        private int lockUntil;
        private Stage stage = Stage.OPEN;

        Session(ServerPlayer a, ServerPlayer b) {
            this.a = a.getUUID();
            this.b = b.getUUID();
            this.nameA = a.getGameProfile().getName();
            this.nameB = b.getGameProfile().getName();
            this.server = a.server;
            offerA.addListener(c -> changed());
            offerB.addListener(c -> changed());
        }

        SimpleContainer offer(boolean sideA) { return sideA ? offerA : offerB; }

        String name(boolean sideA) { return sideA ? nameA : nameB; }

        boolean open() { return stage == Stage.OPEN; }

        /** Dados sincronizados da tela de um lado. */
        ContainerData data(boolean sideA) {
            return new ContainerData() {
                @Override
                public int get(int i) {
                    return switch (i) {
                        case 0 -> (sideA ? confA : confB) ? 1 : 0;
                        case 1 -> (sideA ? confB : confA) ? 1 : 0;
                        case 2 -> Math.max(0, lockUntil - server.getTickCount());
                        case 3 -> partnerHere(sideA) ? 1 : 0;
                        default -> 0;
                    };
                }

                @Override
                public void set(int i, int v) {}

                @Override
                public int getCount() { return 4; }
            };
        }

        private boolean partnerHere(boolean sideA) {
            ServerPlayer p = server.getPlayerList().getPlayer(sideA ? b : a);
            return p != null && p.containerMenu instanceof TradeMenu m && m.session == this;
        }

        private void changed() {
            if (stage != Stage.OPEN) return;
            confA = false;
            confB = false;
            lockUntil = server.getTickCount() + LOCK_TICKS;
        }

        boolean valid(Player player) {
            if (stage != Stage.OPEN) return false;
            ServerPlayer pa = server.getPlayerList().getPlayer(a);
            ServerPlayer pb = server.getPlayerList().getPlayer(b);
            if (pa == null || pb == null || !pa.isAlive() || !pb.isAlive()) return false;
            return pa.level() == pb.level() && pa.distanceTo(pb) <= MAX_DISTANCE;
        }

        void toggleConfirm(boolean sideA) {
            if (stage != Stage.OPEN) return;
            ServerPlayer me = server.getPlayerList().getPlayer(sideA ? a : b);
            if (me == null) return;
            if (server.getTickCount() < lockUntil) {
                me.displayClientMessage(Component.translatable("social.isekai_mahou.trade.wait").withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            if (sideA) confA = !confA;
            else confB = !confB;
            me.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, (sideA ? confA : confB) ? 1.3F : 0.8F);
            ServerPlayer other = server.getPlayerList().getPlayer(sideA ? b : a);
            if (other != null && (sideA ? confA : confB)) {
                other.displayClientMessage(Component.translatable("social.isekai_mahou.trade.partner_confirmed", me.getGameProfile().getName())
                        .withColor(0x7CFF8A), true);
                other.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5F, 1.4F);
            }
            if (confA && confB) complete();
        }

        private void complete() {
            ServerPlayer pa = server.getPlayerList().getPlayer(a);
            ServerPlayer pb = server.getPlayerList().getPlayer(b);
            if (pa == null || pb == null) {
                cancel(null, "?", false);
                return;
            }
            List<ItemStack> toA = stacks(offerB), toB = stacks(offerA);
            // Espaço no inventário de cada um (a própria oferta já saiu do inventário dele)
            boolean fitA = fits(pa, toA), fitB = fits(pb, toB);
            if (!fitA || !fitB) {
                confA = false;
                confB = false;
                for (ServerPlayer p : List.of(pa, pb)) {
                    String full = !fitA ? nameA : nameB;
                    p.displayClientMessage(Component.translatable("social.isekai_mahou.trade.no_space", full).withStyle(ChatFormatting.RED), true);
                    p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1F);
                }
                return;
            }
            stage = Stage.DONE;
            offerA.clearContent();
            offerB.clearContent();
            for (ItemStack s : toA) pa.getInventory().placeItemBackInInventory(s);
            for (ItemStack s : toB) pb.getInventory().placeItemBackInInventory(s);
            forget();
            for (ServerPlayer p : List.of(pa, pb)) {
                if (p.containerMenu instanceof TradeMenu m && m.session == this) p.closeContainer();
                p.sendSystemMessage(Component.translatable("social.isekai_mahou.trade.done", p == pa ? nameB : nameA,
                        p == pa ? toA.size() : toB.size(), p == pa ? toB.size() : toA.size()).withColor(0x7CFF8A));
                p.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.7F, 1.1F);
                p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.2F);
            }
            IsekaiMahou.LOGGER.info("[social] troca concluída: {} ({} itens) <-> {} ({} itens)", nameA, toB.size(), nameB, toA.size());
            NeoForge.EVENT_BUS.post(new SocialEvent.TradeCompleted(pa, pb));
        }

        /**
         * Cancela (uma vez): devolve cada oferta ao dono e fecha a tela do outro. {@code closing} = quem já está fechando a
         * própria tela (não fecha de novo). {@code explicit}: clicou em Cancelar.
         */
        void cancel(@Nullable UUID closing, String byName, boolean explicit) {
            if (stage != Stage.OPEN) return;
            stage = Stage.CANCELLED;
            ServerPlayer pa = server.getPlayerList().getPlayer(a);
            ServerPlayer pb = server.getPlayerList().getPlayer(b);
            giveBack(pa, offerA);
            giveBack(pb, offerB);
            forget();
            for (ServerPlayer p : new ServerPlayer[] {pa, pb}) {
                if (p == null) continue;
                if (!p.getUUID().equals(closing) && p.containerMenu instanceof TradeMenu m && m.session == this) p.closeContainer();
                p.displayClientMessage(Component.translatable(explicit ? "social.isekai_mahou.trade.cancelled_by"
                        : "social.isekai_mahou.trade.cancelled", byName).withStyle(ChatFormatting.GRAY), true);
                p.playNotifySound(SoundEvents.CHEST_CLOSE, SoundSource.PLAYERS, 0.4F, 1.2F);
            }
        }

        private void giveBack(@Nullable ServerPlayer owner, SimpleContainer offer) {
            List<ItemStack> list = stacks(offer);
            offer.clearContent();
            if (list.isEmpty()) return;
            if (owner != null) {
                for (ItemStack s : list) owner.getInventory().placeItemBackInInventory(s);
            } else {
                // Não deveria acontecer (a saída do jogo cancela antes de salvar); registra para não sumir em silêncio
                IsekaiMahou.LOGGER.warn("[social] troca cancelada sem o dono online; {} pilhas perdidas", list.size());
            }
        }

        private void forget() {
            SESSIONS.remove(a, this);
            SESSIONS.remove(b, this);
        }
    }

    // =====================================================================================
    //  Pedido / aceite
    // =====================================================================================
    public static boolean isTrading(UUID player) {
        Session s = SESSIONS.get(player);
        return s != null && s.open();
    }

    public static Result request(ServerPlayer from, ServerPlayer to) {
        Result r = check(from, to);
        if (!r.ok()) return r;
        if (Requests.has(from.server, from.getUUID(), to.getUUID(), Requests.Kind.TRADE)) {
            Requests.take(from.server, from.getUUID(), to.getUUID(), Requests.Kind.TRADE);
            open(to, from);
            return Result.OK;
        }
        Requests.send(from, to, Requests.Kind.TRADE);
        return Result.OK;
    }

    public static Result accept(ServerPlayer player, @Nullable UUID from) {
        Requests.Request req = take(player, from);
        if (req == null) return Result.NO_REQUEST;
        ServerPlayer other = player.server.getPlayerList().getPlayer(req.from());
        if (other == null) return Result.OFFLINE;
        Result r = check(player, other);
        if (!r.ok()) return r;
        open(other, player);
        return Result.OK;
    }

    public static Result decline(ServerPlayer player, @Nullable UUID from) {
        Requests.Request req = take(player, from);
        if (req == null) return Result.NO_REQUEST;
        ServerPlayer other = player.server.getPlayerList().getPlayer(req.from());
        if (other != null) {
            other.displayClientMessage(Component.translatable("social.isekai_mahou.trade.declined", player.getGameProfile().getName())
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return Result.OK;
    }

    @Nullable
    private static Requests.Request take(ServerPlayer player, @Nullable UUID from) {
        if (from != null) return Requests.take(player.server, player.getUUID(), from, Requests.Kind.TRADE);
        Requests.Request latest = Requests.latest(player.server, player.getUUID(), Requests.Kind.TRADE);
        return latest == null ? null : Requests.take(player.server, player.getUUID(), latest.from(), Requests.Kind.TRADE);
    }

    private static Result check(ServerPlayer from, ServerPlayer to) {
        if (from == to) return Result.SELF;
        if (!from.isAlive() || !to.isAlive()) return Result.DEAD;
        if (from.isSpectator() || to.isSpectator()) return Result.NOT_ALLOWED;
        if (from.level() != to.level() || from.distanceTo(to) > Party.INTERACT_RANGE) return Result.TOO_FAR;
        if (isTrading(from.getUUID()) || DuelService.inDuel(from.getUUID())) return Result.BUSY;
        if (isTrading(to.getUUID()) || DuelService.inDuel(to.getUUID())) return Result.TARGET_BUSY;
        return Result.OK;
    }

    private static void open(ServerPlayer a, ServerPlayer b) {
        Session s = new Session(a, b);
        SESSIONS.put(a.getUUID(), s);
        SESSIONS.put(b.getUUID(), s);
        Requests.forget(a.getUUID(), Requests.Kind.TRADE);
        Requests.forget(b.getUUID(), Requests.Kind.TRADE);
        openFor(a, s, true);
        if (!s.open()) return;
        openFor(b, s, false);
    }

    private static void openFor(ServerPlayer p, Session s, boolean sideA) {
        String partner = s.name(!sideA);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new TradeMenu(id, inv, s, sideA),
                Component.translatable("screen.isekai_mahou.trade.title", partner)), buf -> buf.writeUtf(partner, 64));
        p.playNotifySound(SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.4F, 1.3F);
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    /** Saída do jogo / morte: cancela ANTES do save/drop (os itens da oferta voltam ao inventário). */
    static void abort(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s != null) s.cancel(null, player.getGameProfile().getName(), false);
    }

    static void clear() {
        for (Session s : new ArrayList<>(SESSIONS.values())) s.cancel(null, "?", false);
        SESSIONS.clear();
    }

    // =====================================================================================
    //  Espaço no inventário
    // =====================================================================================
    private static List<ItemStack> stacks(SimpleContainer c) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty()) out.add(s.copy());
        }
        return out;
    }

    /** Simula a entrada dos itens no inventário principal (36 slots) sem mexer nele. */
    static boolean fits(ServerPlayer p, List<ItemStack> incoming) {
        List<ItemStack> inv = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) inv.add(p.getInventory().items.get(i).copy());
        for (ItemStack in : incoming) {
            ItemStack s = in.copy();
            for (ItemStack slot : inv) {
                if (s.isEmpty()) break;
                if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, s)) continue;
                int room = slot.getMaxStackSize() - slot.getCount();
                if (room <= 0) continue;
                int move = Math.min(room, s.getCount());
                slot.grow(move);
                s.shrink(move);
            }
            for (int i = 0; i < inv.size() && !s.isEmpty(); i++) {
                if (inv.get(i).isEmpty()) inv.set(i, s.split(s.getMaxStackSize()));
            }
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    private TradeService() {}
}
