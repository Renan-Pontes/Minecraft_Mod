package com.renanpontes.isekaimahou.feature.stands;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede dos stands (servidor → cliente): um pacote minúsculo, {@link Show} — "a entidade X mostra o stand S fazendo a
 * ação A por N ticks" (N = 0 apaga). Nada fica salvo; o cliente desenha sozinho o resto (pose, posição, brilho).
 */
public final class StandNet {
    public record Show(int entity, byte stand, byte action, short ticks) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(IsekaiMahou.id("stand_show"));
        public static final StreamCodec<FriendlyByteBuf, Show> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entity);
                    buf.writeByte(p.stand);
                    buf.writeByte(p.action);
                    buf.writeShort(p.ticks);
                },
                buf -> new Show(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readShort()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preenchido pelo StandsClient. No servidor dedicado fica sem fazer nada. */
    public static volatile Consumer<Show> client = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("stands-1");
        r.playToClient(Show.TYPE, Show.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> client.accept(p)));
    }

    /** Mostra para quem acompanha a entidade e para ela mesma. */
    public static void showAll(Entity user, Stand stand, Stand.Action action, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, payload(user, stand, action, ticks));
    }

    /** Mostra SÓ para o próprio usuário (King Crimson: ninguém mais pode saber onde você está). */
    public static void showSelf(ServerPlayer user, Stand stand, Stand.Action action, int ticks) {
        PacketDistributor.sendToPlayer(user, payload(user, stand, action, ticks));
    }

    private static Show payload(Entity user, Stand stand, Stand.Action action, int ticks) {
        return new Show(user.getId(), (byte) stand.ordinal(), (byte) action.ordinal(), (short) Math.max(0, Math.min(600, ticks)));
    }

    private StandNet() {}
}
