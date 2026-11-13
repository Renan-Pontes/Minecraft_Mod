package com.renanpontes.isekaimahou.feature.class_gunslinger;

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
 * (tipo, posição, vetor, tamanho, cor, entidade, dado extra) e o cliente ({@code client/GsFxClient}) monta a
 * coreografia inteira (carga → impacto → rescaldo) respeitando o preset gráfico. Troca dezenas de partículas enviadas
 * pelo servidor por ~45 bytes.
 */
public final class GsFxNet {
    /** Clarão do cano. pos = cano, vec = direção, size = escala, data = tipo de arma (ordinal). */
    public static final int MUZZLE = 0;
    /** Traçante. pos = cano, vec = ponto final, size = largura. */
    public static final int TRACER = 1;
    /** Acerto da bala. vec = direção, size = escala, data 1 = acertou criatura. */
    public static final int IMPACT = 2;
    /** Corte da Espada de Fótons. pos = olhos, vec = mira, size = alcance, data = inclinação em graus (+360). */
    public static final int PHOTON_SLASH = 3;
    /** Corte de fótons acertando o alvo (entity). */
    public static final int PHOTON_HIT = 4;
    /** Camuflagem óptica: o manto fecha dos pés à cabeça em entity. */
    public static final int CAMO = 5;
    /** Estrela Negra: o sinal da cruz vermelho diante do peito (pos). */
    public static final int BLACK_STAR_CHARGE = 6;
    /** Estrela Negra: tiro negro-rubro de pos até vec; entity = alvo (a estrela de 5 pontas). */
    public static final int BLACK_STAR = 7;
    /** Fuzil antimaterial: estrondo, onda no cano e rastro grosso de pos até vec. */
    public static final int ANTI_MATERIEL = 8;
    /** Explosão Estelar: aura de duas cores em entity. */
    public static final int STARBURST_START = 9;
    /** Explosão Estelar: um golpe (data = índice; par = corte, ímpar = tiro) em pos, vec = direção. */
    public static final int STARBURST_HIT = 10;
    /** Explosão Estelar: o golpe final em pos. */
    public static final int STARBURST_FINISH = 11;
    /** Meio-Dia: uma badalada (data = número) em volta de entity. */
    public static final int HIGH_NOON_TOLL = 12;
    /** Meio-Dia: a mira dourada sobre o alvo (entity). */
    public static final int HIGH_NOON_MARK = 13;
    /** Arma Espiritual: carga e disparo no dedo. pos = dedo, vec = direção, size = escala. */
    public static final int SPIRIT_GUN = 14;
    /** Explosão da bala explosiva. size = raio. */
    public static final int EXPLOSION = 15;
    /** Tiro carregado: onda de choque no cano. vec = direção. */
    public static final int CHARGED_SHOT = 16;
    /** Técnica nova aprendida (aura dourada). */
    public static final int LEARN = 17;
    /** Teleporte rápido (Explosão Estelar): de pos até vec. */
    public static final int BLINK = 18;

    /** Consumidores do cliente (nulos no servidor dedicado). */
    public static volatile Consumer<Burst> clientBurst;
    /** Rastro desenhado no CLIENTE a cada tick de um projétil/entidade da classe (nenhum pacote). */
    public static volatile Consumer<Entity> clientTrail;

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_gunslinger_fx-1");
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
        public static final Type<Burst> TYPE = new Type<>(IsekaiMahou.id("class_gunslinger_fx"));
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

    private GsFxNet() {}
}
