package com.renanpontes.isekaimahou.feature.class_enchanter;

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
 * (tipo, posição, vetor, tamanho, cor, entidade, dado extra) e o cliente ({@code client/EnchFxClient}) monta a
 * coreografia inteira (carga → impacto → rescaldo) respeitando o preset gráfico. Troca dezenas de partículas enviadas
 * pelo servidor por ~45 bytes.
 */
public final class EnchFxNet {
    /** Anel horizontal de runas. size = raio. */
    public static final int RING = 0;
    /** Círculo de inscrição (anéis + hexagrama + runas). size = raio, data = cor de destaque. */
    public static final int SIGIL = 1;
    /** Encantamento aplicado: espiral de runas subindo em entity. data = cor de destaque. */
    public static final int SPIRAL = 2;
    /** Elo de Encantamento: circuito de luz de pos até vec. */
    public static final int BEAM = 3;
    /** Anti-Magia: a magia bate no hexágono e se estilhaça. size = raio. */
    public static final int SHATTER = 4;
    /** Portão aberto (entity). data = número do portão. */
    public static final int GATE = 5;
    /** 8º portão: a pressão do corpo explode em volta (entity). */
    public static final int DEATH_GATE = 6;
    /** Kaioken: aura vermelha explodindo (entity). data = nível. */
    public static final int KAIOKEN = 7;
    /** Restrição Celestial: explosão de pressão sem energia (entity). */
    public static final int HEAVENLY = 8;
    /** Circuitos de runas acesos na manopla (entity) por 1 s. */
    public static final int IMBUE_GLOW = 9;
    /** Golpe imbuído do elemento em pos. */
    public static final int IMBUE_HIT = 10;
    /** Rito do Encantador concluído (pos). */
    public static final int RITE_DONE = 11;
    /** Santuário Soberano se abre (pos). size = raio. */
    public static final int SANCTUM_OPEN = 12;

    /** Consumidores do cliente (nulos no servidor dedicado). */
    public static volatile Consumer<Burst> clientBurst;
    /** Rastro desenhado no CLIENTE a cada tick de um projétil/entidade da classe (nenhum pacote). */
    public static volatile Consumer<Entity> clientTrail;

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_enchanter_fx-1");
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
        public static final Type<Burst> TYPE = new Type<>(IsekaiMahou.id("class_enchanter_fx"));
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

    private EnchFxNet() {}
}
