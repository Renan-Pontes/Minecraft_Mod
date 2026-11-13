package com.renanpontes.isekaimahou.feature.class_smith;

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
 * (tipo, posição, vetor, tamanho, cor, entidade, dado extra) e o cliente ({@code client/SmithFxClient}) monta a
 * coreografia inteira (carga → impacto → rescaldo) respeitando o preset gráfico. Troca dezenas de partículas enviadas
 * pelo servidor por ~45 bytes.
 */
public final class SmithFxNet {
    /** Fagulhas de martelada em pos. size = quantidade base. */
    public static final int SPARKS = 0;
    /** Terra que pula do chão em pos. size = força. */
    public static final int GROUND_BURST = 1;
    /** Martelada no chão (início do Terremoto da Forja): anel de choque da bigorna. vec = direção. */
    public static final int HAMMER_SLAM = 2;
    /** Um passo da onda do Terremoto: erupção de brasas e terra em pos. */
    public static final int QUAKE = 3;
    /** Martelo Estelar caindo: cratera. size = raio, data 1 = último golpe. */
    public static final int STAR_IMPACT = 4;
    /** Mundo de Lâminas: o cântico — anel de fogo correndo do centro até a borda. size = raio, data = duração (ticks). */
    public static final int WORLD_ARIA = 5;
    /** Mundo de Lâminas: o mundo se abre (clarão, fogo, espadas). size = raio. */
    public static final int WORLD_OPEN = 6;
    /** Mundo de Lâminas: a borda de fogo (a cada 4 ticks). size = raio. */
    public static final int WORLD_BORDER = 7;
    /** Mundo de Lâminas: se fecha em fumaça. size = raio. */
    public static final int WORLD_CLOSE = 8;
    /** Escudo de 7 pétalas se abre em pos. vec = frente. */
    public static final int RHO_AIAS_UP = 9;
    /** Uma pétala segura um projétil em pos. */
    public static final int RHO_AIAS_BLOCK = 10;
    /** Uma pétala quebra (pos). data = pétalas restantes. */
    public static final int RHO_AIAS_BREAK = 11;
    /** Lâmina projetada acerta em pos. vec = direção. */
    public static final int BLADE_HIT = 12;
    /** Lâmina explode (Fantasma Quebrado). size = raio. */
    public static final int BROKEN_PHANTASM = 13;
    /** Caladbolg: a flecha-espada sai torcendo o espaço. pos = olho, vec = mira. */
    public static final int CALADBOLG = 14;
    /** Espada efêmera se formando sobre pos. */
    public static final int EPHEMERAL = 15;
    /** Espada efêmera estilhaça e explode em pos. size = raio. */
    public static final int EPHEMERAL_BLAST = 16;
    /** A lâmina volta para a mão (pos). */
    public static final int BLADE_CAUGHT = 17;
    /** Grau/forja nova: brasas e anel da bigorna. */
    public static final int LEARN = 18;

    /** Consumidores do cliente (nulos no servidor dedicado). */
    public static volatile Consumer<Burst> clientBurst;
    /** Rastro desenhado no CLIENTE a cada tick de um projétil/entidade da classe (nenhum pacote). */
    public static volatile Consumer<Entity> clientTrail;

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_smith_fx-1");
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
        public static final Type<Burst> TYPE = new Type<>(IsekaiMahou.id("class_smith_fx"));
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

    private SmithFxNet() {}
}
