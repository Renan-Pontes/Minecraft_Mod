package com.renanpontes.isekaimahou.feature.endless_abyss;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do Abismo Infinito: a aba do menu B pede o estado ({@link Action#INFO}) e manda as ações (entrar, enfrentar
 * uma Sombra, sair); o servidor responde com {@link Info} (recorde, ponto de partida, Sombras disponíveis, ranking).
 */
public final class AbyssNetwork {
    /** Cliente → servidor. {@code floor} só vale em {@link #ENTER}; {@code boss} só em {@link #SHADOW}. */
    public record Action(int op, int floor, String boss) implements CustomPacketPayload {
        public static final int INFO = 0, ENTER = 1, SHADOW = 2, LEAVE = 3;
        public static final Type<Action> TYPE = new Type<>(IsekaiMahou.id("endless_abyss_action"));
        public static final StreamCodec<FriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of((buf, a) -> {
            buf.writeVarInt(a.op);
            buf.writeVarInt(a.floor);
            buf.writeUtf(a.boss, 64);
        }, buf -> new Action(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Uma Sombra disponível: path do chefe e quantas vezes o jogador já venceu a Sombra dele. */
    public record ShadowRow(String boss, int wins) {}

    /** Uma linha do ranking. */
    public record RankRow(String name, int floor) {}

    /**
     * Servidor → cliente. {@code best} = maior andar vencido; {@code start} = andar de onde dá para continuar;
     * {@code runFloor} = andar da descida em curso (0 = não está em nenhuma; −1 = numa Luta das Sombras avulsa).
     */
    public record Info(int best, int start, int shadowWins, int runFloor, List<ShadowRow> shadows, List<RankRow> top)
            implements CustomPacketPayload {
        public static final Info EMPTY = new Info(0, 1, 0, 0, List.of(), List.of());
        public static final Type<Info> TYPE = new Type<>(IsekaiMahou.id("endless_abyss_info"));
        public static final StreamCodec<FriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.of(Info::write, Info::read);

        private static void write(FriendlyByteBuf buf, Info i) {
            buf.writeVarInt(i.best);
            buf.writeVarInt(i.start);
            buf.writeVarInt(i.shadowWins);
            buf.writeInt(i.runFloor);
            buf.writeVarInt(i.shadows.size());
            for (ShadowRow r : i.shadows) {
                buf.writeUtf(r.boss, 64);
                buf.writeVarInt(r.wins);
            }
            buf.writeVarInt(i.top.size());
            for (RankRow r : i.top) {
                buf.writeUtf(r.name, 32);
                buf.writeVarInt(r.floor);
            }
        }

        private static Info read(FriendlyByteBuf buf) {
            int best = buf.readVarInt(), start = buf.readVarInt(), wins = buf.readVarInt(), run = buf.readInt();
            int n = Math.min(128, buf.readVarInt());
            List<ShadowRow> shadows = new ArrayList<>(n);
            for (int k = 0; k < n; k++) shadows.add(new ShadowRow(buf.readUtf(64), buf.readVarInt()));
            int m = Math.min(16, buf.readVarInt());
            List<RankRow> top = new ArrayList<>(m);
            for (int k = 0; k < m; k++) top.add(new RankRow(buf.readUtf(32), buf.readVarInt()));
            return new Info(best, start, wins, run, shadows, top);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Gancho preenchido pela parte de cliente (no servidor dedicado continua vazio). */
    public static Consumer<Info> clientInfo = i -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("endless_abyss-1");
        r.playToServer(Action.TYPE, Action.STREAM_CODEC, (a, c) -> {
            if (c.player() instanceof ServerPlayer sp) AbyssManager.onAction(sp, a);
        });
        r.playToClient(Info.TYPE, Info.STREAM_CODEC, (i, c) -> clientInfo.accept(i));
    }

    /** Manda o estado atual para a aba do jogador. */
    static void sendInfo(ServerPlayer p) {
        MinecraftServer server = p.server;
        int best = AbyssRecords.bestFloor(server, p.getUUID());
        List<ShadowRow> shadows = new ArrayList<>();
        for (String boss : AbyssShadows.defeated(p)) shadows.add(new ShadowRow(boss, AbyssRecords.shadowWins(server, p.getUUID(), boss)));
        List<RankRow> top = new ArrayList<>();
        for (AbyssRecords.Entry e : AbyssRecords.top(server, 5)) top.add(new RankRow(e.name(), e.bestFloor()));
        AbyssRun run = AbyssManager.of(p.getUUID());
        PacketDistributor.sendToPlayer(p, new Info(best, AbyssFloors.checkpointStart(best), AbyssRecords.shadowWins(server, p.getUUID()),
                run == null ? 0 : run.shownFloor(), shadows, top));
    }

    private AbyssNetwork() {}
}
