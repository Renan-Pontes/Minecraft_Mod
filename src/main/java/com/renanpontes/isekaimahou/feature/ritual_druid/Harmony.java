package com.renanpontes.isekaimahou.feature.ritual_druid;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * Harmonia do bosque em volta da Árvore Coração (raio {@link #RADIUS}). Cada categoria tem um teto, então o que
 * conta é a DIVERSIDADE: muitos tipos diferentes de flores, árvores, plantações e animais, água por perto e
 * nenhuma corrupção (monstros, netherrack, sculk, fogo...).
 */
public record Harmony(int flowers, int trees, int crops, int animals, int water, int corruption,
                      int flowerKinds, int treeKinds, int cropKinds, int animalKinds, int waterBlocks, int hostiles, int corruptBlocks) {
    public static final int RADIUS = 16;
    private static final int DOWN = 6;
    private static final int UP = 20;

    /** Tetos de cada categoria (para as barras da tela). */
    public static final int MAX_FLOWERS = 58;
    public static final int MAX_TREES = 40;
    public static final int MAX_CROPS = 45;
    public static final int MAX_ANIMALS = 60;
    public static final int MAX_WATER = 20;
    public static final int MAX_CORRUPTION = 80;

    public static final Harmony EMPTY = new Harmony(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public int total() {
        return Math.max(0, flowers + trees + crops + animals + water - corruption);
    }

    // =====================================================================================
    //  Cálculo (servidor)
    // =====================================================================================
    public static Harmony compute(ServerLevel level, BlockPos center) {
        Set<Block> flowerTypes = new HashSet<>();
        Set<Block> saplingTypes = new HashSet<>();
        Set<Block> logTypes = new HashSet<>();
        Set<Block> cropTypes = new HashSet<>();
        int flowerCount = 0, cropCount = 0, waterCount = 0, corruptCount = 0;

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int r2 = RADIUS * RADIUS;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                int x = center.getX() + dx, z = center.getZ() + dz;
                // Não força o carregamento de chunks: colunas fora da área carregada simplesmente não contam.
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int dy = -DOWN; dy <= UP; dy++) {
                    m.set(x, center.getY() + dy, z);
                    BlockState s = level.getBlockState(m);
                    if (s.isAir()) continue;
                    Block b = s.getBlock();
                    if (b == DruidContent.HEART_TREE_LEAVES.get() || b == DruidContent.HEART_TREE_LOG.get()
                            || b == DruidContent.HEART_TREE_CORE.get()) continue;

                    if (s.is(DruidContent.CORRUPTION)) {
                        corruptCount++;
                        continue;
                    }
                    if (s.is(BlockTags.FLOWERS) && !s.is(BlockTags.LEAVES)) {
                        flowerTypes.add(b);
                        flowerCount++;
                    } else if (s.is(BlockTags.SAPLINGS)) {
                        saplingTypes.add(b);
                    } else if (s.is(BlockTags.OVERWORLD_NATURAL_LOGS)) {
                        logTypes.add(b);
                    } else if (s.is(BlockTags.CROPS) || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.COCOA
                            || b == Blocks.SUGAR_CANE || b == Blocks.CAVE_VINES || b == Blocks.CAVE_VINES_PLANT) {
                        // Videiras de cavernas contam como um tipo só ("frutas brilhantes")
                        cropTypes.add(b == Blocks.CAVE_VINES_PLANT ? Blocks.CAVE_VINES : b);
                        cropCount++;
                    }
                    FluidState f = s.getFluidState();
                    if (!f.isEmpty() && f.isSource() && f.is(FluidTags.WATER)) waterCount++;
                }
            }
        }

        // Criaturas: tipos diferentes de animais (inclui peixes, morcegos, axolotes...) e monstros (corrupção)
        Set<EntityType<?>> animalTypes = new HashSet<>();
        int animalCount = 0, hostiles = 0;
        AABB box = new AABB(center).inflate(RADIUS, 10, RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            double ddx = e.getX() - (center.getX() + 0.5), ddz = e.getZ() - (center.getZ() + 0.5);
            if (ddx * ddx + ddz * ddz > r2 || e instanceof Player) continue;
            if (e.getType() == DruidContent.ANIMAL_SPIRIT.get()) continue;
            if (e instanceof Enemy) {
                hostiles++;
            } else if (e instanceof Animal || isWildlife(e.getType().getCategory())) {
                animalTypes.add(e.getType());
                animalCount++;
            }
        }

        int treeKinds = saplingTypes.size() + logTypes.size();
        int flowers = Math.min(48, flowerTypes.size() * 4) + Math.min(10, flowerCount / 3);
        int trees = Math.min(MAX_TREES, Math.min(20, saplingTypes.size() * 4) + Math.min(24, logTypes.size() * 4));
        int crops = Math.min(28, cropTypes.size() * 4) + Math.min(17, cropCount / 4);
        int animals = Math.min(50, animalTypes.size() * 5) + Math.min(10, animalCount);
        int water = waterCount > 0 ? Math.min(MAX_WATER, 5 + waterCount / 2) : 0;
        int corruption = Math.min(MAX_CORRUPTION, hostiles * 6 + corruptCount * 2);
        return new Harmony(flowers, trees, crops, animals, water, corruption,
                flowerTypes.size(), treeKinds, cropTypes.size(), animalTypes.size(), waterCount, hostiles, corruptCount);
    }

    private static boolean isWildlife(MobCategory c) {
        return c == MobCategory.CREATURE || c == MobCategory.AMBIENT || c == MobCategory.WATER_CREATURE
                || c == MobCategory.WATER_AMBIENT || c == MobCategory.UNDERGROUND_WATER_CREATURE || c == MobCategory.AXOLOTLS;
    }

    // =====================================================================================
    //  Rede
    // =====================================================================================
    public void write(FriendlyByteBuf buf) {
        int[] v = {flowers, trees, crops, animals, water, corruption, flowerKinds, treeKinds, cropKinds, animalKinds,
                waterBlocks, hostiles, corruptBlocks};
        for (int i : v) buf.writeVarInt(i);
    }

    public static Harmony read(FriendlyByteBuf buf) {
        int[] v = new int[13];
        for (int i = 0; i < v.length; i++) v[i] = buf.readVarInt();
        return new Harmony(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12]);
    }
}
