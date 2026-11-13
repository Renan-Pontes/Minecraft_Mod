package com.renanpontes.isekaimahou.feature.forms_b.block;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Barreira mágica temporária (forma Muralha). Translúcida, tingida pela cor do elemento,
 * inquebrável à mão (só no criativo) e some sozinha: o {@link SpellBarrierBlockEntity} guarda quando.
 * Ao sumir, volta ao que havia ali (ar, água ou a plantinha que ocupou) — nunca apaga outro bloco.
 */
public class SpellBarrierBlock extends TransparentBlock implements EntityBlock, SimpleWaterloggedBlock {
    public static final MapCodec<SpellBarrierBlock> CODEC = simpleCodec(SpellBarrierBlock::new);
    /** Elemento (ordinal de {@link Element}) — só para a cor. */
    public static final IntegerProperty ELEMENT = IntegerProperty.create("element", 0, Element.values().length - 1);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** Duração padrão quando colocada à mão (criativo). */
    public static final int DEFAULT_TICKS = 200;

    public SpellBarrierBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ELEMENT, 0).setValue(WATERLOGGED, false));
    }

    public static BlockBehaviour.Properties barrierProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .noOcclusion()
                .lightLevel(s -> 7)
                .sound(SoundType.AMETHYST)
                .pushReaction(PushReaction.BLOCK)
                .isValidSpawn((s, l, p, t) -> false)
                .isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false);
    }

    @Override
    protected MapCodec<? extends SpellBarrierBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ELEMENT, WATERLOGGED);
    }

    /** Estado para um elemento (e alagado se o lugar tinha água parada). */
    public BlockState stateFor(Element element, boolean water) {
        return defaultBlockState().setValue(ELEMENT, element.ordinal()).setValue(WATERLOGGED, water);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return defaultBlockState().setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) != 0) return;
        Direction d = Direction.getRandom(random);
        double x = pos.getX() + 0.5 + d.getStepX() * 0.52 + (d.getStepX() == 0 ? random.nextDouble() - 0.5 : 0);
        double y = pos.getY() + 0.5 + d.getStepY() * 0.52 + (d.getStepY() == 0 ? random.nextDouble() - 0.5 : 0);
        double z = pos.getZ() + 0.5 + d.getStepZ() * 0.52 + (d.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0);
        level.addParticle(Vfx.enchant(), x, y, z, 0, 0.05, 0);
    }

    /**
     * Remove a barreira (se AINDA for barreira), com partículas de quebra. Devolve o que havia antes:
     * água (se estava alagada), a plantinha/neve que ela ocupou (se ainda puder ficar ali) ou ar.
     */
    public static void dissolve(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof SpellBarrierBlock)) return;
        BlockState restore = st.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        if (level.getBlockEntity(pos) instanceof SpellBarrierBlockEntity be && be.previous() != null
                && !st.getValue(WATERLOGGED)) {
            // A planta só volta se o chão embaixo ainda a sustenta (alguém pode ter cavado)
            BlockState prev = be.previous();
            if (prev.canSurvive(level, pos)) restore = prev;
        }
        level.setBlock(pos, restore, Block.UPDATE_ALL);
        // Evento 2001 = partículas de quebra + som do bloco (ametista)
        if (level instanceof ServerLevel sl) sl.levelEvent(2001, pos, Block.getId(st));
    }

    // =====================================================================================
    //  Bloco-entidade com o cronômetro
    // =====================================================================================
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpellBarrierBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != FormsBRegistry.SPELL_BARRIER_BE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<SpellBarrierBlockEntity>) SpellBarrierBlockEntity::serverTick;
    }
}
