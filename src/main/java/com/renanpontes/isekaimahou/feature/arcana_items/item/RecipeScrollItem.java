package com.renanpontes.isekaimahou.feature.arcana_items.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.arcana_items.ArcanaContent;
import com.renanpontes.isekaimahou.feature.arcana_items.RecipeScrolls;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Pergaminho de Receita (gravado): guarda UMA magia para dar a um amigo. Uso único — some ao ser lido — e só pode ser
 * lido por quem já conhece todos os glifos da magia (o pergaminho ensina a receita, não os glifos).
 */
public class RecipeScrollItem extends Item {
    public RecipeScrollItem(Properties props) {
        super(props);
    }

    public static ItemStack of(SpellRecipe recipe, String author) {
        ItemStack stack = new ItemStack(ArcanaContent.RECIPE_SCROLL.get());
        stack.set(CoreRegistries.SPELL.get(), recipe);
        stack.set(ArcanaContent.AUTHOR.get(), author.length() > 48 ? author.substring(0, 48) : author);
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        SpellRecipe recipe = stack.get(CoreRegistries.SPELL.get());
        if (recipe == null || recipe.isEmpty()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer sp && RecipeScrolls.read(sp, recipe)) {
            stack.consume(1, sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        SpellRecipe r = stack.get(CoreRegistries.SPELL.get());
        return r == null || r.name().isEmpty() ? super.getName(stack)
                : Component.translatable("item.isekai_mahou.recipe_scroll.named", r.name());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(CoreRegistries.SPELL.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        SpellRecipe r = stack.get(CoreRegistries.SPELL.get());
        if (r == null || r.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.recipe_scroll.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.recipe_scroll.glyphs", r.glyphs().size())
                .withStyle(ChatFormatting.GRAY));
        String author = stack.get(ArcanaContent.AUTHOR.get());
        if (author != null && !author.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.recipe_scroll.author", author)
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (!r.chant().isEmpty()) {
            tooltip.add(Component.literal("« " + r.chant() + " »").withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.recipe_scroll.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
