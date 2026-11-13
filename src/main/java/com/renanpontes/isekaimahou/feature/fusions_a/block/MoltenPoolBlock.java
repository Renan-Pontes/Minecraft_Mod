package com.renanpontes.isekaimahou.feature.fusions_a.block;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Poça de Magma (Poça de Lava): lâmina fina de lava sobre o chão que não escorre, não queima o mundo e some
 * sozinha. O dano/fogo em inimigos vem da zona da magia (creditada ao conjurador); o bloco é visual + luz.
 */
public class MoltenPoolBlock extends FaTempBlock {
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 2, 16);

    public MoltenPoolBlock(Properties props) {
        super(props, Blocks.AIR::defaultBlockState);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
        if (random.nextInt(6) == 0) level.addParticle(ParticleTypes.LAVA, x, pos.getY() + 0.15, z, 0, 0, 0);
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 0.2, z, 0, 0.02, 0);
        if (random.nextInt(40) == 0) {
            level.playLocalSound(x, pos.getY(), z, SoundEvents.LAVA_POP, SoundSource.BLOCKS,
                    0.3F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
        }
    }

    @Override
    public void onExpire(ServerLevel level, BlockPos pos) {
        level.sendParticles(Vfx.largeSmoke(), pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0.01);
    }
}
