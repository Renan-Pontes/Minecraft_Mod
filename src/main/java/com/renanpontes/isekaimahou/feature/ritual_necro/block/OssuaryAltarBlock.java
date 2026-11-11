package com.renanpontes.isekaimahou.feature.ritual_necro.block;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.ritual_necro.NecroContent;
import com.renanpontes.isekaimahou.feature.ritual_necro.NecroRitual;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Altar Ossuário: centro do Rito Ossuário do Necromante.
 * Clique com o Filactério (na mão, ou na mão secundária com a mão principal vazia) para tentar o rito;
 * com a mão vazia mostra a lista do que falta. {@link #LIT} = multibloco completo (brilha).
 */
public class OssuaryAltarBlock extends BaseEntityBlock {
    public static final MapCodec<OssuaryAltarBlock> CODEC = simpleCodec(OssuaryAltarBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape SHAPE = Shapes.or(
            box(1, 0, 1, 15, 3, 15),
            box(4, 3, 4, 12, 10, 12),
            box(2, 10, 2, 14, 13, 14),
            box(2, 13, 2, 4, 16, 4), box(12, 13, 2, 14, 16, 4), box(2, 13, 12, 4, 16, 14), box(12, 13, 12, 14, 16, 14));

    public OssuaryAltarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OssuaryAltarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, NecroContent.ALTAR_BE.get(),
                level.isClientSide ? OssuaryAltarBlockEntity::clientTick : OssuaryAltarBlockEntity::serverTick);
    }

    // =====================================================================================
    //  Interação
    // =====================================================================================
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.is(NecroContent.PHYLACTERY.get())) {
            if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof OssuaryAltarBlockEntity be) {
                NecroRitual.tryStart(sp, stack, be);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // Mão vazia ou a Foice: mostra o estado do altar. Outros itens seguem o uso normal (ex.: colocar blocos).
        if (stack.isEmpty() || stack.is(NecroContent.BONE_SCYTHE.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof OssuaryAltarBlockEntity be) {
            ItemStack off = player.getOffhandItem();
            if (off.is(NecroContent.PHYLACTERY.get())) NecroRitual.tryStart(sp, off, be);
            else NecroRitual.showStatus(sp, be);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Altar quebrado no meio do rito: as almas já foram oferecidas, então o rito se conclui na hora
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof OssuaryAltarBlockEntity be) {
            be.interrupt();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ossuary_altar.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ossuary_altar.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ossuary_altar.3").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ossuary_altar.4").withStyle(ChatFormatting.DARK_AQUA));
    }
}
