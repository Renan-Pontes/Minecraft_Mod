package com.renanpontes.isekaimahou.core.item;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.core.MagicPath;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Conteúdo de uma Lição escrita ({@code isekai_mahou:lesson}): o glifo de origem ensinado, quem escreveu (nome e
 * UUID — o professor ganha XP quando alguém aprende) e a classe do professor na hora de escrever.
 */
public record LessonData(ResourceLocation glyph, String author, UUID authorId, String authorPath) {
    public static final Codec<LessonData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("glyph").forGetter(LessonData::glyph),
            Codec.STRING.fieldOf("author").forGetter(LessonData::author),
            UUIDUtil.CODEC.fieldOf("author_id").forGetter(LessonData::authorId),
            Codec.STRING.optionalFieldOf("author_path", MagicPath.NONE.id).forGetter(LessonData::authorPath)
    ).apply(i, LessonData::new));

    public static final StreamCodec<ByteBuf, LessonData> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, LessonData::glyph,
            ByteBufCodecs.STRING_UTF8, LessonData::author,
            UUIDUtil.STREAM_CODEC, LessonData::authorId,
            ByteBufCodecs.STRING_UTF8, LessonData::authorPath,
            LessonData::new);

    /** Classe do professor (NONE se o id não existe mais). */
    public MagicPath teacherPath() {
        return MagicPath.byId(authorPath);
    }
}
