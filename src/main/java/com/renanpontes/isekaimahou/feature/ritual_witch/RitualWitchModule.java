package com.renanpontes.isekaimahou.feature.ritual_witch;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.ritual_witch.item.WitchRecipePageItem;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Modulo "ritual_witch": Ritual da Bruxa — Caldeirão Ritual.
 * Receitas data-driven (data/&lt;ns&gt;/witch_recipes), ingredientes na ordem, mexidas com a Colher Ritual,
 * calor embaixo e fase da lua. Resultados: elixires, maldições engarrafadas e as 5 Poções de Revelação
 * (o "up" da Bruxa: XP, glifos de Maldição e despertar de Água/Sombra).
 */
public class RitualWitchModule implements Module {
    /** Receitas que vêm com o mod (as 3 primeiras são as páginas do kit inicial). */
    public static final List<ResourceLocation> BUILTIN_RECIPES = List.of(
            IsekaiMahou.id("elixir_vitality"), IsekaiMahou.id("curse_frailty"), IsekaiMahou.id("revelation_1"),
            IsekaiMahou.id("elixir_owl_eyes"), IsekaiMahou.id("elixir_tides"), IsekaiMahou.id("elixir_feather"),
            IsekaiMahou.id("elixir_mana"), IsekaiMahou.id("elixir_shadow_veil"), IsekaiMahou.id("elixir_stone_skin"),
            IsekaiMahou.id("curse_hex"), IsekaiMahou.id("curse_mire"), IsekaiMahou.id("curse_blight"),
            IsekaiMahou.id("revelation_2"), IsekaiMahou.id("revelation_3"), IsekaiMahou.id("revelation_4"),
            IsekaiMahou.id("revelation_5"));

    @Override
    public String id() {
        return "ritual_witch";
    }

    @Override
    public void init(IEventBus modBus) {
        WitchContent.register(modBus);
        modBus.addListener(WitchNetwork::register);
        modBus.addListener(RitualWitchModule::creativeTab);

        NeoForge.EVENT_BUS.addListener(WitchEvents::onReload);
        NeoForge.EVENT_BUS.addListener(WitchEvents::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(WitchEvents::onDrops);
        NeoForge.EVENT_BUS.addListener(WitchEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(WitchEvents::onLootLoad);
        NeoForge.EVENT_BUS.addListener(WitchEvents::onWandererTrades);

        registerKit();
        registerCommands();
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CoreRegistries.TAB_RITUALS.getKey()) return;
        for (Item item : WitchContent.creativeItems()) {
            event.accept(item);
            if (item == WitchContent.RECIPE_PAGE.get()) {
                for (ResourceLocation id : BUILTIN_RECIPES) event.accept(WitchRecipePageItem.of(id));
            }
        }
    }

    /** Kit da Bruxa: caldeirão, fogueira, colher, livro, 3 páginas iniciais e ingredientes básicos. */
    private static void registerKit() {
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(WitchContent.CAULDRON_ITEM.get()));
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(Items.CAMPFIRE));
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(WitchContent.RITUAL_SPOON.get()));
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(WitchContent.RECIPE_BOOK.get()));
        for (int i = 0; i < 3; i++) {
            ResourceLocation id = BUILTIN_RECIPES.get(i);
            ClassKits.addItem(MagicPath.WITCH, () -> WitchRecipePageItem.of(id));
        }
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(WitchContent.MANDRAKE_ROOT.get(), 4));
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(WitchContent.MOONPETAL.get(), 3));
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(WitchContent.BAT_WING.get(), 4));
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(WitchContent.TOAD_EYE.get(), 2));
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(WitchContent.GRAVE_DUST.get(), 2));
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(Items.AMETHYST_SHARD, 2));
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(Items.SWEET_BERRIES, 4));
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(Items.SPIDER_EYE, 2));
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(Items.APPLE, 2));
        ClassKits.addItem(MagicPath.WITCH, () -> new ItemStack(Items.BONE_MEAL, 3));
    }

    /** /mahou witch learnall | rank &lt;n&gt; (operadores, para testes). */
    private static void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("witch")
                .then(Commands.literal("learnall").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    int n = 0;
                    for (WitchRecipe r : WitchRecipes.all()) if (WitchProgress.learn(p, r.id(), false)) n++;
                    final int learned = n;
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.witch.learnall", learned), false);
                    return learned;
                }))
                .then(Commands.literal("rank").then(Commands.argument("n", IntegerArgumentType.integer(0, 5)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    MagicData d = MagicApi.get(p);
                    int target = IntegerArgumentType.getInteger(c, "n");
                    MagicApi.addCounter(p, WitchProgress.RANK_COUNTER, target - WitchProgress.rank(d));
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.witch.rank", target), false);
                    return target;
                })))));
    }
}
