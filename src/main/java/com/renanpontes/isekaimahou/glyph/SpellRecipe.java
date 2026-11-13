package com.renanpontes.isekaimahou.glyph;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Uma magia criada pelo jogador: sequência de glifos + nome, cor (RGB, -1 = cor do elemento) e cântico.
 * Gramática (validada por {@link SpellParser}):  [Gatilhos...] FORMA [Aumentos...] (EFEITO [Aumentos...])* [LEIS...]
 * v0.7: a lista continua plana; ligações ({@link LinkGlyph}) dentro dela separam os estágios
 * (ex.: beam, ignite, link_hit, zone, ignite).
 * <p>
 * v1.0 (release): {@code circle} = o círculo mágico desta magia ({@link CircleStyle}: forma, símbolo e cor do círculo
 * de conjuração). Campo OPCIONAL — receitas salvas antes dele carregam com {@link CircleStyle#DEFAULT}.
 */
public record SpellRecipe(List<ResourceLocation> glyphs, String name, int color, String chant, CircleStyle circle) {
    public static final int MAX_NAME = 40;
    public static final int MAX_CHANT = 120;

    public static final Codec<SpellRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("glyphs").forGetter(SpellRecipe::glyphs),
            Codec.STRING.optionalFieldOf("name", "").forGetter(SpellRecipe::name),
            Codec.INT.optionalFieldOf("color", -1).forGetter(SpellRecipe::color),
            Codec.STRING.optionalFieldOf("chant", "").forGetter(SpellRecipe::chant),
            CircleStyle.CODEC.optionalFieldOf("circle", CircleStyle.DEFAULT).forGetter(SpellRecipe::circle)
    ).apply(i, SpellRecipe::new));

    public static final StreamCodec<ByteBuf, SpellRecipe> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(64)), SpellRecipe::glyphs,
            ByteBufCodecs.stringUtf8(MAX_NAME * 4), SpellRecipe::name,
            ByteBufCodecs.INT, SpellRecipe::color,
            ByteBufCodecs.stringUtf8(MAX_CHANT * 4), SpellRecipe::chant,
            CircleStyle.STREAM_CODEC, SpellRecipe::circle,
            SpellRecipe::new);

    public static final SpellRecipe EMPTY = new SpellRecipe(List.of(), "", -1, "");

    /** Receita com o círculo mágico padrão (todo o código anterior ao círculo por magia usa este). */
    public SpellRecipe(List<ResourceLocation> glyphs, String name, int color, String chant) {
        this(glyphs, name, color, chant, CircleStyle.DEFAULT);
    }

    public SpellRecipe {
        glyphs = List.copyOf(glyphs);
        name = name == null ? "" : (name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name);
        chant = chant == null ? "" : (chant.length() > MAX_CHANT ? chant.substring(0, MAX_CHANT) : chant);
        circle = circle == null ? CircleStyle.DEFAULT : circle;
    }

    public boolean isEmpty() {
        return glyphs.isEmpty();
    }

    public SpellRecipe withGlyphs(List<ResourceLocation> g) { return new SpellRecipe(g, name, color, chant, circle); }
    public SpellRecipe withName(String n) { return new SpellRecipe(glyphs, n, color, chant, circle); }
    public SpellRecipe withColor(int c) { return new SpellRecipe(glyphs, name, c, chant, circle); }
    public SpellRecipe withChant(String c) { return new SpellRecipe(glyphs, name, color, c, circle); }
    public SpellRecipe withCircle(CircleStyle c) { return new SpellRecipe(glyphs, name, color, chant, c); }
}
