package com.renanpontes.isekaimahou.core.anim;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Pacotes das animações de combate (registrador {@code "anim-1"}, ids {@code isekai_mahou:anim_*}). */
public final class AnimPayloads {
    /** Tamanho máximo do id de animação na rede. */
    static final int MAX_ID = 48;

    /**
     * S→C: um jogador começou uma animação ({@code stop = false}) ou parou a pose mantida ({@code stop = true},
     * só desliga se o que está tocando for uma pose contínua — canalização/carga).
     */
    public record Play(int entityId, String anim, boolean stop) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(IsekaiMahou.id("anim_play"));
        public static final StreamCodec<ByteBuf, Play> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Play::entityId, ByteBufCodecs.stringUtf8(MAX_ID), Play::anim,
                ByteBufCodecs.BOOL, Play::stop, Play::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C→S: o cliente tocou um golpe do combo (o servidor repassa a quem está vendo). */
    public record Melee(String anim) implements CustomPacketPayload {
        public static final Type<Melee> TYPE = new Type<>(IsekaiMahou.id("anim_melee"));
        public static final StreamCodec<ByteBuf, Melee> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.stringUtf8(MAX_ID), Melee::anim, Melee::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C→S: o cliente esquivou (direção {@link MahouAnims#DIR_FORWARD}...). O servidor valida e dá a invulnerabilidade. */
    public record Dodge(byte dir) implements CustomPacketPayload {
        public static final Type<Dodge> TYPE = new Type<>(IsekaiMahou.id("anim_dodge"));
        public static final StreamCodec<ByteBuf, Dodge> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BYTE, Dodge::dir, Dodge::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: regras da esquiva do servidor (ao entrar). */
    public record Rules(boolean dodge, int cooldown) implements CustomPacketPayload {
        public static final Type<Rules> TYPE = new Type<>(IsekaiMahou.id("anim_rules"));
        public static final StreamCodec<ByteBuf, Rules> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Rules::dodge, ByteBufCodecs.VAR_INT, Rules::cooldown, Rules::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C→S (v1.0): o jogador apertou ({@code down}) ou soltou a tecla de bloqueio. O servidor valida ({@link GuardService}). */
    public record Guard(boolean down) implements CustomPacketPayload {
        public static final Type<Guard> TYPE = new Type<>(IsekaiMahou.id("anim_guard"));
        public static final StreamCodec<ByteBuf, Guard> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, Guard::down, Guard::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * S→C (v1.0): resposta do bloqueio só para o próprio jogador — {@link GuardService#FX_END} (o servidor recusou ou
     * encerrou a guarda) ou {@link GuardService#FX_PARRY} (aparo: clarão curto).
     */
    public record GuardFx(byte kind) implements CustomPacketPayload {
        public static final Type<GuardFx> TYPE = new Type<>(IsekaiMahou.id("anim_guard_fx"));
        public static final StreamCodec<ByteBuf, GuardFx> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BYTE, GuardFx::kind, GuardFx::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private AnimPayloads() {}
}
