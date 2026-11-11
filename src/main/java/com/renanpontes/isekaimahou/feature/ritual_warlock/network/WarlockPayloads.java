package com.renanpontes.isekaimahou.feature.ritual_warlock.network;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Pacotes do Ritual do Warlock. */
public final class WarlockPayloads {

    /** Modos da tela de diálogo do Patrono. */
    public static final int MODE_OFFER = 0;
    public static final int MODE_PENDING = 1;
    public static final int MODE_STRANGER = 2;

    /** Respostas do jogador. */
    public static final int ANSWER_ACCEPT = 0;
    public static final int ANSWER_REFUSE = 1;
    public static final int ANSWER_DELIVER = 2;

    /**
     * Servidor → cliente: abre o diálogo com o Patrono.
     * @param kind       id do Patrono (ember_lord, void_lady, drowned_king)
     * @param demandType ordinal de {@code PatronDemand.Type}
     * @param detail     prazo / progresso
     * @param patience   ticks até o Patrono perder a paciência (só no modo oferta; -1 = sem limite)
     */
    public record PatronDialog(int entityId, String kind, int mode, int pactNumber, Component line, int demandType,
                               Component demand, Component detail, ItemStack icon, int patience) implements CustomPacketPayload {
        public static final Type<PatronDialog> TYPE = new Type<>(IsekaiMahou.id("ritual_warlock_dialog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PatronDialog> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entityId());
                    buf.writeUtf(p.kind(), 64);
                    buf.writeVarInt(p.mode());
                    buf.writeVarInt(p.pactNumber());
                    ComponentSerialization.STREAM_CODEC.encode(buf, p.line());
                    buf.writeVarInt(p.demandType());
                    ComponentSerialization.STREAM_CODEC.encode(buf, p.demand());
                    ComponentSerialization.STREAM_CODEC.encode(buf, p.detail());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.icon());
                    buf.writeVarInt(p.patience());
                },
                buf -> new PatronDialog(buf.readVarInt(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(),
                        ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(),
                        ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Cliente → servidor: resposta no diálogo (aceitar, recusar, entregar). */
    public record PatronAnswer(int entityId, int action) implements CustomPacketPayload {
        public static final Type<PatronAnswer> TYPE = new Type<>(IsekaiMahou.id("ritual_warlock_answer"));
        public static final StreamCodec<ByteBuf, PatronAnswer> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PatronAnswer::entityId,
                ByteBufCodecs.VAR_INT, PatronAnswer::action,
                PatronAnswer::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Servidor → cliente: estado do pacto pendente para o HUD.
     * @param remaining ticks até o fim da janela/prazo (o cliente faz a contagem regressiva)
     */
    public record PactHud(boolean active, String kind, Component demand, int progress, int amount, int remaining,
                          boolean fulfilled, boolean tracked) implements CustomPacketPayload {
        public static final Type<PactHud> TYPE = new Type<>(IsekaiMahou.id("ritual_warlock_hud"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PactHud> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.active());
                    buf.writeUtf(p.kind(), 64);
                    ComponentSerialization.STREAM_CODEC.encode(buf, p.demand());
                    buf.writeVarInt(p.progress());
                    buf.writeVarInt(p.amount());
                    buf.writeVarInt(p.remaining());
                    buf.writeBoolean(p.fulfilled());
                    buf.writeBoolean(p.tracked());
                },
                buf -> new PactHud(buf.readBoolean(), buf.readUtf(64), ComponentSerialization.STREAM_CODEC.decode(buf),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));

        public static PactHud empty() {
            return new PactHud(false, "", Component.empty(), 0, 0, 0, false, false);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private WarlockPayloads() {}
}
