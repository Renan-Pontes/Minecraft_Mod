package com.renanpontes.isekaimahou.feature.ritual_witch;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Calor embaixo do Caldeirão Ritual. As fontes vêm de tags de bloco (dá para estender por datapack):
 * {@code isekai_mahou:witch_heat_low|medium|high}. Fogueiras apagadas não contam.
 */
public enum WitchHeat implements StringRepresentable {
    NONE("none", ChatFormatting.GRAY),
    LOW("low", ChatFormatting.GOLD),
    MEDIUM("medium", ChatFormatting.RED),
    HIGH("high", ChatFormatting.DARK_RED);

    public static final Codec<WitchHeat> CODEC = StringRepresentable.fromEnum(WitchHeat::values);
    public static final TagKey<Block> TAG_LOW = TagKey.create(Registries.BLOCK, IsekaiMahou.id("witch_heat_low"));
    public static final TagKey<Block> TAG_MEDIUM = TagKey.create(Registries.BLOCK, IsekaiMahou.id("witch_heat_medium"));
    public static final TagKey<Block> TAG_HIGH = TagKey.create(Registries.BLOCK, IsekaiMahou.id("witch_heat_high"));

    public final String id;
    public final ChatFormatting color;
    /** Chave do nome ("witch_heat.isekai_mahou.<id>"). */
    public final String nameKey;
    /** Chave da dica de quais blocos dão este calor ("screen.isekai_mahou.witch_book.heat_<id>"). */
    public final String hintKey;

    WitchHeat(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
        this.nameKey = "witch_heat.isekai_mahou." + id;
        this.hintKey = "screen.isekai_mahou.witch_book.heat_" + id;
    }

    /** Calor fornecido pelo bloco logo abaixo do caldeirão. */
    public static WitchHeat below(BlockGetter level, BlockPos cauldronPos) {
        BlockState s = level.getBlockState(cauldronPos.below());
        if (s.hasProperty(BlockStateProperties.LIT) && !s.getValue(BlockStateProperties.LIT)) return NONE;
        if (s.is(TAG_HIGH)) return HIGH;
        if (s.is(TAG_MEDIUM)) return MEDIUM;
        if (s.is(TAG_LOW)) return LOW;
        return NONE;
    }

    public MutableComponent displayName() {
        return Component.translatable(nameKey).withStyle(color);
    }

    public static WitchHeat byOrdinal(int i) {
        WitchHeat[] v = values();
        return i >= 0 && i < v.length ? v[i] : NONE;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
