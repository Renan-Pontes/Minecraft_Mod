package com.renanpontes.isekaimahou.feature.class_hollow;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede da classe Hollow. Servidor → cliente: efeitos ({@link Fx}), a aparência de um Hollow ({@link Look}), a cena da
 * hollowificação ({@link Cinematic}), as telas de escolha ({@link Open}) e o sentido da Pesquisa ({@link Sense}).
 * Cliente → servidor: a resposta das telas ({@link Choice}). Os consumidores de cliente são preenchidos pelo módulo de
 * cliente (nulos no servidor dedicado).
 */
public final class HollowNetwork {
    // ---------------- tipos de Fx ----------------
    /** Carga do Cero na boca de {@code entity} por {@code size} ticks. */
    public static final int CERO_CHARGE = 0;
    /** O feixe do Cero de pos até vec; size = largura. */
    public static final int CERO_BEAM = 1;
    /** Bala: risco rápido de pos até vec. */
    public static final int BALA = 2;
    /** Sonído: imagem residual em pos, chegada em vec. */
    public static final int SONIDO = 3;
    /** Garganta/Descorrer: o céu se rasga em pos (vec = para onde olha), size = altura do rasgo. */
    public static final int GARGANTA = 4;
    /** Pesquisa: pulso de sentido saindo de pos até size blocos. */
    public static final int PESQUISA = 5;
    /** Devorar: almas saem de pos e entram na boca de {@code entity}; size = quantas. */
    public static final int DEVOUR = 6;
    /** Resurrección: casulo de Reiatsu que explode; size = raio. */
    public static final int RESURRECCION = 7;
    /** Hierro: a pele endurece (brilho de osso). */
    public static final int HIERRO = 8;
    /** Regeneração: a carne se fecha. */
    public static final int REGEN = 9;
    /** Onda de choque no chão em pos; size = raio. */
    public static final int SHOCKWAVE = 10;
    /** Descorrer: rasgo de saída em pos e de chegada em vec. */
    public static final int RIFT = 11;
    /** A máscara se formando no rosto de {@code entity}. */
    public static final int MASK_FORM = 12;
    /** Carga do Gran Rey Cero (o sangue no Cero) em {@code entity} por size ticks. */
    public static final int GRAN_REY_CHARGE = 13;
    /** Tremor de câmera para quem está perto de pos; size = força. */
    public static final int SHAKE = 14;
    /** v1.4 — Negación: a coluna de luz dourada que desce sobre {@code entity} por size ticks. */
    public static final int NEGACION = 15;
    /** v1.4 — Cero Oscuras: feixe NEGRO com borda na cor (rgb) de pos até vec; size = largura. */
    public static final int OSCURAS_BEAM = 16;
    /** v1.4 — apaga a carga (Cero/Gran Rey) que estiver na boca/mão de {@code entity}. */
    public static final int CHARGE_STOP = 17;
    /** v1.4 — a invocação da Resurrección: espiral de Reiatsu subindo em volta de {@code entity} por size ticks. */
    public static final int RELEASE_CHANNEL = 18;
    /**
     * w10 — Desgarrón da Pantera: 3 marcas de garra azuis LANÇADAS de pos (centro) andando na direção de vec;
     * |vec| = alcance (já cortado na parede), size = velocidade (blocos/tick).
     */
    public static final int CLAWS = 19;

    // ---------------- telas ----------------
    public static final int SCREEN_HOLLOW_CHOICE = 0;
    public static final int SCREEN_RELEASE = 1;

    // ---------------- respostas ----------------
    public static final int CHOICE_FIGHT = 0;
    public static final int CHOICE_ACCEPT = 1;
    /** Pedido da aba: abrir a escolha da Resurrección (se já pode). */
    public static final int CHOICE_OPEN_RELEASE = 2;
    /** Resurrección escolhida: {@code value} = índice do arquétipo. */
    public static final int CHOICE_RELEASE = 3;
    /** Liga/desliga o Hierro passivo. */
    public static final int CHOICE_TOGGLE_HIERRO = 4;

    public static volatile Consumer<Fx> clientFx;
    public static volatile Consumer<Look> clientLook;
    public static volatile Consumer<Cinematic> clientCinematic;
    public static volatile Consumer<Open> clientOpen;
    public static volatile Consumer<Sense> clientSense;
    public static volatile Consumer<TechCd> clientTechCd;

    // ---------------- v1.4: técnicas inatas (tecla R e Roda Hollow) ----------------
    /** Apertou a tecla "Instinto Hollow" (o servidor lê agachado/correndo). */
    public static final int TECH_PRESS = 0;
    /** Soltou a tecla "Instinto Hollow" (toque ou fim da carga). */
    public static final int TECH_RELEASE = 1;
    /** Escolheu na Roda Hollow: {@code value} = índice da técnica. */
    public static final int TECH_WHEEL = 2;

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_hollow-2");
        r.playToClient(Fx.TYPE, Fx.STREAM_CODEC, (p, c) -> accept(clientFx, p));
        r.playToClient(Look.TYPE, Look.STREAM_CODEC, (p, c) -> accept(clientLook, p));
        r.playToClient(Cinematic.TYPE, Cinematic.STREAM_CODEC, (p, c) -> accept(clientCinematic, p));
        r.playToClient(Open.TYPE, Open.STREAM_CODEC, (p, c) -> accept(clientOpen, p));
        r.playToClient(Sense.TYPE, Sense.STREAM_CODEC, (p, c) -> accept(clientSense, p));
        r.playToServer(Choice.TYPE, Choice.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) HollowEvents.onChoice(sp, p.option(), p.value());
        });
        r.playToClient(TechCd.TYPE, TechCd.STREAM_CODEC, (p, c) -> accept(clientTechCd, p));
        r.playToServer(TechInput.TYPE, TechInput.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowTechniques.onInput(sp, p.action(), p.value());
        });
    }

    private static <T> void accept(Consumer<T> h, T p) {
        if (h != null) h.accept(p);
    }

    // =====================================================================================
    //  Envio
    // =====================================================================================
    /** Efeito para quem acompanha a entidade (e ela mesma). */
    public static void fx(Entity e, int kind, Vec3 pos, Vec3 vec, float size, int rgb) {
        if (e.level().isClientSide) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new Fx(kind, e.getId(), pos, vec, size, rgb));
    }

    /** Efeito para todos num raio. */
    public static void fxNear(ServerLevel level, int kind, int entityId, Vec3 pos, Vec3 vec, float size, int rgb, double radius) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius, new Fx(kind, entityId, pos, vec, size, rgb));
    }

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    public record Fx(int kind, int entity, Vec3 pos, Vec3 vec, float size, int rgb) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(IsekaiMahou.id("class_hollow_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fx> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeByte(p.kind);
            buf.writeVarInt(p.entity + 1);
            buf.writeDouble(p.pos.x);
            buf.writeDouble(p.pos.y);
            buf.writeDouble(p.pos.z);
            buf.writeDouble(p.vec.x);
            buf.writeDouble(p.vec.y);
            buf.writeDouble(p.vec.z);
            buf.writeFloat(p.size);
            buf.writeInt(p.rgb);
        }, buf -> new Fx(buf.readByte(), buf.readVarInt() - 1, new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Aparência de um Hollow: rank (0..4; -1 = não é Hollow), arquétipo da Resurrección (0 nenhum, 1..3) e estados
     * ({@link #RELEASED}, {@link #HIERRO}, {@link #STARVING}).
     */
    public record Look(int entity, int rank, int release, int flags) implements CustomPacketPayload {
        public static final int RELEASED = 1;
        public static final int HIERRO = 2;
        public static final int STARVING = 4;
        public static final Type<Look> TYPE = new Type<>(IsekaiMahou.id("class_hollow_look"));
        public static final StreamCodec<ByteBuf, Look> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            ByteBufCodecs.VAR_INT.encode(buf, p.entity);
            ByteBufCodecs.VAR_INT.encode(buf, p.rank + 1);
            buf.writeByte(p.release);
            buf.writeByte(p.flags);
        }, buf -> new Look(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf) - 1, buf.readByte(), buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Etapa da cena da hollowificação (0 começa, 1 máscara, 2 buraco, 3 branco-negro, 4 rugido, 5 fim). */
    public record Cinematic(int stage, int ticks) implements CustomPacketPayload {
        public static final Type<Cinematic> TYPE = new Type<>(IsekaiMahou.id("class_hollow_cinematic"));
        public static final StreamCodec<ByteBuf, Cinematic> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Cinematic::stage, ByteBufCodecs.VAR_INT, Cinematic::ticks, Cinematic::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Abre uma tela ({@link #SCREEN_HOLLOW_CHOICE}, {@link #SCREEN_RELEASE}). */
    public record Open(int screen) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(IsekaiMahou.id("class_hollow_open"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Open::new, Open::screen);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Pesquisa: ids das entidades sentidas e o tipo (0 criatura, 1 hostil, 2 jogador, 3 Vazio/forte), por {@code ticks}. */
    public record Sense(int[] ids, byte[] kinds, int ticks) implements CustomPacketPayload {
        public static final Type<Sense> TYPE = new Type<>(IsekaiMahou.id("class_hollow_sense"));
        public static final StreamCodec<ByteBuf, Sense> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            int n = Math.min(p.ids.length, p.kinds.length);
            ByteBufCodecs.VAR_INT.encode(buf, n);
            for (int i = 0; i < n; i++) {
                ByteBufCodecs.VAR_INT.encode(buf, p.ids[i]);
                buf.writeByte(p.kinds[i]);
            }
            ByteBufCodecs.VAR_INT.encode(buf, p.ticks);
        }, buf -> {
            int n = Math.min(256, ByteBufCodecs.VAR_INT.decode(buf));
            int[] ids = new int[n];
            byte[] kinds = new byte[n];
            for (int i = 0; i < n; i++) {
                ids[i] = ByteBufCodecs.VAR_INT.decode(buf);
                kinds[i] = buf.readByte();
            }
            return new Sense(ids, kinds, ByteBufCodecs.VAR_INT.decode(buf));
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Resposta do cliente: opção ({@link #CHOICE_FIGHT}...) e um valor. */
    public record Choice(int option, int value) implements CustomPacketPayload {
        public static final Type<Choice> TYPE = new Type<>(IsekaiMahou.id("class_hollow_choice"));
        public static final StreamCodec<ByteBuf, Choice> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Choice::option, ByteBufCodecs.VAR_INT, Choice::value, Choice::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** v1.4 — C→S: a tecla "Instinto Hollow" ({@link #TECH_PRESS}/{@link #TECH_RELEASE}) ou a Roda ({@link #TECH_WHEEL}). */
    public record TechInput(int action, int value) implements CustomPacketPayload {
        public static final Type<TechInput> TYPE = new Type<>(IsekaiMahou.id("class_hollow_tech_input"));
        public static final StreamCodec<ByteBuf, TechInput> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TechInput::action, ByteBufCodecs.VAR_INT, TechInput::value, TechInput::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** v1.4 — S→C: recarga de uma técnica (índice de {@code HollowTech}) em ticks; {@code chain} = passos de Sonído usados. */
    public record TechCd(int tech, int ticks, int chain) implements CustomPacketPayload {
        public static final Type<TechCd> TYPE = new Type<>(IsekaiMahou.id("class_hollow_tech_cd"));
        public static final StreamCodec<ByteBuf, TechCd> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TechCd::tech, ByteBufCodecs.VAR_INT, TechCd::ticks, ByteBufCodecs.VAR_INT, TechCd::chain, TechCd::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private HollowNetwork() {}
}
