package com.renanpontes.isekaimahou.feature.guild.item;

import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.feature.guild.GuildFaction;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

/**
 * Item do Quadro de Facção. A facção vai no componente vanilla {@code minecraft:block_state} ({@code {faction: "kingdom"}}),
 * que o BlockItem aplica sozinho ao colocar e que a loot table copia ao quebrar — o nome do item segue a facção.
 */
public class FactionBoardItem extends BlockItem {
    /** "block.isekai_mahou.faction_board.kingdom" = "Quadro do Reino". */
    private static final String VARIANT_KEY = "block.isekai_mahou.faction_board.";

    public FactionBoardItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static GuildFaction factionOf(ItemStack stack) {
        BlockItemStateProperties props = stack.get(DataComponents.BLOCK_STATE);
        return props == null ? GuildFaction.GUILD : GuildFaction.of(props.properties().get("faction"));
    }

    /** Pilha do quadro de uma facção. */
    public static ItemStack of(net.minecraft.world.item.Item item, GuildFaction faction) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("faction", faction.id())));
        return s;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(VARIANT_KEY + factionOf(stack).id());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GuildFaction f = factionOf(stack);
        tooltip.add(f.displayName().withColor(f.rgb()));
        tooltip.add(Component.translatable("block.isekai_mahou.faction_board.hint").withStyle(ChatFormatting.GRAY));
    }
}
