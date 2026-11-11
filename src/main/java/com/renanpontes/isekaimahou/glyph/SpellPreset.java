package com.renanpontes.isekaimahou.glyph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v1.0 — Preset: um rascunho de magia NOMEADO guardado no {@code MagicData} (até {@code MagicData.MAX_PRESETS}).
 * Diferente da biblioteca (magias prontas para gravar em pergaminho), o preset é para testar variações no Grimório:
 * salvar, carregar no editor ou direto num slot, renomear e apagar ({@code MagicApi.savePreset/loadPreset...}).
 *
 * @param name   nome dado pelo jogador (1..{@link #MAX_NAME} caracteres; vazio vira o nome da receita)
 * @param recipe a receita (pode ser inválida: rascunho)
 */
public record SpellPreset(String name, SpellRecipe recipe) {
    public static final int MAX_NAME = 32;

    public static final Codec<SpellPreset> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(SpellPreset::name),
            SpellRecipe.CODEC.fieldOf("recipe").forGetter(SpellPreset::recipe)
    ).apply(i, SpellPreset::new));

    public static final StreamCodec<ByteBuf, SpellPreset> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_NAME * 4), SpellPreset::name,
            SpellRecipe.STREAM_CODEC, SpellPreset::recipe,
            SpellPreset::new);

    public SpellPreset {
        recipe = recipe == null ? SpellRecipe.EMPTY : recipe;
        name = cleanName(name);
        if (name.isEmpty()) name = cleanName(recipe.name());
    }

    /** Nome sem espaços nas pontas, sem quebras de linha e no máximo {@link #MAX_NAME} caracteres. */
    public static String cleanName(String raw) {
        if (raw == null) return "";
        String n = raw.replaceAll("[\\r\\n\\t]", " ").trim();
        return n.length() > MAX_NAME ? n.substring(0, MAX_NAME).trim() : n;
    }

    public SpellPreset withName(String n) { return new SpellPreset(n, recipe); }
}
