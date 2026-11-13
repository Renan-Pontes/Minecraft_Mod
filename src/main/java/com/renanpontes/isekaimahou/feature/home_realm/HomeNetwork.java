package com.renanpontes.isekaimahou.feature.home_realm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes da Dimensão Casa: a tecla e a aba "Lar" do menu B mandam {@link Action}; o servidor responde com {@link Info}
 * (tem lar? está lá? quanto falta para sair de combate? convidados e lares que dá para visitar).
 */
public final class HomeNetwork {
    /** Cliente → servidor. {@code target} = jogador alvo (convidar/tirar convite/visitar); ignorado nas outras. */
    public record Action(int op, UUID target) implements CustomPacketPayload {
        public static final int INFO = 0, TOGGLE = 1, ENTER = 2, LEAVE = 3, VISIT = 4, INVITE = 5, UNINVITE = 6, GUESTS_BUILD = 7,
                SET_ARRIVAL = 8;
        public static final Type<Action> TYPE = new Type<>(IsekaiMahou.id("home_realm_action"));
        public static final StreamCodec<FriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of((buf, a) -> {
            buf.writeVarInt(a.op);
            buf.writeUUID(a.target);
        }, buf -> new Action(buf.readVarInt(), buf.readUUID()));

        public Action(int op) {
            this(op, Util.NIL_UUID);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Uma pessoa numa lista (convidado, ou dono de um lar que dá para visitar). */
    public record Person(UUID id, String name, boolean online) {}

    /**
     * Servidor → cliente.
     *
     * @param owned       o jogador tem um lar
     * @param inRealm     está na Dimensão Casa agora
     * @param hostName    dono do terreno onde ele está ("" = no próprio lar ou fora da dimensão)
     * @param combatTicks ticks que faltam para completar os 2 min fora de combate (0 = pode entrar)
     * @param guestsBuild convidados podem mexer no lar dele
     * @param invited     quem ele convidou
     * @param visitable   lares para os quais foi convidado
     */
    public record Info(boolean owned, boolean inRealm, String hostName, int combatTicks, boolean guestsBuild, List<Person> invited,
                       List<Person> visitable) implements CustomPacketPayload {
        public static final Info EMPTY = new Info(false, false, "", 0, false, List.of(), List.of());
        public static final Type<Info> TYPE = new Type<>(IsekaiMahou.id("home_realm_info"));
        public static final StreamCodec<FriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.of(Info::write, Info::read);

        private static void write(FriendlyByteBuf buf, Info i) {
            buf.writeBoolean(i.owned);
            buf.writeBoolean(i.inRealm);
            buf.writeUtf(i.hostName, 32);
            buf.writeVarInt(i.combatTicks);
            buf.writeBoolean(i.guestsBuild);
            writePeople(buf, i.invited);
            writePeople(buf, i.visitable);
        }

        private static Info read(FriendlyByteBuf buf) {
            return new Info(buf.readBoolean(), buf.readBoolean(), buf.readUtf(32), buf.readVarInt(), buf.readBoolean(), readPeople(buf),
                    readPeople(buf));
        }

        private static void writePeople(FriendlyByteBuf buf, List<Person> list) {
            int n = Math.min(MAX_PEOPLE, list.size());
            buf.writeVarInt(n);
            for (int k = 0; k < n; k++) {
                Person p = list.get(k);
                buf.writeUUID(p.id);
                buf.writeUtf(p.name.length() > 32 ? p.name.substring(0, 32) : p.name, 32);
                buf.writeBoolean(p.online);
            }
        }

        private static List<Person> readPeople(FriendlyByteBuf buf) {
            int n = Math.min(MAX_PEOPLE, buf.readVarInt());
            List<Person> out = new ArrayList<>(n);
            for (int k = 0; k < n; k++) out.add(new Person(buf.readUUID(), buf.readUtf(32), buf.readBoolean()));
            return out;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Teto de nomes por lista (e de convidados por lar). */
    public static final int MAX_PEOPLE = 32;

    /** Gancho preenchido pela parte de cliente (no servidor dedicado continua vazio). */
    public static Consumer<Info> clientInfo = i -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("home_realm-1");
        r.playToServer(Action.TYPE, Action.STREAM_CODEC, (a, c) -> {
            if (c.player() instanceof ServerPlayer sp) HomeManager.onAction(sp, a);
        });
        r.playToClient(Info.TYPE, Info.STREAM_CODEC, (i, c) -> clientInfo.accept(i));
    }

    private HomeNetwork() {}
}
