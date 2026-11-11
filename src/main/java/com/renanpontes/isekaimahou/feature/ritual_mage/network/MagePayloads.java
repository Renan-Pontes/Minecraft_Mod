package com.renanpontes.isekaimahou.feature.ritual_mage.network;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_mage.MageRegistries;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.MageResearchData;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.PhenomenaManager;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do Ritual do Mago (servidor -> cliente). As ações da Mesa de Pesquisa usam o clique de botão
 * do próprio menu (ServerboundContainerButtonClickPacket), então não há pacote cliente -> servidor aqui.
 */
public final class MagePayloads {

    /** Definição de um fenômeno para o Atlas (sem as condições, que só o servidor usa). */
    public record PhenomenonInfo(ResourceLocation id, String category, List<String> elements, int rarity, List<ResourceLocation> icons) {
        public static final StreamCodec<ByteBuf, PhenomenonInfo> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, PhenomenonInfo::id,
                ByteBufCodecs.STRING_UTF8, PhenomenonInfo::category,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PhenomenonInfo::elements,
                ByteBufCodecs.VAR_INT, PhenomenonInfo::rarity,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), PhenomenonInfo::icons,
                PhenomenonInfo::new);
    }

    /** Lista de fenômenos disponíveis (enviada ao entrar e a cada /reload). */
    public record PhenomenaSync(List<PhenomenonInfo> list) implements CustomPacketPayload {
        public static final Type<PhenomenaSync> TYPE = new Type<>(IsekaiMahou.id("ritual_mage_phenomena"));
        public static final StreamCodec<ByteBuf, PhenomenaSync> STREAM_CODEC =
                PhenomenonInfo.STREAM_CODEC.apply(ByteBufCodecs.list()).map(PhenomenaSync::new, PhenomenaSync::list);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Atlas do jogador: fenômenos descobertos e teses concluídas. */
    public record AtlasSync(List<String> discovered, List<String> theses) implements CustomPacketPayload {
        public static final Type<AtlasSync> TYPE = new Type<>(IsekaiMahou.id("ritual_mage_atlas"));
        public static final StreamCodec<ByteBuf, AtlasSync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AtlasSync::discovered,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AtlasSync::theses,
                AtlasSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("ritual_mage-1");
        // Os handlers só guardam dados numa classe comum (sem imports de cliente): seguro no servidor dedicado.
        r.playToClient(PhenomenaSync.TYPE, PhenomenaSync.STREAM_CODEC, (p, c) -> ClientResearch.applyPhenomena(p));
        r.playToClient(AtlasSync.TYPE, AtlasSync.STREAM_CODEC, (p, c) -> ClientResearch.applyAtlas(p));
    }

    // ---------------------------------------------------------------- Envio
    public static void syncAtlas(ServerPlayer player) {
        MageResearchData d = player.getData(MageRegistries.RESEARCH);
        PacketDistributor.sendToPlayer(player, new AtlasSync(new ArrayList<>(d.discovered()), new ArrayList<>(d.theses())));
    }

    public static void syncPhenomena(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, PhenomenaManager.syncPayload(player.server));
    }

    private MagePayloads() {}
}
