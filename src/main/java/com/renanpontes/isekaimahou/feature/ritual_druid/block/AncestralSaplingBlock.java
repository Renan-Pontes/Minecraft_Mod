package com.renanpontes.isekaimahou.feature.ritual_druid.block;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.ritual_druid.DruidContent;
import com.renanpontes.isekaimahou.feature.ritual_druid.DruidGrove;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Muda Ancestral: plantada em grama/terra, germina em poucos segundos e vira o Núcleo da Árvore Coração
 * do jogador que a plantou (só uma por jogador — o item confere isso antes de plantar).
 */
public class AncestralSaplingBlock extends BushBlock implements EntityBlock {
    public static final MapCodec<AncestralSaplingBlock> CODEC = simpleCodec(AncestralSaplingBlock::new);
    private static final VoxelShape SHAPE = BushBlock.box(3, 0, 3, 13, 14, 13);

    public AncestralSaplingBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AncestralSaplingBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != DruidContent.ANCESTRAL_SAPLING_BE.get()) return null;
        return (l, p, s, be) -> AncestralSaplingBlockEntity.serverTick(l, p, s, (AncestralSaplingBlockEntity) be);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof AncestralSaplingBlockEntity be) {
            be.setOwner(sp);
            DruidGrove.of(sp).setTree(GlobalPos.of(level.dimension(), pos));
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.ritual_druid.planted")
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // Arrancada antes de germinar: libera o "slot" de árvore do dono (se ele estiver online)
        if (!state.is(newState.getBlock()) && !newState.is(DruidContent.HEART_TREE_CORE.get())
                && level.getBlockEntity(pos) instanceof AncestralSaplingBlockEntity be && level.getServer() != null) {
            ServerPlayer owner = be.owner() == null ? null : level.getServer().getPlayerList().getPlayer(be.owner());
            if (owner != null) {
                DruidGrove grove = DruidGrove.of(owner);
                if (GlobalPos.of(level.dimension(), pos).equals(grove.tree())) grove.setTree(null);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.3, z = pos.getZ() + 0.5;
        if (random.nextInt(2) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.HAPPY_VILLAGER, x + Math.cos(a) * 0.5, y + random.nextDouble() * 0.7,
                    z + Math.sin(a) * 0.5, 0, 0.02, 0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.3, y + 0.6, z + (random.nextDouble() - 0.5) * 0.3,
                    0, 0.03, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ancestral_sapling").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ancestral_sapling.unique").withStyle(ChatFormatting.DARK_GREEN));
    }
}
