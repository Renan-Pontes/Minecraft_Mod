package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * Sistema de coordenadas de um Enigma, preso ao Selo: {@code v} anda "para dentro" da área (para onde o Selo aponta,
 * {@code facing}), {@code u} anda para a DIREITA de quem olha para dentro (0..8) e {@code h} é a altura (0 = altura dos
 * pés, −1 = o piso). O Selo fica na fileira da frente (v = 0), na coluna {@code u0} (4 = no meio; 1 nas salas da
 * masmorra, para não tapar a porta). A área do enigma é u 0..8 × v 1..7; a fileira v = 0 é o corredor de quem chega.
 * Gira junto com o molde (usa a direção do bloco), então funciona em qualquer rotação de estrutura.
 */
public record Frame(BlockPos core, Direction forward, int u0) {
    public static final int W = 9;
    public static final int D = 7;

    public Direction right() {
        return forward.getClockWise();
    }

    public BlockPos pos(int u, int v, int h) {
        Direction r = right();
        int du = u - u0;
        return core.offset(r.getStepX() * du + forward.getStepX() * v, h, r.getStepZ() * du + forward.getStepZ() * v);
    }

    public int u(BlockPos p) {
        Direction r = right();
        return (p.getX() - core.getX()) * r.getStepX() + (p.getZ() - core.getZ()) * r.getStepZ() + u0;
    }

    public int v(BlockPos p) {
        return (p.getX() - core.getX()) * forward.getStepX() + (p.getZ() - core.getZ()) * forward.getStepZ();
    }

    public int h(BlockPos p) {
        return p.getY() - core.getY();
    }

    /** Posição dentro da área do enigma (fileira da frente inclusa), do piso até 4 acima dos pés. */
    public boolean covers(BlockPos p) {
        int u = u(p), v = v(p), h = h(p);
        return u >= 0 && u < W && v >= 0 && v <= D && h >= -1 && h <= 4;
    }

    public BoundingBox box() {
        return BoundingBox.fromCorners(pos(0, 0, -1), pos(W - 1, D, 4));
    }

    public AABB aabb() {
        BoundingBox b = box();
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
    }

    /** Converte uma direção do enigma (0 = +u, 1 = +v, 2 = −u, 3 = −v) para a do mundo. */
    public Direction world(int dir) {
        return switch (dir & 3) {
            case 0 -> right();
            case 1 -> forward;
            case 2 -> right().getOpposite();
            default -> forward.getOpposite();
        };
    }
}
