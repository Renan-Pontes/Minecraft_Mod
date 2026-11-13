package com.renanpontes.isekaimahou.feature.combat_lock;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Pacotes do módulo (registrador {@code "combat_lock-1"}, ids {@code isekai_mahou:combat_lock_*}). */
public final class LockPayloads {
    /** Aviso de golpe forte de monstro comum. */
    public static final byte WARN_HEAVY = 0;
    /** Aviso de ataque pelas costas. */
    public static final byte WARN_BEHIND = 1;

    /** C→S: o jogador travou no alvo {@code entityId} (-1 = soltou). O servidor usa para mirar as magias. */
    public record Target(int entityId) implements CustomPacketPayload {
        public static final Type<Target> TYPE = new Type<>(IsekaiMahou.id("combat_lock_target"));
        public static final StreamCodec<ByteBuf, Target> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Target::entityId, Target::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: mostre o "!!!" para o monstro {@code entityId} (-1 = sem monstro, só o aviso) do tipo {@code kind}. */
    public record Warn(int entityId, byte kind) implements CustomPacketPayload {
        public static final Type<Warn> TYPE = new Type<>(IsekaiMahou.id("combat_lock_warn"));
        public static final StreamCodec<ByteBuf, Warn> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Warn::entityId, ByteBufCodecs.BYTE, Warn::kind, Warn::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private LockPayloads() {}
}
