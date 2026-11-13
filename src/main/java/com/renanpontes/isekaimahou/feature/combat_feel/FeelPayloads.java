package com.renanpontes.isekaimahou.feature.combat_feel;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Pacotes do módulo (registrador {@code "combat_feel-1"}, ids {@code isekai_mahou:combat_feel_*}). Todos S→C. */
public final class FeelPayloads {
    /** Tipos de micro-pausa, do mais leve ao mais forte. */
    public static final byte STOP_CRIT = 0, STOP_HURT = 1, STOP_HEAVY = 2, STOP_PARRY = 3, STOP_BLAST = 4, STOP_FINISHER = 5;

    /** Micro-pausa do tipo {@code kind} na tela de quem recebe. */
    public record HitStop(byte kind) implements CustomPacketPayload {
        public static final Type<HitStop> TYPE = new Type<>(IsekaiMahou.id("combat_feel_hit_stop"));
        public static final StreamCodec<ByteBuf, HitStop> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BYTE, HitStop::kind, HitStop::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Estado do combo de magias: {@code count} magias seguidas (0 = acabou), {@code ticksLeft} até zerar (de
     * {@code window}) e o bônus atual em décimos de % ({@code bonusTenths} 45 = +4,5%).
     */
    public record Combo(int count, int ticksLeft, int window, int bonusTenths) implements CustomPacketPayload {
        public static final Type<Combo> TYPE = new Type<>(IsekaiMahou.id("combat_feel_combo"));
        public static final StreamCodec<ByteBuf, Combo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Combo::count, ByteBufCodecs.VAR_INT, Combo::ticksLeft,
                ByteBufCodecs.VAR_INT, Combo::window, ByteBufCodecs.VAR_INT, Combo::bonusTenths, Combo::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private FeelPayloads() {}
}
