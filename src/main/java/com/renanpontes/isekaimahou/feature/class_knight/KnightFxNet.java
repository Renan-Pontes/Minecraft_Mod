package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede dos EFEITOS de anime da classe (só visual — a regra fica no servidor). O servidor manda um {@link Burst} curto
 * (tipo, posição, vetor, tamanho, cor, entidade, dado extra) e o cliente ({@code client/KnightFxClient}) monta a
 * coreografia inteira (carga → impacto → rescaldo) respeitando o preset gráfico. Troca dezenas de partículas enviadas
 * pelo servidor por ~45 bytes.
 */
public final class KnightFxNet {
    /** Meia-lua de lâmina (onda viajando, a cada tick). vec = direção, size = meia-largura, data 1 = grande. */
    public static final int CRESCENT = 0;
    /** A onda de lâmina se desfaz ao acertar/bater. data 1 = grande. */
    public static final int WAVE_BURST = 1;
    /** Círculo de runas no chão (pos). size = raio. */
    public static final int RUNE_CIRCLE = 2;
    /** Feixe/lâmina de luz de pos até vec. size = espessura. */
    public static final int LINE = 3;
    /** Coluna de luz em pos. size = raio, data = altura. */
    public static final int PILLAR = 4;
    /** Excalibur: a lâmina erguida recebe a luz do céu (entity). */
    public static final int EXCALIBUR_CHARGE = 5;
    /** Excalibur: um pulso do feixe de pos até vec. size = raio, data 1 = primeiro. */
    public static final int EXCALIBUR_PULSE = 6;
    /** Balmung: meia-lua horizontal enorme. vec = direção, size = distância. */
    public static final int BALMUNG_ARC = 7;
    /** Chuva de Espadas: uma espada crava no chão. */
    public static final int SWORD_LAND = 8;
    /** Strike Air: túnel de vento de pos na direção vec (comprimento = |vec|). size = raio. */
    public static final int STRIKE_AIR = 9;
    /** Égide: as placas se formam em entity. */
    public static final int AEGIS_UP = 10;
    /** Égide: uma placa absorve o golpe (entity). */
    public static final int AEGIS_HIT = 11;
    /** Égide: a última placa explode (entity). size = raio. */
    public static final int AEGIS_BREAK = 12;
    /** Julgamento: a cruz sagrada desce sobre pos. */
    public static final int JUDGMENT = 13;
    /** Anti-Magia: corte negro e verde. vec = direção. */
    public static final int ANTI_MAGIC = 14;
    /** Marca rúnica no alvo (entity). */
    public static final int RUNE_MARK = 15;
    /** Golpe de Caliburn: lâmina dourada do céu sobre pos. */
    public static final int CALIBURN = 16;

    /** Consumidores do cliente (nulos no servidor dedicado). */
    public static volatile Consumer<Burst> clientBurst;
    /** Rastro desenhado no CLIENTE a cada tick de um projétil/entidade da classe (nenhum pacote). */
    public static volatile Consumer<Entity> clientTrail;

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_knight_fx-1");
        r.playToClient(Burst.TYPE, Burst.STREAM_CODEC, (p, c) -> {
            Consumer<Burst> h = clientBurst;
            if (h != null) h.accept(p);
        });
    }

    /** Efeito para todos num raio de 96 blocos (o cliente corta pela distância do preset). */
    public static void at(Level level, int kind, Vec3 pos, Vec3 vec, float size, int rgb, int entityId, int data) {
        if (!(level instanceof ServerLevel sl)) return;
        PacketDistributor.sendToPlayersNear(sl, null, pos.x, pos.y, pos.z, 96, new Burst(kind, pos, vec, size, rgb, entityId, data));
    }

    public static void at(Level level, int kind, Vec3 pos, Vec3 vec, float size, int rgb, int entityId) {
        at(level, kind, pos, vec, size, rgb, entityId, 0);
    }

    /** Efeito preso a uma entidade (posição = pés). */
    public static void on(Entity e, int kind, Vec3 vec, float size, int rgb, int data) {
        at(e.level(), kind, e.position(), vec, size, rgb, e.getId(), data);
    }

    /** Chamado no tick de CLIENTE de projéteis/entidades: o rastro é local. */
    public static void trail(Entity e) {
        Consumer<Entity> h = clientTrail;
        if (h != null && e.level().isClientSide) h.accept(e);
    }

    /** Efeito curto de anime. */
    public record Burst(int kind, Vec3 pos, Vec3 vec, float size, int rgb, int entity, int data) implements CustomPacketPayload {
        public static final Type<Burst> TYPE = new Type<>(IsekaiMahou.id("class_knight_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Burst> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeByte(p.kind);
            buf.writeDouble(p.pos.x);
            buf.writeDouble(p.pos.y);
            buf.writeDouble(p.pos.z);
            buf.writeFloat((float) p.vec.x);
            buf.writeFloat((float) p.vec.y);
            buf.writeFloat((float) p.vec.z);
            buf.writeFloat(p.size);
            buf.writeMedium(p.rgb & 0xFFFFFF);
            buf.writeVarInt(p.entity + 1);
            buf.writeVarInt(p.data);
        }, buf -> new Burst(buf.readByte(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()), buf.readFloat(), buf.readUnsignedMedium(), buf.readVarInt() - 1,
                buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private KnightFxNet() {}
}
