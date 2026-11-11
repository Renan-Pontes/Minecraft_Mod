package com.renanpontes.isekaimahou.core.classes;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponStances;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do núcleo de classes (v0.9: teclas seguradas; v1.0: escrever Lições). */
public final class ClassesPayloads {

    /**
     * C→S: quais teclas de slot (Z X C G) o jogador está segurando agora (bit i = slot i). Enviado só quando muda.
     * A Postura dura enquanto a tecla do slot dela estiver segurada.
     */
    public record SlotKeys(int mask) implements CustomPacketPayload {
        public static final Type<SlotKeys> TYPE = new Type<>(IsekaiMahou.id("classes_slot_keys"));
        public static final StreamCodec<ByteBuf, SlotKeys> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(SlotKeys::new, SlotKeys::mask);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * S→C: abre a tela de escrever Lição (o jogador usou uma Lição em branco). {@code hand} = ordinal da
     * {@code InteractionHand}; {@code glyphs} = glifos de origem da classe dele que ele conhece (o que pode ensinar).
     */
    public record OpenLessonWriter(int hand, List<ResourceLocation> glyphs) implements CustomPacketPayload {
        public static final Type<OpenLessonWriter> TYPE = new Type<>(IsekaiMahou.id("classes_open_lesson_writer"));
        public static final StreamCodec<ByteBuf, OpenLessonWriter> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenLessonWriter::hand,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(512)), OpenLessonWriter::glyphs,
                OpenLessonWriter::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C→S: escrever o glifo escolhido na Lição em branco da mão {@code hand}. O servidor confere tudo de novo. */
    public record WriteLesson(int hand, ResourceLocation glyph) implements CustomPacketPayload {
        public static final Type<WriteLesson> TYPE = new Type<>(IsekaiMahou.id("classes_write_lesson"));
        public static final StreamCodec<ByteBuf, WriteLesson> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WriteLesson::hand,
                ResourceLocation.STREAM_CODEC, WriteLesson::glyph,
                WriteLesson::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(SlotKeys.TYPE, SlotKeys.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) WeaponStances.setHeld(sp, p.mask() & ((1 << Progression.SLOTS) - 1));
        });
        r.playToServer(WriteLesson.TYPE, WriteLesson.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) Lessons.write(sp, p.hand(), p.glyph());
        });
        // Código de cliente só dentro do lambda (servidor dedicado nunca executa este handler)
        r.playToClient(OpenLessonWriter.TYPE, OpenLessonWriter.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.classes.LessonClient.openWriter(p.hand(), p.glyphs()));
    }

    private ClassesPayloads() {}
}
