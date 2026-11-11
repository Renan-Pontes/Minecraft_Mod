package com.renanpontes.isekaimahou.feature.ritual_druid.block;

import org.joml.Vector3f;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Folhas da Árvore Coração: brilham de leve e soltam faíscas. Nos estágios altos o topo fica DOURADO
 * (propriedade {@link #GOLDEN}), mais luminoso e com brilhos caindo. Não apodrecem: quem cuida delas é o Núcleo.
 */
public class HeartTreeLeavesBlock extends Block {
    public static final MapCodec<HeartTreeLeavesBlock> CODEC = simpleCodec(HeartTreeLeavesBlock::new);
    public static final BooleanProperty GOLDEN = BooleanProperty.create("golden");
    private static final DustParticleOptions GREEN_GLOW = new DustParticleOptions(new Vector3f(0.6F, 1.0F, 0.55F), 0.6F);
    private static final DustParticleOptions GOLD_GLOW = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.35F), 0.8F);

    public HeartTreeLeavesBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(GOLDEN, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GOLDEN);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 1;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean golden = state.getValue(GOLDEN);
        if (!level.getBlockState(pos.below()).isAir()) return;
        double x = pos.getX() + random.nextDouble(), y = pos.getY() - 0.05, z = pos.getZ() + random.nextDouble();
        if (golden) {
            if (random.nextInt(8) == 0) level.addParticle(GOLD_GLOW, x, y, z, 0, -0.02, 0);
            if (random.nextInt(30) == 0) level.addParticle(ParticleTypes.WAX_ON, x, y, z, 0, -0.5, 0);
            if (random.nextInt(60) == 0) level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, -0.03, 0);
        } else {
            if (random.nextInt(20) == 0) level.addParticle(GREEN_GLOW, x, y, z, 0, -0.02, 0);
            if (random.nextInt(70) == 0) level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 0, 0, 0);
        }
    }
}
