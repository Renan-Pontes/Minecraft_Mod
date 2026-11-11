package com.renanpontes.isekaimahou.feature.armory.item;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Escudo do Cavaleiro: escudo de verdade (bloqueia com o botão direito, conta para a técnica Aparar, machados derrubam
 * a guarda) com modelo 3D próprio (escudo-pipa de aço com o brasão do sol), o dobro da durabilidade do escudo comum e
 * armadura na mão secundária (v1.0: +3 de armadura e +1,5 de resistência, ×1,5 da v0.8). Equipamento avulso desde a
 * v1.0 (o Cavaleiro da v0.9 saiu): qualquer classe usa. Ao bloquear um golpe corpo a corpo, empurra o atacante (Muralha); a Lâmina do Cavaleiro
 * IV ainda devolve parte do dano (Bastião) — ver {@code ClassWeaponPassives}.
 */
public class KnightShieldItem extends ShieldItem {
    public static final int DURABILITY = 672;
    private static final ResourceLocation ARMOR_ID = IsekaiMahou.id("knight_shield_armor");
    private static final ResourceLocation TOUGHNESS_ID = IsekaiMahou.id("knight_shield_toughness");
    private static final ResourceLocation KNOCKBACK_ID = IsekaiMahou.id("knight_shield_knockback");
    private static final String TIP = "tooltip.isekai_mahou.armory.knight_shield.";

    public KnightShieldItem(Properties props) {
        super(props.stacksTo(1).durability(DURABILITY).attributes(attributes()));
    }

    private static ItemAttributeModifiers attributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(ARMOR_ID, 3.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.OFFHAND)
                .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_ID, 1.5, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.OFFHAND)
                .add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(KNOCKBACK_ID, 0.1, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.OFFHAND)
                .build();
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        // O escudo comum muda o nome pela cor do estandarte; este não aceita estandarte
        return getDescriptionId();
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(Items.IRON_INGOT) || repair.is(CoreRegistries.MANA_CRYSTAL.get()) || repair.is(ItemTags.PLANKS);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 12;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.armory.generic_weapon", MagicPath.Category.PHYSICAL.displayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.armory.trait", Component.translatable(TIP + "trait"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(TIP + "trait.desc").withStyle(ChatFormatting.GRAY));
    }
}
