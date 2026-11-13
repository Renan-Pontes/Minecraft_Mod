package com.renanpontes.isekaimahou.feature.arcana_items;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.arcana_items.item.ManaNecklaceItem;
import com.renanpontes.isekaimahou.feature.arcana_items.item.RecipeScrollItem;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Comandos de teste (op): {@code /mahou arcana_items give|fill|scroll [n]|city|return}.
 * <ul>
 *   <li>{@code give}: todos os itens do módulo + alguns reagentes;</li>
 *   <li>{@code fill}: enche o Colar de Mana que está na mão;</li>
 *   <li>{@code scroll [n]}: grava de graça a magia n (0 = primeira) da sua biblioteca num Pergaminho de Receita;</li>
 *   <li>{@code city}: mostra a última cidade guardada; {@code return}: volta para ela sem gastar pergaminho.</li>
 * </ul>
 */
final class ArcanaCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("arcana_items")
                .then(MahouCommands.op("give").executes(ArcanaCommands::give))
                .then(MahouCommands.op("fill").executes(ArcanaCommands::fill))
                .then(MahouCommands.op("scroll").executes(c -> scroll(c, 0))
                        .then(Commands.argument("n", IntegerArgumentType.integer(0, MagicData.MAX_LIBRARY - 1))
                                .executes(c -> scroll(c, IntegerArgumentType.getInteger(c, "n")))))
                .then(MahouCommands.op("city").executes(ArcanaCommands::city))
                .then(MahouCommands.op("return").executes(ArcanaCommands::goBack))));
    }

    private static int give(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Rewards.give(sp, new ItemStack(ArcanaContent.MANA_NECKLACE.get()));
        Rewards.give(sp, new ItemStack(ArcanaContent.GREATER_MANA_NECKLACE.get()));
        Rewards.give(sp, new ItemStack(ArcanaContent.GRAND_MANA_NECKLACE.get()));
        Rewards.give(sp, new ItemStack(ArcanaContent.BLANK_RECIPE_SCROLL.get(), 2));
        Rewards.give(sp, new ItemStack(ArcanaContent.ALCHEMIST_BAG.get()));
        Rewards.give(sp, new ItemStack(ArcanaContent.RETURN_SCROLL.get(), 4));
        Rewards.give(sp, new ItemStack(CoreRegistries.ESSENCES.get(Element.ARCANE).get(), 8));
        Rewards.give(sp, new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 8));
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.arcana_items.given"), false);
        return 1;
    }

    private static int fill(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ItemStack held = sp.getMainHandItem();
        if (!(held.getItem() instanceof ManaNecklaceItem necklace)) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.arcana_items.hold_necklace"));
            return 0;
        }
        necklace.setStored(held, necklace.capacity());
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.arcana_items.filled", necklace.capacity()), false);
        return 1;
    }

    private static int scroll(CommandContext<CommandSourceStack> c, int index) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        MagicData d = MagicApi.get(sp);
        if (index >= d.library().size()) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.arcana_items.no_spell", d.library().size()));
            return 0;
        }
        Rewards.give(sp, RecipeScrollItem.of(d.library().get(index), sp.getGameProfile().getName()));
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.arcana_items.scroll_given", index), false);
        return 1;
    }

    private static int city(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Places.Place p = ReturnTravel.lastCity(sp);
        if (p == null) {
            c.getSource().sendSuccess(() -> Component.translatable("message.isekai_mahou.arcana_items.return_no_city"), false);
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.arcana_items.city", p.name(), p.id(),
                p.dim().location().toString()), false);
        return 1;
    }

    private static int goBack(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ReturnTravel.Block b = ReturnTravel.travel(sp);
        if (b != null) {
            c.getSource().sendFailure(ReturnTravel.message(b, sp));
            return 0;
        }
        return 1;
    }

    private ArcanaCommands() {}
}
