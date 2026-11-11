package com.renanpontes.isekaimahou.feature.bosses_d.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.Progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;

/**
 * Elemento/rank dos chefes de Provação. Quem invoca (módulo trials, comando) grava no persistentData:
 * {@code "isekai_mahou.trial_element"} (id do elemento) e {@code "isekai_mahou.trial_rank"} (1..7).
 * Sem nada gravado: Arcano, rank 1.
 */
public final class TrialElement {
    public static final String KEY_ELEMENT = "isekai_mahou.trial_element";
    public static final String KEY_RANK = "isekai_mahou.trial_rank";

    public static Element readElement(Entity e) {
        CompoundTag pd = e.getPersistentData();
        Element el = pd.contains(KEY_ELEMENT) ? Element.byId(pd.getString(KEY_ELEMENT)) : null;
        return el != null ? el : Element.ARCANE;
    }

    public static int readRank(Entity e) {
        CompoundTag pd = e.getPersistentData();
        int r = pd.contains(KEY_RANK) ? pd.getInt(KEY_RANK) : 1;
        return Mth.clamp(r, 1, Progression.MAX_TRIAL_RANK);
    }

    /** Grava elemento e rank para o próximo 1º tick (comando, ovo, outros módulos). */
    public static void write(Entity e, Element element, int rank) {
        e.getPersistentData().putString(KEY_ELEMENT, element.id);
        e.getPersistentData().putInt(KEY_RANK, Mth.clamp(rank, 1, Progression.MAX_TRIAL_RANK));
    }

    /** Elemento do "corpo" (textura e golpes principais): base/arcano = ele mesmo; fusão = pai A. */
    public static Element body(Element e) {
        return e.isFusion() ? e.parentA : e;
    }

    /** Elemento do brilho (e golpes extras): fusão = pai B; senão ele mesmo. */
    public static Element accent(Element e) {
        return e.isFusion() ? e.parentB : e;
    }

    /** Elemento oposto (fraqueza) de um elemento BASE, ou null. */
    @Nullable
    public static Element opposite(Element base) {
        return switch (base) {
            case FIRE -> Element.WATER;
            case WATER -> Element.FIRE;
            case EARTH -> Element.WIND;
            case WIND -> Element.EARTH;
            case LIGHT -> Element.SHADOW;
            case SHADOW -> Element.LIGHT;
            default -> null;
        };
    }

    /** Cor RGB clareada (brilho do modelo e partículas). */
    public static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r + (255 - r) * f);
        g = (int) (g + (255 - g) * f);
        b = (int) (b + (255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** Mistura duas cores RGB (t = 0 → a, 1 → b). */
    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Cor da barra de chefe: base pelo elemento; fusões pela cor mais próxima. */
    public static BossEvent.BossBarColor barColor(Element e) {
        switch (e) {
            case FIRE, MAGMA, HELLFIRE: return BossEvent.BossBarColor.RED;
            case WATER, ABYSS, ICE: return BossEvent.BossBarColor.BLUE;
            case EARTH, NATURE: return BossEvent.BossBarColor.GREEN;
            case WIND, STEAM, PURITY, ASTRAL: return BossEvent.BossBarColor.WHITE;
            case LIGHT, SUN, SAND, LIGHTNING: return BossEvent.BossBarColor.YELLOW;
            case SHADOW, VOID, GRAVITY: return BossEvent.BossBarColor.PURPLE;
            default: return BossEvent.BossBarColor.PINK;
        }
    }

    private TrialElement() {}
}
