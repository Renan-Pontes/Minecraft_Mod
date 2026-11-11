package com.renanpontes.isekaimahou.feature.bosses_b.block;

import com.mojang.serialization.MapCodec;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Troféu do Arauto do Crepúsculo: estatueta sobre um pedestal, metade luz e metade sombra.
 * Decorativo; brilha e solta partículas de luz e sombra.
 */
public class HeraldTrophyBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<HeraldTrophyBlock> CODEC = simpleCodec(HeraldTrophyBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 3, 14),
            Block.box(3, 3, 3, 13, 4, 13),
            Block.box(5, 4, 5, 11, 15, 11));
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.89F, 0.48F), 0.8F);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.54F, 0.31F, 0.78F), 0.8F);

    public HeraldTrophyBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.75;
        double z = pos.getZ() + 0.5;
        if (rnd.nextInt(2) == 0) {
            level.addParticle(GOLD, x + (rnd.nextDouble() - 0.5) * 0.6, y + rnd.nextDouble() * 0.4, z + (rnd.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
        if (rnd.nextInt(2) == 0) {
            level.addParticle(VIOLET, x + (rnd.nextDouble() - 0.5) * 0.6, y + rnd.nextDouble() * 0.4, z + (rnd.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
        if (rnd.nextInt(8) == 0) level.addParticle(ParticleTypes.END_ROD, x, pos.getY() + 1.0, z, 0, 0.01, 0);
    }
}
