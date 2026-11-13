package com.renanpontes.isekaimahou.feature.legends_i;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede do módulo legends_i (servidor → cliente). Três pacotes:
 * <ul>
 *   <li>{@link Fx}: efeito curto desenhado de verdade (braço esticado do Gomu Gomu, relâmpagos negros do Haki do Rei,
 *       esfera do Shinra Tensei, núcleo do Chibaku Tensei, fio do Bansho Ten'in, rastro do Chidori, corte do Susanoo).</li>
 *   <li>{@link State}: "a entidade N está no estado K por T ticks" (braços negros do Haki, costelas do Susanoo, asas de
 *       papel). {@code ticks = 0} limpa.</li>
 *   <li>{@link Sense}: SÓ para quem usou o Haki da Observação — contornos das presenças, vistos através das paredes.</li>
 * </ul>
 * Os handlers chamam ganchos que a parte de cliente preenche: nenhuma classe de cliente no servidor dedicado.
 */
public final class LiNet {
    // ---------------- Efeitos curtos ----------------
    /** Gomu Gomu: braço esticado de {@code pos} até {@code pos+dir} e de volta ({@code size} = grossura do punho). */
    public static final byte GOMU = 0;
    /** Haki do Rei: relâmpagos negro-vermelhos saindo de {@code pos} até o raio {@code size}. */
    public static final byte CONQUEROR = 1;
    /** Shinra Tensei: esfera de repulsão que cresce até {@code size}. */
    public static final byte SHINRA = 2;
    /** Bansho Ten'in: fio de força de {@code pos} até {@code pos+dir}. */
    public static final byte BANSHO = 3;
    /** Chibaku Tensei: núcleo negro em {@code pos} que cresce até {@code size} e dura {@code life}. */
    public static final byte CHIBAKU = 4;
    /** Chidori: rastro de raio de {@code pos} até {@code pos+dir}. */
    public static final byte CHIDORI = 5;
    /** Susanoo: meia-lua espectral gigante em {@code pos} na direção {@code dir} ({@code size} = alcance). */
    public static final byte SUSANOO_SLASH = 6;

    // ---------------- Estados ----------------
    public static final byte S_HAKI = 0;
    public static final byte S_SUSANOO = 1;
    public static final byte S_WINGS = 2;
    public static final byte S_CHIDORI = 3;
    public static final int STATE_KINDS = 4;

    public record Fx(byte kind, Vec3 pos, Vec3 dir, int color, int life, float size) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(IsekaiMahou.id("legends_i_fx"));
        public static final StreamCodec<FriendlyByteBuf, Fx> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeDouble(p.pos.x); buf.writeDouble(p.pos.y); buf.writeDouble(p.pos.z);
                    buf.writeFloat((float) p.dir.x); buf.writeFloat((float) p.dir.y); buf.writeFloat((float) p.dir.z);
                    buf.writeInt(p.color);
                    buf.writeVarInt(p.life);
                    buf.writeFloat(p.size);
                },
                buf -> new Fx(buf.readByte(),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                        buf.readInt(), buf.readVarInt(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record State(byte kind, int entity, int ticks) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(IsekaiMahou.id("legends_i_state"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeVarInt(p.entity);
                    buf.writeVarInt(Math.max(0, p.ticks));
                },
                buf -> new State(buf.readByte(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Presenças sentidas: ids das entidades, se cada uma é ameaça (vermelho) ou não (branco), e por quanto tempo. */
    public record Sense(int[] ids, boolean[] hostile, int ticks) implements CustomPacketPayload {
        public static final Type<Sense> TYPE = new Type<>(IsekaiMahou.id("legends_i_sense"));
        public static final StreamCodec<FriendlyByteBuf, Sense> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    int n = Math.min(p.ids.length, 64);
                    buf.writeVarInt(n);
                    for (int i = 0; i < n; i++) {
                        buf.writeVarInt(p.ids[i]);
                        buf.writeBoolean(p.hostile[i]);
                    }
                    buf.writeVarInt(p.ticks);
                },
                buf -> {
                    int n = Math.min(buf.readVarInt(), 64);
                    int[] ids = new int[n];
                    boolean[] h = new boolean[n];
                    for (int i = 0; i < n; i++) {
                        ids[i] = buf.readVarInt();
                        h[i] = buf.readBoolean();
                    }
                    return new Sense(ids, h, buf.readVarInt());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preenchidos pelo LegendsIClient. No servidor dedicado nunca são chamados. */
    public static volatile Consumer<Fx> clientFx = p -> {};
    public static volatile Consumer<State> clientState = p -> {};
    public static volatile Consumer<Sense> clientSense = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("legends_i-1");
        r.playToClient(Fx.TYPE, Fx.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientFx.accept(p)));
        r.playToClient(State.TYPE, State.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientState.accept(p)));
        r.playToClient(Sense.TYPE, Sense.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientSense.accept(p)));
    }

    private LiNet() {}
}
