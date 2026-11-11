package com.renanpontes.isekaimahou.feature.origins_a;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do módulo. */
public final class OaNetwork {

    /**
     * Servidor → cliente: forma atual de um jogador (0 = humano) e o elemento da Metamorfose.
     * Efeitos de status não são enviados para quem só está vendo o jogador, por isso a forma vai por aqui.
     */
    public record FormSync(int entityId, int shape, int element) implements CustomPacketPayload {
        public static final Type<FormSync> TYPE = new Type<>(IsekaiMahou.id("origins_a_form"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FormSync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FormSync::entityId,
                ByteBufCodecs.VAR_INT, FormSync::shape,
                ByteBufCodecs.VAR_INT, FormSync::element,
                FormSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("origins_a-1");
        // O handler chama a classe de cliente só dentro do lambda (servidor dedicado nunca executa)
        r.playToClient(FormSync.TYPE, FormSync.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.feature.origins_a.client.ShapeClient.apply(p.entityId(), p.shape(), p.element()));
    }

    private OaNetwork() {}
}
