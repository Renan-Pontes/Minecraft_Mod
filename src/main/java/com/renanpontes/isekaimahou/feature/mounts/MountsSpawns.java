package com.renanpontes.isekaimahou.feature.mounts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
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
 * Regras de spawn natural (os biomas ficam nos biome modifiers JSON
 * {@code data/isekai_mahou/neoforge/biome_modifier/mounts_*.json}):
 * <ul>
 *   <li>Grifo: montanhas do Overworld, no alto (y ≥ 85), ao ar livre, sobre chão firme;</li>
 *   <li>Arraia do Céu: Plano Elemental, no ar livre (pode surgir sob/sobre as ilhas);</li>
 *   <li>Pégaso: Santuário Celestial, sobre chão firme;</li>
 *   <li>Wyvern: Abismo, sobre chão firme, nunca no Pacífico.</li>
 * </ul>
 * Todas evitam lotar: no máximo algumas do mesmo tipo num raio de 48 blocos.
 */
public final class MountsSpawns {

    static void register(RegisterSpawnPlacementsEvent e) {
        e.register(MountsRegistries.GRIFFIN.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, rnd) -> solidGround(level, pos) && notCrowded(type, level, reason, pos, 3)
                        && (!natural(reason) || pos.getY() >= 85 && atSurface(level, pos)),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(MountsRegistries.SKY_RAY.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, rnd) -> openAir(level, pos) && notCrowded(type, level, reason, pos, 4),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(MountsRegistries.PEGASUS.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, rnd) -> solidGround(level, pos) && notCrowded(type, level, reason, pos, 6),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(MountsRegistries.WYVERN.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, rnd) -> level.getDifficulty() != Difficulty.PEACEFUL && wyvern(type, level, reason, pos, rnd),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // v1.0: Dragão Terrestre solto nas savanas e planícies (poucos, só na superfície)
        e.register(MountsRegistries.LAND_DRAKE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, rnd) -> solidGround(level, pos) && notCrowded(type, level, reason, pos, 3)
                        && (!natural(reason) || atSurface(level, pos)),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // Pesadelo e Cervo da Aurora não nascem soltos: só pelas Rédeas (e ovos/comandos)
        e.register(MountsRegistries.NIGHTMARE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, rnd) -> !natural(reason) && solidGround(level, pos),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(MountsRegistries.AURORA_STAG.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, rnd) -> !natural(reason) && solidGround(level, pos),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static boolean wyvern(EntityType<? extends Mob> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource rnd) {
        return solidGround(level, pos) && notCrowded(type, level, reason, pos, 2);
    }

    private static boolean natural(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
    }

    /** Bloco de baixo com topo sólido e espaço livre em cima. */
    private static boolean solidGround(ServerLevelAccessor level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    /** Na superfície (a céu aberto). Usa o heightmap porque a luz do céu ainda não existe na geração do mundo. */
    private static boolean atSurface(ServerLevelAccessor level, BlockPos pos) {
        return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
    }

    /** Ar livre (sem líquido) com um pouco de espaço em volta. */
    private static boolean openAir(ServerLevelAccessor level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) return false;
        }
        return pos.getY() > level.getMinBuildHeight() + 8;
    }

    private static boolean notCrowded(EntityType<?> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, int max) {
        if (!natural(reason)) return true;
        return level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(48), m -> m.getType() == type).size() < max;
    }

    private MountsSpawns() {}
}
