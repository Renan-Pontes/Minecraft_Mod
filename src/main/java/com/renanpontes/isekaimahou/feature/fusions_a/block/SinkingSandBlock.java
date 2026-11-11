package com.renanpontes.isekaimahou.feature.fusions_a.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Areia Movediça Maior: criaturas afundam devagar (sem conseguir pular) e ficam presas até a areia endurecer.
 * Itens e blocos continuam apoiados nela. Não sufoca pelo vanilla: o dano vem da zona da magia.
 */
public class SinkingSandBlock extends FaTempBlock {
    public SinkingSandBlock(Properties props) {
        super(props, Blocks.SAND::defaultBlockState);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (ctx instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof LivingEntity) return Shapes.empty();
        return Shapes.block();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof LivingEntity)) return;
        entity.makeStuckInBlock(state, new Vec3(0.35, 0.12, 0.35));
        if (level.isClientSide && level.random.nextInt(4) == 0) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                    entity.getX(), pos.getY() + 1.0, entity.getZ(), 0, 0.05, 0);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0 || !level.getBlockState(pos.above()).isAir()) return;
        level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()),
                pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, 0, 0);
    }
}
