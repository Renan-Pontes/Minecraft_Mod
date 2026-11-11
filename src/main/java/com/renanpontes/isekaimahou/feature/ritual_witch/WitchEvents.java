package com.renanpontes.isekaimahou.feature.ritual_witch;

import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

/** Eventos de jogo: carregar receitas, origem dos ingredientes/páginas (drops, baús, mercador). */
public final class WitchEvents {
    /** Baús vanilla que podem ter páginas e ingredientes da bruxa. */
    private static final Set<ResourceLocation> CHESTS = Set.of(
            ResourceLocation.withDefaultNamespace("chests/village/village_temple"),
            ResourceLocation.withDefaultNamespace("chests/simple_dungeon"),
            ResourceLocation.withDefaultNamespace("chests/abandoned_mineshaft"),
            ResourceLocation.withDefaultNamespace("chests/woodland_mansion"),
            ResourceLocation.withDefaultNamespace("chests/stronghold_library"),
            ResourceLocation.withDefaultNamespace("chests/ancient_city"),
            ResourceLocation.withDefaultNamespace("chests/desert_pyramid"),
            ResourceLocation.withDefaultNamespace("chests/jungle_temple"),
            ResourceLocation.withDefaultNamespace("chests/igloo_chest"),
            ResourceLocation.withDefaultNamespace("chests/ruined_portal"),
            ResourceLocation.withDefaultNamespace("chests/shipwreck_supply"));

    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new WitchRecipes.Loader(event.getRegistryAccess()));
    }

    /** Ao entrar e após /reload: envia a lista de ingredientes (o cliente precisa para prever cliques). */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(WitchNetwork::syncIngredients);
    }

    public static void onDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(mob.level() instanceof ServerLevel level)) return;
        EntityType<?> type = mob.getType();
        var r = level.random;
        if (type == EntityType.BAT && r.nextFloat() < 0.7F) {
            drop(event, mob, new ItemStack(WitchContent.BAT_WING.get()));
        } else if (type == EntityType.FROG && r.nextFloat() < 0.6F) {
            drop(event, mob, new ItemStack(WitchContent.TOAD_EYE.get()));
        } else if (type == EntityType.WITCH) {
            if (event.isRecentlyHit() && r.nextFloat() < 0.15F) drop(event, mob, new ItemStack(WitchContent.RECIPE_PAGE.get()));
            if (r.nextFloat() < 0.25F) drop(event, mob, new ItemStack(WitchContent.TOAD_EYE.get()));
            if (r.nextFloat() < 0.2F) drop(event, mob, new ItemStack(WitchContent.MANDRAKE_ROOT.get()));
        } else if ((type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.ZOMBIE_VILLAGER) && r.nextFloat() < 0.06F) {
            drop(event, mob, new ItemStack(WitchContent.GRAVE_DUST.get()));
        }
    }

    private static void drop(LivingDropsEvent event, LivingEntity mob, ItemStack stack) {
        ItemEntity item = new ItemEntity(mob.level(), mob.getX(), mob.getY() + 0.3, mob.getZ(), stack);
        item.setDefaultPickUpDelay();
        event.getDrops().add(item);
    }

    /** Mato: raiz de mandrágora (rara). Flores à noite: pétala lunar (mais na lua cheia). */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide || event.getPlayer().isCreative()) return;
        BlockState state = event.getState();
        var r = level.random;
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)) {
            if (r.nextFloat() < 0.04F) Block.popResource(level, event.getPos(), new ItemStack(WitchContent.MANDRAKE_ROOT.get()));
        } else if ((state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS)) && level.isNight()) {
            float chance = level.getMoonPhase() == 0 ? 0.6F : 0.25F;
            if (r.nextFloat() < chance) Block.popResource(level, event.getPos(), new ItemStack(WitchContent.MOONPETAL.get()));
        }
    }

    public static void onLootLoad(LootTableLoadEvent event) {
        if (!CHESTS.contains(event.getName())) return;
        event.getTable().addPool(LootPool.lootPool()
                .name(IsekaiMahou.MODID + ":witch_ritual")
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(WitchContent.RECIPE_PAGE.get()).setWeight(3))
                .add(LootItem.lootTableItem(WitchContent.MANDRAKE_ROOT.get()).setWeight(2))
                .add(LootItem.lootTableItem(WitchContent.MOONPETAL.get()).setWeight(2))
                .add(LootItem.lootTableItem(WitchContent.GRAVE_DUST.get()).setWeight(1))
                .add(EmptyLootItem.emptyItem().setWeight(10))
                .build());
    }

    public static void onWandererTrades(WandererTradesEvent event) {
        event.getGenericTrades().add(new BasicItemListing(2, new ItemStack(WitchContent.MANDRAKE_ROOT.get(), 2), 8, 1));
        event.getGenericTrades().add(new BasicItemListing(3, new ItemStack(WitchContent.MOONPETAL.get(), 2), 6, 1));
        event.getGenericTrades().add(new BasicItemListing(2, new ItemStack(WitchContent.TOAD_EYE.get(), 2), 6, 1));
        event.getRareTrades().add(new BasicItemListing(8, new ItemStack(WitchContent.RECIPE_PAGE.get()), 3, 5));
    }

    private WitchEvents() {}
}
