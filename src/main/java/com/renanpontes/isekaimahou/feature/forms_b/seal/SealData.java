package com.renanpontes.isekaimahou.feature.forms_b.seal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Magia gravada numa arma (forma Selo): a receita, o elemento, o poder da conjuração e as cargas.
 * {@code glintAdded} lembra se fomos nós que ligamos o brilho de encantamento (para desligar no fim).
 */
public record SealData(SpellRecipe recipe, Element element, float power, int charges, int maxCharges, boolean glintAdded) {

    private static final StreamCodec<ByteBuf, Element> ELEMENT_STREAM = ByteBufCodecs.idMapper(
            i -> Element.values()[Math.floorMod(i, Element.values().length)], Element::ordinal);

    public static final Codec<SealData> CODEC = RecordCodecBuilder.create(i -> i.group(
            SpellRecipe.CODEC.fieldOf("recipe").forGetter(SealData::recipe),
            Element.CODEC.fieldOf("element").forGetter(SealData::element),
            Codec.FLOAT.optionalFieldOf("power", 1.0F).forGetter(SealData::power),
            Codec.INT.fieldOf("charges").forGetter(SealData::charges),
            Codec.INT.fieldOf("max_charges").forGetter(SealData::maxCharges),
            Codec.BOOL.optionalFieldOf("glint_added", false).forGetter(SealData::glintAdded)
    ).apply(i, SealData::new));

    public static final StreamCodec<ByteBuf, SealData> STREAM_CODEC = StreamCodec.composite(
            SpellRecipe.STREAM_CODEC, SealData::recipe,
            ELEMENT_STREAM, SealData::element,
            ByteBufCodecs.FLOAT, SealData::power,
            ByteBufCodecs.VAR_INT, SealData::charges,
            ByteBufCodecs.VAR_INT, SealData::maxCharges,
            ByteBufCodecs.BOOL, SealData::glintAdded,
            SealData::new);

    public SealData withCharges(int c) {
        return new SealData(recipe, element, power, c, maxCharges, glintAdded);
    }

    /** Cor RGB da magia (a escolhida pelo jogador ou a do elemento). */
    public int rgb() {
        return recipe.color() >= 0 ? recipe.color() & 0xFFFFFF : element.color;
    }
}
