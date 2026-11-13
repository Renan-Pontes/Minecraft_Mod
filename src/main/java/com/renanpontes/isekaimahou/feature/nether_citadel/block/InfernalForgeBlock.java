package com.renanpontes.isekaimahou.feature.nether_citadel.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.nether_citadel.NcProgress;
import com.renanpontes.isekaimahou.feature.nether_citadel.NcRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Forja Infernal: arma na mão principal + catalisador na outra mão (Essência do Fogo ou Vara de Blaze = Fogo; Essência das
 * Trevas ou Rosa de Wither = Sombra) + 5 níveis de XP grava a infusão na arma (componente {@code citadel_infusion}).
 */
public class InfernalForgeBlock extends Block {
    public static final MapCodec<InfernalForgeBlock> CODEC = simpleCodec(InfernalForgeBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);
    public static final int LEVEL_COST = 5;

    public InfernalForgeBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof TieredItem || stack.getItem() instanceof TridentItem || stack.getItem() instanceof MaceItem;
    }

    /** "fire", "shadow" ou null. */
    @Nullable
    private static String catalyst(ItemStack off) {
        var fire = CoreRegistries.ESSENCES.get(Element.FIRE);
        var shadow = CoreRegistries.ESSENCES.get(Element.SHADOW);
        if (off.is(Items.BLAZE_ROD) || (fire != null && off.is(fire.get()))) return "fire";
        if (off.is(Items.WITHER_ROSE) || (shadow != null && off.is(shadow.get()))) return "shadow";
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !isWeapon(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(player instanceof ServerPlayer sp)) return ItemInteractionResult.SUCCESS;
        ItemStack off = sp.getOffhandItem();
        String kind = catalyst(off);
        if (kind == null) {
            sp.displayClientMessage(Component.translatable("block.isekai_mahou.demon_infernal_forge.need_catalyst")
                    .withStyle(ChatFormatting.RED), true);
            return ItemInteractionResult.CONSUME;
        }
        if (kind.equals(stack.get(NcRegistries.INFUSION.get()))) {
            sp.displayClientMessage(Component.translatable("block.isekai_mahou.demon_infernal_forge.same").withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!sp.isCreative() && sp.experienceLevel < LEVEL_COST) {
            sp.displayClientMessage(Component.translatable("block.isekai_mahou.demon_infernal_forge.need_xp", LEVEL_COST)
                    .withStyle(ChatFormatting.RED), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!sp.isCreative()) sp.giveExperienceLevels(-LEVEL_COST);
        off.consume(1, sp);
        stack.set(NcRegistries.INFUSION.get(), kind);
        ServerLevel sl = sp.serverLevel();
        boolean fire = kind.equals("fire");
        sl.sendParticles(fire ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                40, 0.3, 0.4, 0.3, 0.05);
        sl.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.0);
        sl.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 0.6F);
        sl.playSound(null, pos, fire ? SoundEvents.BLAZE_SHOOT : SoundEvents.WITHER_SHOOT, SoundSource.BLOCKS, 0.8F, 0.8F);
        sp.sendSystemMessage(Component.translatable("block.isekai_mahou.demon_infernal_forge.done." + kind, stack.getHoverName())
                .withColor(fire ? 0xFF7A2A : 0x9B4DFF));
        NcProgress.flag(sp, NcProgress.FLAG_FORGE);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.sendSystemMessage(Component.translatable("block.isekai_mahou.demon_infernal_forge.hint", LEVEL_COST).withColor(0xFF7A2A));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.9, z = pos.getZ() + 0.5;
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.6, y, z + (random.nextDouble() - 0.5) * 0.6, 0, 0.03, 0);
        }
        if (random.nextInt(8) == 0) level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
        if (random.nextInt(24) == 0) level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 0.7F, false);
    }
}
