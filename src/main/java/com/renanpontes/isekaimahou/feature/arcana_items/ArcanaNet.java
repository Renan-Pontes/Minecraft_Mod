package com.renanpontes.isekaimahou.feature.arcana_items;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Pacotes do módulo. */
public final class ArcanaNet {
    /** C→S: gravar a magia {@code index} da biblioteca no pergaminho em branco que está na mão. */
    public record Inscribe(int index, boolean offhand) implements CustomPacketPayload {
        public static final Type<Inscribe> TYPE = new Type<>(IsekaiMahou.id("arcana_items_inscribe"));
        public static final StreamCodec<ByteBuf, Inscribe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Inscribe::index, ByteBufCodecs.BOOL, Inscribe::offhand, Inscribe::new);

        @Override
        public Type<Inscribe> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("arcana_items-1");
        r.playToServer(Inscribe.TYPE, Inscribe.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) RecipeScrolls.inscribe(sp, p.index(), p.offhand());
        }));
    }

    private ArcanaNet() {}
}
