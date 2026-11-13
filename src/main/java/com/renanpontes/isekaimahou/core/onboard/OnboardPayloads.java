package com.renanpontes.isekaimahou.core.onboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Dialogue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do núcleo A2 (diálogo, entrada no jogo, viagem). Os handlers de cliente chamam {@code client.onboard} SÓ
 * dentro do lambda: o servidor dedicado nunca carrega essas classes.
 */
public final class OnboardPayloads {
    private static final int MAX_ID = 128;

    // =====================================================================================
    //  Servidor -> cliente
    // =====================================================================================

    /** Abre (ou põe na fila) um diálogo. */
    public record DialogueShow(String id, Component speaker, int rgb, List<Dialogue.Page> pages) implements CustomPacketPayload {
        public static final Type<DialogueShow> TYPE = new Type<>(IsekaiMahou.id("core_onboard_dialogue_show"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DialogueShow> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.id, MAX_ID);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.speaker);
                    buf.writeInt(p.rgb);
                    int n = Math.min(Dialogue.MAX_PAGES, p.pages.size());
                    buf.writeVarInt(n);
                    for (int i = 0; i < n; i++) {
                        Dialogue.Page page = p.pages.get(i);
                        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, page.text());
                        buf.writeUtf(page.await() == null ? "" : page.await(), MAX_ID);
                    }
                },
                buf -> {
                    String id = buf.readUtf(MAX_ID);
                    Component speaker = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                    int rgb = buf.readInt();
                    int n = Math.min(Dialogue.MAX_PAGES, buf.readVarInt());
                    List<Dialogue.Page> pages = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        Component text = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                        String await = buf.readUtf(MAX_ID);
                        pages.add(new Dialogue.Page(text, await.isEmpty() ? null : await));
                    }
                    return new DialogueShow(id, speaker, rgb, pages);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Fecha um diálogo ({@code id} vazio = todos, inclusive a fila). */
    public record DialogueClose(String id) implements CustomPacketPayload {
        public static final Type<DialogueClose> TYPE = new Type<>(IsekaiMahou.id("core_onboard_dialogue_close"));
        public static final StreamCodec<FriendlyByteBuf, DialogueClose> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.id, MAX_ID), buf -> new DialogueClose(buf.readUtf(MAX_ID)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** O jogador fez a ação {@code token} (ex.: "cast"): páginas que esperam por ela avançam. */
    public record DialogueSignal(String token) implements CustomPacketPayload {
        public static final Type<DialogueSignal> TYPE = new Type<>(IsekaiMahou.id("core_onboard_dialogue_signal"));
        public static final StreamCodec<FriendlyByteBuf, DialogueSignal> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.token, MAX_ID), buf -> new DialogueSignal(buf.readUtf(MAX_ID)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Estado da entrada no jogo. {@code quiet} = avisos guardados continuam guardados; {@code hideTracker} = começar
     * com o rastreador de objetivos fechado (personagem novo).
     */
    public record OnboardState(boolean quiet, boolean tutorialDone, boolean hideTracker) implements CustomPacketPayload {
        public static final Type<OnboardState> TYPE = new Type<>(IsekaiMahou.id("core_onboard_state"));
        public static final StreamCodec<FriendlyByteBuf, OnboardState> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.quiet);
                    buf.writeBoolean(p.tutorialDone);
                    buf.writeBoolean(p.hideTracker);
                },
                buf -> new OnboardState(buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Letreiro "Parabéns!" da classe escolhida. */
    public record Congrats(Component className, int rgb, Component category, Component resource, int resourceRgb,
                           Component style, ItemStack icon, List<Component> extras) implements CustomPacketPayload {
        /** Sem linhas extras (compatível com quem montava o letreiro antes da v1.0). */
        public Congrats(Component className, int rgb, Component category, Component resource, int resourceRgb,
                        Component style, ItemStack icon) {
            this(className, rgb, category, resource, resourceRgb, style, icon, List.of());
        }

        public Congrats {
            extras = extras == null ? List.of() : List.copyOf(extras);
        }

        public static final Type<Congrats> TYPE = new Type<>(IsekaiMahou.id("core_onboard_congrats"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Congrats> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.className);
                    buf.writeInt(p.rgb);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.category);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.resource);
                    buf.writeInt(p.resourceRgb);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.style);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.icon);
                    int n = Math.min(3, p.extras.size());
                    buf.writeVarInt(n);
                    for (int i = 0; i < n; i++) ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.extras.get(i));
                },
                buf -> {
                    Component name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                    int rgb = buf.readInt();
                    Component category = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                    Component resource = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                    int resRgb = buf.readInt();
                    Component style = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                    ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    int n = Math.min(3, buf.readVarInt());
                    List<Component> extras = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) extras.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
                    return new Congrats(name, rgb, category, resource, resRgb, style, icon, extras);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Um destino na lista de viagem. {@code status} = ordinal de {@code Travel.TravelResult} (OK = pode ir agora). */
    public record TravelDest(String id, Component name, String type, String faction, ResourceKey<Level> dim, BlockPos pos,
                             int color, int cost, int status, boolean hub) {
        static void write(RegistryFriendlyByteBuf buf, TravelDest d) {
            buf.writeUtf(d.id, MAX_ID);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, d.name);
            buf.writeUtf(d.type, 64);
            buf.writeUtf(d.faction, 64);
            buf.writeResourceKey(d.dim);
            buf.writeBlockPos(d.pos);
            buf.writeInt(d.color);
            buf.writeVarInt(d.cost);
            buf.writeVarInt(d.status);
            buf.writeBoolean(d.hub);
        }

        static TravelDest read(RegistryFriendlyByteBuf buf) {
            String id = buf.readUtf(MAX_ID);
            Component name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            String type = buf.readUtf(64);
            String faction = buf.readUtf(64);
            ResourceKey<Level> dim = buf.readResourceKey(Registries.DIMENSION);
            BlockPos pos = buf.readBlockPos();
            int color = buf.readInt();
            int cost = buf.readVarInt();
            int status = buf.readVarInt();
            boolean hub = buf.readBoolean();
            return new TravelDest(id, name, type, faction, dim, pos, color, cost, status, hub);
        }
    }

    /**
     * Lista de destinos de viagem. {@code stone} presente = viagem de Pedra de Teleporte; {@code cooldown} = ticks de
     * recarga do menu que faltam ({@code cooldownTotal} = recarga cheia).
     */
    public record TravelMenu(boolean fromStone, Optional<BlockPos> stone, int cooldown, int cooldownTotal, Component resourceName,
                             int resourceRgb, List<TravelDest> dests) implements CustomPacketPayload {
        public static final Type<TravelMenu> TYPE = new Type<>(IsekaiMahou.id("core_onboard_travel_menu"));
        private static final int MAX_DESTS = 512;
        public static final StreamCodec<RegistryFriendlyByteBuf, TravelMenu> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.fromStone);
                    buf.writeBoolean(p.stone.isPresent());
                    p.stone.ifPresent(buf::writeBlockPos);
                    buf.writeVarInt(p.cooldown);
                    buf.writeVarInt(p.cooldownTotal);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.resourceName);
                    buf.writeInt(p.resourceRgb);
                    int n = Math.min(MAX_DESTS, p.dests.size());
                    buf.writeVarInt(n);
                    for (int i = 0; i < n; i++) TravelDest.write(buf, p.dests.get(i));
                },
                buf -> {
                    boolean fromStone = buf.readBoolean();
                    Optional<BlockPos> stone = buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty();
                    int cd = buf.readVarInt();
                    int cdTotal = buf.readVarInt();
                    Component res = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                    int resRgb = buf.readInt();
                    int n = Math.min(MAX_DESTS, buf.readVarInt());
                    List<TravelDest> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) list.add(TravelDest.read(buf));
                    return new TravelMenu(fromStone, stone, cd, cdTotal, res, resRgb, list);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Resultado de um pedido de viagem ({@code result} = ordinal de {@code Travel.TravelResult}). */
    public record TravelOutcome(String placeId, int result) implements CustomPacketPayload {
        public static final Type<TravelOutcome> TYPE = new Type<>(IsekaiMahou.id("core_onboard_travel_outcome"));
        public static final StreamCodec<FriendlyByteBuf, TravelOutcome> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.placeId, MAX_ID);
                    buf.writeVarInt(p.result);
                },
                buf -> new TravelOutcome(buf.readUtf(MAX_ID), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Clarão na tela ao partir/chegar de uma viagem. */
    public record TravelFx(int rgb, boolean arrival) implements CustomPacketPayload {
        public static final Type<TravelFx> TYPE = new Type<>(IsekaiMahou.id("core_onboard_travel_fx"));
        public static final StreamCodec<FriendlyByteBuf, TravelFx> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeInt(p.rgb);
                    buf.writeBoolean(p.arrival);
                },
                buf -> new TravelFx(buf.readInt(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // =====================================================================================
    //  Cliente -> servidor
    // =====================================================================================

    /** O cliente terminou (ou pulou) um diálogo. */
    public record DialogueDone(String id, boolean skipped) implements CustomPacketPayload {
        public static final Type<DialogueDone> TYPE = new Type<>(IsekaiMahou.id("core_onboard_dialogue_done"));
        public static final StreamCodec<FriendlyByteBuf, DialogueDone> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.id, MAX_ID);
                    buf.writeBoolean(p.skipped);
                },
                buf -> new DialogueDone(buf.readUtf(MAX_ID), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Pede a lista de destinos do menu (aba Viagem). */
    public record TravelOpen() implements CustomPacketPayload {
        public static final TravelOpen INSTANCE = new TravelOpen();
        public static final Type<TravelOpen> TYPE = new Type<>(IsekaiMahou.id("core_onboard_travel_open"));
        public static final StreamCodec<FriendlyByteBuf, TravelOpen> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Pede para viajar até {@code placeId} (pela sessão aberta: pedra ou menu). */
    public record TravelGo(String placeId) implements CustomPacketPayload {
        public static final Type<TravelGo> TYPE = new Type<>(IsekaiMahou.id("core_onboard_travel_go"));
        public static final StreamCodec<FriendlyByteBuf, TravelGo> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.placeId, MAX_ID), buf -> new TravelGo(buf.readUtf(MAX_ID)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * v1.0 — "cutucão" da entrada no jogo (S→C, a cada 2 s enquanto a criação do personagem está pendente): se o
     * cliente estiver sem tela nenhuma, ele responde com {@link EntryResume} e o servidor reabre a tela certa.
     */
    public record EntryNudge() implements CustomPacketPayload {
        public static final EntryNudge INSTANCE = new EntryNudge();
        public static final Type<EntryNudge> TYPE = new Type<>(IsekaiMahou.id("core_onboard_entry_nudge"));
        public static final StreamCodec<FriendlyByteBuf, EntryNudge> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** v1.0 — resposta ao cutucão (C→S): "estou sem tela; reabra a criação do personagem". */
    public record EntryResume() implements CustomPacketPayload {
        public static final EntryResume INSTANCE = new EntryResume();
        public static final Type<EntryResume> TYPE = new Type<>(IsekaiMahou.id("core_onboard_entry_resume"));
        public static final StreamCodec<FriendlyByteBuf, EntryResume> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // =====================================================================================
    //  Registro
    // =====================================================================================
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(DialogueShow.TYPE, DialogueShow.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.onboard.OnboardClientHandlers.dialogueShow(p));
        r.playToClient(DialogueClose.TYPE, DialogueClose.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.onboard.OnboardClientHandlers.dialogueClose(p));
        r.playToClient(DialogueSignal.TYPE, DialogueSignal.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.onboard.OnboardClientHandlers.dialogueSignal(p));
        r.playToClient(OnboardState.TYPE, OnboardState.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.onboard.OnboardClientHandlers.state(p));
        r.playToClient(Congrats.TYPE, Congrats.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.onboard.OnboardClientHandlers.congrats(p));
        r.playToClient(TravelMenu.TYPE, TravelMenu.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.onboard.OnboardClientHandlers.travelMenu(p));
        r.playToClient(TravelOutcome.TYPE, TravelOutcome.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.onboard.OnboardClientHandlers.travelOutcome(p));
        r.playToClient(TravelFx.TYPE, TravelFx.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.onboard.OnboardClientHandlers.travelFx(p));

        r.playToClient(EntryNudge.TYPE, EntryNudge.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.onboard.OnboardClientHandlers.entryNudge());
        r.playToServer(EntryResume.TYPE, EntryResume.STREAM_CODEC,
                (p, c) -> withPlayer(c, EntryGuard::onResume));

        r.playToServer(DialogueDone.TYPE, DialogueDone.STREAM_CODEC,
                (p, c) -> withPlayer(c, sp -> DialogueService.onDone(sp, p.id(), p.skipped())));
        r.playToServer(TravelOpen.TYPE, TravelOpen.STREAM_CODEC,
                (p, c) -> withPlayer(c, sp -> TravelService.openMenu(sp, null)));
        r.playToServer(TravelGo.TYPE, TravelGo.STREAM_CODEC,
                (p, c) -> withPlayer(c, sp -> TravelService.requestTravel(sp, p.placeId())));
    }

    private static void withPlayer(IPayloadContext c, Consumer<ServerPlayer> action) {
        if (c.player() instanceof ServerPlayer sp) action.accept(sp);
    }

    private OnboardPayloads() {}
}
