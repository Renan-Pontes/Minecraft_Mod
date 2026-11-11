package com.renanpontes.isekaimahou.feature.hueco_mundo.world;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.Vec3;

/**
 * Árvore de quartzo do Mundo Oco: tronco fino de pilar de quartzo e galhos secos que se abrem para cima em ângulos
 * tortos, com cristais brilhando em algumas pontas. Fica dentro de ~9 blocos da origem (limite da decoração do chunk).
 */
public class QuartzTreeFeature extends Feature<NoneFeatureConfiguration> {
    private static final int MAX_REACH = 9;

    public QuartzTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        BlockPos origin = ctx.origin();
        BlockState ground = level.getBlockState(origin.below());
        if (!ground.is(HmRegistries.HOLLOW_SAND.get()) && !ground.is(HmRegistries.HOLLOW_SANDSTONE.get())) return false;
        int height = 5 + rnd.nextInt(6);
        BlockState trunk = Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        // Raiz enterrada para não ficar "flutuando" quando a areia escorre.
        for (int y = -2; y < 0; y++) setBlock(level, origin.offset(0, y, 0), trunk);
        for (int y = 0; y < height; y++) {
            BlockPos p = origin.above(y);
            if (!level.isEmptyBlock(p) && !level.getBlockState(p).canBeReplaced()) return y > 2;
            setBlock(level, p, trunk);
        }
        BlockPos top = origin.above(height - 1);
        int branches = 2 + rnd.nextInt(3);
        float base = rnd.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < branches; i++) {
            float yaw = base + i * Mth.TWO_PI / branches + (rnd.nextFloat() - 0.5F) * 0.8F;
            float pitch = 0.45F + rnd.nextFloat() * 0.6F;
            int startY = height - 1 - rnd.nextInt(Math.max(1, height / 2));
            branch(level, rnd, origin, origin.above(startY), yaw, pitch, 3 + rnd.nextInt(4), 1);
        }
        if (rnd.nextInt(3) == 0) crystal(level, top.above());
        return true;
    }

    private void branch(WorldGenLevel level, RandomSource rnd, BlockPos root, BlockPos from, float yaw, float pitch, int length, int depth) {
        Vec3 dir = new Vec3(Mth.cos(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.sin(yaw) * Mth.cos(pitch));
        Vec3 at = Vec3.atCenterOf(from);
        BlockPos last = from;
        for (int i = 0; i < length; i++) {
            at = at.add(dir);
            BlockPos p = BlockPos.containing(at);
            if (Math.abs(p.getX() - root.getX()) > MAX_REACH || Math.abs(p.getZ() - root.getZ()) > MAX_REACH) break;
            if (!level.isEmptyBlock(p)) break;
            setBlock(level, p, i < length / 2 ? Blocks.QUARTZ_BLOCK.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState());
            last = p;
            // Galho torto: muda um pouco de rumo a cada passo.
            dir = dir.add((rnd.nextDouble() - 0.5) * 0.35, 0.05, (rnd.nextDouble() - 0.5) * 0.35).normalize();
        }
        if (depth > 0 && length > 3) {
            for (int k = 0; k < 2; k++) {
                if (rnd.nextBoolean()) {
                    branch(level, rnd, root, last, yaw + (k == 0 ? 0.9F : -0.9F), pitch + 0.2F, 2 + rnd.nextInt(2), depth - 1);
                }
            }
        }
        if (rnd.nextInt(3) == 0) crystal(level, last.above());
    }

    private void crystal(WorldGenLevel level, BlockPos p) {
        if (level.isEmptyBlock(p) && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
            setBlock(level, p, HmRegistries.HOLLOW_CRYSTAL.get().defaultBlockState());
        }
    }
}
