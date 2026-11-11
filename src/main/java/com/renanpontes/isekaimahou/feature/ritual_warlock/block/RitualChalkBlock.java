package com.renanpontes.isekaimahou.feature.ritual_warlock.block;

import org.joml.Vector3f;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.ritual_warlock.WarlockContent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Giz Ritual desenhado no chão. Fino como um tapete e sem colisão (como o pó de redstone).
 * {@link #RUNE}: qual das 8 runas está desenhada. {@link #BLOOD}: o círculo foi ativado com sangue
 * (fica vermelho e brilha enquanto um Patrono está preso nele).
 */
public class RitualChalkBlock extends Block {
    public static final MapCodec<RitualChalkBlock> CODEC = simpleCodec(RitualChalkBlock::new);
    public static final int RUNES = 8;
    public static final IntegerProperty RUNE = IntegerProperty.create("rune", 0, RUNES - 1);
    public static final BooleanProperty BLOOD = BooleanProperty.create("blood");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);
    private static final Vector3f BLOOD_COLOR = new Vector3f(0.75F, 0.05F, 0.08F);

    public RitualChalkBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(RUNE, 0).setValue(BLOOD, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNE, BLOOD);
    }

    /** Runa "natural" para uma posição (o desenho parece variado sem o jogador escolher). */
    public static int runeFor(BlockPos pos) {
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        return (int) Math.floorMod(h ^ (h >>> 29), RUNES);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.DOWN && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** Bloco do meio (criativo) devolve o Giz Ritual. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(WarlockContent.RITUAL_CHALK_ITEM.get());
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }

    /** Giz ensanguentado solta uma névoa vermelha de vez em quando. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(BLOOD) || random.nextInt(4) != 0) return;
        level.addParticle(new DustParticleOptions(BLOOD_COLOR, 0.8F),
                pos.getX() + random.nextDouble(), pos.getY() + 0.1, pos.getZ() + random.nextDouble(),
                0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
    }
}
