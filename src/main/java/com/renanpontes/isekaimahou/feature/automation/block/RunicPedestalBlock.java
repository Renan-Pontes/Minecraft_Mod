package com.renanpontes.isekaimahou.feature.automation.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.automation.AutoRegistries;
import com.renanpontes.isekaimahou.feature.automation.ManaNet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pedestal Rúnico: base larga, coluna com runas e um tampo com o círculo mágico. Clique com um Pergaminho de Magia
 * para colocá-lo; clique vazio para abrir a tela de configuração. {@link #ACTIVE} acende o tampo e liga o anel.
 */
public class RunicPedestalBlock extends BaseEntityBlock {
    public static final MapCodec<RunicPedestalBlock> CODEC = simpleCodec(RunicPedestalBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(4, 3, 4, 12, 11, 12),
            Block.box(2, 11, 2, 14, 14, 14));

    public RunicPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    /** Respeita o limite de pedestais por chunk (config). */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        int max = RunicPedestalBlockEntity.maxPerChunk();
        if (max <= 0 || ManaNet.pedestalsInChunk(level, ctx.getClickedPos()).size() >= max) {
            if (!level.isClientSide && ctx.getPlayer() != null) {
                ctx.getPlayer().displayClientMessage(max <= 0
                        ? Component.translatable("message.isekai_mahou.automation.pedestal_disabled").withStyle(ChatFormatting.RED)
                        : Component.translatable("message.isekai_mahou.automation.pedestal_chunk_limit", max).withStyle(ChatFormatting.RED), true);
            }
            return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof RunicPedestalBlockEntity be) {
            be.setOwner(p);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RunicPedestalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, AutoRegistries.RUNIC_PEDESTAL_BE.get(), RunicPedestalBlockEntity::clientTick)
                : createTickerHelper(type, AutoRegistries.RUNIC_PEDESTAL_BE.get(), RunicPedestalBlockEntity::serverTick);
    }

    /** Clique com um Pergaminho de Magia num pedestal vazio: coloca direto. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(CoreRegistries.SPELL_SCROLL.get()) || !(level.getBlockEntity(pos) instanceof RunicPedestalBlockEntity be)
                || !be.scroll().isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!be.mayConfigure(player)) {
            if (!level.isClientSide) denied(player, be);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            if (be.owner() == null) be.setOwner(player); // pedestal sem dono (comando/estrutura): quem usa adota
            be.setItem(0, stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.2F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof RunicPedestalBlockEntity be) {
            if (sp.isSecondaryUseActive()) {
                // Agachado: só mostra o estado na barra de ação (qualquer um pode olhar)
                PedestalStatus st = be.status();
                sp.displayClientMessage(Component.translatable("gui.isekai_mahou.pedestal.status", be.statusMessage())
                        .withColor(st.rgb), true);
            } else if (!be.mayConfigure(sp)) {
                denied(sp, be);
            } else {
                if (be.owner() == null) be.setOwner(sp); // pedestal sem dono (comando/estrutura): quem usa adota
                sp.openMenu(be, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.CONSUME;
    }

    private static void denied(Player player, RunicPedestalBlockEntity be) {
        player.displayClientMessage(Component.translatable("message.isekai_mahou.automation.pedestal_not_owner", be.ownerName())
                .withStyle(ChatFormatting.RED), true);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RunicPedestalBlockEntity be) {
            be.onNeighborChanged(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RunicPedestalBlockEntity be) {
            Containers.dropContents(level, pos, be);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Ativo: runas subindo em espiral até o tampo. Parado: um fiapo de fumaça de vez em quando. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE)) {
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5 + Math.cos(a) * 0.9, pos.getY() + 0.2,
                    pos.getZ() + 0.5 + Math.sin(a) * 0.9, -Math.cos(a) * 0.6, 0.9, -Math.sin(a) * 0.6);
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 0.95,
                        pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            }
        } else if (random.nextInt(8) == 0 && level.getBlockEntity(pos) instanceof RunicPedestalBlockEntity be && !be.scroll().isEmpty()) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 0, 0.02, 0);
        }
    }
}
