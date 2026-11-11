package com.renanpontes.isekaimahou.feature.bosses_g;

import java.util.function.IntConsumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do módulo: o "Canto do Esquecimento" apaga o HUD do jogador por alguns ticks (servidor → cliente). */
public final class BgPayloads {
    /** Gancho preenchido pela parte de cliente (no servidor dedicado não faz nada). */
    public static volatile IntConsumer onMemoryWipe = ticks -> {};

    public record MemoryWipe(int ticks) implements CustomPacketPayload {
        public static final Type<MemoryWipe> TYPE = new Type<>(IsekaiMahou.id("bosses_g_memory_wipe"));
        public static final StreamCodec<ByteBuf, MemoryWipe> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(MemoryWipe::new, MemoryWipe::ticks);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("bosses_g-1");
        r.playToClient(MemoryWipe.TYPE, MemoryWipe.STREAM_CODEC, (p, c) -> onMemoryWipe.accept(Math.max(0, Math.min(400, p.ticks()))));
    }

    public static void sendMemoryWipe(ServerPlayer player, int ticks) {
        PacketDistributor.sendToPlayer(player, new MemoryWipe(ticks));
    }

    private BgPayloads() {}
}
