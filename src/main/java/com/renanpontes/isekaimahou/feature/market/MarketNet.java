package com.renanpontes.isekaimahou.feature.market;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do Mercado (registrador "market-1"). Os de cliente chamam ganchos que o {@code MarketClient} troca — o código
 * comum não toca em classes de cliente (servidor dedicado).
 */
public final class MarketNet {
    public static volatile Consumer<Listings> onListings = p -> {};
    public static volatile Consumer<BoxSync> onBox = p -> {};
    public static volatile Consumer<Result> onResult = p -> {};
    public static volatile Runnable onOpen = () -> {};

    /** Anúncios por pacote: a lista vai em partes para um item pesado (baú cheio) não estourar o tamanho do pacote. */
    static final int PART = 24;

    // =====================================================================================
    //  C→S
    // =====================================================================================
    /** C→S: "tenho a versão X da lista" — o servidor só responde se a dele for outra (-1 = manda tudo). */
    public record Query(int knownVersion) implements CustomPacketPayload {
        public static final Type<Query> TYPE = new Type<>(IsekaiMahou.id("market_query"));
        public static final StreamCodec<FriendlyByteBuf, Query> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Query::knownVersion, Query::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final int OP_SELL = 0, OP_BUY = 1, OP_CANCEL = 2, OP_COLLECT = 3;
    /** O que retirar em {@link #OP_COLLECT}. */
    public static final int COLLECT_ALL = 0, COLLECT_COINS = 1, COLLECT_ITEMS = 2;

    /**
     * C→S: uma ação. {@code SELL}: a = slot do inventário (0–35), b = quantidade, id = preço (32 bits baixos) + id
     * numérico do item que o jogador viu naquele slot (32 bits altos — se o item mudou de lugar, o anúncio não sai).
     * {@code BUY}: id = anúncio, a = preço que o jogador viu (se mudou, a compra não sai). {@code CANCEL}: id.
     * {@code COLLECT}: a = o quê.
     */
    public record Op(int op, long id, int a, int b) implements CustomPacketPayload {
        public static final Type<Op> TYPE = new Type<>(IsekaiMahou.id("market_op"));
        public static final StreamCodec<FriendlyByteBuf, Op> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Op::op, ByteBufCodecs.VAR_LONG, Op::id, ByteBufCodecs.VAR_INT, Op::a,
                ByteBufCodecs.VAR_INT, Op::b, Op::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static Op sell(int slot, int count, int price, int itemRawId) {
            return new Op(OP_SELL, (price & 0xFFFFFFFFL) | ((long) itemRawId << 32), slot, count);
        }
        public static Op buy(long id, int price) { return new Op(OP_BUY, id, price, 0); }
        public static Op cancel(long id) { return new Op(OP_CANCEL, id, 0, 0); }
        public static Op collect(int what) { return new Op(OP_COLLECT, 0, what, 0); }
    }

    // =====================================================================================
    //  S→C
    // =====================================================================================
    /** Um anúncio na lista do cliente. {@code hoursLeft} = horas reais até vencer. */
    public record Entry(long id, UUID seller, String sellerName, ItemStack stack, int price, int hoursLeft) {}

    /** S→C: um pedaço da lista de anúncios ({@code first} = começa lista nova; {@code last} = a lista está completa). */
    public record Listings(int version, boolean first, boolean last, List<Entry> list) implements CustomPacketPayload {
        public static final Type<Listings> TYPE = new Type<>(IsekaiMahou.id("market_listings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Listings> STREAM_CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.version);
            b.writeBoolean(p.first);
            b.writeBoolean(p.last);
            b.writeVarInt(p.list.size());
            for (Entry e : p.list) {
                b.writeVarLong(e.id());
                b.writeUUID(e.seller());
                b.writeUtf(e.sellerName(), 64);
                ItemStack.STREAM_CODEC.encode(b, e.stack());
                b.writeVarInt(e.price());
                b.writeVarInt(e.hoursLeft());
            }
        }, b -> {
            int version = b.readVarInt();
            boolean first = b.readBoolean(), last = b.readBoolean();
            int n = Math.min(b.readVarInt(), 256);
            List<Entry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(new Entry(b.readVarLong(), b.readUUID(), b.readUtf(64), ItemStack.STREAM_CODEC.decode(b), b.readVarInt(),
                        b.readVarInt()));
            }
            return new Listings(version, first, last, list);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: a caixa de retirada do jogador (moedas de vendas + itens esperando). */
    public record BoxSync(int coins, List<ItemStack> items) implements CustomPacketPayload {
        public static final Type<BoxSync> TYPE = new Type<>(IsekaiMahou.id("market_box"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BoxSync> STREAM_CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.coins);
            b.writeVarInt(p.items.size());
            for (ItemStack s : p.items) ItemStack.STREAM_CODEC.encode(b, s);
        }, b -> {
            int coins = b.readVarInt();
            int n = Math.min(b.readVarInt(), 256);
            List<ItemStack> items = new ArrayList<>(n);
            for (int i = 0; i < n; i++) items.add(ItemStack.STREAM_CODEC.decode(b));
            return new BoxSync(coins, items);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: resultado de uma ação (uma linha na aba; fora dela, na barra de ação). */
    public record Result(boolean ok, Component message) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(IsekaiMahou.id("market_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Result::ok, ComponentSerialization.TRUSTED_STREAM_CODEC, Result::message, Result::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: abrir a aba Mercado (Quadro do Mercado, quadro da Guilda, comando). */
    public record Open() implements CustomPacketPayload {
        public static final Open INSTANCE = new Open();
        public static final Type<Open> TYPE = new Type<>(IsekaiMahou.id("market_open"));
        public static final StreamCodec<FriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.unit(INSTANCE);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // =====================================================================================
    //  Registro
    // =====================================================================================
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("market-1");
        r.playToServer(Query.TYPE, Query.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) MarketService.onQuery(sp, p.knownVersion());
        });
        r.playToServer(Op.TYPE, Op.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) MarketService.onOp(sp, p);
        });
        r.playToClient(Listings.TYPE, Listings.STREAM_CODEC, (p, c) -> onListings.accept(p));
        r.playToClient(BoxSync.TYPE, BoxSync.STREAM_CODEC, (p, c) -> onBox.accept(p));
        r.playToClient(Result.TYPE, Result.STREAM_CODEC, (p, c) -> onResult.accept(p));
        r.playToClient(Open.TYPE, Open.STREAM_CODEC, (p, c) -> onOpen.run());
    }

    private MarketNet() {}
}
