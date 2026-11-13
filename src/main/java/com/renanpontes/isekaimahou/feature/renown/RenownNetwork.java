package com.renanpontes.isekaimahou.feature.renown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do Renome: a aba pede o quadro ({@link Request#BOARD}) ou a lista de títulos ({@link Request#TITLES}); o
 * servidor responde com {@link Board} / {@link Titles}. {@link TitleSync} leva os títulos em uso de todo mundo (para o
 * nome sobre a cabeça) e {@link Timer} liga/desliga o cronômetro da luta de chefe.
 */
public final class RenownNetwork {
    private static final int MAX_NAME = 64, MAX_KEY = 200;

    /** Cliente → servidor. */
    public record Request(int op) implements CustomPacketPayload {
        public static final int BOARD = 0, TITLES = 1;
        public static final Type<Request> TYPE = new Type<>(IsekaiMahou.id("renown_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.of((buf, r) -> buf.writeVarInt(r.op), buf -> new Request(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Uma linha do ranking. Jogadores: {@code name} = jogador, {@code title} = título em uso, {@code detail} = id da
     * classe, {@code aux} = número extra da categoria. Cidades: {@code name} = cidade, {@code detail} = dono.
     */
    public record Row(String name, String title, long value, String detail, int aux, boolean self) {}

    /** Uma categoria: as melhores linhas + a posição de quem pediu (0 = fora do ranking). */
    public record Category(String id, List<Row> rows, int selfRank, long selfValue) {}

    /** Recorde de tempo de um chefe: melhor do servidor ({@code ticks}, {@code holders}) e o do jogador ({@code mine}, 0 = nenhum). */
    public record BossRow(String boss, int ticks, String holders, long day, int mine) {}

    /** Servidor → cliente: o quadro inteiro. {@code open} = abrir a aba (clique no bloco). */
    public record Board(List<Category> categories, List<BossRow> bosses, boolean open) implements CustomPacketPayload {
        public static final Board EMPTY = new Board(List.of(), List.of(), false);
        public static final Type<Board> TYPE = new Type<>(IsekaiMahou.id("renown_board"));
        public static final StreamCodec<FriendlyByteBuf, Board> STREAM_CODEC = StreamCodec.of(Board::write, Board::read);

        private static void write(FriendlyByteBuf buf, Board b) {
            buf.writeVarInt(b.categories.size());
            for (Category c : b.categories) {
                buf.writeUtf(c.id, 32);
                buf.writeVarInt(c.rows.size());
                for (Row r : c.rows) {
                    buf.writeUtf(r.name, MAX_NAME);
                    buf.writeUtf(r.title, MAX_KEY);
                    buf.writeVarLong(r.value);
                    buf.writeUtf(r.detail, MAX_NAME);
                    buf.writeVarInt(r.aux);
                    buf.writeBoolean(r.self);
                }
                buf.writeVarInt(c.selfRank);
                buf.writeVarLong(c.selfValue);
            }
            buf.writeVarInt(b.bosses.size());
            for (BossRow r : b.bosses) {
                buf.writeUtf(r.boss, MAX_KEY);
                buf.writeVarInt(r.ticks);
                buf.writeUtf(r.holders, 128);
                buf.writeVarLong(r.day);
                buf.writeVarInt(r.mine);
            }
            buf.writeBoolean(b.open);
        }

        private static Board read(FriendlyByteBuf buf) {
            int n = Math.min(16, buf.readVarInt());
            List<Category> cats = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String id = buf.readUtf(32);
                int m = Math.min(32, buf.readVarInt());
                List<Row> rows = new ArrayList<>(m);
                for (int k = 0; k < m; k++) {
                    rows.add(new Row(buf.readUtf(MAX_NAME), buf.readUtf(MAX_KEY), buf.readVarLong(), buf.readUtf(MAX_NAME),
                            buf.readVarInt(), buf.readBoolean()));
                }
                cats.add(new Category(id, rows, buf.readVarInt(), buf.readVarLong()));
            }
            int b = Math.min(256, buf.readVarInt());
            List<BossRow> bosses = new ArrayList<>(b);
            for (int i = 0; i < b; i++) {
                bosses.add(new BossRow(buf.readUtf(MAX_KEY), buf.readVarInt(), buf.readUtf(128), buf.readVarLong(), buf.readVarInt()));
            }
            return new Board(cats, bosses, buf.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Um título na lista: {@code feat} = título de feito do Renome (tem requisito e progresso); senão veio de outro sistema. */
    public record TitleRow(String key, int tier, boolean unlocked, int progress, int goal, boolean feat) {}

    /** Servidor → cliente: todos os títulos e o que está em uso ("" = nenhum). */
    public record Titles(List<TitleRow> rows, String selected) implements CustomPacketPayload {
        public static final Titles EMPTY = new Titles(List.of(), "");
        public static final Type<Titles> TYPE = new Type<>(IsekaiMahou.id("renown_titles"));
        public static final StreamCodec<FriendlyByteBuf, Titles> STREAM_CODEC = StreamCodec.of((buf, t) -> {
            buf.writeVarInt(t.rows.size());
            for (TitleRow r : t.rows) {
                buf.writeUtf(r.key, MAX_KEY);
                buf.writeVarInt(r.tier);
                buf.writeBoolean(r.unlocked);
                buf.writeVarInt(r.progress);
                buf.writeVarInt(r.goal);
                buf.writeBoolean(r.feat);
            }
            buf.writeUtf(t.selected, MAX_KEY);
        }, buf -> {
            int n = Math.min(256, buf.readVarInt());
            List<TitleRow> rows = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                rows.add(new TitleRow(buf.readUtf(MAX_KEY), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                        buf.readBoolean()));
            }
            return new Titles(rows, buf.readUtf(MAX_KEY));
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Servidor → cliente: títulos em uso por jogador ("" = tirou). {@code full} = troca a lista inteira. */
    public record TitleSync(boolean full, Map<UUID, String> titles) implements CustomPacketPayload {
        public static final Type<TitleSync> TYPE = new Type<>(IsekaiMahou.id("renown_title_sync"));
        public static final StreamCodec<FriendlyByteBuf, TitleSync> STREAM_CODEC = StreamCodec.of((buf, s) -> {
            buf.writeBoolean(s.full);
            buf.writeVarInt(s.titles.size());
            s.titles.forEach((id, key) -> {
                buf.writeUUID(id);
                buf.writeUtf(key, MAX_KEY);
            });
        }, buf -> {
            boolean full = buf.readBoolean();
            int n = Math.min(1024, buf.readVarInt());
            Map<UUID, String> map = new HashMap<>();
            for (int i = 0; i < n; i++) map.put(buf.readUUID(), buf.readUtf(MAX_KEY));
            return new TitleSync(full, map);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Servidor → cliente: cronômetro da luta. {@link #RUNNING}: {@code ticks} já passados; {@link #FINISHED}: tempo
     * final; {@link #CANCELLED}: a luta acabou sem vitória. {@code personalBest}/{@code worldBest} em ticks (0 = não há).
     */
    public record Timer(int state, String boss, int ticks, int personalBest, int worldBest) implements CustomPacketPayload {
        public static final int RUNNING = 0, FINISHED = 1, CANCELLED = 2;
        public static final Type<Timer> TYPE = new Type<>(IsekaiMahou.id("renown_timer"));
        public static final StreamCodec<FriendlyByteBuf, Timer> STREAM_CODEC = StreamCodec.of((buf, t) -> {
            buf.writeVarInt(t.state);
            buf.writeUtf(t.boss, MAX_KEY);
            buf.writeVarInt(t.ticks);
            buf.writeVarInt(t.personalBest);
            buf.writeVarInt(t.worldBest);
        }, buf -> new Timer(buf.readVarInt(), buf.readUtf(MAX_KEY), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Ganchos preenchidos pela parte de cliente (no servidor dedicado continuam vazios). */
    public static Consumer<Board> clientBoard = b -> {};
    public static Consumer<Titles> clientTitles = t -> {};
    public static Consumer<TitleSync> clientTitleSync = s -> {};
    public static Consumer<Timer> clientTimer = t -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("renown-1");
        r.playToServer(Request.TYPE, Request.STREAM_CODEC, (req, c) -> {
            if (!(c.player() instanceof ServerPlayer sp)) return;
            if (req.op() == Request.TITLES) RenownTitles.sendList(sp);
            else RenownRanking.sendBoard(sp, false);
        });
        r.playToClient(Board.TYPE, Board.STREAM_CODEC, (b, c) -> clientBoard.accept(b));
        r.playToClient(Titles.TYPE, Titles.STREAM_CODEC, (t, c) -> clientTitles.accept(t));
        r.playToClient(TitleSync.TYPE, TitleSync.STREAM_CODEC, (s, c) -> clientTitleSync.accept(s));
        r.playToClient(Timer.TYPE, Timer.STREAM_CODEC, (t, c) -> clientTimer.accept(t));
    }

    static void send(ServerPlayer p, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(p, payload);
    }

    private RenownNetwork() {}
}
