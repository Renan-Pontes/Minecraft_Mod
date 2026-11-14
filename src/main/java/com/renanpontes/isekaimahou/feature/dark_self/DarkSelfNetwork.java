package com.renanpontes.isekaimahou.feature.dark_self;

import java.util.UUID;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacote da Sombra: avisa os clientes que a sombra (o círculo no chão) de um jogador sumiu ou voltou. */
public final class DarkSelfNetwork {

    /** Servidor → cliente: {@code hidden=true} = a sombra do jogador sumiu. */
    public record ShadowState(UUID player, boolean hidden) implements CustomPacketPayload {
        public static final Type<ShadowState> TYPE = new Type<>(IsekaiMahou.id("dark_self_shadow"));
        public static final StreamCodec<FriendlyByteBuf, ShadowState> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, ShadowState::player,
                ByteBufCodecs.BOOL, ShadowState::hidden,
                ShadowState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Servidor → cliente (SÓ o dono): presságio em primeira pessoa. {@code detach=false}: a silhueta aparece presa aos
     * pés; {@code detach=true}: ela se solta e desliza até ({@code x},{@code y},{@code z}), onde a Sombra vai surgir.
     */
    public record Omen(boolean detach, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Omen> TYPE = new Type<>(IsekaiMahou.id("dark_self_omen"));
        public static final StreamCodec<FriendlyByteBuf, Omen> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Omen::detach,
                ByteBufCodecs.DOUBLE, Omen::x,
                ByteBufCodecs.DOUBLE, Omen::y,
                ByteBufCodecs.DOUBLE, Omen::z,
                Omen::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preenchidos pela parte de cliente (o lado comum não conhece classes de cliente). */
    public static Consumer<ShadowState> clientShadow = s -> {};
    public static Consumer<Omen> clientOmen = o -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("dark_self-1");
        r.playToClient(ShadowState.TYPE, ShadowState.STREAM_CODEC, (p, ctx) -> clientShadow.accept(p));
        r.playToClient(Omen.TYPE, Omen.STREAM_CODEC, (p, ctx) -> clientOmen.accept(p));
    }

    /** Para o próprio jogador e quem o está vendo. */
    static void broadcast(ServerPlayer player, boolean hidden) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ShadowState(player.getUUID(), hidden));
    }

    static void sendOmen(ServerPlayer owner, boolean detach, net.minecraft.world.phys.Vec3 spot) {
        PacketDistributor.sendToPlayer(owner, new Omen(detach, spot.x, spot.y, spot.z));
    }

    static void sendTo(ServerPlayer viewer, UUID player, boolean hidden) {
        PacketDistributor.sendToPlayer(viewer, new ShadowState(player, hidden));
    }

    private DarkSelfNetwork() {}
}
