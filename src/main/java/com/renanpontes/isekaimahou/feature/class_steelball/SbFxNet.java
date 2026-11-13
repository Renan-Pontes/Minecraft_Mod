package com.renanpontes.isekaimahou.feature.class_steelball;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede dos EFEITOS de anime do Steel Ball (só visual — a regra fica no servidor). O servidor manda um {@link Burst}
 * curto (tipo, posição, vetor, tamanho, cor, entidade) e o cliente ({@code client/SbFxClient}) monta a coreografia
 * inteira (carga → impacto → rescaldo) respeitando o preset gráfico. Pacote pequeno: troca centenas de partículas
 * enviadas pelo servidor por ~40 bytes.
 */
public final class SbFxNet {
    // ---------------- tipos ----------------
    /** Bola de aço lançada: anéis dourados girando na mão + estalo. vec = direção, size = 1 normal / 2 dourada. */
    public static final int BALL_THROW = 0;
    /** Bola acerta: espiral de impacto, faíscas de aço e anel de choque. vec = direção, size = força (1..3). */
    public static final int BALL_HIT = 1;
    /** Unha giratória disparada (Ato 1/2/3 conforme size): estrela da unha na ponta do dedo. */
    public static final int NAIL_FIRE = 2;
    /** Unha acerta: furo em espiral. size = ato. */
    public static final int NAIL_HIT = 3;
    /** Ato 2: o buraco em espiral marcado no alvo (entidade). */
    public static final int CHASE_MARK = 4;
    /** Ato 3: atira em si mesmo e some no buraco (pos) → sai em vec. */
    public static final int WORMHOLE = 5;
    /** Ball Breaker: carga da esfera espectral gigante sobre a mão. size = raio. */
    public static final int BREAKER_CHARGE = 6;
    /** Ball Breaker: a esfera espectral se choca contra o alvo. size = raio. */
    public static final int BREAKER_HIT = 7;
    /** Love Train: a cortina de luz dourada desce em volta de pos. size = raio. */
    public static final int LOVE_TRAIN = 8;
    /** Rotação Infinita (Ato 4): espiral dourada fechando no alvo. size = raio. */
    public static final int INFINITE = 9;
    /** Turbilhão giratório: anéis horizontais crescendo. size = raio. */
    public static final int VORTEX = 10;
    /** Retângulo Dourado (rotação perfeita) desenhado de frente para vec. size = tamanho. */
    public static final int GOLDEN_RECT = 11;
    /** Cura por rotação: espiral verde subindo pelo corpo. */
    public static final int SPIN_HEAL = 12;
    /** Bola escâner: pulso de radar. size = raio. */
    public static final int SCAN = 13;
    /** Galope dourado: rastro de ferraduras de luz. vec = direção. */
    public static final int GALLOP = 14;
    /** Rotação aprendida/técnica nova: espiral + retângulo dourado. */
    public static final int LEARN = 15;
    /** Espiral subindo em volta de pos (giro pelo corpo). size = raio, vec.y = altura, data na cor. */
    public static final int SPIRAL = 16;
    /** Anel horizontal de rotação em pos. size = raio. */
    public static final int RING = 17;

    /** Consumidores do cliente (nulos no servidor dedicado). */
    public static volatile Consumer<Burst> clientBurst;
    /** Rastro de voo desenhado no CLIENTE a cada tick do projétil (bola, unhas) — troca o pó enviado pelo servidor. */
    public static volatile Consumer<Entity> clientTrail;

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_steelball_fx-1");
        r.playToClient(Burst.TYPE, Burst.STREAM_CODEC, (p, c) -> {
            Consumer<Burst> h = clientBurst;
            if (h != null) h.accept(p);
        });
    }

    /** Efeito para quem acompanha a entidade (e ela mesma). */
    public static void burst(Entity e, int kind, Vec3 pos, Vec3 vec, float size, int rgb) {
        if (e.level().isClientSide) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new Burst(kind, pos, vec, size, rgb, e.getId()));
    }

    /** Efeito para todos num raio de 96 blocos (o cliente corta pela distância do preset). */
    public static void at(ServerLevel level, int kind, Vec3 pos, Vec3 vec, float size, int rgb, int entityId) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, 96, new Burst(kind, pos, vec, size, rgb, entityId));
    }

    /** Chamado no tick de CLIENTE dos projéteis: o rastro é local (nenhum pacote). */
    public static void trail(Entity e) {
        Consumer<Entity> h = clientTrail;
        if (h != null && e.level().isClientSide) h.accept(e);
    }

    /** Efeito curto de anime. */
    public record Burst(int kind, Vec3 pos, Vec3 vec, float size, int rgb, int entity) implements CustomPacketPayload {
        public static final Type<Burst> TYPE = new Type<>(IsekaiMahou.id("class_steelball_fx"));
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
        }, buf -> new Burst(buf.readByte(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()), buf.readFloat(), buf.readUnsignedMedium(), buf.readVarInt() - 1));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private SbFxNet() {}
}
