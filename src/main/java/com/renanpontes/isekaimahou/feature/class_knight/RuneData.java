package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Runas gravadas numa lâmina (componente {@code isekai_mahou:knight_runes}): as magias inteiras que a forma
 * "Gravar na Lâmina" escreveu na arma, na ordem de gravação. O rodízio (qual sai no próximo golpe) fica no servidor
 * ({@link RuneEngraving}); o número de espaços depende da arma ({@link KnightSwords#runeSlots}).
 */
public record RuneData(List<SpellRecipe> runes) {
    /** Teto absoluto (o item mais generoso tem 6 espaços com o Juramento IV). */
    public static final int MAX = 8;

    public static final RuneData EMPTY = new RuneData(List.of());

    public static final Codec<RuneData> CODEC = RecordCodecBuilder.create(i -> i.group(
            SpellRecipe.CODEC.listOf().fieldOf("runes").forGetter(RuneData::runes)
    ).apply(i, RuneData::new));

    public static final StreamCodec<ByteBuf, RuneData> STREAM_CODEC =
            SpellRecipe.STREAM_CODEC.apply(ByteBufCodecs.list(MAX)).map(RuneData::new, RuneData::runes);

    public RuneData {
        runes = List.copyOf(runes.size() > MAX ? runes.subList(0, MAX) : runes);
    }

    public boolean isEmpty() {
        return runes.isEmpty();
    }

    public int size() {
        return runes.size();
    }

    /**
     * Grava uma receita. A mesma receita (mesmos glifos) é só atualizada no lugar; se há espaço, entra no fim;
     * sem espaço, a runa MAIS ANTIGA é apagada e a nova entra no fim.
     */
    public Result engrave(SpellRecipe recipe, int slots) {
        int cap = Math.max(1, Math.min(MAX, slots));
        List<SpellRecipe> list = new ArrayList<>(runes);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).glyphs().equals(recipe.glyphs())) {
                list.set(i, recipe);
                while (list.size() > cap) list.remove(0);
                return new Result(new RuneData(list), Math.min(i, cap - 1), false);
            }
        }
        boolean replaced = false;
        while (list.size() >= cap) {
            list.remove(0);
            replaced = true;
        }
        list.add(recipe);
        return new Result(new RuneData(list), list.size() - 1, replaced);
    }

    /** Resultado de uma gravação. {@code replaced} = uma runa antiga foi apagada para caber. */
    public record Result(RuneData data, int index, boolean replaced) {}
}
