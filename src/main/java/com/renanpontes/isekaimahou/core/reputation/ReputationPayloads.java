package com.renanpontes.isekaimahou.core.reputation;

import java.util.Optional;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Reputation;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes da reputação. O cliente instala {@link #onNotify} (ganchos sem classes de cliente no código comum). */
public final class ReputationPayloads {
    /** Aviso "+15 Reputação" no cliente. Trocado pelo ReputationCoreClient. */
    public static volatile Consumer<Notify> onNotify = n -> {};

    /** S→C: definições das facções (JSON do datapack), no login e no /reload. */
    public record FactionSync(String json) implements CustomPacketPayload {
        public static final Type<FactionSync> TYPE = new Type<>(IsekaiMahou.id("rep_factions"));
        public static final StreamCodec<ByteBuf, FactionSync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(1 << 20), FactionSync::json, FactionSync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S→C: a reputação mudou (para o aviso lateral). {@code value} = valor novo. */
    public record Notify(String faction, int delta, int value, Optional<Component> reason) implements CustomPacketPayload {
        public static final Type<Notify> TYPE = new Type<>(IsekaiMahou.id("rep_notify"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Notify> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Notify::faction, ByteBufCodecs.VAR_INT, Notify::delta, ByteBufCodecs.VAR_INT, Notify::value,
                ComponentSerialization.OPTIONAL_STREAM_CODEC, Notify::reason, Notify::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C→S: usar um título desbloqueado (texto vazio = tirar o título). */
    public record SelectTitle(String title) implements CustomPacketPayload {
        public static final Type<SelectTitle> TYPE = new Type<>(IsekaiMahou.id("rep_select_title"));
        public static final StreamCodec<ByteBuf, SelectTitle> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), SelectTitle::title, SelectTitle::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C→S: marcar/desmarcar facção aliada (botão da aba Reputação). */
    public record SetAllied(String faction, boolean allied) implements CustomPacketPayload {
        public static final Type<SetAllied> TYPE = new Type<>(IsekaiMahou.id("rep_set_allied"));
        public static final StreamCodec<ByteBuf, SetAllied> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(128), SetAllied::faction, ByteBufCodecs.BOOL, SetAllied::allied, SetAllied::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(FactionSync.TYPE, FactionSync.STREAM_CODEC,
                (p, c) -> FactionRegistry.setClient(FactionRegistry.fromSync(p.json())));
        r.playToClient(Notify.TYPE, Notify.STREAM_CODEC, (p, c) -> onNotify.accept(p));
        r.playToServer(SelectTitle.TYPE, SelectTitle.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) {
                String key = p.title().isBlank() ? null : p.title();
                ReputationService.selectTitle(sp, key);
            }
        });
        r.playToServer(SetAllied.TYPE, SetAllied.STREAM_CODEC, (p, c) -> {
            if (!(c.player() instanceof ServerPlayer sp)) return;
            Reputation.AllyResult res = ReputationService.setAllied(sp, p.faction(), p.allied());
            if (!res.ok() && res != Reputation.AllyResult.UNCHANGED) {
                sp.displayClientMessage(res.message().withStyle(ChatFormatting.RED), true);
                sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5F, 1.1F);
            }
        });
    }

    private ReputationPayloads() {}
}
