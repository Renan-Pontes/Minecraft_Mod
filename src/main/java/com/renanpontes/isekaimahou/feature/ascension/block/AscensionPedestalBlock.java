package com.renanpontes.isekaimahou.feature.ascension.block;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.ascension.AscensionAltarGuard;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pedestal de Ascensão: guarda UM tipo de item (uma pilha) do tributo, que flutua girando em cima dele.
 * Clique com o item para colocar (agachado: só 1); mão vazia retira.
 */
public class AscensionPedestalBlock extends BaseEntityBlock {
    public static final MapCodec<AscensionPedestalBlock> CODEC = simpleCodec(AscensionPedestalBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 2, 15),
            Block.box(4, 2, 4, 12, 10, 12),
            Block.box(2, 10, 2, 14, 12, 14));

    public AscensionPedestalBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AscensionPedestalBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof AscensionPedestalBlockEntity ped)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemStack held = ped.getItem();
        boolean same = !held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack);
        // Mesmo item e pilha cheia: não faz nada (nem retira por engano, nem usa o item no bloco).
        if (same && held.getCount() >= held.getMaxStackSize()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.ascension.pedestal_full")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        // Item diferente: o clique retira o que está no pedestal (useWithoutItem).
        if (!held.isEmpty() && !same) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            int amount = player.isShiftKeyDown() ? 1 : stack.getCount();
            int inserted = ped.insert(stack, amount);
            if (inserted > 0) {
                if (!player.getAbilities().instabuild) stack.shrink(inserted);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.4F);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AscensionPedestalBlockEntity ped) || ped.getItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            ItemStack out = ped.removeAll();
            if (player.getMainHandItem().isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, out);
            else if (!player.getInventory().add(out)) player.drop(out, false);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Peça de altar público: não quebra fora do criativo.
        if (AscensionAltarGuard.lockedFor(player, level, pos)) return 0F;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return AscensionAltarGuard.isPublicPiece(level, pos) ? AscensionAltarGuard.BLAST_PROOF
                : super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return AscensionAltarGuard.entityMayDestroy(entity, level, pos) && super.canEntityDestroy(state, level, pos, entity);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AscensionPedestalBlockEntity ped) {
            ItemStack s = ped.removeAll();
            if (!s.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, s);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AscensionPedestalBlockEntity ped) || ped.getItem().isEmpty()) return 0;
        ItemStack s = ped.getItem();
        return 1 + Math.round(14F * s.getCount() / s.getMaxStackSize());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ascension_pedestal").withStyle(ChatFormatting.GRAY));
    }
}
