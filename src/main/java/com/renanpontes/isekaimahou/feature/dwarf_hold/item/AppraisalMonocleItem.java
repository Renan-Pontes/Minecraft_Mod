package com.renanpontes.isekaimahou.feature.dwarf_hold.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Monóculo de Avaliação ("Appraisal" de isekai): acessório do Curios (espaço Talismã). Enquanto equipado, o HUD mostra
 * nível de ameaça, vida, armadura, ataque e as fraquezas/resistências elementais do alvo que você olha (até 24 blocos),
 * e a dica de cada item ganha o valor estimado em esmeraldas. Tudo isso é desenhado no cliente ({@code AppraisalHud}).
 */
public class AppraisalMonocleItem extends Item implements ICurioItem {
    public AppraisalMonocleItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.dwarf_hold.appraisal_monocle.desc")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.dwarf_hold.appraisal_monocle.use").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(SoundEvents.SPYGLASS_USE, 1.0F, 1.3F);
    }
}
