package com.renanpontes.isekaimahou.feature.mounts;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.mounts.entity.FlyingMount;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do módulo mounts. */
public final class MountsNetwork {

    /** C -> S: o cavaleiro apertou a tecla de habilidade da montaria. */
    public record MountAbility() implements CustomPacketPayload {
        public static final Type<MountAbility> TYPE = new Type<>(IsekaiMahou.id("mounts_ability"));
        public static final StreamCodec<ByteBuf, MountAbility> STREAM_CODEC = StreamCodec.unit(new MountAbility());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("mounts-1");
        r.playToServer(MountAbility.TYPE, MountAbility.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp && sp.getControlledVehicle() instanceof FlyingMount mount) {
                mount.tryAbility(sp);
            }
        });
    }

    private MountsNetwork() {}
}
