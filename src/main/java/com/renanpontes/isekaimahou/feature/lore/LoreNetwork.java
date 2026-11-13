package com.renanpontes.isekaimahou.feature.lore;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do módulo lore. Os handlers de cliente só chamam os ganchos {@link #onSync}/{@link #onRead}, que a parte de
 * cliente preenche — nenhuma classe de cliente é tocada no servidor dedicado.
 */
public final class LoreNetwork {
    /** Ações do cliente: seguir um fragmento no rastreador, parar, procurar o lugar de novo. */
    public static final int TRACK = 0, UNTRACK = 1, RELOCATE = 2;

    public static Consumer<Sync> onSync = p -> {};
    public static Consumer<Read> onRead = p -> {};

    /** Servidor → cliente: os dados de lore do jogador inteiros (formato em {@link LoreData}). */
    public record Sync(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(IsekaiMahou.id("lore_sync"));
        public static final StreamCodec<FriendlyByteBuf, Sync> STREAM_CODEC = ByteBufCodecs.TRUSTED_COMPOUND_TAG
                .<FriendlyByteBuf>cast().map(Sync::new, Sync::data);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Servidor → cliente: o jogador leu um fragmento — abre a página na tela.
     *
     * @param fresh       fragmento novo (false = releitura)
     * @param setComplete esta leitura fechou a coleção (libera uma Verdade Oculta)
     */
    public record Read(String fragment, boolean fresh, boolean setComplete) implements CustomPacketPayload {
        public static final Type<Read> TYPE = new Type<>(IsekaiMahou.id("lore_read"));
        public static final StreamCodec<FriendlyByteBuf, Read> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Read::fragment, ByteBufCodecs.BOOL, Read::fresh,
                ByteBufCodecs.BOOL, Read::setComplete, Read::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Cliente → servidor: {@link #TRACK}, {@link #UNTRACK} ou {@link #RELOCATE} de um fragmento. */
    public record Action(int op, String fragment) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(IsekaiMahou.id("lore_action"));
        public static final StreamCodec<FriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::op, ByteBufCodecs.stringUtf8(64), Action::fragment, Action::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("lore-1");
        r.playToClient(Sync.TYPE, Sync.STREAM_CODEC, (p, c) -> onSync.accept(p));
        r.playToClient(Read.TYPE, Read.STREAM_CODEC, (p, c) -> onRead.accept(p));
        r.playToServer(Action.TYPE, Action.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) LoreService.action(sp, p.op(), p.fragment());
        });
    }

    private LoreNetwork() {}
}
