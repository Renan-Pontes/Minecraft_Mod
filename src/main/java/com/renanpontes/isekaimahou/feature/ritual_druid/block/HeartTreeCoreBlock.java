package com.renanpontes.isekaimahou.feature.ritual_druid.block;

import java.util.List;

import org.joml.Vector3f;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.ritual_druid.DruidContent;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Núcleo da Árvore Coração: a base viva do tronco, com o coração de luz pulsando.
 * Clique: oferenda (com o item certo na mão) ou a tela da Harmonia. Só o dono consegue quebrá-lo.
 */
public class HeartTreeCoreBlock extends BaseEntityBlock {
    public static final MapCodec<HeartTreeCoreBlock> CODEC = simpleCodec(HeartTreeCoreBlock::new);
    private static final DustParticleOptions HEART_DUST = new DustParticleOptions(new Vector3f(0.6F, 1.0F, 0.6F), 1.0F);

    public HeartTreeCoreBlock(Properties props) {
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
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeartTreeCoreBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, DruidContent.HEART_TREE_CORE_BE.get(), HeartTreeCoreBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof HeartTreeCoreBlockEntity be) {
            be.interact(sp, stack);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof HeartTreeCoreBlockEntity be) {
            be.interact(sp, ItemStack.EMPTY);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Núcleo colocado direto (criativo): vira a árvore de quem colocou, se ele ainda não tiver uma
        if (placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof HeartTreeCoreBlockEntity be) {
            be.faceToward(sp);
            be.claim(sp);
        }
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Só o dono quebra a própria Árvore Coração (no sobrevivência)
        if (level.getBlockEntity(pos) instanceof HeartTreeCoreBlockEntity be && be.owner() != null
                && !be.owner().equals(player.getUUID())) return 0F;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HeartTreeCoreBlockEntity be) {
            be.onRemoved();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int stage = level.getBlockEntity(pos) instanceof HeartTreeCoreBlockEntity be ? be.stage() : 0;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        // Pulsos de luz saindo do coração pelas faces
        if (random.nextInt(3) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(HEART_DUST, x + Math.cos(a) * 0.62, y + (random.nextDouble() - 0.5) * 0.6, z + Math.sin(a) * 0.62,
                    0, 0.02, 0);
        }
        if (random.nextInt(Math.max(2, 10 - stage)) == 0) {
            level.addParticle(Vfx.happy(), x + (random.nextDouble() - 0.5) * 3, pos.getY() + 0.1,
                    z + (random.nextDouble() - 0.5) * 3, 0, 0.05, 0);
        }
        if (stage >= 5 && random.nextInt(6) == 0) {
            level.addParticle(Vfx.endRod(), x + (random.nextDouble() - 0.5), y + 0.6, z + (random.nextDouble() - 0.5),
                    0, 0.04, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.heart_tree_core").withStyle(ChatFormatting.GRAY));
    }
}
