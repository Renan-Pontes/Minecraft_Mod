package com.renanpontes.isekaimahou.feature.hueco_mundo.world;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Restos de um Vazio gigante devorado: espinha enterrada na areia e costelas em arco saindo do chão (homenagem às
 * ossadas das dunas brancas). Até ~8 blocos da origem.
 */
public class BoneRemainsFeature extends Feature<NoneFeatureConfiguration> {

    public BoneRemainsFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        BlockPos o = ctx.origin();
        if (!level.getBlockState(o.below()).is(HmRegistries.HOLLOW_SAND.get())) return false;
        boolean alongX = rnd.nextBoolean();
        Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
        BlockState spine = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        BlockState rib = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        int len = 8 + rnd.nextInt(7);
        int half = len / 2;
        int ribR = 3 + rnd.nextInt(2);
        for (int i = -half; i <= half; i++) {
            BlockPos s = alongX ? o.offset(i, -1, 0) : o.offset(0, -1, i);
            setBlock(level, s, spine);
            if ((i + half) % 2 != 0 || Math.abs(i) > half - 1) continue;
            // Costela: arco de meio círculo para cada lado, mais baixo nas pontas da espinha.
            int r = Math.max(2, ribR - Math.abs(i) / 3);
            for (int side = -1; side <= 1; side += 2) {
                for (int a = 0; a <= 8; a++) {
                    double ang = Math.PI * a / 16.0;
                    int dx = (int) Math.round(Math.cos(ang) * r) * side;
                    int dy = (int) Math.round(Math.sin(ang) * r * 1.4);
                    BlockPos p = alongX ? s.offset(0, dy, dx) : s.offset(dx, dy, 0);
                    if (level.isEmptyBlock(p) || level.getBlockState(p).is(HmRegistries.HOLLOW_SAND.get())) setBlock(level, p, rib);
                }
            }
        }
        // Crânio na ponta: bloco de osso com olhos de cristal.
        BlockPos head = alongX ? o.offset(half + 1, 0, 0) : o.offset(0, 0, half + 1);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) setBlock(level, head.offset(dx, dy - 1, dz), Blocks.BONE_BLOCK.defaultBlockState());
            }
        }
        if (rnd.nextBoolean()) {
            BlockPos eye = head.above(1);
            if (level.isEmptyBlock(eye)) setBlock(level, eye, HmRegistries.HOLLOW_CRYSTAL.get().defaultBlockState());
        }
        return true;
    }
}
