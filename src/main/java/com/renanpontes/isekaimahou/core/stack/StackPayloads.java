package com.renanpontes.isekaimahou.core.stack;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes da Pilha (todos servidor → cliente). Os handlers chamam código de cliente SÓ dentro do lambda, então o
 * servidor dedicado nunca carrega classes de {@code client/}.
 */
public final class StackPayloads {
    public static final int FLAG_COUNTERABLE = 1;
    public static final int FLAG_BOSS = 2;
    public static final int FLAG_PLAYER = 4;

    /** Motivo de saída de uma pendente (vai no {@link Remove} para o HUD animar). */
    public static final byte REASON_RESOLVED = 0;
    public static final byte REASON_COUNTERED = 1;
    public static final byte REASON_CANCELLED = 2;
    public static final byte REASON_OUT_OF_RANGE = 3;

    /** Contramágica: o conjurador era chefe / ficou atordoado / alguém contrariou (by não é vazio). */
    public static final int FX_BOSS = 1;
    public static final int FX_STUNNED = 2;
    public static final int FX_HAS_BY = 4;

    /**
     * Uma pendente hostil entrou (ou mudou) no alcance do jogador. {@code element} = ordinal de Element ou -1;
     * {@code tier} = ordinal de SpellStack.Tier; {@code total}/{@code remaining} em ticks.
     */
    public record Add(int id, int casterId, Component casterName, Component castName, int element, int tier,
                      int total, int remaining, int flags) implements CustomPacketPayload {
        public static final Type<Add> TYPE = new Type<>(IsekaiMahou.id("core_stack_add"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Add> STREAM_CODEC = StreamCodec.of(Add::write, Add::read);

        private static void write(RegistryFriendlyByteBuf buf, Add p) {
            buf.writeVarInt(p.id);
            buf.writeVarInt(p.casterId + 1);
            ComponentSerialization.STREAM_CODEC.encode(buf, p.casterName);
            ComponentSerialization.STREAM_CODEC.encode(buf, p.castName);
            buf.writeVarInt(p.element + 1);
            buf.writeByte(p.tier);
            buf.writeVarInt(p.total);
            buf.writeVarInt(p.remaining);
            buf.writeByte(p.flags);
        }

        private static Add read(RegistryFriendlyByteBuf buf) {
            return new Add(buf.readVarInt(), buf.readVarInt() - 1, ComponentSerialization.STREAM_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt() - 1, buf.readByte(),
                    buf.readVarInt(), buf.readVarInt(), buf.readByte());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A pendente saiu: {@code reason} = REASON_* (resolveu, contrariada, cancelada, fora do alcance). */
    public record Remove(int id, byte reason) implements CustomPacketPayload {
        public static final Type<Remove> TYPE = new Type<>(IsekaiMahou.id("core_stack_remove"));
        public static final StreamCodec<FriendlyByteBuf, Remove> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeVarInt(p.id); buf.writeByte(p.reason); },
                buf -> new Remove(buf.readVarInt(), buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Contramágica bem-sucedida perto do jogador: letreiro "CONTRAMÁGICA!" e círculo rúnico estilhaçando no ponto
     * ({@code x,y,z}, raio) e no conjurador ({@code casterId}, -1 = sumiu).
     */
    public record Countered(int id, double x, double y, double z, float radius, int casterId, Component casterName,
                            Component spellName, Component byName, int tier, int rgb, int flags) implements CustomPacketPayload {
        public static final Type<Countered> TYPE = new Type<>(IsekaiMahou.id("core_stack_countered"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Countered> STREAM_CODEC = StreamCodec.of(Countered::write, Countered::read);

        private static void write(RegistryFriendlyByteBuf buf, Countered p) {
            buf.writeVarInt(p.id);
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
            buf.writeFloat(p.radius);
            buf.writeVarInt(p.casterId + 1);
            ComponentSerialization.STREAM_CODEC.encode(buf, p.casterName);
            ComponentSerialization.STREAM_CODEC.encode(buf, p.spellName);
            ComponentSerialization.STREAM_CODEC.encode(buf, p.byName);
            buf.writeByte(p.tier);
            buf.writeInt(p.rgb);
            buf.writeByte(p.flags);
        }

        private static Countered read(RegistryFriendlyByteBuf buf) {
            return new Countered(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                    buf.readVarInt() - 1, ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf), buf.readByte(), buf.readInt(), buf.readByte());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(Add.TYPE, Add.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.stack.ClientStack.onAdd(p));
        r.playToClient(Remove.TYPE, Remove.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.stack.ClientStack.onRemove(p));
        r.playToClient(Countered.TYPE, Countered.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.stack.ClientStack.onCountered(p));
    }

    private StackPayloads() {}
}
