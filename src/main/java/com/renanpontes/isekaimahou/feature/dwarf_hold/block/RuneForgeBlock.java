package com.renanpontes.isekaimahou.feature.dwarf_hold.block;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.dwarf_hold.DwarfRegistry;
import com.renanpontes.isekaimahou.feature.dwarf_hold.rune.Rune;
import com.renanpontes.isekaimahou.feature.dwarf_hold.rune.RuneForging;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * Forja Rúnica dos anões: segure a peça (arma, armadura, escudo ou ferramenta) na mão principal e uma Essência na
 * secundária e clique na forja. Custa {@link RuneForging#INGOTS} Lingotes de Mithril e {@link RuneForging#LEVELS} níveis
 * de XP. Clique sem nada na mão para ver as runas.
 */
public class RuneForgeBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RuneForgeBlock> CODEC = simpleCodec(RuneForgeBlock::new);
    private static final VoxelShape SHAPE_X = Shapes.or(Block.box(3, 0, 2, 13, 4, 14), Block.box(5, 4, 4, 11, 10, 12),
            Block.box(2, 10, 0, 14, 15, 16));
    private static final VoxelShape SHAPE_Z = Shapes.or(Block.box(2, 0, 3, 14, 4, 13), Block.box(4, 4, 5, 12, 10, 11),
            Block.box(0, 10, 2, 16, 15, 14));

    public RuneForgeBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getClockWise());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_Z : SHAPE_X;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) RuneForging.tryEngrave(player, pos);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) RuneForging.showHelp(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // brasas no braseiro e runas flutuando de vez em quando
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.3,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
        }
        if (random.nextInt(5) == 0) {
            Rune r = Rune.values()[random.nextInt(Rune.values().length)];
            level.addParticle(ElementFx.dust(r.element, 0.7F), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                    pos.getY() + 1.05, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
        }
        if (random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5), -0.6, (random.nextDouble() - 0.5));
        }
    }

    /** O item da forja (para receitas/comandos). */
    public static ItemStack stack() {
        return new ItemStack(DwarfRegistry.RUNE_FORGE_ITEM.get());
    }
}
