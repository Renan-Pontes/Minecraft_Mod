package com.renanpontes.isekaimahou.feature.hueco_mundo.world;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Ruínas do Mundo Oco meio engolidas pela areia: colunatas quebradas, arcos e cúpulas rachadas de uma civilização que o
 * deserto apagou. Às vezes guardam um baú ({@code chests/hollow_ruin}). Tudo dentro de ~7 blocos da origem.
 */
public class HollowRuinFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, IsekaiMahou.id("chests/hollow_ruin"));

    public HollowRuinFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        BlockPos o = ctx.origin();
        if (!level.getBlockState(o.below()).is(HmRegistries.HOLLOW_SAND.get())) return false;
        switch (rnd.nextInt(3)) {
            case 0 -> colonnade(level, rnd, o);
            case 1 -> arch(level, rnd, o);
            default -> dome(level, rnd, o);
        }
        if (rnd.nextInt(4) == 0) chest(level, rnd, o);
        return true;
    }

    private BlockState brick(RandomSource rnd) {
        int r = rnd.nextInt(10);
        if (r < 6) return HmRegistries.HOLLOW_SANDSTONE_BRICKS.get().defaultBlockState();
        if (r < 8) return Blocks.QUARTZ_BRICKS.defaultBlockState();
        return HmRegistries.HOLLOW_SANDSTONE.get().defaultBlockState();
    }

    /** Duas fileiras de colunas de alturas diferentes; uma caída deitada na areia. */
    private void colonnade(WorldGenLevel level, RandomSource rnd, BlockPos o) {
        BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int i = -1; i <= 1; i++) {
            for (int side = -1; side <= 1; side += 2) {
                BlockPos base = ground(level, o.offset(i * 3, 0, side * 2));
                int h = 1 + rnd.nextInt(6);
                setBlock(level, base.below(), brick(rnd));
                for (int y = 0; y < h; y++) setBlock(level, base.above(y), y == h - 1 && h > 3 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : pillar);
            }
        }
        // Coluna tombada.
        BlockState lying = Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockPos start = ground(level, o.offset(-3, 0, 4));
        for (int x = 0; x < 3 + rnd.nextInt(3); x++) setBlock(level, start.offset(x, 0, 0), lying);
    }

    /** Arco solitário com a viga do topo rachada. */
    private void arch(WorldGenLevel level, RandomSource rnd, BlockPos o) {
        int w = 3 + rnd.nextInt(3), h = 5 + rnd.nextInt(4);
        boolean alongX = rnd.nextBoolean();
        BlockPos a = ground(level, alongX ? o.offset(-w, 0, 0) : o.offset(0, 0, -w));
        BlockPos b = ground(level, alongX ? o.offset(w, 0, 0) : o.offset(0, 0, w));
        int top = Math.max(a.getY(), b.getY()) + h;
        for (BlockPos leg : new BlockPos[] {a, b}) {
            for (int y = leg.getY() - 1; y < top; y++) setBlock(level, new BlockPos(leg.getX(), y, leg.getZ()), brick(rnd));
        }
        for (int i = -w; i <= w; i++) {
            if (rnd.nextInt(6) == 0) continue;
            BlockPos p = alongX ? new BlockPos(o.getX() + i, top, o.getZ()) : new BlockPos(o.getX(), top, o.getZ() + i);
            setBlock(level, p, i == 0 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : brick(rnd));
            if (Math.abs(i) == w - 1) setBlock(level, p.below(), brick(rnd));
        }
    }

    /** Cúpula afundada: meia esfera de tijolos com buracos (dá para entrar e achar o baú). */
    private void dome(WorldGenLevel level, RandomSource rnd, BlockPos o) {
        int r = 4 + rnd.nextInt(3);
        BlockPos c = o.below(2);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = 0; y <= r; y++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    BlockPos p = c.offset(x, y, z);
                    if (d <= r + 0.5 && d >= r - 0.5) {
                        if (y > 1 && rnd.nextInt(5) == 0) continue;
                        setBlock(level, p, brick(rnd));
                    } else if (d < r - 0.5 && y >= 1) {
                        setBlock(level, p, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        for (int x = -r + 1; x <= r - 1; x++) {
            for (int z = -r + 1; z <= r - 1; z++) {
                if (x * x + z * z <= (r - 1) * (r - 1)) setBlock(level, c.offset(x, 0, z), HmRegistries.HOLLOW_SANDSTONE_BRICKS.get().defaultBlockState());
            }
        }
        setBlock(level, c.offset(0, 1, 0), HmRegistries.HOLLOW_CRYSTAL.get().defaultBlockState());
    }

    private void chest(WorldGenLevel level, RandomSource rnd, BlockPos o) {
        BlockPos p = ground(level, o.offset(rnd.nextInt(3) - 1, 0, rnd.nextInt(3) - 1));
        if (!level.isEmptyBlock(p)) p = p.above();
        if (!level.isEmptyBlock(p)) return;
        setBlock(level, p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(rnd)));
        RandomizableContainer.setBlockEntityLootTable(level, rnd, p, LOOT);
    }

    /** Primeiro bloco livre acima do chão naquela coluna (procura 6 para cima e para baixo). */
    private static BlockPos ground(WorldGenLevel level, BlockPos p) {
        BlockPos q = p;
        for (int i = 0; i < 6 && !level.isEmptyBlock(q); i++) q = q.above();
        for (int i = 0; i < 6 && level.isEmptyBlock(q.below()); i++) q = q.below();
        return q;
    }
}
