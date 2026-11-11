package com.renanpontes.isekaimahou.feature.class_smith.forge;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.feature.class_smith.SmithKeys;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Material da arma forjada: dano base, durabilidade, peso, encantabilidade, cor do metal (o renderer tinge as peças) e o
 * custo de forjar uma lâmina nova (ou trocar para este material) na Bigorna Arcana. O Aço Estelar é do módulo
 * ({@code smith_star_metal}, procurado por id).
 */
public enum BladeMaterial {
    //          id           dano  durab. peso encant. cor        veloc.
    IRON("iron", 4.0F, 600, 1.0F, 14, 0xC9CFD8, 0.0F),
    GOLD("gold", 3.0F, 260, 0.8F, 22, 0xF2C94C, 0.2F),
    DIAMOND("diamond", 5.0F, 1700, 1.0F, 10, 0x8FEFE2, 0.0F),
    NETHERITE("netherite", 6.0F, 2400, 1.2F, 15, 0x5A5260, -0.1F),
    STARMETAL("starmetal", 7.0F, 3500, 0.9F, 20, 0x7C93FF, 0.1F);

    public static final BladeMaterial[] VALUES = values();
    /** Id do lingote de Aço Estelar (registrado pelo próprio módulo). */
    public static final String STAR_METAL_ID = "smith_star_metal";

    public final String id;
    public final float damage;
    public final int durability;
    public final float weight;
    public final int enchantability;
    public final int color;
    public final float speed;

    BladeMaterial(String id, float damage, int durability, float weight, int enchantability, int color, float speed) {
        this.id = id;
        this.damage = damage;
        this.durability = durability;
        this.weight = weight;
        this.enchantability = enchantability;
        this.color = color;
        this.speed = speed;
    }

    public MutableComponent displayName() {
        return Component.translatable(SmithKeys.BLADE_MATERIAL + id);
    }

    /** Resiste a lava/fogo como o netherite. */
    public boolean fireResistant() {
        return this == NETHERITE || this == STARMETAL;
    }

    /** Custo de forjar uma lâmina nova deste material (ou trocar o material da atual). */
    public List<ItemStack> cost() {
        return switch (this) {
            case IRON -> List.of(new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.STICK, 1));
            case GOLD -> List.of(new ItemStack(Items.GOLD_INGOT, 3), new ItemStack(Items.STICK, 1));
            case DIAMOND -> List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.STICK, 1));
            case NETHERITE -> List.of(new ItemStack(Items.NETHERITE_INGOT, 1), new ItemStack(Items.DIAMOND, 2));
            case STARMETAL -> List.of(new ItemStack(starMetal(), 2));
        };
    }

    /** Material usado para reparar (na bigorna vanilla) e para pagar a forja. */
    public Supplier<Item> repairItem() {
        return switch (this) {
            case IRON -> () -> Items.IRON_INGOT;
            case GOLD -> () -> Items.GOLD_INGOT;
            case DIAMOND -> () -> Items.DIAMOND;
            case NETHERITE -> () -> Items.NETHERITE_INGOT;
            case STARMETAL -> BladeMaterial::starMetal;
        };
    }

    public static Item starMetal() {
        return BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(STAR_METAL_ID)).orElse(Items.NETHER_STAR);
    }

    public static BladeMaterial byId(String id) {
        for (BladeMaterial m : VALUES) if (m.id.equals(id)) return m;
        return IRON;
    }
}
