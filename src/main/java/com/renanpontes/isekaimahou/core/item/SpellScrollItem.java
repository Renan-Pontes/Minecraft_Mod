package com.renanpontes.isekaimahou.core.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
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
 * Pergaminho de Magia: guarda uma magia (receita) para dar a outra pessoa.
 * Clique direito: copia a magia para a biblioteca do Grimório (precisa conhecer os glifos para usar).
 */
public class SpellScrollItem extends Item {
    public SpellScrollItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(SpellRecipe recipe) {
        ItemStack stack = new ItemStack(CoreRegistries.SPELL_SCROLL.get());
        stack.set(CoreRegistries.SPELL.get(), recipe);
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        SpellRecipe recipe = stack.get(CoreRegistries.SPELL.get());
        if (recipe == null || recipe.isEmpty()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (MagicApi.get(sp).addToLibrary(recipe)) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.spell_copied", recipe.name())
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            } else {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.library_full").withStyle(ChatFormatting.RED), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        SpellRecipe r = stack.get(CoreRegistries.SPELL.get());
        return r == null || r.name().isEmpty() ? super.getName(stack)
                : Component.translatable("item.isekai_mahou.spell_scroll.named", r.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        SpellRecipe r = stack.get(CoreRegistries.SPELL.get());
        if (r == null) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.spell_scroll.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.spell_scroll.glyphs", r.glyphs().size()).withStyle(ChatFormatting.GRAY));
        if (!r.chant().isEmpty()) {
            tooltip.add(Component.literal("« " + r.chant() + " »").withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_PURPLE));
        }
    }
}
