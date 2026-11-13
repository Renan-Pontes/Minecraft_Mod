package com.renanpontes.isekaimahou.feature.city_arcana;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede dos efeitos visuais dos prédios mágicos (só visual — a regra fica no servidor). O servidor manda um {@link Fx}
 * curto e o cliente ({@code client/CityArcanaClient}) desenha respeitando o preset gráfico ({@code FxQuality}).
 */
public final class CityArcanaNet {
    /** Barreira bloqueou algo: placa hexagonal brilhando no "domo". pos = ponto, vec = normal (para fora), size = raio. */
    public static final int WARD_HIT = 0;
    /** Obelisco anulou uma magia: runas implodem no conjurador. pos = peito, size = raio. */
    public static final int NULLIFY = 1;
    /** Barreira dá um choque: raio do cristal (pos) até o alvo (vec). */
    public static final int ZAP = 2;
    /** Farol revelou criaturas: anel de luz se abrindo. pos = olho, size = raio. */
    public static final int REVEAL = 3;
    /** Domo inteiro piscando (comando de teste / governante mudou algo). pos = centro, size = raio. */
    public static final int DOME = 4;
    /** Pulso do Obelisco (modo trocado). pos = topo, size = altura. */
    public static final int OBELISK_PULSE = 5;
    /** Torre de Mana: fagulhas de mana subindo no jogador. pos = pés. */
    public static final int MANA = 6;

    /** Cliente (nulo no servidor dedicado). */
    public static volatile Consumer<Fx> client;

    public record Fx(int kind, double x, double y, double z, float vx, float vy, float vz, float size, int rgb)
            implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(IsekaiMahou.id("city_arcana_fx"));
        public static final StreamCodec<FriendlyByteBuf, Fx> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeByte(p.kind);
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
            buf.writeFloat(p.vx);
            buf.writeFloat(p.vy);
            buf.writeFloat(p.vz);
            buf.writeFloat(p.size);
            buf.writeInt(p.rgb);
        }, buf -> new Fx(buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public Vec3 pos() {
            return new Vec3(x, y, z);
        }

        public Vec3 vec() {
            return new Vec3(vx, vy, vz);
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("city_arcana-1");
        r.playToClient(Fx.TYPE, Fx.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            Consumer<Fx> h = client;
            if (h != null) h.accept(p);
        }));
    }

    /** Efeito para quem está a até 96 blocos (o cliente ainda corta pela distância do preset). */
    public static void send(ServerLevel sl, int kind, Vec3 pos, Vec3 vec, float size, int rgb) {
        PacketDistributor.sendToPlayersNear(sl, null, pos.x, pos.y, pos.z, 96,
                new Fx(kind, pos.x, pos.y, pos.z, (float) vec.x, (float) vec.y, (float) vec.z, size, rgb));
    }

    private CityArcanaNet() {}
}
