package com.renanpontes.isekaimahou.feature.journal;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.journal.data.JournalBook;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do Diário:
 * <ul>
 *   <li>{@link Book} (S→C): os capítulos (JSON) para a aba do Diário — no login e a cada /reload;</li>
 *   <li>{@link Target} (S→C): onde fica a estrutura do objetivo atual (seta do rastreador);</li>
 *   <li>{@link CallGuide} (C→S): botão "Chamar Espírito Guia" da aba;</li>
 *   <li>{@link Tutorial} (C→S): o cliente avisa os passos do tutorial que só ele enxerga (abriu o menu, montou uma magia).</li>
 * </ul>
 */
public final class JournalNetwork {
    /** Limite do JSON do livro (bem acima do tamanho real: ~70 KB com as 15 rotas de classe da v1.0). */
    private static final int MAX_BOOK = 1 << 19;

    public record Book(String json) implements CustomPacketPayload {
        public static final Type<Book> TYPE = new Type<>(IsekaiMahou.id("journal_book"));
        public static final StreamCodec<ByteBuf, Book> STREAM_CODEC =
                ByteBufCodecs.stringUtf8(MAX_BOOK).map(Book::new, Book::json);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Alvo do passo {@code step} ("capitulo.passo"; vazio = nenhum alvo) na trilha {@code track}
     * ({@link JournalTargets#MAIN} história, {@link JournalTargets#CLASS} rota da classe). {@code found} false = nada por
     * perto. {@code yKnown} false = altura desconhecida.
     */
    public record Target(int track, String step, boolean found, BlockPos pos, boolean yKnown, String dimension) implements CustomPacketPayload {
        public static final Type<Target> TYPE = new Type<>(IsekaiMahou.id("journal_target"));
        public static final StreamCodec<ByteBuf, Target> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Target::track,
                ByteBufCodecs.stringUtf8(128), Target::step, ByteBufCodecs.BOOL, Target::found,
                BlockPos.STREAM_CODEC, Target::pos, ByteBufCodecs.BOOL, Target::yKnown,
                ByteBufCodecs.stringUtf8(256), Target::dimension, Target::new);
        public static final Target NONE = none(JournalTargets.MAIN);

        /** "Sem alvo" na trilha. */
        public static Target none(int track) {
            return new Target(track, "", false, BlockPos.ZERO, false, "");
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CallGuide() implements CustomPacketPayload {
        public static final CallGuide INSTANCE = new CallGuide();
        public static final Type<CallGuide> TYPE = new Type<>(IsekaiMahou.id("journal_call_guide"));
        public static final StreamCodec<ByteBuf, CallGuide> STREAM_CODEC = StreamCodec.unit(INSTANCE);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Passo de tutorial visto pelo cliente: "menu", "grimoire" ou "spell_built" (o servidor confere a lista). */
    public record Tutorial(String key) implements CustomPacketPayload {
        public static final Type<Tutorial> TYPE = new Type<>(IsekaiMahou.id("journal_tutorial"));
        public static final StreamCodec<ByteBuf, Tutorial> STREAM_CODEC =
                ByteBufCodecs.stringUtf8(32).map(Tutorial::new, Tutorial::key);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("journal-3");
        // Só guardam dados: nada de classes de cliente aqui (a aba e o rastreador leem JournalBook/JournalTargets).
        r.playToClient(Book.TYPE, Book.STREAM_CODEC, (p, c) -> JournalBook.setClient(p.json()));
        r.playToClient(Target.TYPE, Target.STREAM_CODEC,
                (p, c) -> JournalTargets.accept(p.track(), p.step(), p.found(), p.pos(), p.yKnown(), p.dimension()));
        r.playToServer(CallGuide.TYPE, CallGuide.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) JournalService.callGuide(sp);
        });
        r.playToServer(Tutorial.TYPE, Tutorial.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) JournalService.tutorial(sp, p.key());
        });
    }

    public static void sendBook(ServerPlayer player) {
        String json = JournalBook.server().json();
        if (json.length() > MAX_BOOK) {
            IsekaiMahou.LOGGER.error("Diario: livro grande demais para sincronizar ({} caracteres)", json.length());
            return;
        }
        PacketDistributor.sendToPlayer(player, new Book(json));
    }

    private JournalNetwork() {}
}
