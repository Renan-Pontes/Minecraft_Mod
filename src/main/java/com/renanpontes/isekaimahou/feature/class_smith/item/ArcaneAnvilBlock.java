package com.renanpontes.isekaimahou.feature.class_smith.item;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.class_smith.SmithNet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bigorna Arcana: bancada do Ferreiro. Clique direito abre a tela de montagem (forjar lâmina nova, mexer nas partes da
 * Lâmina Forjada da mão, trocar material, temperar a qualidade, gravar runa). Também é o altar das Provas da Forja
 * (subir de rank perto dela, pela aba "Forja" do menu B). Brasas e runas brilham sobre o bloco.
 */
public class ArcaneAnvilBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ArcaneAnvilBlock> CODEC = simpleCodec(ArcaneAnvilBlock::new);

    private static final VoxelShape BASE = Block.box(2, 0, 2, 14, 4, 14);
    private static final VoxelShape X_SHAPE = Shapes.or(BASE, Block.box(4, 4, 3, 12, 5, 13), Block.box(6, 5, 4, 10, 10, 12),
            Block.box(3, 10, 0, 13, 16, 16));
    private static final VoxelShape Z_SHAPE = Shapes.or(BASE, Block.box(3, 4, 4, 13, 5, 12), Block.box(4, 5, 6, 12, 10, 10),
            Block.box(0, 10, 3, 16, 16, 13));

    public ArcaneAnvilBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getClockWise());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.35F, 1.5F);
            SmithNet.openAnvil(sp, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
        double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
        level.addParticle(random.nextInt(4) == 0 ? ParticleTypes.LAVA : ParticleTypes.SMALL_FLAME, x, pos.getY() + 1.02, z,
                0, 0.02, 0);
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 1.2, -0.6, (random.nextDouble() - 0.5) * 1.2);
        }
    }
}
