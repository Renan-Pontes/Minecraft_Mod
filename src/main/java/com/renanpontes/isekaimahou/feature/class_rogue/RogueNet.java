package com.renanpontes.isekaimahou.feature.class_rogue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede do Ladino. S→C: {@link Sync} (páginas do Grimório Roubado, para a dica do item e a aba "Ladino"). C→S:
 * {@link Action} (subir de rank pela aba, trocar a página escolhida). O servidor confere tudo de novo.
 */
public final class RogueNet {
    /** Uma página como o cliente vê. */
    public record PageView(String name, int color, String element, int charges, String from, boolean ultimate) {}

    public record Sync(List<PageView> pages, int selected) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(IsekaiMahou.id("class_rogue_sync"));
        public static final StreamCodec<FriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeVarInt(s.pages.size());
                    for (PageView p : s.pages) {
                        buf.writeUtf(p.name, 64);
                        buf.writeInt(p.color);
                        buf.writeUtf(p.element, 32);
                        buf.writeVarInt(p.charges);
                        buf.writeUtf(p.from, 64);
                        buf.writeBoolean(p.ultimate);
                    }
                    buf.writeVarInt(s.selected);
                },
                buf -> {
                    int n = Math.min(8, buf.readVarInt());
                    List<PageView> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        list.add(new PageView(buf.readUtf(64), buf.readInt(), buf.readUtf(32), buf.readVarInt(), buf.readUtf(64),
                                buf.readBoolean()));
                    }
                    return new Sync(list, buf.readVarInt());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** {@code RANK_UP} (arg ignorado), {@code SELECT} (arg = índice da página). */
    public record Action(byte kind, int arg) implements CustomPacketPayload {
        public static final byte RANK_UP = 0;
        public static final byte SELECT = 1;
        public static final Type<Action> TYPE = new Type<>(IsekaiMahou.id("class_rogue_action"));
        public static final StreamCodec<FriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of(
                (buf, a) -> {
                    buf.writeByte(a.kind);
                    buf.writeVarInt(a.arg);
                },
                buf -> new Action(buf.readByte(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preenchido pelo ClassRogueClient (nunca chamado no servidor dedicado). */
    public static volatile Consumer<Sync> clientSync = s -> {};
    /** Última cópia recebida (só o cliente escreve; a dica do Grimório Roubado e a aba leem). */
    public static volatile Sync clientView = new Sync(List.of(), 0);

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_rogue-1");
        r.playToClient(Sync.TYPE, Sync.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientSync.accept(p)));
        r.playToServer(Action.TYPE, Action.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (!(c.player() instanceof ServerPlayer sp)) return;
            if (p.kind == Action.RANK_UP) {
                RogueRanks.tryRankUp(sp, true);
            } else if (p.kind == Action.SELECT) {
                int n = RogueData.pages(sp).size();
                if (n > 0) {
                    RogueData.select(sp, Math.floorMod(p.arg, n));
                    sync(sp);
                }
            }
        }));
    }

    /** Manda as páginas do Grimório Roubado ao jogador (login, roubo, conjuração, troca de página). */
    public static void sync(ServerPlayer sp) {
        List<PageView> views = new ArrayList<>();
        for (RogueData.Page pg : RogueData.pages(sp)) {
            int color = pg.recipe().color() >= 0 ? pg.recipe().color() : pg.element().color;
            views.add(new PageView(trim(pg.displayName()), color, pg.element().id, pg.charges(), trim(pg.from()), pg.ultimate()));
        }
        PacketDistributor.sendToPlayer(sp, new Sync(views, RogueData.selected(sp)));
    }

    /** Ladino? (para enviar só a quem precisa). */
    static boolean isRogue(ServerPlayer sp) {
        return MagicApi.get(sp).path() == MagicPath.ROGUE;
    }

    private static String trim(String s) {
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    private RogueNet() {}
}
