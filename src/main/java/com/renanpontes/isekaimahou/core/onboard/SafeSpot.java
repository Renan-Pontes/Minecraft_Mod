package com.renanpontes.isekaimahou.core.onboard;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Procura um lugar seguro para os pés perto de um ponto: chão firme (não líquido, não fogo/magma/cacto), dois blocos
 * livres e sem líquido, dentro da borda do mundo. Carrega os chunks necessários (como o /tp do vanilla).
 */
final class SafeSpot {
    /** Altura acima/abaixo do ponto testada antes de cair para o topo do terreno. */
    private static final int UP = 4, DOWN = 10;

    /**
     * Pés num lugar seguro perto de {@code near} (até {@code radius} blocos na horizontal), ou null.
     * Primeiro tenta perto da altura pedida (dentro de prédios/hubs), depois o topo do terreno da coluna.
     */
    @Nullable
    static BlockPos find(ServerLevel level, BlockPos near, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // só o anel deste raio
                    BlockPos found = column(level, near.getX() + dx, near.getY(), near.getZ() + dz);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos column(ServerLevel level, int x, int y, int z) {
        if (!level.getWorldBorder().isWithinBounds(x, z)) return null;
        level.getChunk(x >> 4, z >> 4); // garante o chunk gerado/carregado
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = Math.min(level.getMaxBuildHeight() - 2, y + UP);
        int bottom = Math.max(level.getMinBuildHeight() + 1, y - DOWN);
        for (int fy = top; fy >= bottom; fy--) {
            p.set(x, fy, z);
            if (standable(level, p)) return p.immutable();
        }
        // Topo do terreno (ex.: centro registrado no subsolo ou no ar). Dimensões com teto (Abismo, Nether): o topo da
        // coluna é a rocha de cima — nunca usar
        if (level.dimensionType().hasCeiling()) return null;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (surface > level.getMinBuildHeight() + 1 && surface < level.getMaxBuildHeight() - 2) {
            p.set(x, surface, z);
            if (standable(level, p)) return p.immutable();
        }
        return null;
    }

    /** Pés em {@code feet}: chão firme e seguro embaixo, dois blocos livres. */
    static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockState floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP) || !floor.getFluidState().isEmpty()) return false;
        if (floor.is(BlockTags.FIRE) || floor.is(BlockTags.CAMPFIRES) || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS)
                || floor.is(Blocks.SWEET_BERRY_BUSH) || floor.is(Blocks.POWDER_SNOW)) return false;
        return free(level, feet) && free(level, feet.above());
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty() && !s.is(BlockTags.FIRE)
                && !s.is(Blocks.POWDER_SNOW) && !s.is(Blocks.COBWEB) && !s.is(Blocks.SWEET_BERRY_BUSH);
    }

    /**
     * Último recurso (hub de dimensão sem chão, ex. ilha flutuante sumida): plataforma 3×3 de pedra lisa sob
     * {@code feet}, só em blocos substituíveis, e ar para a cabeça. Null se ainda assim os pés não ficarem seguros.
     */
    @Nullable
    static BlockPos platform(ServerLevel level, BlockPos feet) {
        if (!level.getWorldBorder().isWithinBounds(feet) || feet.getY() <= level.getMinBuildHeight()
                || feet.getY() + 1 >= level.getMaxBuildHeight()) return null;
        level.getChunk(feet.getX() >> 4, feet.getZ() >> 4);
        BlockPos below = feet.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos f = below.offset(dx, 0, dz);
                if (level.getBlockState(f).canBeReplaced()) level.setBlockAndUpdate(f, Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos h = feet.above(dy);
            if (level.getBlockState(h).canBeReplaced()) level.setBlockAndUpdate(h, Blocks.AIR.defaultBlockState());
        }
        // Rocha no lugar dos pés/cabeça (não substituível): melhor não viajar do que sufocar
        return standable(level, feet) ? feet : null;
    }

    private SafeSpot() {}
}
