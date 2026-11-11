package com.renanpontes.isekaimahou.feature.ascension.block;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.ascension.AscensionAltarGuard;
import com.renanpontes.isekaimahou.feature.ascension.AscensionAltars;
import com.renanpontes.isekaimahou.feature.ascension.AscensionRegistry;
import com.renanpontes.isekaimahou.feature.ascension.AscensionTracking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Núcleo de Ascensão: o centro do Círculo. Clique com a mão vazia para conferir a estrutura, o tributo,
 * o nível e a mana — e, se estiver tudo certo, começar a Ascensão.
 */
public class AscensionCoreBlock extends BaseEntityBlock {
    public static final MapCodec<AscensionCoreBlock> CODEC = simpleCodec(AscensionCoreBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 4, 16),
            Block.box(2, 4, 2, 14, 10, 14),
            Block.box(1, 10, 1, 15, 13, 15));

    public AscensionCoreBlock(Properties props) {
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
        return new AscensionCoreBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, AscensionRegistry.CORE_BE.get(), AscensionCoreBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        // Com item na mão o clique também confere/ativa (o Núcleo não guarda itens).
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            // O rastreador de objetivos aponta para o Núcleo usado por último (só os que fazem a Ascensão).
            if (level.getBlockEntity(pos) instanceof AscensionCoreBlockEntity be && be.usable()) AscensionTracking.remember(level, pos);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof AscensionCoreBlockEntity be) {
            be.interact(sp, sp.isShiftKeyDown());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Não dá para quebrar o Núcleo no meio da Tribulação, nem um altar público fora do criativo.
        if (level.getBlockEntity(pos) instanceof AscensionCoreBlockEntity be
                && (be.isRitualRunning() || be.isPublic() && !player.getAbilities().instabuild)) return 0F;
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
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AscensionCoreBlockEntity be) {
            be.refundConsumed();
            if (be.isPublic() && level instanceof ServerLevel sl) AscensionAltars.remove(sl, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Só roda no cliente, para blocos perto do jogador: um Núcleo à vista vira o alvo do rastreador.
        if (level.getBlockEntity(pos) instanceof AscensionCoreBlockEntity be && be.usable()) AscensionTracking.noticed(level, pos);
        double x = pos.getX() + 0.5, y = pos.getY() + 1.2, z = pos.getZ() + 0.5;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, x + (random.nextDouble() - 0.5) * 2.5, y + random.nextDouble(),
                    z + (random.nextDouble() - 0.5) * 2.5, (random.nextDouble() - 0.5) * 0.2, -0.3, (random.nextDouble() - 0.5) * 0.2);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.4, y + 0.3, z + (random.nextDouble() - 0.5) * 0.4,
                    0, 0.02, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ascension_core").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ascension_core.layout").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ascension_core.shift").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ascension_core.public").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ascension_core.private").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ascension_core.barrier").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
