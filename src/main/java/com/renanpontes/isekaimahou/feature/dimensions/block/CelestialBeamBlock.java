package com.renanpontes.isekaimahou.feature.dimensions.block;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.dimensions.DimKind;
import com.renanpontes.isekaimahou.feature.dimensions.DimRegistry;
import com.renanpontes.isekaimahou.feature.dimensions.DimTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A coluna de luz acima de um Farol Celestial aceso (o feixe visível é desenhado pelo renderer do farol).
 * Ficar dentro dela por 2 segundos leva ao Santuário Celestial; lá, a coluna do hub leva de volta.
 */
public class CelestialBeamBlock extends Block implements Portal {
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0);

    public CelestialBeamBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Precisa de um farol aceso (ou mais feixe) logo abaixo. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !neighbor.is(this)
                && !(neighbor.is(DimRegistry.CELESTIAL_BEACON.get()) && neighbor.getValue(CelestialBeaconBlock.ACTIVE))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.canUsePortal(false)) entity.setAsInsidePortal(this, pos);
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return entity instanceof Player p && !p.getAbilities().invulnerable ? 40 : 1;
    }

    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        return level.dimension().equals(DimKind.CELESTIAL.key)
                ? DimTravel.leave(level, entity, DimKind.CELESTIAL)
                : DimTravel.enter(level, entity, DimKind.CELESTIAL);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + random.nextDouble(),
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.06 + random.nextDouble() * 0.05, 0.0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.WAX_OFF, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0.0, 0.04, 0.0);
        }
    }
}
