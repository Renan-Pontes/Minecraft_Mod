package com.renanpontes.isekaimahou.feature.structures_a.block;

import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

/** Item do altar: o elemento vem do componente block_state (o vanilla já aplica o estado ao colocar). */
public class ShrineAltarItem extends BlockItem {
    public ShrineAltarItem(Block block, Properties props) {
        super(block, props);
    }

    /** Elemento guardado no item (null = padrão, fogo). */
    public static Element elementOf(ItemStack stack) {
        BlockItemStateProperties props = stack.get(DataComponents.BLOCK_STATE);
        return props == null ? null : props.get(ShrineAltarBlock.ELEMENT);
    }

    @Override
    public Component getName(ItemStack stack) {
        Element e = elementOf(stack);
        return Component.translatable("block.isekai_mahou.shrine_altar.named", (e == null ? Element.FIRE : e).displayName());
    }
}
