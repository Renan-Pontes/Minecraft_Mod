package com.renanpontes.isekaimahou.feature.nether_citadel.block;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.nether_citadel.NcPact;
import com.renanpontes.isekaimahou.feature.nether_citadel.NcProgress;
import com.renanpontes.isekaimahou.feature.nether_citadel.NcRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Altar do Pacto: oferenda (Essência das Trevas ou Lágrima de Ghast; o Selo do Lorde Demônio sela sem dívida) + preço de
 * sangue = Pacto Demoníaco. Sem oferenda na mão, explica as regras.
 */
public class PactAltarBlock extends Block {
    public static final MapCodec<PactAltarBlock> CODEC = simpleCodec(PactAltarBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

    public PactAltarBlock(Properties props) {
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

    private static boolean isOffering(ItemStack stack) {
        var shadow = CoreRegistries.ESSENCES.get(Element.SHADOW);
        return stack.is(Items.GHAST_TEAR) || stack.is(NcRegistries.DEMON_LORD_SIGIL.get()) || (shadow != null && stack.is(shadow.get()));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!isOffering(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(player instanceof ServerPlayer sp)) return ItemInteractionResult.SUCCESS;
        offer(sp, stack);
        return ItemInteractionResult.CONSUME;
    }

    /** Tenta selar o pacto com a oferenda {@code stack} (já conferida). */
    private static void offer(ServerPlayer sp, ItemStack stack) {
        String refusal = NcPact.refusal(sp);
        if (refusal != null) {
            sp.displayClientMessage(Component.translatable(refusal).withStyle(ChatFormatting.RED), true);
            return;
        }
        boolean free = stack.is(NcRegistries.DEMON_LORD_SIGIL.get());
        stack.consume(1, sp);
        NcPact.seal(sp, free);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        // v1.0 (correção): com a oferenda na mão secundária (e outra coisa na principal) o altar só mostrava a dica
        if (player instanceof ServerPlayer sp && isOffering(sp.getOffhandItem())) {
            offer(sp, sp.getOffhandItem());
            return InteractionResult.CONSUME;
        }
        String refusal = NcPact.refusal(player);
        player.sendSystemMessage(Component.translatable("block.isekai_mahou.demon_pact_altar.hint").withColor(NcPact.COLOR));
        if (refusal != null) {
            player.sendSystemMessage(Component.translatable(refusal).withStyle(ChatFormatting.GRAY));
        } else if (NcProgress.isWarlock(player)) {
            player.sendSystemMessage(Component.translatable("block.isekai_mahou.demon_pact_altar.hint_warlock").withStyle(ChatFormatting.DARK_RED));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.85, z = pos.getZ() + 0.5;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5,
                    0, 0.02, 0);
        }
        if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.CRIMSON_SPORE, x, y + 0.3, z, 0, 0, 0);
    }
}
