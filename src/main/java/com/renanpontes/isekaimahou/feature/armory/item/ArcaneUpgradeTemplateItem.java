package com.renanpontes.isekaimahou.feature.armory.item;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.SmithingTemplateItem;

/**
 * Molde de Melhoria Arcana: na Mesa de Ferraria, molde + arma do Arsenal (tier N) + material → mesma arma no tier N+1,
 * mantendo encantamentos, nome e durabilidade (receitas {@code smithing_transform} em data/isekai_mahou/recipe/armory).
 * O módulo outfits usa o MESMO molde (por id) para subir os trajes de classe de grau; por isso as silhuetas dos
 * espaços vazios alternam entre armas, peças de armadura e os materiais dos dois.
 */
public class ArcaneUpgradeTemplateItem extends SmithingTemplateItem {
    private static final String KEY = "item." + IsekaiMahou.MODID + ".arcane_upgrade_template.";

    public ArcaneUpgradeTemplateItem() {
        super(Component.translatable(KEY + "applies_to").withStyle(ChatFormatting.BLUE),
                Component.translatable(KEY + "ingredients").withStyle(ChatFormatting.BLUE),
                Component.translatable(KEY + "upgrade").withStyle(ChatFormatting.GRAY),
                Component.translatable(KEY + "base_slot"),
                Component.translatable(KEY + "addition_slot"),
                List.of(icon("staff"), vanilla("empty_armor_slot_helmet"), icon("wand"), vanilla("empty_armor_slot_chestplate"),
                        icon("blade"), vanilla("empty_armor_slot_leggings"), icon("scythe"), vanilla("empty_armor_slot_boots"),
                        icon("bow"), icon("axe")),
                List.of(icon("crystal"), icon("diamond"), icon("core"), vanilla("empty_slot_ingot")));
    }

    /** Silhueta de espaço vazio do Arsenal (textura de item 16x16 no atlas de blocos). */
    private static ResourceLocation icon(String name) {
        return IsekaiMahou.id("item/empty_slot_armory_" + name);
    }

    /** Silhueta do vanilla (as mesmas dos moldes de netherita). */
    private static ResourceLocation vanilla(String name) {
        return ResourceLocation.withDefaultNamespace("item/" + name);
    }
}
