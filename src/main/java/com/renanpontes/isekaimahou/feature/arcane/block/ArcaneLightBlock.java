package com.renanpontes.isekaimahou.feature.arcane.block;

import org.joml.Vector3f;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Orbe de luz mágica (glifo Luz): emite luz 15, não tem colisão e pode ser substituído/quebrado com a mão.
 * Some sozinho com um "tick agendado" (salvo no chunk, então sobrevive a recarregar o mundo).
 */
public class ArcaneLightBlock extends Block {
    public static final MapCodec<ArcaneLightBlock> CODEC = simpleCodec(ArcaneLightBlock::new);
    private static final VoxelShape SHAPE = Block.box(5, 5, 5, 11, 11, 11);
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.85F, 0.75F, 1.0F), 0.6F);

    public ArcaneLightBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Coloca (ou renova) a luz em {@code pos} por {@code ticks}. Retorna false se o lugar não aceitar a luz. */
    public boolean placeFor(ServerLevel level, BlockPos pos, int ticks) {
        BlockState here = level.getBlockState(pos);
        if (!canHost(here)) return false;
        // Renovar: apaga o tick agendado antigo (senão ele apagaria a luz nova no horário antigo)
        if (here.is(this)) level.getBlockTicks().clearArea(new BoundingBox(pos));
        else level.setBlock(pos, defaultBlockState(), 3);
        level.scheduleTick(pos, this, Math.max(20, ticks));
        return true;
    }

    /** Ar, plantinhas/neve (substituíveis sem líquido) ou outra luz arcana. */
    public boolean canHost(BlockState state) {
        return state.isAir() || state.is(this) || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.15, 0.15, 0.15, 0.04);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.4F, 1.6F);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        level.addParticle(GLOW, x + (random.nextDouble() - 0.5) * 0.5, y + (random.nextDouble() - 0.5) * 0.5,
                z + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x, y, z, (random.nextDouble() - 0.5) * 0.02, 0.015,
                    (random.nextDouble() - 0.5) * 0.02);
        }
    }
}
