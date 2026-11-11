package com.renanpontes.isekaimahou.feature.ritual_mage.item;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.ritual_mage.MageRegistries;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.Phenomenon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Nota de Pesquisa: ganha ao observar fenômenos com a Lente Arcana. Na Mesa de Pesquisa cada nota vira
 * uma runa do seu elemento no minijogo da Tese. A cor da runa no ícone vem do elemento (tint no cliente).
 */
public class ResearchNoteItem extends Item {
    /** Fenômeno usado em notas criadas sem origem (criativo/comando). */
    public static final ResourceLocation UNKNOWN = IsekaiMahou.id("unknown");

    public ResearchNoteItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(ResourceLocation phenomenon, Element element, int count) {
        ItemStack stack = new ItemStack(MageRegistries.RESEARCH_NOTE.get(), count);
        stack.set(MageRegistries.NOTE.get(), new ResearchNote(phenomenon, element));
        return stack;
    }

    /** Elemento da nota (Arcano se não tiver componente). */
    public static Element elementOf(ItemStack stack) {
        ResearchNote note = stack.get(MageRegistries.NOTE.get());
        return note == null ? Element.ARCANE : note.element();
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.isekai_mahou.research_note.named", elementOf(stack).displayName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        ResearchNote note = stack.get(MageRegistries.NOTE.get());
        if (note != null && !UNKNOWN.equals(note.phenomenon())) {
            tooltip.add(Component.translatable("item.isekai_mahou.research_note.phenomenon",
                    Phenomenon.displayName(note.phenomenon())).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.isekai_mahou.research_note.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
