package com.renanpontes.isekaimahou.feature.mana_core.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.mana_core.ManaCoreRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Núcleo de Mana: base e tampa de pedra rúnica com uma câmara de cristal no meio que enche de mana (7 níveis de
 * textura, luz e comparador). Clique: abre a tela (depositar/retirar). Agachado + mão vazia: despeja TODO o seu
 * mana (e a sobrecarga) nele, como na Bateria. Quebrado, o item guarda a carga.
 */
public class ManaCoreBlock extends BaseEntityBlock {
    public static final MapCodec<ManaCoreBlock> CODEC = simpleCodec(ManaCoreBlock::new);
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 6);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(1, 3, 1, 15, 13, 15),
            Block.box(0, 13, 0, 16, 16, 16));

    public ManaCoreBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FILL, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        int stored = ctx.getItemInHand().getOrDefault(ManaCoreRegistries.CORE_MANA.get(), 0);
        return defaultBlockState().setValue(FILL, ManaCoreBlockEntity.fillLevel(stored));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof ManaCoreBlockEntity be) {
            be.setOwner(p);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManaCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ManaCoreRegistries.MANA_CORE_BE.get(), ManaCoreBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof ManaCoreBlockEntity core) {
            if (sp.isSecondaryUseActive()) {
                core.deposit(sp, Integer.MAX_VALUE);
            } else {
                if (core.owner() == null) core.setOwner(sp); // núcleo sem dono (comando): quem usa adota
                sp.openMenu(core, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ManaCoreBlockEntity b ? ManaCoreBlockEntity.comparatorSignal(b.mana()) : 0;
    }

    /** Faíscas de mana subindo e girando em volta da câmara — mais quanto mais cheio. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int fill = state.getValue(FILL);
        if (fill == 0) return;
        int n = FxQuality.count(1 + fill / 2);
        for (int i = 0; i < n; i++) {
            if (random.nextInt(3) != 0) continue;
            double a = random.nextDouble() * Math.PI * 2;
            double r = 0.6 + random.nextDouble() * 0.15;
            level.addParticle(AutoFx.dust(AutoFx.MANA_RGB, 0.6F), pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 0.2 + random.nextDouble() * 0.6,
                    pos.getZ() + 0.5 + Math.sin(a) * r, 0, 0.03, 0);
        }
        if (random.nextInt(7 - fill) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 1.2, -0.6, (random.nextDouble() - 0.5) * 1.2);
        }
        if (fill == 6 && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.05,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.04, 0);
        }
    }
}
