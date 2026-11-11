package com.renanpontes.isekaimahou.network;

import java.util.Optional;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellPreset;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Pacotes do núcleo. Módulos criam os seus próprios (com ids "isekai_mahou:<modulo>_<nome>"). */
public final class CorePayloads {

    // =========================== Servidor -> cliente ===========================

    /** Todos os dados mágicos (MagicData codificado em NBT). */
    public record MagicSync(CompoundTag data) implements CustomPacketPayload {
        public static final Type<MagicSync> TYPE = new Type<>(IsekaiMahou.id("magic_sync"));
        public static final StreamCodec<ByteBuf, MagicSync> STREAM_CODEC =
                ByteBufCodecs.COMPOUND_TAG.map(MagicSync::new, MagicSync::data);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Mana atual + máximo (com bônus) + meditando (enviado com frequência). */
    public record ManaSync(float mana, int maxMana, boolean meditating) implements CustomPacketPayload {
        public static final Type<ManaSync> TYPE = new Type<>(IsekaiMahou.id("mana_sync"));
        public static final StreamCodec<ByteBuf, ManaSync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, ManaSync::mana, ByteBufCodecs.VAR_INT, ManaSync::maxMana,
                ByteBufCodecs.BOOL, ManaSync::meditating, ManaSync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Um slot entrou em recarga. key = {@code MagicData.slotKey(loadout, slot)} ("loadout:slot", v1.0). */
    public record Cooldown(String key, int ticks) implements CustomPacketPayload {
        public static final Type<Cooldown> TYPE = new Type<>(IsekaiMahou.id("cooldown"));
        public static final StreamCodec<ByteBuf, Cooldown> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Cooldown::key, ByteBufCodecs.VAR_INT, Cooldown::ticks, Cooldown::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Estado da conjuração: state 0 = começou (ticks = duração), 1 = terminou, 2 = interrompida/falhou. */
    public record CastState(int slot, int ticks, String chant, byte state) implements CustomPacketPayload {
        public static final Type<CastState> TYPE = new Type<>(IsekaiMahou.id("cast_state"));
        public static final StreamCodec<ByteBuf, CastState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, CastState::slot, ByteBufCodecs.VAR_INT, CastState::ticks,
                ByteBufCodecs.STRING_UTF8, CastState::chant, ByteBufCodecs.BYTE, CastState::state, CastState::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Anúncio "falado" (nome de magia forte, fala de chefe). style: ver Announce.STYLE_*. */
    public record AnnounceText(int entityId, Component text, int rgb, int style) implements CustomPacketPayload {
        public static final Type<AnnounceText> TYPE = new Type<>(IsekaiMahou.id("announce"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AnnounceText> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AnnounceText::entityId, ComponentSerialization.STREAM_CODEC, AnnounceText::text,
                ByteBufCodecs.INT, AnnounceText::rgb, ByteBufCodecs.VAR_INT, AnnounceText::style, AnnounceText::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Cântico durante a conjuração (mostrado palavra por palavra sobre a cabeça). */
    public record Chant(int entityId, String chant, int ticks) implements CustomPacketPayload {
        public static final Type<Chant> TYPE = new Type<>(IsekaiMahou.id("chant"));
        public static final StreamCodec<ByteBuf, Chant> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Chant::entityId, ByteBufCodecs.STRING_UTF8, Chant::chant,
                ByteBufCodecs.VAR_INT, Chant::ticks, Chant::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Cutscene (ver Cutscenes). */
    public record Cutscene(String kind, int focusId, Component title, Component subtitle, int rgb, int ticks,
                           boolean lockControls) implements CustomPacketPayload {
        public static final Type<Cutscene> TYPE = new Type<>(IsekaiMahou.id("cutscene"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cutscene> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, p.kind());
                    ByteBufCodecs.VAR_INT.encode(buf, p.focusId());
                    ComponentSerialization.STREAM_CODEC.encode(buf, p.title());
                    ComponentSerialization.STREAM_CODEC.encode(buf, p.subtitle());
                    ByteBufCodecs.INT.encode(buf, p.rgb());
                    ByteBufCodecs.VAR_INT.encode(buf, p.ticks());
                    ByteBufCodecs.BOOL.encode(buf, p.lockControls());
                },
                buf -> new Cutscene(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                        ByteBufCodecs.INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Abre a tela de escolha de classe. */
    public record OpenPathScreen() implements CustomPacketPayload {
        public static final OpenPathScreen INSTANCE = new OpenPathScreen();
        public static final Type<OpenPathScreen> TYPE = new Type<>(IsekaiMahou.id("open_path_screen"));
        public static final StreamCodec<ByteBuf, OpenPathScreen> STREAM_CODEC = StreamCodec.unit(INSTANCE);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Toast estilizado de recompensa (ver {@code core.api.Rewards#toast}). rgb = cor de destaque. */
    public record RewardToast(Component title, Component subtitle, ItemStack icon, int rgb) implements CustomPacketPayload {
        public static final Type<RewardToast> TYPE = new Type<>(IsekaiMahou.id("reward_toast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RewardToast> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, RewardToast::title, ComponentSerialization.STREAM_CODEC, RewardToast::subtitle,
                ItemStack.OPTIONAL_STREAM_CODEC, RewardToast::icon, ByteBufCodecs.INT, RewardToast::rgb, RewardToast::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // =========================== Cliente -> servidor ===========================

    /** Tecla de magia (0=Z, 1=X, 2=C, 3=G) apertada/solta. */
    public record CastKey(int slot, boolean pressed) implements CustomPacketPayload {
        public static final Type<CastKey> TYPE = new Type<>(IsekaiMahou.id("cast_key"));
        public static final StreamCodec<ByteBuf, CastKey> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CastKey::slot, ByteBufCodecs.BOOL, CastKey::pressed, CastKey::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** @deprecated v1.0 não tem Roda Elemental: o servidor ignora (o elemento vem dos glifos). */
    @Deprecated
    public record SelectWheel(int index) implements CustomPacketPayload {
        public static final Type<SelectWheel> TYPE = new Type<>(IsekaiMahou.id("select_wheel"));
        public static final StreamCodec<ByteBuf, SelectWheel> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(SelectWheel::new, SelectWheel::index);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** v1.0: troca o loadout ativo (0..8). Alt/Mouse 5 + scroll e Alt + 1..9 mandam este pacote. */
    public record SelectLoadout(int index) implements CustomPacketPayload {
        public static final Type<SelectLoadout> TYPE = new Type<>(IsekaiMahou.id("select_loadout"));
        public static final StreamCodec<ByteBuf, SelectLoadout> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(SelectLoadout::new, SelectLoadout::index);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * @deprecated v1.0: use {@link SetLoadoutSlot}. Continua funcionando: o {@code element} é ignorado e o
     * {@code loadout} é um dos 9 loadouts (0..8).
     */
    @Deprecated
    public record SetSlot(String element, int loadout, int slot, int trigger, Optional<SpellRecipe> recipe)
            implements CustomPacketPayload {
        public static final Type<SetSlot> TYPE = new Type<>(IsekaiMahou.id("set_slot"));
        public static final StreamCodec<ByteBuf, SetSlot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SetSlot::element, ByteBufCodecs.VAR_INT, SetSlot::loadout,
                ByteBufCodecs.VAR_INT, SetSlot::slot, ByteBufCodecs.VAR_INT, SetSlot::trigger,
                ByteBufCodecs.optional(SpellRecipe.STREAM_CODEC), SetSlot::recipe, SetSlot::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** @deprecated v1.0 não tem Roda Elemental: o servidor ignora. */
    @Deprecated
    public record WheelEdit(String element, boolean add) implements CustomPacketPayload {
        public static final Type<WheelEdit> TYPE = new Type<>(IsekaiMahou.id("wheel_edit"));
        public static final StreamCodec<ByteBuf, WheelEdit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, WheelEdit::element, ByteBufCodecs.BOOL, WheelEdit::add, WheelEdit::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * v1.0 — Grava (ou apaga, com {@code recipe} vazio) a versão {@code trigger} ({@link MoveTrigger#ordinal()}) de um
     * slot (0=Z, 1=X, 2=C, 3=G) de um dos 9 loadouts (0..8).
     */
    public record SetLoadoutSlot(int loadout, int slot, int trigger, Optional<SpellRecipe> recipe) implements CustomPacketPayload {
        public static final Type<SetLoadoutSlot> TYPE = new Type<>(IsekaiMahou.id("set_loadout_slot"));
        public static final StreamCodec<ByteBuf, SetLoadoutSlot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetLoadoutSlot::loadout, ByteBufCodecs.VAR_INT, SetLoadoutSlot::slot,
                ByteBufCodecs.VAR_INT, SetLoadoutSlot::trigger, ByteBufCodecs.optional(SpellRecipe.STREAM_CODEC), SetLoadoutSlot::recipe,
                SetLoadoutSlot::new);

        public static SetLoadoutSlot set(int loadout, int slot, MoveTrigger trigger, SpellRecipe recipe) {
            return new SetLoadoutSlot(loadout, slot, trigger.ordinal(), Optional.ofNullable(recipe).filter(r -> !r.isEmpty()));
        }

        public static SetLoadoutSlot clear(int loadout, int slot, MoveTrigger trigger) {
            return new SetLoadoutSlot(loadout, slot, trigger.ordinal(), Optional.empty());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * v1.0 — Presets (rascunhos nomeados). {@code op}: {@link #SAVE} (name, recipe; mesmo nome substitui),
     * {@link #DELETE} (index), {@link #LOAD} (index → loadout/slot/trigger), {@link #RENAME} (index, name).
     * Use as fábricas {@link #save}, {@link #delete}, {@link #load}, {@link #rename}.
     */
    public record PresetEdit(int op, int index, String name, SpellRecipe recipe, int loadout, int slot, int trigger)
            implements CustomPacketPayload {
        public static final int SAVE = 0, DELETE = 1, LOAD = 2, RENAME = 3;
        public static final Type<PresetEdit> TYPE = new Type<>(IsekaiMahou.id("preset_edit"));
        public static final StreamCodec<ByteBuf, PresetEdit> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, p.op());
                    ByteBufCodecs.VAR_INT.encode(buf, p.index());
                    ByteBufCodecs.stringUtf8(SpellPreset.MAX_NAME * 4).encode(buf, p.name());
                    SpellRecipe.STREAM_CODEC.encode(buf, p.recipe());
                    ByteBufCodecs.VAR_INT.encode(buf, p.loadout());
                    ByteBufCodecs.VAR_INT.encode(buf, p.slot());
                    ByteBufCodecs.VAR_INT.encode(buf, p.trigger());
                },
                buf -> new PresetEdit(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.stringUtf8(SpellPreset.MAX_NAME * 4).decode(buf), SpellRecipe.STREAM_CODEC.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

        public PresetEdit {
            name = SpellPreset.cleanName(name);
            recipe = recipe == null ? SpellRecipe.EMPTY : recipe;
        }

        public static PresetEdit save(String name, SpellRecipe recipe) {
            return new PresetEdit(SAVE, -1, name, recipe, 0, 0, 0);
        }

        public static PresetEdit delete(int index) {
            return new PresetEdit(DELETE, index, "", SpellRecipe.EMPTY, 0, 0, 0);
        }

        public static PresetEdit load(int index, int loadout, int slot, MoveTrigger trigger) {
            return new PresetEdit(LOAD, index, "", SpellRecipe.EMPTY, loadout, slot, trigger.ordinal());
        }

        public static PresetEdit rename(int index, String name) {
            return new PresetEdit(RENAME, index, name, SpellRecipe.EMPTY, 0, 0, 0);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Escolha de classe na tela inicial. */
    public record ChoosePath(int path) implements CustomPacketPayload {
        public static final Type<ChoosePath> TYPE = new Type<>(IsekaiMahou.id("choose_path"));
        public static final StreamCodec<ByteBuf, ChoosePath> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(ChoosePath::new, ChoosePath::path);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Biblioteca do Grimório: op 0 = salvar receita, 1 = apagar índice, 2 = gravar num Pergaminho de Magia (gasta 1 papel). */
    public record LibraryEdit(int op, int index, SpellRecipe recipe) implements CustomPacketPayload {
        public static final Type<LibraryEdit> TYPE = new Type<>(IsekaiMahou.id("library_edit"));
        public static final StreamCodec<ByteBuf, LibraryEdit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LibraryEdit::op, ByteBufCodecs.VAR_INT, LibraryEdit::index,
                SpellRecipe.STREAM_CODEC, LibraryEdit::recipe, LibraryEdit::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Pedido para começar uma Câmara de Provação (botão da aba Caminhos etc.). Ver {@code core.api.TrialsApi#request}. */
    public record RequestTrial(String element, int rank) implements CustomPacketPayload {
        public static final Type<RequestTrial> TYPE = new Type<>(IsekaiMahou.id("request_trial"));
        public static final StreamCodec<ByteBuf, RequestTrial> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), RequestTrial::element, ByteBufCodecs.VAR_INT, RequestTrial::rank, RequestTrial::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private CorePayloads() {}
}
