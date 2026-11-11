package com.renanpontes.isekaimahou.feature.ritual_mage.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.core.Element;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Conteúdo de uma Nota de Pesquisa: de qual fenômeno veio e o elemento da runa que ela vira na Mesa. */
public record ResearchNote(ResourceLocation phenomenon, Element element) {
    public static final Codec<ResearchNote> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("phenomenon").forGetter(ResearchNote::phenomenon),
            Element.CODEC.fieldOf("element").forGetter(ResearchNote::element)
    ).apply(i, ResearchNote::new));

    /** Elemento pela posição no enum (pacotes). */
    public static final StreamCodec<ByteBuf, Element> ELEMENT_STREAM = ByteBufCodecs.VAR_INT.map(
            i -> Element.values()[Math.max(0, Math.min(Element.values().length - 1, i))], Element::ordinal);

    public static final StreamCodec<ByteBuf, ResearchNote> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ResearchNote::phenomenon,
            ELEMENT_STREAM, ResearchNote::element,
            ResearchNote::new);
}
