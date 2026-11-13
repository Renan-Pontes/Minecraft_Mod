package com.renanpontes.isekaimahou.feature.ui_hud;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do módulo ui_hud (v1.0 RELEASE): dicas de primeira vez e preferências do jogador que o servidor precisa saber. */
public final class HintPayloads {
    /** Servidor → cliente: mostre esta dica (texto {@code hint.isekai_mahou.<id>}). {@code force} = depuração. */
    public record Show(String id, List<Component> args, boolean force) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(IsekaiMahou.id("ui_hud_hint_show"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Show> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(Hints.MAX_ID), Show::id,
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(4)), Show::args,
                ByteBufCodecs.BOOL, Show::force,
                Show::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Cliente → servidor: a dica apareceu na tela (agora conta como vista). */
    public record Seen(String id) implements CustomPacketPayload {
        public static final Type<Seen> TYPE = new Type<>(IsekaiMahou.id("ui_hud_hint_seen"));
        public static final StreamCodec<FriendlyByteBuf, Seen> STREAM_CODEC =
                ByteBufCodecs.stringUtf8(Hints.MAX_ID).map(Seen::new, Seen::id).cast();
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Cliente → servidor: algo que só o cliente percebe pediu uma dica (o servidor decide se ela já foi vista). */
    public record Request(String id) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(IsekaiMahou.id("ui_hud_hint_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC =
                ByteBufCodecs.stringUtf8(Hints.MAX_ID).map(Request::new, Request::id).cast();
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Cliente → servidor: preferências da config local que valem no servidor (bits {@code MahouConfig.PREF_*}). */
    public record Prefs(int bits) implements CustomPacketPayload {
        public static final Type<Prefs> TYPE = new Type<>(IsekaiMahou.id("ui_hud_prefs"));
        public static final StreamCodec<FriendlyByteBuf, Prefs> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(Prefs::new, Prefs::bits).cast();
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("ui_hud-1").optional();
        r.playToClient(Show.TYPE, Show.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> com.renanpontes.isekaimahou.feature.ui_hud.client.HintOverlay.receive(p)));
        r.playToServer(Seen.TYPE, Seen.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) Hints.confirm(sp, p.id());
        }));
        r.playToServer(Request.TYPE, Request.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            // Só as dicas que o cliente tem motivo para pedir (as "client.*"): o resto é o servidor que dispara
            if (c.player() instanceof ServerPlayer sp && p.id().startsWith("client.")) Hints.show(sp, p.id());
        }));
        r.playToServer(Prefs.TYPE, Prefs.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) {
                MahouConfig.setPlayerPrefs(sp.getUUID(), p.bits() & (MahouConfig.PREF_PEACE_AUTO | MahouConfig.PREF_TUTORIAL));
                // Entrou no mundo já dentro de uma cidade: o Modo Paz ligou sozinho antes de a preferência chegar
                if (!MahouConfig.peaceAuto(sp) && sp.tickCount < 200 && PeaceMode.isOn(sp)) PeaceMode.set(sp, false, false);
            }
        }));
    }

    private HintPayloads() {}
}
