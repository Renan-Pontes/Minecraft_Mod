package com.renanpontes.isekaimahou.feature.transform_laws;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Rede do módulo: a aba Votos marca/desmarca uma lei para a Transformação Racial. */
public final class TlawNetwork {
    /**
     * Cliente → servidor: {@code slot} = 0 forma racial / 1 Resurrección (Hollow); {@code law} = id (path) da lei;
     * {@code on} = escolher ou tirar.
     */
    public record SetRacePick(int slot, String law, boolean on) implements CustomPacketPayload {
        public static final Type<SetRacePick> TYPE = new Type<>(IsekaiMahou.id("tlaw_race_pick"));
        public static final StreamCodec<ByteBuf, SetRacePick> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetRacePick::slot, ByteBufCodecs.STRING_UTF8, SetRacePick::law, ByteBufCodecs.BOOL,
                SetRacePick::on, SetRacePick::new);

        @Override
        public Type<SetRacePick> type() { return TYPE; }
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("1");
            r.playToServer(SetRacePick.TYPE, SetRacePick.STREAM_CODEC, (p, c) -> {
                if (c.player() instanceof ServerPlayer sp && p.law().length() < 64) {
                    c.enqueueWork(() -> TlawRace.setPick(sp, p.slot(), p.law(), p.on()));
                }
            });
        });
    }

    private TlawNetwork() {}
}
