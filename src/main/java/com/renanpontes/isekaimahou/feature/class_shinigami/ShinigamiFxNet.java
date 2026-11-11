package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede dos EFEITOS visuais do Shinigami (só visual — toda a regra fica no servidor):
 * <ul>
 *   <li>{@link Swarm} — o enxame de pétalas da Senbonzakura: alguns pontos de controle (cabeça do enxame, modo, alvo)
 *   a cada 2 ticks; o cliente desenha centenas de pétalas seguindo esses pontos;</li>
 *   <li>{@link Burst} — um efeito curto (liberação da Shikai/Bankai, Kageyoshi, Shunpo, carga do Cero, Mugetsu,
 *   Máscara...), com posição, vetor, tamanho, cor, entidade e um texto opcional (o comando de liberação).</li>
 * </ul>
 * Os consumidores de cliente são preenchidos pelo módulo de cliente (nulos no servidor dedicado).
 */
public final class ShinigamiFxNet {
    // ---------------- tipos de Burst ----------------
    /** Liberação da Shikai: pressão espiritual (anel + coluna) e o comando escrito no ar. size = raio. */
    public static final int RELEASE = 0;
    /** Liberação da Bankai (ou Vasto Lorde): pressão enorme. size = raio. */
    public static final int BANKAI = 1;
    /** Senbonzakura Kageyoshi: lâminas gigantes afundando no chão em volta. size = raio. */
    public static final int KAGEYOSHI = 2;
    /** Shunpo: imagem residual em pos, linhas de velocidade até vec. entity = quem passou. */
    public static final int SHUNPO = 3;
    /** Carga do Cero na boca/mão de {@code entity} por size ticks. */
    public static final int CERO_CHARGE = 4;
    /** Mugetsu: a tela fica negra e um único corte escuro a atravessa (para quem está perto). */
    public static final int MUGETSU = 5;
    /** A máscara do Vazio se formando no rosto de {@code entity}. */
    public static final int VISORED = 6;
    /** Gōkei: a esfera de pétalas se fecha e explode em pos. size = raio. */
    public static final int GOKEI_HIT = 7;
    /** Cicatriz no chão (rastro do Getsuga). vec = direção, size = largura. */
    public static final int SCAR = 8;
    /** Carga escura na lâmina (Getsuga/Kidō pesado) em {@code entity} por size ticks. */
    public static final int BLADE_CHARGE = 9;

    /** Consumidores do cliente (nulos no servidor dedicado). */
    public static volatile Consumer<Swarm> clientSwarm;
    public static volatile Consumer<Burst> clientBurst;

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_shinigami_fx-1");
        r.playToClient(Swarm.TYPE, Swarm.STREAM_CODEC, (p, c) -> {
            Consumer<Swarm> h = clientSwarm;
            if (h != null) h.accept(p);
        });
        r.playToClient(Burst.TYPE, Burst.STREAM_CODEC, (p, c) -> {
            Consumer<Burst> h = clientBurst;
            if (h != null) h.accept(p);
        });
    }

    // =====================================================================================
    //  Envio
    // =====================================================================================
    /** Burst para quem acompanha a entidade (e ela mesma). */
    public static void burst(Entity e, int kind, Vec3 pos, Vec3 vec, float size, int rgb, @Nullable Component text) {
        if (e.level().isClientSide) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new Burst(kind, pos, vec, size, rgb, e.getId(), text == null ? Component.empty() : text));
    }

    /** Burst para todos num raio (sem entidade dona). */
    public static void burstNear(ServerLevel level, int kind, Vec3 pos, Vec3 vec, float size, int rgb, int entityId, double radius) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius, new Burst(kind, pos, vec, size, rgb, entityId, Component.empty()));
    }

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    /**
     * Estado do enxame de pétalas de {@code owner}: {@code mode} (ver {@link PetalSwarm}), a cabeça/centro, o alvo
     * (id de entidade ou -1), um parâmetro (raio/progresso) e se é da Bankai.
     */
    public record Swarm(int owner, int mode, float x, float y, float z, int target, float param, boolean bankai) implements CustomPacketPayload {
        public static final Type<Swarm> TYPE = new Type<>(IsekaiMahou.id("shinigami_fx_swarm"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Swarm> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.owner);
            buf.writeByte(p.mode);
            buf.writeFloat(p.x);
            buf.writeFloat(p.y);
            buf.writeFloat(p.z);
            buf.writeVarInt(p.target + 1);
            buf.writeFloat(p.param);
            buf.writeBoolean(p.bankai);
        }, buf -> new Swarm(buf.readVarInt(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt() - 1,
                buf.readFloat(), buf.readBoolean()));

        public Vec3 pos() {
            return new Vec3(x, y, z);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Efeito curto. */
    public record Burst(int kind, Vec3 pos, Vec3 vec, float size, int rgb, int entity, Component text) implements CustomPacketPayload {
        public static final Type<Burst> TYPE = new Type<>(IsekaiMahou.id("shinigami_fx_burst"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Burst> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeByte(p.kind);
            buf.writeDouble(p.pos.x);
            buf.writeDouble(p.pos.y);
            buf.writeDouble(p.pos.z);
            buf.writeFloat((float) p.vec.x);
            buf.writeFloat((float) p.vec.y);
            buf.writeFloat((float) p.vec.z);
            buf.writeFloat(p.size);
            buf.writeInt(p.rgb);
            buf.writeVarInt(p.entity + 1);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.text);
        }, buf -> new Burst(buf.readByte(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()), buf.readFloat(), buf.readInt(), buf.readVarInt() - 1,
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private ShinigamiFxNet() {}
}
