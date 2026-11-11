package com.renanpontes.isekaimahou.feature.ascension;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do módulo Ascensão. Os tributos vêm de arquivos de dados do SERVIDOR; o cliente recebe o texto deles
 * (no login, no /reload e a cada Círculo novo) para o rastreador de objetivos mostrar o que falta. v0.9: o servidor
 * também avisa qual é o altar PÚBLICO mais próximo ({@link AscensionAltarFinder}) para a setinha do rastreador.
 */
public final class AscensionNetwork {

    /** Servidor → cliente: Círculo-alvo → JSON do tributo. */
    public record TributesSync(Map<Integer, String> tributes) implements CustomPacketPayload {
        public static final Type<TributesSync> TYPE = new Type<>(IsekaiMahou.id("ascension_tributes"));
        public static final StreamCodec<ByteBuf, TributesSync> STREAM_CODEC =
                ByteBufCodecs.<ByteBuf, Integer, String, Map<Integer, String>>map(HashMap::new, ByteBufCodecs.VAR_INT,
                        ByteBufCodecs.stringUtf8(16384), 32).map(TributesSync::new, TributesSync::tributes);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Servidor → cliente: altar público mais próximo ({@code present=false} = nenhum / jogador fora do limite do
     * Círculo). {@code exact=false}: ponto de uma estrutura com altar (cidade/santuário), ainda não carregada.
     */
    public record AltarHint(boolean present, String dim, BlockPos pos, Component name, boolean exact) implements CustomPacketPayload {
        public static final Type<AltarHint> TYPE = new Type<>(IsekaiMahou.id("ascension_altar_hint"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AltarHint> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, AltarHint::present,
                ByteBufCodecs.stringUtf8(256), AltarHint::dim,
                BlockPos.STREAM_CODEC, AltarHint::pos,
                ComponentSerialization.TRUSTED_STREAM_CODEC, AltarHint::name,
                ByteBufCodecs.BOOL, AltarHint::exact,
                AltarHint::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("ascension-2");
        // Só dados: o cliente monta a tabela em código comum (nenhuma classe de cliente aqui).
        r.playToClient(TributesSync.TYPE, TributesSync.STREAM_CODEC, (p, ctx) -> AscensionTributes.acceptSync(p.tributes()));
        r.playToClient(AltarHint.TYPE, AltarHint.STREAM_CODEC, (p, ctx) -> AscensionTracking.acceptAltar(p));
    }

    public static void sendTributes(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new TributesSync(AscensionTributes.rawForSync()));
    }

    static void sendAltar(ServerPlayer player, @Nullable AscensionAltarFinder.Hint hint) {
        AltarHint packet = hint == null
                ? new AltarHint(false, "", BlockPos.ZERO, Component.empty(), false)
                : new AltarHint(true, hint.dim().location().toString(), hint.pos(), hint.name(), hint.exact());
        PacketDistributor.sendToPlayer(player, packet);
    }

    private AscensionNetwork() {}
}
