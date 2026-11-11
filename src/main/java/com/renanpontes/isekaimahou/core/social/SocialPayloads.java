package com.renanpontes.isekaimahou.core.social;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes sociais (registrador "1"). Os de cliente chamam ganchos trocados pelo {@code SocialCoreClient} — o código
 * comum não toca em classes de cliente (servidor dedicado).
 */
public final class SocialPayloads {
    /** Perfil recebido → abrir a tela. */
    public static volatile Consumer<Profile> onProfile = p -> {};
    /** Estado social mudou (para avisos/sons no cliente). */
    public static volatile Consumer<State> onState = s -> {};

    // =====================================================================================
    //  C→S
    // =====================================================================================
    /** Ações da roda de interação. */
    public static final int ACTION_PARTY = 0, ACTION_TRADE = 1, ACTION_DUEL = 2, ACTION_PROFILE = 3;

    /** C→S: ação da roda sobre outro jogador. */
    public record Action(UUID target, int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(IsekaiMahou.id("social_action"));
        public static final StreamCodec<FriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Action::target, ByteBufCodecs.VAR_INT, Action::action, Action::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C→S: responder a um pedido ({@code kind} = {@link Requests.Kind#ordinal()}). */
    public record Reply(UUID from, int kind, boolean accept) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(IsekaiMahou.id("social_reply"));
        public static final StreamCodec<FriendlyByteBuf, Reply> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Reply::from, ByteBufCodecs.VAR_INT, Reply::kind, ByteBufCodecs.BOOL, Reply::accept, Reply::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final int OP_LEAVE = 0, OP_KICK = 1, OP_PROMOTE = 2, OP_DISBAND = 3, OP_CHAT_TOGGLE = 4, OP_PROFILE = 5;

    /** C→S: operações de grupo pela aba Grupo (sair, expulsar, promover, desfazer, chat de grupo, ver perfil). */
    public record PartyOp(int op, UUID target) implements CustomPacketPayload {
        public static final Type<PartyOp> TYPE = new Type<>(IsekaiMahou.id("social_party_op"));
        public static final StreamCodec<FriendlyByteBuf, PartyOp> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PartyOp::op, UUIDUtil.STREAM_CODEC, PartyOp::target, PartyOp::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // =====================================================================================
    //  S→C
    // =====================================================================================
    /** Um membro no HUD de grupo. {@code dim} = id da dimensão (cinza se diferente da do jogador local). */
    public record Member(UUID id, String name, boolean online, float hp, float maxHp, float res, float maxRes, int resArgb,
                         String path, int level, String dim) {
        static void write(FriendlyByteBuf b, Member m) {
            b.writeUUID(m.id);
            b.writeUtf(m.name, 64);
            b.writeBoolean(m.online);
            b.writeFloat(m.hp);
            b.writeFloat(m.maxHp);
            b.writeFloat(m.res);
            b.writeFloat(m.maxRes);
            b.writeInt(m.resArgb);
            b.writeUtf(m.path, 64);
            b.writeVarInt(m.level);
            b.writeUtf(m.dim, 256);
        }

        static Member read(FriendlyByteBuf b) {
            return new Member(b.readUUID(), b.readUtf(64), b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readInt(), b.readUtf(64), b.readVarInt(), b.readUtf(256));
        }
    }

    /** S→C: o grupo do jogador (lista vazia = sem grupo). */
    public record PartySync(UUID leader, List<Member> members) implements CustomPacketPayload {
        public static final Type<PartySync> TYPE = new Type<>(IsekaiMahou.id("social_party"));
        public static final StreamCodec<FriendlyByteBuf, PartySync> STREAM_CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.leader);
            b.writeVarInt(p.members.size());
            for (Member m : p.members) Member.write(b, m);
        }, b -> {
            UUID leader = b.readUUID();
            int n = Math.min(b.readVarInt(), 64);
            List<Member> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(Member.read(b));
            return new PartySync(leader, list);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static PartySync empty() {
            return new PartySync(new UUID(0, 0), List.of());
        }
    }

    /** Pedido pendente para o jogador local. */
    public record Req(UUID from, String name, int kind, int ticksLeft) {}

    /** Arena de chefe em que o jogador está (HUD). */
    public record ArenaInfo(double x, double y, double z, float radius, boolean releasing, int ticksLeft, Component boss) {}

    /**
     * S→C: estado social do jogador local — cidade (lealdade), guerras, jogadores inimigos online, duelo, pedidos
     * pendentes, arena de chefe e chat de grupo.
     */
    public record State(String allegiance, Optional<Component> allegianceName, List<Component> wars, List<UUID> enemies,
                        Optional<UUID> duelWith, String duelName, int duelPhase, int duelTicks, List<Req> requests,
                        Optional<ArenaInfo> arena, boolean partyChat) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(IsekaiMahou.id("social_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(State::write, State::read);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static State empty() {
            return new State("", Optional.empty(), List.of(), List.of(), Optional.empty(), "", 0, 0, List.of(), Optional.empty(), false);
        }

        private static void write(RegistryFriendlyByteBuf b, State s) {
            b.writeUtf(s.allegiance, 256);
            ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(b, s.allegianceName);
            b.writeVarInt(s.wars.size());
            for (Component c : s.wars) ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b, c);
            b.writeVarInt(s.enemies.size());
            for (UUID u : s.enemies) b.writeUUID(u);
            b.writeOptional(s.duelWith, (buf, u) -> buf.writeUUID(u));
            b.writeUtf(s.duelName, 64);
            b.writeVarInt(s.duelPhase);
            b.writeVarInt(s.duelTicks);
            b.writeVarInt(s.requests.size());
            for (Req r : s.requests) {
                b.writeUUID(r.from());
                b.writeUtf(r.name(), 64);
                b.writeVarInt(r.kind());
                b.writeVarInt(r.ticksLeft());
            }
            b.writeBoolean(s.arena.isPresent());
            s.arena.ifPresent(a -> {
                b.writeDouble(a.x());
                b.writeDouble(a.y());
                b.writeDouble(a.z());
                b.writeFloat(a.radius());
                b.writeBoolean(a.releasing());
                b.writeVarInt(a.ticksLeft());
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b, a.boss());
            });
            b.writeBoolean(s.partyChat);
        }

        private static State read(RegistryFriendlyByteBuf b) {
            String allegiance = b.readUtf(256);
            Optional<Component> name = ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(b);
            int nw = Math.min(b.readVarInt(), 256);
            List<Component> wars = new ArrayList<>(nw);
            for (int i = 0; i < nw; i++) wars.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b));
            int ne = Math.min(b.readVarInt(), 4096);
            List<UUID> enemies = new ArrayList<>(ne);
            for (int i = 0; i < ne; i++) enemies.add(b.readUUID());
            Optional<UUID> duel = b.readOptional(buf -> buf.readUUID());
            String duelName = b.readUtf(64);
            int phase = b.readVarInt();
            int ticks = b.readVarInt();
            int nr = Math.min(b.readVarInt(), 64);
            List<Req> reqs = new ArrayList<>(nr);
            for (int i = 0; i < nr; i++) reqs.add(new Req(b.readUUID(), b.readUtf(64), b.readVarInt(), b.readVarInt()));
            Optional<ArenaInfo> arena = Optional.empty();
            if (b.readBoolean()) {
                arena = Optional.of(new ArenaInfo(b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readBoolean(),
                        b.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b)));
            }
            boolean chat = b.readBoolean();
            return new State(allegiance, name, wars, enemies, duel, duelName, phase, ticks, reqs, arena, chat);
        }
    }

    /** S→C: perfil de outro jogador (abre a tela). */
    public record Profile(UUID id, String name, String path, int level, int circle, Optional<Component> title,
                          Optional<Component> allegiance, int partySize, String partyLeader, int duelWins, int duelLosses,
                          int kills, int bosses, List<Component> extra, boolean sameParty, boolean atWar) implements CustomPacketPayload {
        public static final Type<Profile> TYPE = new Type<>(IsekaiMahou.id("social_profile"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Profile> STREAM_CODEC = StreamCodec.of(Profile::write, Profile::read);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        private static void write(RegistryFriendlyByteBuf b, Profile p) {
            b.writeUUID(p.id);
            b.writeUtf(p.name, 64);
            b.writeUtf(p.path, 64);
            b.writeVarInt(p.level);
            b.writeVarInt(p.circle);
            ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(b, p.title);
            ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(b, p.allegiance);
            b.writeVarInt(p.partySize);
            b.writeUtf(p.partyLeader, 64);
            b.writeVarInt(p.duelWins);
            b.writeVarInt(p.duelLosses);
            b.writeVarInt(p.kills);
            b.writeVarInt(p.bosses);
            b.writeVarInt(p.extra.size());
            for (Component c : p.extra) ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b, c);
            b.writeBoolean(p.sameParty);
            b.writeBoolean(p.atWar);
        }

        private static Profile read(RegistryFriendlyByteBuf b) {
            UUID id = b.readUUID();
            String name = b.readUtf(64);
            String path = b.readUtf(64);
            int level = b.readVarInt();
            int circle = b.readVarInt();
            Optional<Component> title = ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(b);
            Optional<Component> allegiance = ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(b);
            int partySize = b.readVarInt();
            String leader = b.readUtf(64);
            int wins = b.readVarInt();
            int losses = b.readVarInt();
            int kills = b.readVarInt();
            int bosses = b.readVarInt();
            int n = Math.min(b.readVarInt(), 32);
            List<Component> extra = new ArrayList<>(n);
            for (int i = 0; i < n; i++) extra.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b));
            return new Profile(id, name, path, level, circle, title, allegiance, partySize, leader, wins, losses, kills, bosses, extra,
                    b.readBoolean(), b.readBoolean());
        }
    }

    // =====================================================================================
    //  Registro
    // =====================================================================================
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(Action.TYPE, Action.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) SocialActions.onWheel(sp, p.target(), p.action());
        });
        r.playToServer(Reply.TYPE, Reply.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) SocialActions.onReply(sp, p.from(), Requests.Kind.byIndex(p.kind()), p.accept());
        });
        r.playToServer(PartyOp.TYPE, PartyOp.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) SocialActions.onPartyOp(sp, p.op(), p.target());
        });
        r.playToClient(PartySync.TYPE, PartySync.STREAM_CODEC, (p, c) -> ClientSocialView.setParty(p));
        r.playToClient(State.TYPE, State.STREAM_CODEC, (p, c) -> {
            ClientSocialView.setState(p);
            onState.accept(p);
        });
        r.playToClient(Profile.TYPE, Profile.STREAM_CODEC, (p, c) -> onProfile.accept(p));
    }

    private SocialPayloads() {}
}
