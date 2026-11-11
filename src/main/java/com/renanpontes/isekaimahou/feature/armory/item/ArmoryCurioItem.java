package com.renanpontes.isekaimahou.feature.armory.item;

import java.util.List;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
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
 * Acessório de marco de nível (Talismã da Vitalidade, Anel da Guarda, Sigilo do Arquimago): um ou mais modificadores
 * de atributo fixos enquanto equipado no espaço do Curios. O Curios mostra os bônus no tooltip sozinho.
 */
public class ArmoryCurioItem extends Item implements ICurioItem {
    /** Um bônus: atributo, valor e operação. */
    public record Bonus(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {}

    private final List<Bonus> bonuses;
    private final SoundEvent equipSound;
    private final ParticleOptions equipParticle;

    public ArmoryCurioItem(Properties props, SoundEvent equipSound, ParticleOptions equipParticle, Bonus... bonuses) {
        super(props.stacksTo(1));
        this.bonuses = List.of(bonuses);
        this.equipSound = equipSound;
        this.equipParticle = equipParticle;
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext, ResourceLocation id,
                                                                                ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> map = LinkedHashMultimap.create();
        // o id vem do Curios e é único por espaço; atributos diferentes podem dividir o mesmo id
        for (Bonus b : bonuses) map.put(b.attribute(), new AttributeModifier(id, b.amount(), b.operation()));
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
        // Só um brilho ao vestir de verdade (não ao entrar no mundo nem ao trocar por um igual)
        if (e.level() instanceof ServerLevel level && e.tickCount > 20 && !ItemStack.isSameItem(prevStack, stack)) {
            level.sendParticles(equipParticle, e.getX(), e.getY() + 1.0, e.getZ(), 14, 0.35, 0.5, 0.35, 0.02);
        }
    }
}
