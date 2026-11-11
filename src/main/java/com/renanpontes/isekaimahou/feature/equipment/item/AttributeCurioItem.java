package com.renanpontes.isekaimahou.feature.equipment.item;

import java.util.List;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Acessório do Curios que dá um modificador de atributo fixo (Anel de Mana, Anel da Renovação, Amuleto Arcano).
 * O Curios mostra sozinho o bônus no tooltip ("Quando como anel: +60 Mana Máximo").
 */
public class AttributeCurioItem extends Item implements ICurioItem {
    private final Holder<Attribute> attribute;
    private final double amount;
    private final AttributeModifier.Operation operation;
    private final SoundEvent equipSound;
    private final Element fxElement;

    public AttributeCurioItem(Properties props, Holder<Attribute> attribute, double amount,
                              AttributeModifier.Operation operation, SoundEvent equipSound, Element fxElement) {
        super(props);
        this.attribute = attribute;
        this.amount = amount;
        this.operation = operation;
        this.equipSound = equipSound;
        this.fxElement = fxElement;
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext, ResourceLocation id,
                                                                                ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> map = LinkedHashMultimap.create();
        // o id vem do Curios e já é único por espaço (dois anéis iguais somam)
        map.put(attribute, new AttributeModifier(id, amount, operation));
        return map;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(equipSound, 1.0F, 1.0F);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        LivingEntity e = slotContext.entity();
        if (e.level() instanceof ServerLevel level && e.tickCount > 20 && !ItemStack.isSameItem(prevStack, stack)) {
            level.sendParticles(ElementFx.dust(fxElement, 0.9F), e.getX(), e.getY() + 1.1, e.getZ(),
                    10, 0.35, 0.4, 0.35, 0.0);
        }
    }
}
