package com.renanpontes.isekaimahou.feature.creatures_a.entity.slime;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.creatures_a.CaRegistry;

import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.item.Item;

/**
 * Variedades de slime do módulo. Os números valem por "tamanho" (1, 2, 4...), igual ao slime vanilla:
 * vida = 1 + hp * tamanho², dano = dmg * tamanho, velocidade = base + porTamanho * tamanho.
 */
public enum SlimeKind {
    //            id              hp     dmg   vel   vel/t  armadura/t  kb    pulo  neutro elemento (absorção/visual)
    BLUE("slime",          1.25F, 0.75F, 0.20F, 0.10F, 0.0F, 0.0F, 0.42F, true, Element.WATER, new int[]{1, 1, 2, 2, 4}),
    FIRE("fire_slime",     1.25F, 1.00F, 0.20F, 0.10F, 0.0F, 0.0F, 0.42F, false, Element.FIRE, new int[]{1, 2, 2, 4}),
    WATER("water_slime",   1.50F, 0.90F, 0.20F, 0.10F, 0.0F, 0.0F, 0.42F, false, Element.WATER, new int[]{1, 2, 2, 4}),
    STONE("stone_slime",   2.50F, 1.25F, 0.15F, 0.06F, 2.0F, 0.6F, 0.40F, false, Element.EARTH, new int[]{2, 2, 4}),
    WIND("wind_slime",     1.00F, 0.80F, 0.30F, 0.12F, 0.0F, 0.0F, 0.62F, false, Element.WIND, new int[]{1, 1, 2}),
    POISON("poison_jelly", 1.25F, 0.60F, 0.20F, 0.10F, 0.0F, 0.0F, 0.42F, false, Element.SHADOW, new int[]{1, 2, 2, 4}),
    KING("king_slime",     0.00F, 0.00F, 0.55F, 0.00F, 0.0F, 0.85F, 0.46F, false, Element.WATER, new int[]{6});

    public final String id;
    private final float hp;
    private final float dmg;
    private final float speed;
    private final float speedPerSize;
    private final float armorPerSize;
    public final float knockbackResist;
    public final float jump;
    public final boolean neutral;
    public final Element element;
    private final int[] naturalSizes;

    SlimeKind(String id, float hp, float dmg, float speed, float speedPerSize, float armorPerSize, float kb, float jump,
              boolean neutral, Element element, int[] naturalSizes) {
        this.id = id;
        this.hp = hp;
        this.dmg = dmg;
        this.speed = speed;
        this.speedPerSize = speedPerSize;
        this.armorPerSize = armorPerSize;
        this.knockbackResist = kb;
        this.jump = jump;
        this.neutral = neutral;
        this.element = element;
        this.naturalSizes = naturalSizes;
    }

    public double health(int size) {
        if (this == KING) return KingSlime.MAX_HEALTH;
        return 1.0 + hp * size * size;
    }

    public double damage(int size) {
        // Dano de contato repete a cada meio segundo enquanto encosta: 6 já pesa (cubo de magma grande).
        if (this == KING) return 6.0;
        return dmg * size;
    }

    public double speed(int size) {
        return speed + speedPerSize * size;
    }

    public double armor(int size) {
        if (this == KING) return 4.0;
        return armorPerSize > 0 ? 2.0 + armorPerSize * size : 0.0;
    }

    public int xp(int size) {
        return this == KING ? 60 : Math.max(1, size + (this == BLUE ? 0 : 1));
    }

    /** Tamanho ao nascer naturalmente (dificuldade alta puxa um pouco para cima). */
    public int pickNaturalSize(RandomSource random, DifficultyInstance difficulty) {
        int i = random.nextInt(naturalSizes.length);
        if (i < naturalSizes.length - 1 && random.nextFloat() < 0.25F * difficulty.getSpecialMultiplier()) i++;
        return naturalSizes[i];
    }

    /** Gel que a variedade solta (também usado nas partículas ao pousar). */
    public Supplier<? extends Item> gel() {
        return switch (this) {
            case FIRE -> CaRegistry.FIRE_SLIME_GEL;
            case WATER -> CaRegistry.WATER_SLIME_GEL;
            case STONE -> CaRegistry.STONE_SLIME_GEL;
            case WIND -> CaRegistry.WIND_SLIME_GEL;
            case POISON -> CaRegistry.POISON_JELLY_GEL;
            default -> CaRegistry.SLIME_GEL;
        };
    }

    /** Elementos (exatos) que a variedade absorve sem dano — tratados na entidade para não anular as fusões. */
    public boolean absorbs(Element e) {
        return switch (this) {
            case FIRE -> e == Element.FIRE || e == Element.MAGMA || e == Element.HELLFIRE || e == Element.SUN;
            case WATER -> e == Element.WATER;
            default -> false;
        };
    }
}
