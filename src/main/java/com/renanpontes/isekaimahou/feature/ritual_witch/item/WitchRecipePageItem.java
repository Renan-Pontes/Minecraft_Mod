package com.renanpontes.isekaimahou.feature.ritual_witch.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchContent;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchProgress;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchRecipe;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchRecipes;
import com.renanpontes.isekaimahou.network.ClientMagic;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Página de Receita. Com o componente {@code isekai_mahou:witch_recipe} ensina aquela receita;
 * sem ele é uma "página rasgada" que revela uma receita desconhecida ao acaso (Revelações só até o próximo grau).
 * Achadas em baús, com bruxas e com o mercador errante.
 */
public class WitchRecipePageItem extends Item {
    public WitchRecipePageItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(ResourceLocation recipe) {
        ItemStack stack = new ItemStack(WitchContent.RECIPE_PAGE.get());
        stack.set(WitchContent.RECIPE_ID.get(), recipe);
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);
        ResourceLocation id = stack.get(WitchContent.RECIPE_ID.get());
        WitchRecipe recipe;
        if (id != null) {
            recipe = WitchRecipes.get(id);
            if (recipe == null) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.witch.page_unreadable").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
            if (WitchProgress.knows(MagicApi.get(sp), id)) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.witch.page_known").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
        } else {
            recipe = WitchProgress.pickRandomUnknown(MagicApi.get(sp), sp.getRandom());
            if (recipe == null) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.witch.page_nothing").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        WitchProgress.learn(sp, recipe.id(), true);
        sp.serverLevel().sendParticles(Vfx.enchant(), sp.getX(), sp.getY() + 1.5, sp.getZ(), 20, 0.4, 0.3, 0.4, 0.6);
        sp.serverLevel().sendParticles(Vfx.witch(), sp.getX(), sp.getY() + 1.2, sp.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        stack.consume(1, player);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = stack.get(WitchContent.RECIPE_ID.get());
        return id == null ? Component.translatable("item.isekai_mahou.witch_recipe_page.torn")
                : Component.translatable("item.isekai_mahou.witch_recipe_page.named", WitchRecipe.displayName(id));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(WitchContent.RECIPE_ID.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation id = stack.get(WitchContent.RECIPE_ID.get());
        if (id == null) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.witch_recipe_page.torn").withStyle(ChatFormatting.GRAY));
            return;
        }
        Component desc = WitchRecipe.description(id);
        if (!desc.getString().isEmpty()) tooltip.add(desc.copy().withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        // Cópia do cliente dos dados mágicos (classe sem imports de cliente: segura no servidor)
        MagicData d = ClientMagic.data();
        if (WitchProgress.knows(d, id)) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.witch_recipe_page.known").withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.witch_recipe_page.learn").withStyle(ChatFormatting.GRAY));
        }
    }
}
