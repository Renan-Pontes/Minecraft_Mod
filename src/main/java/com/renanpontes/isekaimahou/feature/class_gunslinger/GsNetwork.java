package com.renanpontes.isekaimahou.feature.class_gunslinger;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_gunslinger.duel.DuelService;
import com.renanpontes.isekaimahou.feature.class_gunslinger.path.PathService;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Pacotes do Pistoleiro (registrador {@code "class_gunslinger-1"}):
 * <ul>
 *   <li>S→C {@link Anim}: animação de arma de um jogador (tiro, recarga, leque, saque...) para quem está vendo.</li>
 *   <li>C→S {@link Claim} (concluir rank), {@link Materialize} (criar arma de mana), {@link Duel} (desafiar ao meio-dia).</li>
 * </ul>
 * O cliente recebe pelo {@link #clientAnim} (preenchido em client/), então o servidor dedicado não carrega nada de cliente.
 */
public final class GsNetwork {
    public static final byte SHOOT = 0;
    public static final byte SHOOT_LEFT = 1;
    public static final byte RELOAD = 2;
    public static final byte FAN = 3;
    public static final byte DRAW = 4;
    public static final byte CHARGE = 5;
    public static final byte NOON = 6;
    public static final byte RELOAD_CANCEL = 7;

    /** Ponto de entrada do cliente (troca no ClassGunslingerClient). */
    public static volatile Consumer<Anim> clientAnim = a -> {};

    public record Anim(int entityId, byte kind, int ticks, int rgb) implements CustomPacketPayload {
        public static final Type<Anim> TYPE = new Type<>(IsekaiMahou.id("gunslinger_anim"));
        public static final StreamCodec<ByteBuf, Anim> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Anim::entityId, ByteBufCodecs.BYTE, Anim::kind, ByteBufCodecs.VAR_INT, Anim::ticks,
                ByteBufCodecs.INT, Anim::rgb, Anim::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Claim(int rank) implements CustomPacketPayload {
        public static final Type<Claim> TYPE = new Type<>(IsekaiMahou.id("gunslinger_claim"));
        public static final StreamCodec<ByteBuf, Claim> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Claim::rank, Claim::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Materialize(int kind) implements CustomPacketPayload {
        public static final Type<Materialize> TYPE = new Type<>(IsekaiMahou.id("gunslinger_materialize"));
        public static final StreamCodec<ByteBuf, Materialize> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Materialize::kind, Materialize::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Duel(int tier) implements CustomPacketPayload {
        public static final Type<Duel> TYPE = new Type<>(IsekaiMahou.id("gunslinger_duel"));
        public static final StreamCodec<ByteBuf, Duel> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Duel::tier, Duel::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            var r = e.registrar("class_gunslinger-1");
            r.playToClient(Anim.TYPE, Anim.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientAnim.accept(p)));
            r.playToServer(Claim.TYPE, Claim.STREAM_CODEC, (p, c) -> server(c, sp -> PathService.claim(sp, p.rank())));
            r.playToServer(Materialize.TYPE, Materialize.STREAM_CODEC,
                    (p, c) -> server(c, sp -> PathService.materialize(sp, GunKind.byOrdinal(p.kind()))));
            r.playToServer(Duel.TYPE, Duel.STREAM_CODEC, (p, c) -> server(c, sp -> DuelService.challenge(sp, p.tier())));
        });
    }

    private static void server(IPayloadContext c, Consumer<ServerPlayer> action) {
        c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) action.accept(sp);
        });
    }

    /** Animação para quem vê a entidade (e para ela mesma, se for jogador). */
    public static void anim(Entity entity, byte kind, int ticks, int rgb) {
        Anim a = new Anim(entity.getId(), kind, ticks, rgb);
        if (entity instanceof ServerPlayer sp) PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, a);
        else PacketDistributor.sendToPlayersTrackingEntity(entity, a);
    }

    private GsNetwork() {}
}
