package com.renanpontes.isekaimahou.feature.structures_b.block;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.structures_b.StructuresBContent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Flor-Vaga-lume: plantinha das margens do Lago das Fadas. Os botões brilham (luz 9) e, à noite,
 * soltam vaga-lumes que vagueiam em volta. Farinha de osso espalha a flor para a grama ao redor.
 */
public class FairyGlowbloomBlock extends BushBlock implements BonemealableBlock {
    public static final MapCodec<FairyGlowbloomBlock> CODEC = simpleCodec(FairyGlowbloomBlock::new);
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 13.0, 12.0);

    public FairyGlowbloomBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Vec3 off = state.getOffset(level, pos);
        return SHAPE.move(off.x, off.y, off.z);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || super.mayPlaceOn(state, level, pos);
    }

    /** Noite pelo relógio do mundo (o céu escuro no cliente nem sempre está atualizado). */
    private static boolean isNight(Level level) {
        if (level.dimensionType().hasFixedTime()) return false;
        long t = level.getDayTime() % 24000L;
        return t > 12600L && t < 23400L;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Vec3 off = state.getOffset(level, pos);
        double x = pos.getX() + 0.5 + off.x;
        double z = pos.getZ() + 0.5 + off.z;
        if (isNight(level) || level.getBrightness(LightLayer.SKY, pos) < 6) {
            // À noite (ou na sombra) os vaga-lumes acordam: 1–2 por chamada, subindo devagar ao redor da flor.
            int n = 1 + random.nextInt(2);
            for (int i = 0; i < n; i++) {
                level.addParticle(StructuresBContent.FAIRY_FIREFLY.get(),
                        x + (random.nextDouble() - 0.5) * 2.4, pos.getY() + 0.4 + random.nextDouble() * 1.4,
                        z + (random.nextDouble() - 0.5) * 2.4, 0.0, 0.0, 0.0);
            }
        } else if (random.nextInt(6) == 0) {
            // De dia, só um brilho discreto saindo dos botões.
            level.addParticle(ParticleTypes.GLOW, x + (random.nextDouble() - 0.5) * 0.4,
                    pos.getY() + 0.6 + random.nextDouble() * 0.3, z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.02, 0.0);
        }
    }

    // ---------------- Farinha de osso: espalha ----------------
    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return random.nextFloat() < 0.6F;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos target = pos.offset(random.nextInt(5) - 2, random.nextInt(3) - 1, random.nextInt(5) - 2);
            if (level.isEmptyBlock(target) && state.canSurvive(level, target)) {
                level.setBlock(target, state, Block.UPDATE_ALL);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX() + 0.5, target.getY() + 0.4,
                        target.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
                return;
            }
        }
    }
}
