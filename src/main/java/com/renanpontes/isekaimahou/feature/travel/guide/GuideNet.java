package com.renanpontes.isekaimahou.feature.travel.guide;

import java.util.List;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do Guia de Portais e dos marcadores do mapa (v1.0). Os handlers de cliente só chamam os ganchos abaixo, que a
 * parte de cliente preenche — nada de classe de cliente no servidor dedicado.
 */
public final class GuideNet {
    /** Resposta do rastreio: {@link PortalTarget#status}. */
    public static final int FOUND = 0, NONE = 1, WRONG_DIM = 2, COOLDOWN = 3, CLEARED = 4;

    public static Consumer<PortalTarget> onTarget = p -> {};
    public static Consumer<Markers> onMarkers = p -> {};

    /** Cliente → servidor: rastrear o portal mais próximo da dimensão {@code id} ("" = parar de rastrear). */
    public record TrackPortal(String id) implements CustomPacketPayload {
        public static final Type<TrackPortal> TYPE = new Type<>(IsekaiMahou.id("travel_guide_track"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TrackPortal> STREAM_CODEC =
                ByteBufCodecs.STRING_UTF8.<RegistryFriendlyByteBuf>cast().map(TrackPortal::new, TrackPortal::id);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Servidor → cliente: resultado do rastreio.
     *
     * @param exact  {@code true} = estrutura já carregada (posição e altura certas); {@code false} = ponto do mapa
     *               (altura desconhecida: a seta usa a altura do jogador)
     * @param reach  até onde a busca foi (blocos), para o aviso de "nenhum por perto"
     */
    public record PortalTarget(String id, int status, ResourceLocation dim, BlockPos pos, boolean exact, int reach)
            implements CustomPacketPayload {
        public static final Type<PortalTarget> TYPE = new Type<>(IsekaiMahou.id("travel_guide_target"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PortalTarget> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PortalTarget::id, ByteBufCodecs.VAR_INT, PortalTarget::status,
                ResourceLocation.STREAM_CODEC, PortalTarget::dim, BlockPos.STREAM_CODEC, PortalTarget::pos,
                ByteBufCodecs.BOOL, PortalTarget::exact, ByteBufCodecs.VAR_INT, PortalTarget::reach, PortalTarget::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Um ponto do mapa: {@code key} único, nome já pronto, categoria (cor/símbolo no cliente) e posição. */
    public record Marker(String key, Component name, String category, BlockPos pos) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Marker> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Marker::key, ComponentSerialization.TRUSTED_STREAM_CODEC, Marker::name,
                ByteBufCodecs.STRING_UTF8, Marker::category, BlockPos.STREAM_CODEC, Marker::pos, Marker::new);
    }

    /**
     * Servidor → cliente: TODOS os pontos importantes que o jogador já descobriu na dimensão {@code dim} (o cliente
     * troca a lista inteira no minimapa). {@code newKey} = ponto recém-descoberto ("" = só sincronizando).
     */
    public record Markers(ResourceLocation dim, List<Marker> list, String newKey) implements CustomPacketPayload {
        public static final Type<Markers> TYPE = new Type<>(IsekaiMahou.id("travel_map_markers"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Markers> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Markers::dim,
                Marker.STREAM_CODEC.apply(ByteBufCodecs.list()), Markers::list,
                ByteBufCodecs.STRING_UTF8, Markers::newKey, Markers::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("travel_guide-1");
        r.playToServer(TrackPortal.TYPE, TrackPortal.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) GuideService.track(sp, p.id());
        });
        r.playToClient(PortalTarget.TYPE, PortalTarget.STREAM_CODEC, (p, c) -> onTarget.accept(p));
        r.playToClient(Markers.TYPE, Markers.STREAM_CODEC, (p, c) -> onMarkers.accept(p));
    }

    private GuideNet() {}
}
