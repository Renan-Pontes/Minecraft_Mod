package com.renanpontes.isekaimahou.feature.bosses_e.entity;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;

/**
 * As 5 fases do Dragão Primordial: elemento dominante, cor (barra, telegrafia, brilho das escamas), textura e
 * resistências. Fase = ordinal + 1 (é o que vai no dado sincronizado e no NBT).
 */
public enum DragonPhase {
    FIRE("fire", Element.FIRE, 0xFF6A1F, BossEvent.BossBarColor.RED),
    WATER("water", Element.ICE, 0x7FDFFF, BossEvent.BossBarColor.BLUE),
    EARTH("earth", Element.EARTH, 0xE0A23C, BossEvent.BossBarColor.GREEN),
    STORM("storm", Element.LIGHTNING, 0x8FF0FF, BossEvent.BossBarColor.YELLOW),
    TWILIGHT("twilight", Element.TWILIGHT, 0xC06AA0, BossEvent.BossBarColor.PURPLE);

    public static final int COUNT = values().length;
    private static final String ELEMENT_KEY = "boss.isekai_mahou.primordial_dragon.element.";
    private static final DragonPhase[] VALUES = values();

    public final String id;
    public final Element element;
    public final int rgb;
    public final BossEvent.BossBarColor bar;
    public final ResourceLocation texture;

    DragonPhase(String id, Element element, int rgb, BossEvent.BossBarColor bar) {
        this.id = id;
        this.element = element;
        this.rgb = rgb;
        this.bar = bar;
        this.texture = IsekaiMahou.id("textures/entity/primordial_dragon_" + id + ".png");
    }

    /** Número da fase (1..5). */
    public int number() {
        return ordinal() + 1;
    }

    public static DragonPhase of(int number) {
        return VALUES[Math.max(0, Math.min(COUNT - 1, number - 1))];
    }

    /** Nome curto do elemento da fase ("Fogo", "Água e Gelo"...). */
    public MutableComponent displayName() {
        return Component.translatable(ELEMENT_KEY + id).withColor(rgb);
    }

    /**
     * Multiplicador do dano de magia recebido conforme o último elemento que o acertou. Cada fase resiste ao próprio
     * elemento e é fraca ao oposto; no Crepúsculo resiste a Luz e Sombra (e suas fusões) e a nada é fraca.
     */
    public float elementMultiplier(Element el) {
        return switch (this) {
            case FIRE -> el.matches(Element.FIRE) ? 0.35F : el.matches(Element.WATER) ? 1.5F : 1.0F;
            case WATER -> el.matches(Element.WATER) ? 0.35F : el.matches(Element.FIRE) ? 1.5F : 1.0F;
            case EARTH -> el.matches(Element.EARTH) ? 0.35F : el.matches(Element.WIND) ? 1.5F : 1.0F;
            case STORM -> el.matches(Element.WIND) ? 0.35F : el.matches(Element.EARTH) ? 1.5F : 1.0F;
            case TWILIGHT -> el.matches(Element.LIGHT) || el.matches(Element.SHADOW) ? 0.5F : el == Element.ARCANE ? 1.25F : 1.0F;
        };
    }
}
