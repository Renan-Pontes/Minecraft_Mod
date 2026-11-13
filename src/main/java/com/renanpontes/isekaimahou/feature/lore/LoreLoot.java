package com.renanpontes.isekaimahou.feature.lore;

import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * Páginas Perdidas nos baús do mundo, sem editar a tabela de ninguém: quando uma tabela de baú carrega, ganha um
 * sorteio extra com chance de UMA página. Vale para todo baú de estrutura do mod ({@code isekai_mahou:chests/...}) e
 * para alguns do vanilla onde faz sentido achar papel velho.
 */
final class LoreLoot {
    private static final String POOL = "isekai_mahou_lore_page";
    /** Chance nos baús das estruturas do mod. */
    private static final float MOD_CHANCE = 0.2F;

    private static final Map<String, Float> VANILLA = Map.ofEntries(
            Map.entry("chests/simple_dungeon", 0.2F),
            Map.entry("chests/abandoned_mineshaft", 0.12F),
            Map.entry("chests/stronghold_library", 0.5F),
            Map.entry("chests/stronghold_corridor", 0.15F),
            Map.entry("chests/desert_pyramid", 0.25F),
            Map.entry("chests/jungle_temple", 0.3F),
            Map.entry("chests/ruined_portal", 0.2F),
            Map.entry("chests/shipwreck_map", 0.3F),
            Map.entry("chests/shipwreck_treasure", 0.15F),
            Map.entry("chests/buried_treasure", 0.4F),
            Map.entry("chests/pillager_outpost", 0.15F),
            Map.entry("chests/woodland_mansion", 0.25F),
            Map.entry("chests/ancient_city", 0.3F),
            Map.entry("chests/igloo_chest", 0.3F),
            Map.entry("chests/nether_bridge", 0.18F),
            Map.entry("chests/bastion_treasure", 0.35F),
            Map.entry("chests/bastion_other", 0.12F),
            Map.entry("chests/end_city_treasure", 0.25F));

    static void onLoad(LootTableLoadEvent e) {
        float chance = chance(e.getName());
        if (chance <= 0F || e.getTable().getPool(POOL) != null) return;
        e.getTable().addPool(LootPool.lootPool().name(POOL)
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .add(LootItem.lootTableItem(LoreContent.LORE_PAGE.get()))
                .build());
    }

    private static float chance(ResourceLocation id) {
        if (id == null) return 0F;
        String path = id.getPath();
        if (IsekaiMahou.MODID.equals(id.getNamespace())) {
            // As tabelas do próprio módulo já trazem as páginas delas
            return path.startsWith("chests/") && !path.startsWith("chests/lore_") ? MOD_CHANCE : 0F;
        }
        if ("minecraft".equals(id.getNamespace())) return VANILLA.getOrDefault(path, 0F);
        return 0F;
    }

    private LoreLoot() {}
}
