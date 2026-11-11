package com.renanpontes.isekaimahou.feature.legends_f;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede do módulo legends_f (servidor → cliente). Dois pacotes:
 * <ul>
 *   <li>{@link Fx}: efeito curto desenhado de verdade num ponto (anel, pulso de mana, pilar do "Levante-se!", Bang...).</li>
 *   <li>{@link State}: "a entidade N está no estado K por T ticks" (dormindo, motosserra, Modo Sábio, Monarca...), para o
 *   cliente desenhar auras/camadas no jogador ou criatura. {@code ticks = 0} limpa o estado.</li>
 * </ul>
 * Os handlers chamam ganchos que a parte de cliente preenche: nenhuma classe de cliente no servidor dedicado.
 */
public final class LfNet {
    // ---------------- Efeitos curtos ----------------
    /** Onda de choque em anel: centro {@code pos}, eixo {@code dir}, {@code size} = raio final. */
    public static final byte RING = 0;
    /** Pulso de Detectar Mana: esfera hexagonal que se expande até {@code size}. */
    public static final byte PULSE = 1;
    /** "Levante-se!": coluna de chamas negras e roxas que sobe do chão ({@code size} = altura). */
    public static final byte ARISE = 2;
    /** Bang!: estouro vermelho e preto em estrela no alvo. */
    public static final byte BANG = 3;
    /** Clarão do "dedo-arma" na mão de quem conjura. */
    public static final byte MUZZLE = 4;
    /** Troca de Sombras: implosão de sombra (dois pontos: {@code pos} e {@code pos+dir}). */
    public static final byte SWAP = 5;
    /** Florescer: anel de pétalas e folhas que se abre no chão. */
    public static final byte BLOOM = 6;
    /** Barreira quebrando: estilhaços hexagonais voando de {@code pos} (raio {@code size}). */
    public static final byte SHATTER = 7;
    /** Selo do contrato: círculo vermelho com runas que gira e some ({@code size} = raio). */
    public static final byte SEAL = 8;

    // ---------------- Estados ----------------
    public static final byte S_SLEEP = 0;
    public static final byte S_CHAINSAW = 1;
    public static final byte S_SAGE = 2;
    public static final byte S_MEDITATE = 3;
    public static final byte S_MONARCH = 4;
    public static final byte S_CONCEAL = 5;
    /** Contrato Faustiano: {@code value} = progresso 0..100. */
    public static final byte S_FAUST = 6;
    public static final int STATE_KINDS = 7;

    public record Fx(byte kind, Vec3 pos, Vec3 dir, int color, int life, float size) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(IsekaiMahou.id("legends_f_fx"));
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

    public record State(byte kind, int entity, int ticks, int value) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(IsekaiMahou.id("legends_f_state"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeVarInt(p.entity);
                    buf.writeVarInt(Math.max(0, p.ticks));
                    buf.writeVarInt(p.value);
                },
                buf -> new State(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preenchidos pelo LegendsFClient. No servidor dedicado nunca são chamados. */
    public static volatile Consumer<Fx> clientFx = p -> {};
    public static volatile Consumer<State> clientState = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("legends_f-1");
        r.playToClient(Fx.TYPE, Fx.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientFx.accept(p)));
        r.playToClient(State.TYPE, State.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientState.accept(p)));
    }

    private LfNet() {}
}
