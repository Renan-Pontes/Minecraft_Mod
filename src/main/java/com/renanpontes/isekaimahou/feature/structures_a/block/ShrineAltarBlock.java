package com.renanpontes.isekaimahou.feature.structures_a.block;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.structures_a.ShrineRegistry;
import com.renanpontes.isekaimahou.feature.structures_a.ShrineTrial;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Altar do Santuário: o coração de cada Santuário Elemental. A propriedade {@code element} define o elemento
 * (textura, partículas, prova). Clique com a mão vazia (ou com item) para ver a prova e, clicando de novo, começá-la.
 */
public class ShrineAltarBlock extends BaseEntityBlock {
    public static final MapCodec<ShrineAltarBlock> CODEC = simpleCodec(ShrineAltarBlock::new);
    public static final EnumProperty<Element> ELEMENT = EnumProperty.create("element", Element.class, Element.BASE);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(3, 3, 3, 13, 11, 13),
            Block.box(1, 11, 1, 15, 14, 15));

    public ShrineAltarBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ELEMENT, Element.FIRE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ELEMENT);
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
        return new ShrineAltarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ShrineRegistry.ALTAR_BE.get(), ShrineAltarBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        // Item na mão não atrapalha: o clique sempre chega no altar.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && !sp.isSpectator() && level.getBlockEntity(pos) instanceof ShrineAltarBlockEntity be) {
            be.interact(sp);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // Altar removido (modo criativo / comandos) no meio da prova: a prova acaba.
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            ShrineTrial t = ShrineTrial.at(sl, pos);
            if (t != null) t.fail(ShrineTrial.Reason.INTERRUPTED);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        // "Escolher bloco" no criativo devolve o altar do mesmo elemento.
        return ShrineRegistry.altarStack(state.getValue(ELEMENT));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Element e = state.getValue(ELEMENT);
        boolean active = level.getBlockEntity(pos) instanceof ShrineAltarBlockEntity be && be.isActive();
        double x = pos.getX() + 0.5, y = pos.getY() + 0.9, z = pos.getZ() + 0.5;
        // Pó colorido subindo em espiral do tampo; durante a prova, muito mais intenso.
        int n = active ? 4 : 1;
        for (int i = 0; i < n; i++) {
            if (!active && random.nextInt(2) != 0) continue;
            double a = random.nextDouble() * Math.PI * 2;
            double r = 0.35 + random.nextDouble() * 0.25;
            level.addParticle(ElementFx.dust(e, active ? 1.2F : 0.8F), x + Math.cos(a) * r, y, z + Math.sin(a) * r,
                    0, 0.04 + random.nextDouble() * 0.04, 0);
        }
        if (random.nextInt(active ? 2 : 6) == 0) {
            double vx = (random.nextDouble() - 0.5) * 0.02, vz = (random.nextDouble() - 0.5) * 0.02;
            switch (e) {
                case FIRE -> level.addParticle(random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMOKE, x, y + 0.1, z, vx, 0.03, vz);
                case WATER -> level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, x + vx * 10, y, z + vz * 10, 0, 0.05, 0);
                case EARTH -> level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x + vx * 20, y + 0.3, z + vz * 20, 0, 0.0, 0);
                case WIND -> level.addParticle(ParticleTypes.CLOUD, x, y + 0.2, z, vx * 4, 0.02, vz * 4);
                case LIGHT -> level.addParticle(ParticleTypes.END_ROD, x, y + 0.3, z, vx, 0.02, vz);
                default -> level.addParticle(ParticleTypes.SOUL, x, y + 0.2, z, vx, 0.02, vz);
            }
        }
        if (active && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, x + (random.nextDouble() - 0.5) * 3, y + 1.5 + random.nextDouble(),
                    z + (random.nextDouble() - 0.5) * 3, 0, -0.6, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.shrine_altar").withStyle(ChatFormatting.GRAY));
    }
}
