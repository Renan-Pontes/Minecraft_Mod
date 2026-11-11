package com.renanpontes.isekaimahou.core;

import java.util.Arrays;
import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

/**
 * Elementos de magia: Arcano (neutro, todos têm), 6 base e 15 fusões (combinação de 2 base).
 * Toda magia pertence a um elemento. Uma magia de fusão pode usar glifos dos dois elementos-pai.
 */
public enum Element implements StringRepresentable {
    ARCANE("arcane", 0xB98CFF, null, null),

    FIRE("fire", 0xFF6A2B, null, null),
    WATER("water", 0x3FA9FF, null, null),
    EARTH("earth", 0xA0743C, null, null),
    WIND("wind", 0x9FF0D0, null, null),
    LIGHT("light", 0xFFE27A, null, null),
    SHADOW("shadow", 0x8A4FC8, null, null),

    MAGMA("magma", 0xFF4500, FIRE, EARTH),
    STEAM("steam", 0xD8E6EE, FIRE, WATER),
    LIGHTNING("lightning", 0x7FDBFF, FIRE, WIND),
    SUN("sun", 0xFFB300, FIRE, LIGHT),
    HELLFIRE("hellfire", 0xC0182E, FIRE, SHADOW),
    NATURE("nature", 0x4FBF4A, WATER, EARTH),
    ICE("ice", 0xA8E8FF, WATER, WIND),
    PURITY("purity", 0xE8F8FF, WATER, LIGHT),
    ABYSS("abyss", 0x2A4F9A, WATER, SHADOW),
    SAND("sand", 0xE3C77A, EARTH, WIND),
    CRYSTAL("crystal", 0xE07AF0, EARTH, LIGHT),
    GRAVITY("gravity", 0x6B5BA8, EARTH, SHADOW),
    ASTRAL("astral", 0x9DB4FF, WIND, LIGHT),
    VOID("void", 0x5A2A8A, WIND, SHADOW),
    TWILIGHT("twilight", 0xC06AA0, LIGHT, SHADOW);

    public static final Codec<Element> CODEC = StringRepresentable.fromEnum(Element::values);
    public static final List<Element> BASE = List.of(FIRE, WATER, EARTH, WIND, LIGHT, SHADOW);
    public static final List<Element> FUSIONS = Arrays.stream(values()).filter(Element::isFusion).toList();

    public final String id;
    /** Cor RGB (sem alpha). Use {@link #argb()} para desenhar. */
    public final int color;
    public final Element parentA;
    public final Element parentB;

    Element(String id, int color, Element parentA, Element parentB) {
        this.id = id;
        this.color = color;
        this.parentA = parentA;
        this.parentB = parentB;
    }

    public boolean isBase() { return this != ARCANE && parentA == null; }
    public boolean isFusion() { return parentA != null; }
    public int argb() { return 0xFF000000 | color; }

    /** Este elemento "é" o base informado? (fusões contam como os dois pais — usado nas reações). */
    public boolean matches(Element base) {
        return this == base || parentA == base || parentB == base;
    }

    /** Uma magia DESTE elemento pode usar um glifo do elemento informado? */
    public boolean canUseGlyphOf(Element glyphElement) {
        return glyphElement == ARCANE || glyphElement == this
                || (isFusion() && (glyphElement == parentA || glyphElement == parentB));
    }

    public MutableComponent displayName() {
        return Component.translatable("element.isekai_mahou." + id).withColor(color);
    }

    /** Fusão de dois elementos base (ordem não importa), ou null. */
    public static Element fusionOf(Element a, Element b) {
        for (Element e : values()) {
            if (e.isFusion() && ((e.parentA == a && e.parentB == b) || (e.parentA == b && e.parentB == a))) return e;
        }
        return null;
    }

    public static Element byId(String id) {
        for (Element e : values()) if (e.id.equals(id)) return e;
        return null;
    }

    @Override
    public String getSerializedName() { return id; }
}
