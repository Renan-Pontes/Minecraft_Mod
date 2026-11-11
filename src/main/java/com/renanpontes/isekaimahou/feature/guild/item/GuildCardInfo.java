package com.renanpontes.isekaimahou.feature.guild.item;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Conteúdo do Cartão de Aventureiro (componente de item): dono, rank e números.
 * O servidor atualiza os cartões do dono quando os dados da Guilda mudam — o tooltip mostra o estado atual.
 */
public record GuildCardInfo(UUID owner, String name, int rank, int points, int completed) {
    public static final Codec<GuildCardInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(GuildCardInfo::owner),
            Codec.STRING.fieldOf("name").forGetter(GuildCardInfo::name),
            Codec.INT.optionalFieldOf("rank", 1).forGetter(GuildCardInfo::rank),
            Codec.INT.optionalFieldOf("points", 0).forGetter(GuildCardInfo::points),
            Codec.INT.optionalFieldOf("completed", 0).forGetter(GuildCardInfo::completed)
    ).apply(i, GuildCardInfo::new));

    public static final StreamCodec<ByteBuf, GuildCardInfo> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, GuildCardInfo::owner,
            ByteBufCodecs.stringUtf8(64), GuildCardInfo::name,
            ByteBufCodecs.VAR_INT, GuildCardInfo::rank,
            ByteBufCodecs.VAR_INT, GuildCardInfo::points,
            ByteBufCodecs.VAR_INT, GuildCardInfo::completed,
            GuildCardInfo::new);
}
