package com.renanpontes.isekaimahou.feature.class_rogue;

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
 * (tipo, posição, vetor, tamanho, cor, entidade, dado extra) e o cliente ({@code client/RogueFxClient}) monta a
 * coreografia inteira (carga → impacto → rescaldo) respeitando o preset gráfico. Troca dezenas de partículas enviadas
 * pelo servidor por ~45 bytes.
 */
public final class RogueFxNet {
    /** Some/aparece numa nuvem de sombra (pos = pés). */
    public static final int VANISH = 0;
    /** Passo de sombra: de pos até vec (rastro + imagem residual). */
    public static final int STEP = 1;
    /** Corte pelas costas no alvo (entity). */
    public static final int SLASH = 2;
    /** Crítico no alvo (entity). data 1 = ponto vital (vermelho). */
    public static final int CRIT = 3;
    /** Cartão de visita do Ladrão Fantasma em pos. */
    public static final int CARD = 4;
    /** Garra do Assassino: três riscos cruzando o alvo. vec = direção. */
    public static final int CLAW = 5;
    /** Execução: o coração arrancado (entity = alvo). */
    public static final int HEART = 6;
    /** Velocidade Divina: raio e faíscas no alvo. */
    public static final int GODSPEED = 7;
    /** Véu de Fumaça: cúpula densa em pos. size = raio. */
    public static final int SMOKE_VEIL = 8;
    /** Dreno: orbes viajando de pos até a entity (quem rouba). data = 0 vida, 1 mana, 2 bênção. */
    public static final int DRAIN = 9;
    /** Maldição do Corte Único: um traço vermelho longo no alvo. */
    public static final int ONE_CUT = 10;
    /** Leque de facas: brilho na mão. vec = direção, size = abertura. */
    public static final int KNIFE_FAN = 11;
    /** Imagem residual avançando de pos até vec. */
    public static final int LUNGE = 12;
    /** Marca do ponto vital sobre o alvo (entity). */
    public static final int VITAL_MARK = 13;
    /** Técnica/grau novo (aura roxa). */
    public static final int LEARN = 14;

    /** Consumidores do cliente (nulos no servidor dedicado). */
    public static volatile Consumer<Burst> clientBurst;
    /** Rastro desenhado no CLIENTE a cada tick de um projétil/entidade da classe (nenhum pacote). */
    public static volatile Consumer<Entity> clientTrail;

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_rogue_fx-1");
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
        public static final Type<Burst> TYPE = new Type<>(IsekaiMahou.id("class_rogue_fx"));
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

    private RogueFxNet() {}
}
