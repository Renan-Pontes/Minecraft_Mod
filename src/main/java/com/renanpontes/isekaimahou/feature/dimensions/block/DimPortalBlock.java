package com.renanpontes.isekaimahou.feature.dimensions.block;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.dimensions.DimTravel;
import com.renanpontes.isekaimahou.feature.dimensions.PortalKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bloco do portal aceso (Plano Elemental / Abismo). Funciona como o do Nether: fique dentro dele uns
 * segundos para atravessar. Na dimensão de destino, o mesmo portal leva de volta ao ponto salvo.
 */
public class DimPortalBlock extends Block implements Portal {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape X_AXIS = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape Z_AXIS = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
    private static final int[] ELEMENT_COLORS = {0xFF6A2B, 0x3FA9FF, 0xA0743C, 0x9FF0D0};

    private final PortalKind kind;

    public DimPortalBlock(Properties properties, PortalKind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_AXIS : X_AXIS;
    }

    /** Some se a moldura (ou um vizinho de portal no mesmo plano) for removida. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Direction.Axis dirAxis = direction.getAxis();
        boolean inPlane = dirAxis == Direction.Axis.Y || dirAxis == state.getValue(AXIS);
        if (inPlane && !neighbor.is(this) && !neighbor.is(kind.frame())) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.canUsePortal(false)) entity.setAsInsidePortal(this, pos);
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        if (entity instanceof Player player) {
            return Math.max(1, level.getGameRules().getInt(player.getAbilities().invulnerable
                    ? GameRules.RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY
                    : GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY));
        }
        return 0;
    }

    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        return level.dimension().equals(kind.dim.key)
                ? DimTravel.leave(level, entity, kind.dim)
                : DimTravel.enter(level, entity, kind.dim);
    }

    /** O Abismo distorce a visão como o Nether; o Plano Elemental é uma passagem limpa. */
    @Override
    public Transition getLocalTransition() {
        return kind == PortalKind.ABYSS ? Transition.CONFUSION : Transition.NONE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    kind == PortalKind.ABYSS ? SoundEvents.PORTAL_AMBIENT : SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS,
                    0.5F, random.nextFloat() * 0.4F + (kind == PortalKind.ABYSS ? 0.5F : 1.2F), false);
        }
        boolean xAxis = state.getValue(AXIS) == Direction.Axis.X;
        for (int i = 0; i < 3; i++) {
            double x = pos.getX() + (xAxis ? random.nextDouble() : 0.5 + (random.nextDouble() - 0.5) * 0.3);
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + (xAxis ? 0.5 + (random.nextDouble() - 0.5) * 0.3 : random.nextDouble());
            double out = (random.nextDouble() - 0.5) * 0.3;
            if (kind == PortalKind.ABYSS) {
                level.addParticle(i == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.REVERSE_PORTAL, x, y, z,
                        xAxis ? 0 : out, 0.02, xAxis ? out : 0);
            } else {
                int c = ELEMENT_COLORS[random.nextInt(ELEMENT_COLORS.length)];
                level.addParticle(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 1.0F),
                        x, y, z, 0, 0.03, 0);
                if (i == 0) level.addParticle(ParticleTypes.END_ROD, x, y, z, xAxis ? 0 : out * 0.2, 0.01, xAxis ? out * 0.2 : 0);
            }
        }
    }
}
