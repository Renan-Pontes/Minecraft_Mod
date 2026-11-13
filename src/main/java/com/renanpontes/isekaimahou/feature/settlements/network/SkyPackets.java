package com.renanpontes.isekaimahou.feature.settlements.network;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Onda 7 (sky_ui) — pacote da Ilha Celeste para o CLIENTE: de 5 em 5 segundos, quem está perto de uma ilha recebe onde
 * ela fica (coração, raio da terra, estilo) e as cachoeiras — é o que a névoa de nuvem da borda precisa
 * ({@code client/SkyFx}). Registrado junto com os outros pacotes do módulo ({@link SettlementsNetwork#register}).
 */
public final class SkyPackets {
    /** Cliente: uma Ilha Celeste por perto (trocado pela parte de cliente; servidor dedicado nunca toca nisto). */
    public static Consumer<CompoundTag> onNear = t -> {};

    /** {@code Dim}, {@code X/Y/Z} (coração), {@code R} (raio da terra pronta), {@code Style}, {@code Falls} (5 números por cachoeira). */
    public record Near(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Near> TYPE = new Type<>(IsekaiMahou.id("settlements_sky_near"));
        public static final StreamCodec<FriendlyByteBuf, Near> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, Near::data, Near::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar r) {
        r.playToClient(Near.TYPE, Near.CODEC, (p, ctx) -> ctx.enqueueWork(() -> onNear.accept(p.data())));
    }

    private SkyPackets() {}
}
