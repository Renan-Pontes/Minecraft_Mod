package com.renanpontes.isekaimahou.feature.class_enchanter.item;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * Manoplas do Encantador (arma do kit e tiers I–IV). Socos rápidos (2,2 golpes/s), sem varrida, um pouco de velocidade de
 * movimento. São o "condutor" dos encantamentos: com elas na mão, cada golpe do Encantador estende os encantamentos dele
 * (ver {@code EnchanterPassives}); com a outra mão vazia aparecem as duas manoplas.
 */
public class EnchanterGauntletsItem extends SwordItem {
    /** Dano extra (além do soco de 1) por tier: kit, I, II, III, IV. */
    public static final float[] DAMAGE = {3.5F, 4.25F, 5.0F, 6.0F, 7.0F};
    public static final float SPEED = -1.8F;
    public static final double[] MOVE = {0.03, 0.04, 0.05, 0.06, 0.07};
    private static final int[] DURABILITY = {380, 520, 1100, 1700, 2400};

    private final int tier;

    public EnchanterGauntletsItem(int tier, Properties props) {
        super(tierOf(tier), props.attributes(attributes(tier)));
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    private static Tier tierOf(int tier) {
        TagKey<Block> incorrect = tier <= 1 ? BlockTags.INCORRECT_FOR_IRON_TOOL
                : tier <= 3 ? BlockTags.INCORRECT_FOR_DIAMOND_TOOL : BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        return new SimpleTier(incorrect, DURABILITY[tier], 6.0F + tier, 0.0F, 16 + tier * 2,
                () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get()));
    }

    private static ItemAttributeModifiers attributes(int tier) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, DAMAGE[tier], AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, SPEED, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.MOVEMENT_SPEED, new AttributeModifier(IsekaiMahou.id("enchanter_gauntlets.move"), MOVE[tier],
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        if (ability == ItemAbilities.SWORD_SWEEP) return false;
        return super.canPerformAction(stack, ability);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.enchanter_gauntlets.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.enchanter_gauntlets.conduct").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.enchanter_gauntlets.twin").withStyle(ChatFormatting.DARK_GRAY));
    }
}
