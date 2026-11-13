package com.renanpontes.isekaimahou.feature.class_gambler;

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
 * (tipo, posição, vetor, tamanho, cor, entidade, dado extra) e o cliente ({@code client/GbFxClient}) monta a
 * coreografia inteira (carga → impacto → rescaldo) respeitando o preset gráfico. Troca dezenas de partículas enviadas
 * pelo servidor por ~45 bytes.
 */
public final class GbFxNet {
    /** Rolagem de sorte: anel de naipes sobre a cabeça (entity). data = faixa (ordinal de LuckBand). */
    public static final int ROLL = 0;
    /** Moedas e fichas saltando em pos. size = quantidade. */
    public static final int COINS = 1;
    /** JACKPOT! Explosão de fichas, fogos e caça-níquel dourado em entity. */
    public static final int JACKPOT = 2;
    /** Aura do Jackpot (a cada 2 ticks) em entity. data = idade. */
    public static final int JACKPOT_AURA = 3;
    /** Aura do Jackpot Imortal (a cada 3 ticks) em entity. data = idade. */
    public static final int IMMORTAL_AURA = 4;
    /** Estouro cinético da carta em pos. */
    public static final int CARD_BURST = 5;
    /** Anel de mesa no chão. size = raio. */
    public static final int GROUND_RING = 6;
    /** Explosão de área na cor. size = raio. */
    public static final int BLAST = 7;
    /** Leque de cartas saindo da mão. vec = direção. */
    public static final int CARD_FAN = 8;
    /** Um rolo do caça-níquel para em pos. data = símbolo (0..). */
    public static final int SLOT_REEL = 9;
    /** Caça-níquel some em fichas (pos). */
    public static final int SLOT_END = 10;
    /** All-In: o fio de ouro entre os dois (pos até vec). */
    public static final int ALL_IN = 11;
    /** Dados no 12: raio de luz no alvo (pos). */
    public static final int DICE_TWELVE = 12;
    /** Olhos de cobra (2): fumaça escura no alvo (pos). */
    public static final int DICE_SNAKE = 13;
    /** Chiclete estoura (pos). */
    public static final int GUM_SNAP = 14;
    /** Domínio da Morte Ociosa: JACKPOT do domínio (pos). size = raio. */
    public static final int IDLE_WIN = 15;
    /** Roleta girando: faixa de fichas em volta. size = raio, data = ticks. */
    public static final int SPIN = 16;
    /** Moeda do destino: cara (data 1) ou coroa (0) sobre pos. */
    public static final int FATE_COIN = 17;

    /** Consumidores do cliente (nulos no servidor dedicado). */
    public static volatile Consumer<Burst> clientBurst;
    /** Rastro desenhado no CLIENTE a cada tick de um projétil/entidade da classe (nenhum pacote). */
    public static volatile Consumer<Entity> clientTrail;

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_gambler_fx-1");
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
        public static final Type<Burst> TYPE = new Type<>(IsekaiMahou.id("class_gambler_fx"));
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

    private GbFxNet() {}
}
