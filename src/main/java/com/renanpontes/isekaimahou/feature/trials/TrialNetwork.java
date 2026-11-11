package com.renanpontes.isekaimahou.feature.trials;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacote do módulo: estado da Provação para o HUD/rastreador e o céu (servidor → cliente). */
public final class TrialNetwork {

    /**
     * Estado da Provação do jogador. {@code active=false} = não está em nenhuma (limpa o HUD).
     * {@code phase} = ordinal de {@link TrialInstance.Phase}; {@code seconds} = contagem atual da fase (0 = nenhuma).
     */
    public record TrialHud(boolean active, String element, int rank, int phase, int wave, int waves, int enemies, int total,
                           int downed, int members, int seconds, boolean challenge, boolean selfDowned, boolean hasTarget,
                           BlockPos target) implements CustomPacketPayload {
        public static final Type<TrialHud> TYPE = new Type<>(IsekaiMahou.id("trials_hud"));
        public static final TrialHud NONE = new TrialHud(false, "arcane", 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, false, BlockPos.ZERO);
        public static final StreamCodec<FriendlyByteBuf, TrialHud> STREAM_CODEC = StreamCodec.of(TrialHud::write, TrialHud::read);

        private static void write(FriendlyByteBuf buf, TrialHud h) {
            buf.writeBoolean(h.active);
            buf.writeUtf(h.element, 32);
            buf.writeVarInt(h.rank);
            buf.writeVarInt(h.phase);
            buf.writeVarInt(h.wave);
            buf.writeVarInt(h.waves);
            buf.writeVarInt(h.enemies);
            buf.writeVarInt(h.total);
            buf.writeVarInt(h.downed);
            buf.writeVarInt(h.members);
            buf.writeVarInt(h.seconds);
            buf.writeBoolean(h.challenge);
            buf.writeBoolean(h.selfDowned);
            buf.writeBoolean(h.hasTarget);
            buf.writeBlockPos(h.target);
        }

        private static TrialHud read(FriendlyByteBuf buf) {
            return new TrialHud(buf.readBoolean(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBlockPos());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Gancho preenchido pela parte de cliente (no servidor dedicado continua vazio). */
    public static Consumer<TrialHud> clientHud = h -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("trials-1");
        r.playToClient(TrialHud.TYPE, TrialHud.STREAM_CODEC, (p, c) -> clientHud.accept(p));
    }

    public static void send(ServerPlayer p, TrialHud hud) {
        PacketDistributor.sendToPlayer(p, hud);
    }

    public static void clear(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, TrialHud.NONE);
    }

    private TrialNetwork() {}
}
