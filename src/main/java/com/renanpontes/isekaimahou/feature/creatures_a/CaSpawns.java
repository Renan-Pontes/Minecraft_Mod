package com.renanpontes.isekaimahou.feature.creatures_a;

import java.util.List;

import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.AbstractGoblin;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.slime.KingSlime;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.slime.MahouSlime;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Regras de spawn natural. Os biomas ficam nos biome modifiers (data/isekai_mahou/neoforge/biome_modifier/creatures_a_*.json).
 * Goblins aparecem de dia e de noite, mas pouco (limite de densidade), e só na superfície.
 */
public final class CaSpawns {
    private static final int SLIME_DENSITY = 6;
    private static final int GOBLIN_DENSITY = 8;

    public static void register(RegisterSpawnPlacementsEvent e) {
        reg(e, CaRegistry.SLIME.get(), CaSpawns::surfaceSlime);
        reg(e, CaRegistry.FIRE_SLIME.get(), CaSpawns::surfaceSlime);
        reg(e, CaRegistry.WATER_SLIME.get(), CaSpawns::waterSlime);
        reg(e, CaRegistry.WIND_SLIME.get(), CaSpawns::surfaceSlime);
        reg(e, CaRegistry.POISON_JELLY.get(), CaSpawns::surfaceSlime);
        reg(e, CaRegistry.STONE_SLIME.get(), CaSpawns::caveSlime);
        reg(e, CaRegistry.KING_SLIME.get(), CaSpawns::kingSlime);
        reg(e, CaRegistry.GOBLIN.get(), CaSpawns::goblin);
        reg(e, CaRegistry.GOBLIN_ARCHER.get(), CaSpawns::goblin);
        reg(e, CaRegistry.GOBLIN_SHAMAN.get(), CaSpawns::goblin);
        reg(e, CaRegistry.GOBLIN_WOLF_RIDER.get(), CaSpawns::goblin);
        reg(e, CaRegistry.HOBGOBLIN.get(), CaSpawns::goblin);
        reg(e, CaRegistry.GOBLIN_KING.get(), CaSpawns::goblin);
    }

    private static <T extends Mob> void reg(RegisterSpawnPlacementsEvent e, EntityType<T> type, SpawnPlacements.SpawnPredicate<T> predicate) {
        e.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, predicate,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static boolean basic(EntityType<? extends Mob> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        return level.getDifficulty() != Difficulty.PEACEFUL && Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    private static int countNear(ServerLevelAccessor level, BlockPos pos, Class<? extends Mob> cls, double r) {
        return level.getEntitiesOfClass(cls, new AABB(pos).inflate(r)).size();
    }

    /**
     * Slimes da superfície: no escuro (como monstros) ou, raramente, de dia; poucos por área.
     * Em dimensões com teto (Nether, para o slime de fogo) não exige céu aberto.
     */
    private static boolean surfaceSlime(EntityType<? extends Mob> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!basic(type, level, reason, pos, random)) return false;
        if (MobSpawnType.isSpawner(reason)) return true;
        if (!level.dimensionType().hasCeiling() && (pos.getY() < level.getSeaLevel() - 4 || !level.canSeeSky(pos))) return false;
        return darkOrLucky(level, pos, random) && countNear(level, pos, MahouSlime.class, 32) < SLIME_DENSITY;
    }

    /** Slime de água: margens, rios, praias e também cavernas exuberantes (sem exigir céu aberto). */
    private static boolean waterSlime(EntityType<? extends Mob> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!basic(type, level, reason, pos, random)) return false;
        if (MobSpawnType.isSpawner(reason)) return true;
        return darkOrLucky(level, pos, random) && countNear(level, pos, MahouSlime.class, 32) < SLIME_DENSITY;
    }

    private static boolean darkOrLucky(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        return Monster.isDarkEnoughToSpawn(level, pos, random) || random.nextFloat() < 0.12F;
    }

    /** Slime de pedra: só em cavernas escuras. */
    private static boolean caveSlime(EntityType<? extends Mob> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!basic(type, level, reason, pos, random)) return false;
        if (MobSpawnType.isSpawner(reason)) return true;
        return pos.getY() < 50 && !level.canSeeSky(pos) && Monster.isDarkEnoughToSpawn(level, pos, random)
                && countNear(level, pos, MahouSlime.class, 24) < SLIME_DENSITY;
    }

    /** Rei Slime: noite no pântano, raro, nunca dois perto. */
    private static boolean kingSlime(EntityType<? extends Mob> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!basic(type, level, reason, pos, random)) return false;
        if (MobSpawnType.isSpawner(reason)) return true;
        return random.nextFloat() < 0.15F && level.canSeeSky(pos) && Monster.isDarkEnoughToSpawn(level, pos, random)
                && countNear(level, pos, KingSlime.class, 128) == 0;
    }

    /**
     * Goblins: superfície, de dia e de noite (de dia bem menos), com limite por área.
     * O sorteio de dia/noite vale só para o PRIMEIRO do grupo: os outros membros do mesmo grupo (goblins que acabaram de
     * nascer ali perto, ainda sem nenhum tick) passam direto — senão os bandos de 2–5 virariam goblins sozinhos.
     */
    private static boolean goblin(EntityType<? extends Mob> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!basic(type, level, reason, pos, random)) return false;
        if (MobSpawnType.isSpawner(reason)) return true;
        if (!level.canSeeSky(pos)) return false;
        List<AbstractGoblin> near = level.getEntitiesOfClass(AbstractGoblin.class, new AABB(pos).inflate(48.0));
        if (near.size() >= GOBLIN_DENSITY) return false;
        Vec3 c = Vec3.atBottomCenterOf(pos);
        boolean packMate = near.stream().anyMatch(g -> g.tickCount == 0 && g.distanceToSqr(c) < 12.0 * 12.0);
        if (packMate) return true;
        boolean dark = Monster.isDarkEnoughToSpawn(level, pos, random);
        return random.nextFloat() < (dark ? 0.5F : 0.12F);
    }

    private CaSpawns() {}
}
