package com.renanpontes.isekaimahou.feature.familiars;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do Familiar: o cliente manda ações (chamar/guardar, ligar a ajuda, renomear, abrir a bolsa) e o servidor
 * responde com o {@link State} completo (pequeno; só vai quando algo muda ou a aba pede).
 */
public final class FamiliarNetwork {
    /** Cliente → servidor. {@code text} só vale em {@link #RENAME}. */
    public record Action(int op, String text) implements CustomPacketPayload {
        public static final int SYNC = 0, TOGGLE_SUMMON = 1, TOGGLE_HELP = 2, RENAME = 3, STASH = 4;
        /** v1.0 (onda 4): esquecer a receita de apoio ensinada. */
        public static final int FORGET_SPELL = 5;
        public static final Type<Action> TYPE = new Type<>(IsekaiMahou.id("familiars_action"));
        public static final StreamCodec<FriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of((buf, a) -> {
            buf.writeVarInt(a.op);
            buf.writeUtf(a.text, 64);
        }, buf -> new Action(buf.readVarInt(), buf.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Cliente → servidor: ensinar esta receita de apoio ao familiar (o servidor valida — ver {@link FamiliarSpell}). */
    public record Teach(SpellRecipe recipe) implements CustomPacketPayload {
        public static final Type<Teach> TYPE = new Type<>(IsekaiMahou.id("familiars_teach"));
        public static final StreamCodec<FriendlyByteBuf, Teach> STREAM_CODEC = StreamCodec.of(
                (buf, t) -> SpellRecipe.STREAM_CODEC.encode(buf, t.recipe), buf -> new Teach(SpellRecipe.STREAM_CODEC.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Um motivo da forma: tipo ({@code class/race/element/feat}) e id. */
    public record Reason(String kind, String arg) {}

    /**
     * Servidor → cliente. {@code nextLevel}/{@code nextBond} = o que falta para o próximo estágio (0 = último estágio);
     * {@code candidate} = forma para a qual o jogador está "puxando" ("" = nenhuma) e {@code drift}/{@code driftMax} o
     * quanto falta para a mudança. {@code spell} = a receita de apoio ensinada (vazia = nenhuma) e {@code spellWait}
     * os segundos que faltam para ela recarregar.
     */
    public record State(boolean awakened, String form, int stage, int bond, boolean summoned, boolean help, String name,
                        int nextLevel, int nextBond, String candidate, int drift, int driftMax, List<Reason> reasons,
                        SpellRecipe spell, int spellWait)
            implements CustomPacketPayload {
        public static final State EMPTY = new State(false, "", 1, 0, false, true, "", 0, 0, "", 0, 0, List.of(), SpellRecipe.EMPTY, 0);
        public static final Type<State> TYPE = new Type<>(IsekaiMahou.id("familiars_state"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(State::write, State::read);

        private static void write(FriendlyByteBuf buf, State s) {
            buf.writeBoolean(s.awakened);
            buf.writeUtf(s.form, 32);
            buf.writeVarInt(s.stage);
            buf.writeVarInt(s.bond);
            buf.writeBoolean(s.summoned);
            buf.writeBoolean(s.help);
            buf.writeUtf(s.name, 64);
            buf.writeVarInt(s.nextLevel);
            buf.writeVarInt(s.nextBond);
            buf.writeUtf(s.candidate, 32);
            buf.writeVarInt(s.drift);
            buf.writeVarInt(s.driftMax);
            buf.writeVarInt(s.reasons.size());
            for (Reason r : s.reasons) {
                buf.writeUtf(r.kind, 16);
                buf.writeUtf(r.arg, 32);
            }
            SpellRecipe.STREAM_CODEC.encode(buf, s.spell);
            buf.writeVarInt(s.spellWait);
        }

        private static State read(FriendlyByteBuf buf) {
            boolean awakened = buf.readBoolean();
            String form = buf.readUtf(32);
            int stage = buf.readVarInt(), bond = buf.readVarInt();
            boolean summoned = buf.readBoolean(), help = buf.readBoolean();
            String name = buf.readUtf(64);
            int nextLevel = buf.readVarInt(), nextBond = buf.readVarInt();
            String candidate = buf.readUtf(32);
            int drift = buf.readVarInt(), driftMax = buf.readVarInt();
            int n = Math.min(8, buf.readVarInt());
            List<Reason> reasons = new ArrayList<>(n);
            for (int i = 0; i < n; i++) reasons.add(new Reason(buf.readUtf(16), buf.readUtf(32)));
            SpellRecipe spell = SpellRecipe.STREAM_CODEC.decode(buf);
            int spellWait = buf.readVarInt();
            return new State(awakened, form, stage, bond, summoned, help, name, nextLevel, nextBond, candidate, drift, driftMax, reasons,
                    spell, spellWait);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Gancho preenchido pela parte de cliente (no servidor dedicado continua vazio). */
    public static Consumer<State> clientState = s -> {};

    private FamiliarNetwork() {}

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("familiars-1");
        r.playToServer(Action.TYPE, Action.STREAM_CODEC, (a, c) -> {
            if (c.player() instanceof ServerPlayer sp) FamiliarService.onAction(sp, a);
        });
        r.playToServer(Teach.TYPE, Teach.STREAM_CODEC, (t, c) -> {
            if (c.player() instanceof ServerPlayer sp) FamiliarSpell.teach(sp, t.recipe());
        });
        r.playToClient(State.TYPE, State.STREAM_CODEC, (s, c) -> clientState.accept(s));
    }
}
