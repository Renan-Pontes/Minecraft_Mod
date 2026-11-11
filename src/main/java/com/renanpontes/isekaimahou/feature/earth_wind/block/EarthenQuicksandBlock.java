package com.renanpontes.isekaimahou.feature.earth_wind.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Areia Movedica conjurada (temporaria). Criaturas vivas atravessam o bloco e ficam presas, afundando devagar;
 * itens e outras entidades ficam por cima. Com a cabeca enterrada, a criatura perde o ar e sufoca aos poucos.
 */
public class EarthenQuicksandBlock extends Block {
    public EarthenQuicksandBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof LivingEntity) return Shapes.empty();
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        // Preso: quase nao anda e afunda devagar (o makeStuckInBlock tambem zera a distancia de queda).
        entity.makeStuckInBlock(state, new Vec3(0.3, 0.2, 0.3));
        if (level.isClientSide) {
            if (level.random.nextInt(3) == 0) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                        entity.getRandomX(0.6), pos.getY() + 1.0, entity.getRandomZ(0.6), 0, 0.05, 0);
            }
            return;
        }
        // Cabeca enterrada: perde ar mais rapido do que recupera e toma dano ao zerar.
        // (so no bloco dos olhos, para contar uma vez por tick mesmo tocando varios blocos)
        if (pos.equals(BlockPos.containing(living.getEyePosition()))) {
            int air = living.getAirSupply() - 8;
            living.setAirSupply(Math.max(-20, air));
            if (air <= -20 && living.tickCount % 20 == 0) {
                living.hurt(level.damageSources().inWall(), 1.0F);
            }
        }
    }
}
