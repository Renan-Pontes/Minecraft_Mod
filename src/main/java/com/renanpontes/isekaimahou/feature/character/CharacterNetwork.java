package com.renanpontes.isekaimahou.feature.character;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do módulo (registrador {@code "3"} — a v1.0.1 trocou o aviso de raça desbloqueada pelo de nível de raça).
 * Servidor → cliente: dados da raça, abrir a tela de raça, aparência racial dos jogadores visíveis, letreiro de nível de
 * raça, visuais temporários (asas de skill, Transformação). Cliente → servidor: confirmar a raça, tecla racial
 * (transformar / golpe da forma; com Shift, a visão), opções. O cliente liga os ganchos ({@code on*}) no init dele.
 */
public final class CharacterNetwork {
    /** Modos da tela de raça. */
    public static final int MODE_CREATION = 0, MODE_TAB = 1, MODE_ALTAR = 2;
    /** Visuais temporários ({@link RaceFx}): asas da skill Asas Sombrias e a Transformação racial. */
    public static final int FX_WINGS = 0, FX_FORM = 1;
    /** Tecla racial: {@link UseAbility#mode()}. */
    public static final int KEY_MAIN = 0, KEY_VISION = 1;

    public static Consumer<CompoundTag> onData = t -> {};
    public static Consumer<Open> onOpen = o -> {};
    public static Consumer<Look> onLook = l -> {};
    public static Consumer<LevelUp> onLevelUp = u -> {};
    public static Consumer<RaceFx> onFx = f -> {};

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(IsekaiMahou.id("character_" + name));
    }

    // ---------------- Servidor → cliente ----------------
    public record Data(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<Data> TYPE = payloadType("data");
        public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(Data::new, Data::tag);
        @Override public Type<Data> type() { return TYPE; }
    }

    /** Abre a tela de raça ({@code mode}); {@code altar} = posição do altar (modo altar). */
    public record Open(int mode, BlockPos altar) implements CustomPacketPayload {
        public static final Type<Open> TYPE = payloadType("open");
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Open::mode, BlockPos.STREAM_CODEC, Open::altar, Open::new);
        @Override public Type<Open> type() { return TYPE; }
    }

    /** Aparência de um jogador: raça (−1 = sem raça escolhida) e a cor da ancestralidade. */
    public record Look(int entityId, int race, int ancestryRgb) implements CustomPacketPayload {
        public static final Type<Look> TYPE = payloadType("look");
        public static final StreamCodec<ByteBuf, Look> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Look::entityId, ByteBufCodecs.VAR_INT, Look::race, ByteBufCodecs.INT, Look::ancestryRgb, Look::new);
        @Override public Type<Look> type() { return TYPE; }
    }

    /** Nível de raça novo (I–V): letreiro dramático no cliente. */
    public record LevelUp(int race, int level) implements CustomPacketPayload {
        public static final Type<LevelUp> TYPE = payloadType("level_up");
        public static final StreamCodec<ByteBuf, LevelUp> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LevelUp::race, ByteBufCodecs.VAR_INT, LevelUp::level, LevelUp::new);
        @Override public Type<LevelUp> type() { return TYPE; }
    }

    /** Visual temporário numa entidade ({@code kind} = {@link #FX_WINGS}, {@link #FX_FORM}; {@code ticks} 0 = tirar). */
    public record RaceFx(int entityId, int kind, int ticks) implements CustomPacketPayload {
        public static final Type<RaceFx> TYPE = payloadType("fx");
        public static final StreamCodec<ByteBuf, RaceFx> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RaceFx::entityId, ByteBufCodecs.VAR_INT, RaceFx::kind, ByteBufCodecs.VAR_INT, RaceFx::ticks,
                RaceFx::new);
        @Override public Type<RaceFx> type() { return TYPE; }
    }

    // ---------------- Cliente → servidor ----------------
    /** Confirma a raça escolhida na tela ({@code mode} = criação, aba ou altar). */
    public record Confirm(int mode, int race) implements CustomPacketPayload {
        public static final Type<Confirm> TYPE = payloadType("confirm");
        public static final StreamCodec<ByteBuf, Confirm> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Confirm::mode, ByteBufCodecs.VAR_INT, Confirm::race, Confirm::new);
        @Override public Type<Confirm> type() { return TYPE; }
    }

    /** Tecla racial: {@link #KEY_MAIN} = transformar / golpe da forma; {@link #KEY_VISION} (Shift) = visão no escuro. */
    public record UseAbility(int mode) implements CustomPacketPayload {
        public static final Type<UseAbility> TYPE = payloadType("ability");
        public static final StreamCodec<ByteBuf, UseAbility> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(UseAbility::new, UseAbility::mode);
        @Override public Type<UseAbility> type() { return TYPE; }
    }

    /** Opções: 0 = visão no escuro. */
    public record Toggle(int option, boolean on) implements CustomPacketPayload {
        public static final Type<Toggle> TYPE = payloadType("toggle");
        public static final StreamCodec<ByteBuf, Toggle> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Toggle::option, ByteBufCodecs.BOOL, Toggle::on, Toggle::new);
        @Override public Type<Toggle> type() { return TYPE; }
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("3");
            r.playToClient(Data.TYPE, Data.STREAM_CODEC, (p, c) -> onData.accept(p.tag()));
            r.playToClient(Open.TYPE, Open.STREAM_CODEC, (p, c) -> onOpen.accept(p));
            r.playToClient(Look.TYPE, Look.STREAM_CODEC, (p, c) -> onLook.accept(p));
            r.playToClient(LevelUp.TYPE, LevelUp.STREAM_CODEC, (p, c) -> onLevelUp.accept(p));
            r.playToClient(RaceFx.TYPE, RaceFx.STREAM_CODEC, (p, c) -> onFx.accept(p));
            r.playToServer(Confirm.TYPE, Confirm.STREAM_CODEC, (p, c) -> {
                if (c.player() instanceof ServerPlayer sp) CharacterService.confirm(sp, p.mode(), p.race());
            });
            r.playToServer(UseAbility.TYPE, UseAbility.STREAM_CODEC, (p, c) -> {
                if (!(c.player() instanceof ServerPlayer sp)) return;
                if (p.mode() == KEY_VISION) RacePassives.toggleVisionKey(sp);
                else RaceForms.onKey(sp);
            });
            r.playToServer(Toggle.TYPE, Toggle.STREAM_CODEC, (p, c) -> {
                if (c.player() instanceof ServerPlayer sp) CharacterService.toggle(sp, p.option(), p.on());
            });
        });
    }

    private CharacterNetwork() {}
}
