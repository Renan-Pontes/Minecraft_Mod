package com.renanpontes.isekaimahou.feature.structures_c;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Spawns naturais dentro das estruturas: goblins continuam aparecendo na vila goblin, kobolds na mina etc.,
 * e a Vila da Guilda é zona segura (sem monstros, para os NPCs não morrerem à noite).
 * Feito em Java (e não com "spawn_overrides" no JSON) porque as criaturas são de outros módulos: se uma não
 * existir, ela é só ignorada em vez de quebrar o carregamento do mundo.
 */
public final class StructureSpawns {
    /** Os SpawnerData precisam ser as MESMAS instâncias entre chamadas (o NaturalSpawner confere com contains()). */
    private static final Map<RpgStructure, List<MobSpawnSettings.SpawnerData>> CACHE = new EnumMap<>(RpgStructure.class);

    public static void register() {
        NeoForge.EVENT_BUS.addListener(StructureSpawns::onPotentialSpawns);
    }

    private static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        // Só no mundo já gerado (a geração de chunks usa WorldGenRegion e não deve consultar estruturas daqui).
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getMobCategory() == MobCategory.MISC) return;
        RpgStructure.Found found = RpgStructure.find(level, event.getPos(), false);
        if (found == null) return;
        RpgStructure def = found.structure();
        if (def.spawnMode == RpgStructure.SpawnMode.NONE) return;

        MobCategory category = event.getMobCategory();
        List<MobSpawnSettings.SpawnerData> ours = new ArrayList<>();
        for (MobSpawnSettings.SpawnerData data : spawners(def)) {
            if (data.type.getCategory() == category) ours.add(data);
        }
        boolean clearMonsters = category == MobCategory.MONSTER
                && (def.spawnMode == RpgStructure.SpawnMode.SAFE_ZONE
                || (def.spawnMode == RpgStructure.SpawnMode.REPLACE_MONSTERS && !ours.isEmpty()));
        if (clearMonsters) {
            for (MobSpawnSettings.SpawnerData data : List.copyOf(event.getSpawnerDataList())) {
                event.removeSpawnerData(data);
            }
        }
        if (def.spawnMode == RpgStructure.SpawnMode.SAFE_ZONE && category == MobCategory.MONSTER) return;
        for (MobSpawnSettings.SpawnerData data : ours) event.addSpawnerData(data);
    }

    private static synchronized List<MobSpawnSettings.SpawnerData> spawners(RpgStructure def) {
        return CACHE.computeIfAbsent(def, d -> {
            List<MobSpawnSettings.SpawnerData> list = new ArrayList<>();
            for (RpgStructure.Spawn spawn : d.spawns) {
                BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id(spawn.entity()))
                        // MISC não nasce naturalmente (e o SpawnerData trocaria por porco): ignora.
                        .filter(type -> type.getCategory() != MobCategory.MISC)
                        .ifPresent(type -> list.add(new MobSpawnSettings.SpawnerData(type, spawn.weight(), spawn.min(), spawn.max())));
            }
            return List.copyOf(list);
        });
    }

    private StructureSpawns() {}
}
