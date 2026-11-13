package com.renanpontes.isekaimahou.feature.end_library.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.end_library.ElRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * Equipamento Estelar — o topo do mod (acima do netherite), forjado na mesa de ferraria a partir do equipamento de
 * netherite + Lingote Estelar + Molde de Melhoria Estelar. Só obedece a quem chegou ao Círculo {@value #MIN_CIRCLE}
 * (ver {@code ElEvents}: sem o Círculo, a arma não acerta, a picareta não quebra e a armadura sai do corpo).
 * Conjunto completo: visão no escuro e "Passo Estelar" (cair no vazio do End te devolve ao último chão firme).
 */
public final class StarGear {
    public static final int MIN_CIRCLE = 7;
    public static final Tier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2600, 10.0F, 5.0F, 18,
            () -> Ingredient.of(ElRegistries.STAR_INGOT.get()));

    /** Marca das peças estelares (para a checagem de Círculo). */
    public interface Piece {}

    public static boolean isStar(ItemStack stack) {
        return stack.getItem() instanceof Piece;
    }

    static void tooltip(List<Component> tip) {
        tip.add(Component.translatable("item.isekai_mahou.star_gear.requires", MIN_CIRCLE).withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.add(Component.translatable("item.isekai_mahou.star_gear.set_bonus").withStyle(ChatFormatting.GRAY));
    }

    public static class Sword extends SwordItem implements Piece {
        public Sword() {
            super(TIER, new Item.Properties().fireResistant().rarity(Rarity.EPIC).attributes(SwordItem.createAttributes(TIER, 3, -2.4F)));
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
            tooltip(tip);
        }
    }

    public static class Pickaxe extends PickaxeItem implements Piece {
        public Pickaxe() {
            super(TIER, new Item.Properties().fireResistant().rarity(Rarity.EPIC).attributes(DiggerItem.createAttributes(TIER, 1.0F, -2.8F)));
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
            tooltip(tip);
        }
    }

    public static class Armor extends ArmorItem implements Piece {
        public Armor(Holder<ArmorMaterial> material, ArmorItem.Type type) {
            super(material, type, new Item.Properties().fireResistant().rarity(Rarity.EPIC).durability(type.getDurability(42)));
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
            tooltip(tip);
        }
    }

    private StarGear() {}
}
