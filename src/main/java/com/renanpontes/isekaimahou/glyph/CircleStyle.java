package com.renanpontes.isekaimahou.glyph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v1.0 (release) — Círculo mágico POR MAGIA: cada receita escolhe a forma, o símbolo do centro e a cor do círculo de
 * conjuração (aos pés e na frente da mão). Campo opcional da {@link SpellRecipe}: receitas antigas = {@link #DEFAULT}
 * (o círculo de sempre, escolhido pelo tier da magia, na cor da magia).
 * <p>
 * Só visual — não muda custo, dano nem tempo. As artes ficam em {@code textures/particle/fx_circle_s<forma>.png} e
 * {@code fx_circle_y<símbolo>.png} (geradas por {@code tools/gen/mastery_circles.py}).
 *
 * @param shape  0 = padrão (pelo tier); 1..{@link #SHAPES} = forma escolhida
 * @param symbol 0 = sem símbolo; 1..{@link #SYMBOLS} = símbolo no centro
 * @param color  cor RGB do círculo, ou -1 = a cor da magia
 */
public record CircleStyle(int shape, int symbol, int color) {
    /** Quantas formas existem além da padrão. */
    public static final int SHAPES = 7;
    /** Quantos símbolos existem (além de "nenhum"). */
    public static final int SYMBOLS = 12;

    /** Bits da forma/símbolo dentro das flags da partícula de círculo ({@code SpellFxParticles}). */
    public static final int SHAPE_SHIFT = 12;
    public static final int SHAPE_MASK = 0x7;
    public static final int SYMBOL_SHIFT = 15;
    public static final int SYMBOL_MASK = 0xF;

    public static final CircleStyle DEFAULT = new CircleStyle(0, 0, -1);

    public static final Codec<CircleStyle> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("shape", 0).forGetter(CircleStyle::shape),
            Codec.INT.optionalFieldOf("symbol", 0).forGetter(CircleStyle::symbol),
            Codec.INT.optionalFieldOf("color", -1).forGetter(CircleStyle::color)
    ).apply(i, CircleStyle::new));

    public static final StreamCodec<ByteBuf, CircleStyle> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CircleStyle::shape,
            ByteBufCodecs.VAR_INT, CircleStyle::symbol,
            ByteBufCodecs.INT, CircleStyle::color,
            CircleStyle::new);

    public CircleStyle {
        // valores fora da faixa (pacote adulterado, save de versão futura) voltam ao padrão
        if (shape < 0 || shape > SHAPES) shape = 0;
        if (symbol < 0 || symbol > SYMBOLS) symbol = 0;
        color = color < 0 ? -1 : color & 0xFFFFFF;
    }

    public boolean isDefault() {
        return shape == 0 && symbol == 0 && color < 0;
    }

    public CircleStyle withShape(int s) { return new CircleStyle(s, symbol, color); }
    public CircleStyle withSymbol(int s) { return new CircleStyle(shape, s, color); }
    public CircleStyle withColor(int c) { return new CircleStyle(shape, symbol, c); }

    /** Cor do círculo: a escolhida, ou {@code spellRgb} (cor da magia). */
    public int rgb(int spellRgb) {
        return color >= 0 ? color : spellRgb & 0xFFFFFF;
    }

    /** Bits de forma + símbolo para somar às flags da partícula de círculo. */
    public int flags() {
        return ((shape & SHAPE_MASK) << SHAPE_SHIFT) | ((symbol & SYMBOL_MASK) << SYMBOL_SHIFT);
    }

    public static int shapeOf(int flags) {
        int s = (flags >> SHAPE_SHIFT) & SHAPE_MASK;
        return s > SHAPES ? 0 : s;
    }

    public static int symbolOf(int flags) {
        int s = (flags >> SYMBOL_SHIFT) & SYMBOL_MASK;
        return s > SYMBOLS ? 0 : s;
    }

    public static MutableComponent shapeName(int shape) {
        return Component.translatable("circle.isekai_mahou.shape." + Math.max(0, Math.min(SHAPES, shape)));
    }

    public static MutableComponent symbolName(int symbol) {
        return Component.translatable("circle.isekai_mahou.symbol." + Math.max(0, Math.min(SYMBOLS, symbol)));
    }
}
