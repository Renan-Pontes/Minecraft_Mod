package com.renanpontes.isekaimahou.feature.creatures_e;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalCreature;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Regras de spawn das criaturas das dimensoes. ONDE elas aparecem e definido pelos biome modifiers
 * (data/isekai_mahou/neoforge/biome_modifier/creatures_e_*.json, so nos biomas das dimensoes do mod).
 * Aqui: nao precisam de escuridao (as dimensoes sao claras), mas ha limite de quantas do mesmo tipo
 * podem existir perto (para nao lotar ilhas pequenas de criaturas fortes).
 */
public final class CeSpawns {

    static void register(RegisterSpawnPlacementsEvent e) {
        ground(e, CeRegistries.GREATER_FIRE_ELEMENTAL, 3);
        ground(e, CeRegistries.GREATER_EARTH_ELEMENTAL, 2);
        ground(e, CeRegistries.VOID_CRAWLER, 5);
        ground(e, CeRegistries.CRYSTAL_GOLEM, 2);
        amphibious(e, CeRegistries.GREATER_WATER_ELEMENTAL, 3);
        amphibious(e, CeRegistries.YOUNG_KRAKEN, 2);
        air(e, CeRegistries.GREATER_AIR_ELEMENTAL, 3);
        air(e, CeRegistries.LESSER_PHOENIX, 2);
        air(e, CeRegistries.STORM_WYVERN, 1);
        air(e, CeRegistries.WATCHER_EYE, 3);
        air(e, CeRegistries.ANGEL_SENTINEL, 3);
        air(e, CeRegistries.CHERUB, 6);
    }

    private static <T extends DimensionalCreature> void ground(RegisterSpawnPlacementsEvent e, Supplier<EntityType<T>> type, int max) {
        e.register(type.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, rnd) -> difficultyOk(t, level) && Mob.checkMobSpawnRules(t, level, reason, pos, rnd)
                        && notCrowded(t, level, reason, pos, max),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** Na agua ou em terra firme. */
    private static <T extends DimensionalCreature> void amphibious(RegisterSpawnPlacementsEvent e, Supplier<EntityType<T>> type, int max) {
        e.register(type.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, rnd) -> difficultyOk(t, level)
                        && (level.getFluidState(pos).is(FluidTags.WATER) || Mob.checkMobSpawnRules(t, level, reason, pos, rnd)
                                && level.getBlockState(pos).isAir())
                        && notCrowded(t, level, reason, pos, max),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** Voadoras: no ar livre, mas com chao (ilha) ate 24 blocos abaixo. */
    private static <T extends DimensionalCreature> void air(RegisterSpawnPlacementsEvent e, Supplier<EntityType<T>> type, int max) {
        e.register(type.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, rnd) -> difficultyOk(t, level) && level.getBlockState(pos).isAir()
                        && level.getBlockState(pos.above()).isAir() && level.getFluidState(pos).isEmpty()
                        && groundBelow(level, pos, 24) && notCrowded(t, level, reason, pos, max),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** Hostis nao surgem no Pacifico (o Querubim e o Golem de Cristal nao sao hostis). */
    private static boolean difficultyOk(EntityType<?> type, ServerLevelAccessor level) {
        return level.getDifficulty() != Difficulty.PEACEFUL || type == CeRegistries.CHERUB.get() || type == CeRegistries.CRYSTAL_GOLEM.get();
    }

    private static boolean groundBelow(ServerLevelAccessor level, BlockPos pos, int depth) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < depth && p.getY() > level.getMinBuildHeight(); i++) {
            p.move(0, -1, 0);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return true;
        }
        return false;
    }

    /** Spawn natural: no maximo {@code max} do mesmo tipo num raio de 32 blocos. */
    private static boolean notCrowded(EntityType<?> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, int max) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return true;
        return level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(32), m -> m.getType() == type).size() < max;
    }

    private CeSpawns() {}
}
