package com.renanpontes.isekaimahou.feature.farm.machine;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.farm.FarmRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bloco das fazendas mágicas. Clique: abre a tela. Agachar com a mão vazia: canaliza o seu recurso para a reserva.
 * Criadouro: com um animal preso na SUA guia por perto, o clique guarda o animal.
 */
public class FarmMachineBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** Distância máxima do animal na guia. */
    public static final double LEASH_RANGE = 10.0;

    public final MachineKind kind;
    private final MapCodec<FarmMachineBlock> codec;
    private final VoxelShape shape;

    public FarmMachineBlock(MachineKind kind, Properties props) {
        super(props);
        this.kind = kind;
        this.codec = simpleCodec(p -> new FarmMachineBlock(kind, p));
        this.shape = switch (kind) {
            case ACCELERATED_TREE -> Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(1, 4, 1, 15, 9, 15));
            case HATCHERY -> Shapes.or(Block.box(0, 0, 0, 16, 5, 16), Block.box(0, 5, 0, 2, 16, 2), Block.box(14, 5, 0, 16, 16, 2),
                    Block.box(0, 5, 14, 2, 16, 16), Block.box(14, 5, 14, 16, 16, 16), Block.box(0, 14, 0, 16, 16, 16));
            case FISH_POND -> Block.box(0, 0, 0, 16, 14, 16);
            case ESSENCE -> Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(4, 3, 4, 12, 11, 12), Block.box(2, 11, 2, 14, 13, 14));
            default -> Shapes.block();
        };
        registerDefaultState(defaultState(stateDefinition.any()));
    }

    /** Estado inicial (subclasses com mais propriedades completam). */
    protected BlockState defaultState(BlockState any) {
        return any.setValue(ACTIVE, false);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FarmMachineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, FarmRegistries.MACHINE_BE.get(), FarmMachineBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof FarmMachineBlockEntity be)) return InteractionResult.CONSUME;
        if (kind == MachineKind.HATCHERY) {
            List<Animal> leashed = level.getEntitiesOfClass(Animal.class, new AABB(pos).inflate(LEASH_RANGE),
                    a -> a.getLeashHolder() == sp && FarmMachineBlockEntity.capturable(a));
            if (!leashed.isEmpty()) {
                be.capture((ServerLevel) level, leashed.get(0), sp);
                return InteractionResult.CONSUME;
            }
        }
        if (sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty()) {
            be.channel(sp);
            return InteractionResult.CONSUME;
        }
        sp.openMenu(be, buf -> {
            buf.writeBlockPos(pos);
            buf.writeVarInt(kind.ordinal());
        });
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof FarmMachineBlockEntity be) {
            be.dropAll(sl);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FarmMachineBlockEntity be ? be.comparatorSignal() : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) return;
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        switch (kind) {
            case STONE_GENERATOR -> {
                if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.6, y + 1.05, z + (random.nextDouble() - 0.5) * 0.6, 0, 0.03, 0);
                if (random.nextInt(8) == 0) level.addParticle(ParticleTypes.LAVA, x, y + 1.0, z, 0, 0, 0);
                if (random.nextInt(6) == 0) level.addParticle(ParticleTypes.DRIPPING_WATER, x + 0.52, y + 0.4, z + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
            }
            case ACCELERATED_TREE -> {
                if (random.nextInt(2) == 0) level.addParticle(ParticleTypes.HAPPY_VILLAGER, x + (random.nextDouble() - 0.5) * 0.8, y + 0.7 + random.nextDouble() * 0.8, z + (random.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
                if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 1.4, z, 0, 0, 0);
            }
            case HATCHERY -> {
                if (random.nextInt(14) == 0) level.addParticle(ParticleTypes.HEART, x, y + 1.0, z, 0, 0.02, 0);
                if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.WAX_ON, x + (random.nextDouble() - 0.5) * 0.8, y + 0.4, z + (random.nextDouble() - 0.5) * 0.8, 0, 0.01, 0);
            }
            case FISH_POND -> {
                if (random.nextInt(2) == 0) level.addParticle(ParticleTypes.BUBBLE_POP, x + (random.nextDouble() - 0.5) * 0.7, y + 0.82, z + (random.nextDouble() - 0.5) * 0.7, 0, 0.01, 0);
                if (random.nextInt(10) == 0) level.addParticle(ParticleTypes.FISHING, x + (random.nextDouble() - 0.5) * 0.5, y + 0.8, z + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
            }
            default -> { }
        }
    }
}
