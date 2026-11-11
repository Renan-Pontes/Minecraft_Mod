package com.renanpontes.isekaimahou.feature.class_quincy;

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
 * Rede dos EFEITOS visuais do Quincy (só visual):
 * <ul>
 *   <li>{@link Form} — quem está na Letzt Stil, no Vollständig ou com O Todo-Poderoso (asas, auréola, olhos), reenviado
 *   a cada 2 s enquanto dura;</li>
 *   <li>{@link Burst} — efeitos curtos: o arco gigante do Heilig Bogen, os pilares do Sankt Zwinger, a estrela da
 *   Chuva de Luz, a troca da Antítese e os olhos se abrindo.</li>
 * </ul>
 */
public final class QuincyFxNet {
    // ---------------- formas ----------------
    public static final int FORM_NONE = 0;
    public static final int FORM_LETZT = 1;
    public static final int FORM_VOLL = 2;
    public static final int FORM_ALMIGHTY = 3;

    // ---------------- bursts ----------------
    /** Heilig Bogen: o arco gigante de luz em pos, apontando para vec (direção). */
    public static final int BOGEN = 0;
    /** Sankt Zwinger: cinco pilares de luz caindo do céu nas pontas da estrela. size = raio. */
    public static final int ZWINGER = 1;
    /** Estrela Quincy (Quincy Zeichen) no céu (Chuva de Luz). size = raio. */
    public static final int STAR = 2;
    /** Antítese: a troca entre pos e vec (os dois pontos). */
    public static final int ANTITHESIS = 3;
    /** O Todo-Poderoso abre os olhos (em {@code entity}). */
    public static final int EYES = 4;
    /** Explosão de transformação (Letzt Stil / Vollständig): coluna e estrela no chão. size = raio. */
    public static final int ASCEND = 5;

    public static volatile Consumer<Form> clientForm;
    public static volatile Consumer<Burst> clientBurst;

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_quincy_fx-1");
        r.playToClient(Form.TYPE, Form.STREAM_CODEC, (p, c) -> {
            Consumer<Form> h = clientForm;
            if (h != null) h.accept(p);
        });
        r.playToClient(Burst.TYPE, Burst.STREAM_CODEC, (p, c) -> {
            Consumer<Burst> h = clientBurst;
            if (h != null) h.accept(p);
        });
    }

    public static void form(Entity e, int kind, int ticks) {
        if (e.level().isClientSide) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new Form(e.getId(), kind, ticks));
    }

    public static void burst(Entity e, int kind, Vec3 pos, Vec3 vec, float size, int rgb) {
        if (e.level().isClientSide) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new Burst(kind, pos, vec, size, rgb, e.getId()));
    }

    public static void burstNear(ServerLevel level, int kind, Vec3 pos, Vec3 vec, float size, int rgb, int entity) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, 96, new Burst(kind, pos, vec, size, rgb, entity));
    }

    public record Form(int entity, int kind, int ticks) implements CustomPacketPayload {
        public static final Type<Form> TYPE = new Type<>(IsekaiMahou.id("quincy_fx_form"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Form> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.entity);
            buf.writeByte(p.kind);
            buf.writeVarInt(Math.max(0, p.ticks));
        }, buf -> new Form(buf.readVarInt(), buf.readByte(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Burst(int kind, Vec3 pos, Vec3 vec, float size, int rgb, int entity) implements CustomPacketPayload {
        public static final Type<Burst> TYPE = new Type<>(IsekaiMahou.id("quincy_fx_burst"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Burst> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeByte(p.kind);
            buf.writeDouble(p.pos.x);
            buf.writeDouble(p.pos.y);
            buf.writeDouble(p.pos.z);
            buf.writeDouble(p.vec.x);
            buf.writeDouble(p.vec.y);
            buf.writeDouble(p.vec.z);
            buf.writeFloat(p.size);
            buf.writeInt(p.rgb);
            buf.writeVarInt(p.entity + 1);
        }, buf -> new Burst(buf.readByte(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readInt(), buf.readVarInt() - 1));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private QuincyFxNet() {}
}
