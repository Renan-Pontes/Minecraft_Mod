package com.renanpontes.isekaimahou.feature.creatures_c;

import com.renanpontes.isekaimahou.feature.creatures_c.entity.Basilisk;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.DireWolf;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Fairy;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Gargoyle;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Harpy;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Mimic;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Regras de spawn natural das feras (os biomas ficam nos biome modifiers JSON):
 * <ul>
 *   <li>Lobo Atroz: taigas/florestas, no chão de grama/neve, com luz (como os lobos);</li>
 *   <li>Harpia: montanhas, ao ar livre e no alto (y ≥ 90), de dia ou de noite;</li>
 *   <li>Basilisco: desertos/ermos/cavernas, no escuro (noite ou subterrâneo);</li>
 *   <li>Mímico: no fundo das cavernas (y &lt; 40, sem céu), no escuro — raro;</li>
 *   <li>Gárgula: montanhas e picos ventosos, à noite, ao ar livre;</li>
 *   <li>Fada: campos e florestas de flores, de dia, sobre grama.</li>
 * </ul>
 */
public final class CcSpawns {

    static void register(RegisterSpawnPlacementsEvent e) {
        e.register(CcRegistries.DIRE_WOLF.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CcSpawns::direWolf, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(CcRegistries.HARPY.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CcSpawns::harpy, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(CcRegistries.BASILISK.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CcSpawns::basilisk, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(CcRegistries.MIMIC.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CcSpawns::mimic, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(CcRegistries.GARGOYLE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CcSpawns::gargoyle, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(CcRegistries.FAIRY.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CcSpawns::fairy, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static boolean direWolf(EntityType<DireWolf> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        boolean bright = MobSpawnType.ignoresLightRequirements(reason) || level.getRawBrightness(pos, 0) > 8;
        return bright && level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON);
    }

    private static boolean harpy(EntityType<Harpy> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) return false;
        return reason != MobSpawnType.NATURAL || (pos.getY() >= 90 && level.canSeeSky(pos));
    }

    private static boolean basilisk(EntityType<Basilisk> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    private static boolean mimic(EntityType<Mimic> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        return reason != MobSpawnType.NATURAL || (pos.getY() < 40 && !level.canSeeSky(pos));
    }

    private static boolean gargoyle(EntityType<Gargoyle> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        return reason != MobSpawnType.NATURAL || (pos.getY() >= 70 && level.canSeeSky(pos));
    }

    private static boolean fairy(EntityType<Fairy> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        boolean bright = MobSpawnType.ignoresLightRequirements(reason) || level.getRawBrightness(pos, 0) > 8;
        return bright && level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON);
    }

    private CcSpawns() {}
}
