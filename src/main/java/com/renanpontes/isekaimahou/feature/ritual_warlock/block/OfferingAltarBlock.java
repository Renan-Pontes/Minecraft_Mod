package com.renanpontes.isekaimahou.feature.ritual_warlock.block;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.ritual_warlock.WarlockContent;
import com.renanpontes.isekaimahou.feature.ritual_warlock.entity.PatronEntity;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactRitual;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PatronKind;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
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
 * Altar de Oferendas (centro do círculo de giz).
 * <ul>
 *   <li>Clique com uma oferenda (fogo / ender / água): coloca 1 item no altar.</li>
 *   <li>Mão vazia: devolve a oferenda; com um Patrono preso, abre o diálogo.</li>
 *   <li>Agachado com a mão vazia: mostra onde falta giz e velas para o próximo pacto.</li>
 *   <li>Adaga Ritual (à noite): paga 2 corações e invoca o Patrono.</li>
 * </ul>
 */
public class OfferingAltarBlock extends BaseEntityBlock {
    public static final MapCodec<OfferingAltarBlock> CODEC = simpleCodec(OfferingAltarBlock::new);
    /** Altura do tampo (o Patrono fica logo acima). */
    public static final double TOP = 13.0 / 16.0;
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(3, 3, 3, 13, 10, 13),
            Block.box(1, 10, 1, 15, 13, 15));

    public OfferingAltarBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OfferingAltarBlockEntity(pos, state);
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof OfferingAltarBlockEntity altar)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;

        if (stack.is(WarlockContent.RITUAL_DAGGER.get())) {
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) PactRitual.activate(sp, sl, pos, altar);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // O giz cuida de si (agachado no altar = desenhar o anel automaticamente).
        if (stack.is(WarlockContent.RITUAL_CHALK_ITEM.get())) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;

        PatronKind kind = PatronKind.forOffering(stack);
        if (kind == null) {
            // Blocos (velas etc.) seguem o fluxo normal de colocar bloco.
            if (stack.getItem() instanceof BlockItem) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.warlock.bad_offering").withStyle(ChatFormatting.GRAY), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level instanceof ServerLevel sl) {
            if (altar.isOccupied(sl)) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.warlock.already_bound").withStyle(ChatFormatting.RED), true);
                return ItemInteractionResult.CONSUME;
            }
            ItemStack old = altar.takeOffering();
            if (!old.isEmpty() && !player.addItem(old)) player.drop(old, false);
            altar.setOffering(stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            sl.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.8F, 0.7F);
            sl.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.01);
            player.displayClientMessage(Component.translatable("message.isekai_mahou.warlock.offering_placed", kind.displayName()), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof OfferingAltarBlockEntity altar)) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            PactRitual.showGuide(sp, sl, pos);
            return InteractionResult.CONSUME;
        }
        PatronEntity patron = altar.boundPatron(sl);
        if (patron != null) {
            PactRitual.openDialog(sp, patron);
            return InteractionResult.CONSUME;
        }
        if (!altar.getOffering().isEmpty()) {
            ItemStack back = altar.takeOffering();
            if (!player.addItem(back)) player.drop(back, false);
            sl.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 0.8F);
            player.displayClientMessage(Component.translatable("message.isekai_mahou.warlock.offering_taken"), true);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("message.isekai_mahou.warlock.altar_hint").withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof OfferingAltarBlockEntity altar) {
            ItemStack s = altar.getOffering();
            if (!s.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, s.copy());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof OfferingAltarBlockEntity altar)) return;
        double x = pos.getX() + 0.5, y = pos.getY() + TOP, z = pos.getZ() + 0.5;
        if (altar.isActiveClient()) {
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + (random.nextDouble() - 0.5) * 0.8, y + 0.05,
                        z + (random.nextDouble() - 0.5) * 0.8, 0, 0.03, 0);
            }
        } else if (!altar.getOffering().isEmpty() && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, x, y + 0.6, z, (random.nextDouble() - 0.5) * 1.5, -0.4, (random.nextDouble() - 0.5) * 1.5);
        } else if (random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.6, y + 0.05, z + (random.nextDouble() - 0.5) * 0.6, 0, 0.01, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.offering_altar.0").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.offering_altar.1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.offering_altar.2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
