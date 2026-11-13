package com.renanpontes.isekaimahou.feature.mana_core;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do módulo mana_core. O handler só mexe num campo estático comum (seguro no servidor dedicado). */
public final class ManaCoreNet {

    /** Sobrecarga do jogador (servidor → ele mesmo), para a barra de mana do HUD. */
    public record OverchargeSync(float amount) implements CustomPacketPayload {
        public static final Type<OverchargeSync> TYPE = new Type<>(IsekaiMahou.id("mana_overcharge"));
        public static final StreamCodec<ByteBuf, OverchargeSync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, OverchargeSync::amount, OverchargeSync::new);

        @Override
        public Type<OverchargeSync> type() {
            return TYPE;
        }
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("mana_core-1");
            r.playToClient(OverchargeSync.TYPE, OverchargeSync.STREAM_CODEC,
                    (p, c) -> c.enqueueWork(() -> Overcharge.clientAmount = p.amount()));
        });
    }

    private ManaCoreNet() {}
}
