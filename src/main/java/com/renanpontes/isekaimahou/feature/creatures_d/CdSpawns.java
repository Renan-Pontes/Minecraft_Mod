package com.renanpontes.isekaimahou.feature.creatures_d;

import com.renanpontes.isekaimahou.feature.creatures_d.entity.ElementalCreature;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Regras de spawn natural (os biomas vêm dos biome modifiers em data/isekai_mahou/neoforge/biome_modifier).
 * Ovos, spawners e estruturas ignoram as condições de clima/hora.
 * Vários elementais nascem de dia ou na água clara, onde os monstros do vanilla falham — sem freio eles
 * encheriam o limite de monstros da região. Por isso cada regra tem um limite local por tipo ({@link #few})
 * e, nos casos mais fáceis de cumprir, um sorteio extra (como o 1/40 do afogado do vanilla).
 */
public final class CdSpawns {

    public static void register(RegisterSpawnPlacementsEvent e) {
        // Fogo: desertos e badlands não têm chuva, então nascem de dia também — o limite local evita enxames
        ground(e, CdEntities.SALAMANDER.get(), (t, l, s, p, r) -> forced(s)
                || (hostile(l) && onGround(t, l, s, p, r) && few(t, l, p, 32, 3)));
        ground(e, CdEntities.EMBER_IMP.get(), (t, l, s, p, r) -> forced(s)
                || (hostile(l) && onGround(t, l, s, p, r) && few(t, l, p, 32, 3)));
        water(e, CdEntities.UNDINE.get(), (t, l, s, p, r) -> forced(s)
                || (inWater(l, p) && p.getY() <= l.getSeaLevel() && p.getY() >= l.getSeaLevel() - 20 && few(t, l, p, 32, 3)));
        // Serpente: sem exigir escuridão ela seria a única "monstro" a nascer na água de dia; o sorteio
        // (como o 1/40 do afogado) e o limite local a mantêm uma ameaça ocasional para quem navega
        water(e, CdEntities.TIDE_SERPENT.get(), (t, l, s, p, r) -> forced(s)
                || (hostile(l) && r.nextInt(15) == 0 && inWater(l, p) && p.getY() <= l.getSeaLevel() - 5 && few(t, l, p, 48, 2)));
        ground(e, CdEntities.MOSS_GOLEM.get(), (t, l, s, p, r) -> forced(s)
                || (hostile(l) && onGround(t, l, s, p, r) && (!l.canSeeSky(p) || r.nextInt(3) == 0) && few(t, l, p, 32, 2)));
        ground(e, CdEntities.CRYSTAL_MOLE.get(), (t, l, s, p, r) -> forced(s)
                || (hostile(l) && p.getY() < 50 && !l.canSeeSky(p) && l.getMaxLocalRawBrightness(p) <= 7
                && onGround(t, l, s, p, r) && few(t, l, p, 32, 3)));
        ground(e, CdEntities.SYLPH.get(), (t, l, s, p, r) -> forced(s)
                || (hostile(l) && p.getY() > 85 && l.canSeeSky(p) && r.nextInt(2) == 0 && onGround(t, l, s, p, r) && few(t, l, p, 48, 3)));
        // Falcão: muito mais comum durante tempestades
        ground(e, CdEntities.STORM_HAWK.get(), (t, l, s, p, r) -> forced(s)
                || (hostile(l) && p.getY() > 100 && l.canSeeSky(p) && stormChance(l, r) && onGround(t, l, s, p, r)
                && few(t, l, p, 64, l.getLevel().isThundering() ? 3 : 2)));
        ground(e, CdEntities.LUMEN_SPRITE.get(), (t, l, s, p, r) -> forced(s)
                || (l.getLevel().isDay() && l.canSeeSky(p) && l.getMaxLocalRawBrightness(p) > 8 && r.nextInt(3) == 0
                && onGround(t, l, s, p, r) && few(t, l, p, 32, 4)));
        ground(e, CdEntities.SOLAR_SENTINEL.get(), (t, l, s, p, r) -> forced(s)
                || (hostile(l) && l.getLevel().isDay() && l.canSeeSky(p) && r.nextInt(3) == 0 && onGround(t, l, s, p, r)
                && few(t, l, p, 48, 2)));
        ground(e, CdEntities.CREEPING_SHADE.get(), (t, l, s, p, r) -> forced(s)
                || (hostile(l) && Monster.isDarkEnoughToSpawn(l, p, r) && onGround(t, l, s, p, r) && few(t, l, p, 32, 3)));
        ground(e, CdEntities.VOID_BAT.get(), (t, l, s, p, r) -> forced(s)
                || (hostile(l) && (p.getY() < 0 || l.getBiome(p).is(Biomes.DEEP_DARK)) && l.getMaxLocalRawBrightness(p) <= 4
                && onGround(t, l, s, p, r) && few(t, l, p, 32, 4)));
        // Espírito Errante: raro de verdade — no máximo um por região
        ground(e, CdEntities.WANDERING_SPIRIT.get(), (t, l, s, p, r) -> forced(s)
                || (l.getLevel().isNight() && l.canSeeSky(p) && r.nextInt(3) == 0 && onGround(t, l, s, p, r) && few(t, l, p, 64, 1)));
        ground(e, CdEntities.ARCANE_GOLEM.get(), (t, l, s, p, r) -> forced(s)
                || (hostile(l) && p.getY() < 16 && !l.canSeeSky(p) && l.getMaxLocalRawBrightness(p) <= 7 && r.nextInt(4) == 0
                && onGround(t, l, s, p, r) && few(t, l, p, 48, 1)));
    }

    private static <T extends ElementalCreature> void ground(RegisterSpawnPlacementsEvent e, EntityType<T> type,
                                                              net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T> rule) {
        e.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rule, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static <T extends ElementalCreature> void water(RegisterSpawnPlacementsEvent e, EntityType<T> type,
                                                             net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T> rule) {
        e.register(type, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rule, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** Ovo, spawner, comando ou estrutura: não aplica as condições naturais. */
    private static boolean forced(MobSpawnType s) {
        return s != MobSpawnType.NATURAL && s != MobSpawnType.CHUNK_GENERATION;
    }

    private static boolean hostile(ServerLevelAccessor l) {
        return l.getDifficulty() != Difficulty.PEACEFUL;
    }

    private static boolean onGround(EntityType<? extends Mob> t, ServerLevelAccessor l, MobSpawnType s, BlockPos p, RandomSource r) {
        return Mob.checkMobSpawnRules(t, l, s, p, r);
    }

    /**
     * Elementais são raros: só nasce se houver menos de {@code max} do mesmo tipo a até {@code radius} blocos.
     * (Fica por último nas regras: só roda quando todo o resto já passou.)
     */
    private static boolean few(EntityType<?> t, ServerLevelAccessor l, BlockPos p, int radius, int max) {
        AABB box = new AABB(p).inflate(radius, 32, radius);
        return l.getEntitiesOfClass(Mob.class, box, m -> m.getType() == t).size() < max;
    }

    private static boolean inWater(ServerLevelAccessor l, BlockPos p) {
        return l.getFluidState(p).is(FluidTags.WATER) && l.getFluidState(p.below()).is(FluidTags.WATER);
    }

    private static boolean stormChance(ServerLevelAccessor l, RandomSource r) {
        if (l.getLevel().isThundering()) return true;
        if (l.getLevel().isRaining()) return r.nextInt(2) == 0;
        return r.nextInt(4) == 0;
    }

    private CdSpawns() {}
}
