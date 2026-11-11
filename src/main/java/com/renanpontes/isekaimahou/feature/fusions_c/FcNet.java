package com.renanpontes.isekaimahou.feature.fusions_c;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede das Misturas C: um pacote servidor → cliente de "clarão" na tela (Supernova, Canhão Elétrico, Zero Absoluto).
 * O handler chama um gancho preenchido pela parte de cliente — nada de classe de cliente no servidor dedicado.
 */
public final class FcNet {
    /** Clarão de tela: cor RGB, força 0–1 e duração do desvanecer. */
    public record Flash(int rgb, float strength, int ticks) implements CustomPacketPayload {
        public static final Type<Flash> TYPE = new Type<>(IsekaiMahou.id("fusions_c_flash"));
        public static final StreamCodec<FriendlyByteBuf, Flash> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeInt(p.rgb);
                    buf.writeFloat(p.strength);
                    buf.writeVarInt(p.ticks);
                },
                buf -> new Flash(buf.readInt(), buf.readFloat(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preenchido pelo FusionsCClient. No servidor dedicado nunca é chamado. */
    public static volatile Consumer<Flash> clientFlash = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("fusions_c-1");
        r.playToClient(Flash.TYPE, Flash.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientFlash.accept(p)));
    }

    /** Clarão para os jogadores a até {@code radius} blocos (mais forte perto do centro). */
    public static void flash(ServerLevel level, Vec3 at, double radius, int rgb, float strength, int ticks) {
        for (ServerPlayer p : level.players()) {
            double d = p.getEyePosition().distanceTo(at);
            if (d > radius) continue;
            float s = strength * (float) (1.0 - 0.6 * d / radius);
            PacketDistributor.sendToPlayer(p, new Flash(rgb, s, ticks));
        }
    }

    private FcNet() {}
}
