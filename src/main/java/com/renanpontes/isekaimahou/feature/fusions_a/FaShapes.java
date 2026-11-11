package com.renanpontes.isekaimahou.feature.fusions_a;

import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Formas de blocos temporários usadas pelos glifos (prisões, plataformas, superfícies de água). */
public final class FaShapes {

    /**
     * Prende a entidade numa casca de blocos temporários em volta do corpo (lados, teto e, se estiver no ar, chão).
     * Criaturas grandes demais não são presas. Retorna quantos blocos foram colocados.
     */
    public static int cage(SpellContext ctx, LivingEntity t, BlockState block, int ticks) {
        AABB bb = t.getBoundingBox();
        if (bb.getXsize() > 2.2 || bb.getZsize() > 2.2 || bb.getYsize() > 3.2) return 0;
        ServerLevel level = ctx.level();
        BlockPos min = BlockPos.containing(bb.minX, bb.minY, bb.minZ);
        BlockPos max = BlockPos.containing(bb.maxX - 1.0E-3, bb.maxY - 1.0E-3, bb.maxZ - 1.0E-3);
        int placed = 0;
        for (int x = min.getX() - 1; x <= max.getX() + 1; x++) {
            for (int z = min.getZ() - 1; z <= max.getZ() + 1; z++) {
                boolean edgeX = x < min.getX() || x > max.getX();
                boolean edgeZ = z < min.getZ() || z > max.getZ();
                if (edgeX && edgeZ) continue; // sem quinas: casca mais "redonda" e com menos blocos
                for (int y = min.getY() - 1; y <= max.getY() + 1; y++) {
                    boolean inside = !edgeX && !edgeZ;
                    if (inside && y >= min.getY() && y <= max.getY()) continue; // o espaço do corpo
                    if (!inside && (y < min.getY() || y > max.getY())) continue; // só paredes na altura do corpo
                    BlockPos p = new BlockPos(x, y, z);
                    if (y < min.getY() && !level.getBlockState(p).isAir()) continue; // chão só se estiver no ar
                    if (FaUtil.isOpen(level, p) && FaUtil.canPlace(ctx, p) && FaTempBlocks.place(level, p, block, ticks)) placed++;
                }
            }
        }
        return placed;
    }

    /** Líquido "de verdade" (água, lava) que pode ser coberto por uma plataforma e devolvido depois. */
    public static boolean isLiquid(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof LiquidBlock && !s.getFluidState().isEmpty();
    }

    /**
     * Topo de uma coluna de líquido (água ou lava) que começa em {@code start}, subindo até {@code up} blocos; null se
     * {@code start} não é líquido ou a coluna é funda demais. Projéteis atravessam líquidos e batem no fundo: é por
     * aqui que as plataformas sobem até a superfície.
     */
    public static BlockPos liquidSurface(Level level, BlockPos start, int up) {
        if (!isLiquid(level, start)) return null;
        BlockPos p = start;
        for (int i = 0; i < up && isLiquid(level, p.above()); i++) p = p.above();
        return isLiquid(level, p.above()) ? null : p;
    }

    /** Superfície de água (água com ar em cima) procurando para cima a partir de {@code start}; null se não houver. */
    public static BlockPos waterSurface(Level level, BlockPos start, int up) {
        BlockPos p = start;
        for (int i = 0; i <= up; i++) {
            if (level.getFluidState(p).is(FluidTags.WATER) && level.getBlockState(p).getBlock() instanceof LiquidBlock
                    && level.getBlockState(p.above()).isAir()) return p;
            p = p.above();
        }
        return null;
    }

    private FaShapes() {}
}
