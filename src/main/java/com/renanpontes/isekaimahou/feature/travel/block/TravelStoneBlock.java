package com.renanpontes.isekaimahou.feature.travel.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.travel.TravelContent;
import com.renanpontes.isekaimahou.feature.travel.TravelStones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pedra de Teleporte: obelisco rúnico (o modelo animado é do GeckoLib, desenhado pelo renderer do cliente).
 * Tocar = sintonizar (descobre o destino); clicar de novo = tela "Viagem". Etiqueta com nome renomeia a pedra do jogador.
 */
public class TravelStoneBlock extends BaseEntityBlock {
    public static final MapCodec<TravelStoneBlock> CODEC = simpleCodec(TravelStoneBlock::new);
    /** Contorno: plinto, degrau e fuste (até o topo do bloco). */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(2, 3, 2, 14, 5, 14),
            Block.box(4, 5, 4, 12, 16, 12));
    /** Colisão: o fuste sobe 1,5 bloco (não dá para pular por cima do obelisco). */
    private static final VoxelShape COLLISION = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(2, 3, 2, 14, 5, 14),
            Block.box(4, 5, 4, 12, 24, 12));

    public TravelStoneBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return COLLISION;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TravelStoneBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, TravelContent.TRAVEL_STONE_BE.get(), TravelStoneBlockEntity::clientTick)
                : createTickerHelper(type, TravelContent.TRAVEL_STONE_BE.get(), TravelStoneBlockEntity::serverTick);
    }

    // =====================================================================================
    //  Colocar, usar, quebrar
    // =====================================================================================
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl && placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof TravelStoneBlockEntity be) {
            be.placedBy(sl, sp);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof TravelStoneBlockEntity be) {
            be.interact(sl, sp);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.NAME_TAG) || !stack.has(DataComponents.CUSTOM_NAME)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)
                || !(level.getBlockEntity(pos) instanceof TravelStoneBlockEntity be)) {
            return ItemInteractionResult.CONSUME;
        }
        boolean creative = sp.getAbilities().instabuild;
        if (!creative && !be.canBreak(sp.getUUID())) {
            sp.displayClientMessage(Component.translatable("travel.isekai_mahou.stone.rename_denied").withColor(0xFF7070), true);
            return ItemInteractionResult.CONSUME;
        }
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (!be.rename(sl, name)) {
            sp.displayClientMessage(Component.translatable("travel.isekai_mahou.stone.rename_linked", be.display() != null
                    ? be.display() : Component.empty()).withColor(0xFF7070), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!creative) stack.shrink(1);
        sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 1.2F);
        sp.displayClientMessage(Component.translatable("travel.isekai_mahou.stone.renamed", name)
                .withColor(TravelStones.WAYSTONE_RGB), true);
        return ItemInteractionResult.CONSUME;
    }

    /** Pedra das estruturas não quebra; a do jogador só pelo dono (criativo sempre). */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!player.getAbilities().instabuild && level.getBlockEntity(pos) instanceof TravelStoneBlockEntity be
                && !be.canBreak(player.getUUID())) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /** Começou a bater numa pedra protegida: explica por quê. */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && !sp.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof TravelStoneBlockEntity be && !be.canBreak(sp.getUUID())) {
            sp.displayClientMessage(Component.translatable(be.natural() ? "travel.isekai_mahou.stone.protected"
                    : "travel.isekai_mahou.stone.not_owner").withColor(0xFF9A6A), true);
        }
        super.attack(state, level, pos, player);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        if (level.getBlockEntity(pos) instanceof TravelStoneBlockEntity be && be.natural()) return 3_600_000.0F;
        return super.getExplosionResistance(state, level, pos, explosion);
    }

    /** Nenhum mob (Wither, dragão, chefes) derruba uma pedra de teleporte. */
    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl
                && level.getBlockEntity(pos) instanceof TravelStoneBlockEntity be) {
            be.onRemoved(sl);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
