package com.renanpontes.isekaimahou.feature.hueco_mundo.world;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Cogumelo gigante luminoso do Reino das Fadas: haste levemente inclinada, chapéu em domo de "Chapéu-Luz" com pintas de
 * cogumelo-luz por baixo e vinhas pendendo da borda. Até ~6 blocos da origem.
 */
public class FeyMushroomFeature extends Feature<NoneFeatureConfiguration> {

    public FeyMushroomFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        BlockPos o = ctx.origin();
        BlockState ground = level.getBlockState(o.below());
        if (!ground.is(BlockTags.DIRT) && !ground.is(Blocks.MOSS_BLOCK)) return false;
        int h = 4 + rnd.nextInt(5);
        int lean = rnd.nextInt(4);
        BlockState stem = Blocks.MUSHROOM_STEM.defaultBlockState().setValue(HugeMushroomBlock.UP, false).setValue(HugeMushroomBlock.DOWN, false);
        BlockPos p = o;
        for (int y = 0; y < h; y++) {
            if (!level.isEmptyBlock(p) && !level.getBlockState(p).canBeReplaced()) return y > 2;
            setBlock(level, p, stem);
            // Inclina um bloco para o lado na metade de cima (silhueta mais orgânica).
            if (y == h / 2 && lean < 2) p = lean == 0 ? p.east() : p.north();
            p = p.above();
        }
        int r = 2 + rnd.nextInt(3);
        BlockState cap = HmRegistries.FEY_GLOWCAP.get().defaultBlockState();
        BlockPos top = p;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int d2 = x * x + z * z;
                if (d2 > r * r + 1) continue;
                int dy = d2 <= (r - 1) * (r - 1) ? 0 : -1;
                BlockPos c = top.offset(x, dy, z);
                if (level.isEmptyBlock(c)) setBlock(level, c, cap);
                if (dy == 0 && d2 <= (r - 2) * (r - 2) && level.isEmptyBlock(c.above())) setBlock(level, c.above(), cap);
                if (dy == 0 && d2 > 1 && rnd.nextInt(4) == 0) {
                    BlockPos under = c.below();
                    if (level.isEmptyBlock(under)) setBlock(level, under, Blocks.SHROOMLIGHT.defaultBlockState());
                }
                if (dy == -1 && rnd.nextInt(3) == 0) {
                    BlockPos v = c.below();
                    for (int k = 0; k < 1 + rnd.nextInt(3) && level.isEmptyBlock(v); k++) {
                        setBlock(level, v, Blocks.CAVE_VINES_PLANT.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.CaveVines.BERRIES, rnd.nextInt(3) == 0));
                        v = v.below();
                    }
                }
            }
        }
        return true;
    }
}
