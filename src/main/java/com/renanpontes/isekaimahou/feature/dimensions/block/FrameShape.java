package com.renanpontes.isekaimahou.feature.dimensions.block;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Detecta uma moldura retangular de portal (igual à do Nether: os cantos são opcionais).
 * Interior de 2×3 até 21×21. {@code start} é qualquer bloco vazio dentro da moldura.
 */
public record FrameShape(BlockPos bottomLeft, Direction.Axis axis, int width, int height) {
    public static final int MIN_WIDTH = 2;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_SIZE = 21;

    public static Optional<FrameShape> find(LevelAccessor level, BlockPos start, Block frame) {
        FrameShape s = tryAxis(level, start, Direction.Axis.X, frame);
        if (s == null) s = tryAxis(level, start, Direction.Axis.Z, frame);
        return Optional.ofNullable(s);
    }

    /**
     * Procura a moldura a partir de uma peça clicada: primeiro o vizinho da face clicada, depois os outros
     * cinco (o jogador pode clicar na face de fora ou no topo) e, por fim, as diagonais — num CANTO da moldura
     * (as do Portão Elemental e da Fenda Abissal têm cantos) nenhum vizinho direto é o interior.
     */
    public static Optional<FrameShape> findAround(LevelAccessor level, BlockPos framePos, Direction clicked, Block frame) {
        Optional<FrameShape> s = find(level, framePos.relative(clicked), frame);
        for (Direction d : Direction.values()) {
            if (s.isPresent()) return s;
            if (d != clicked) s = find(level, framePos.relative(d), frame);
        }
        for (BlockPos p : diagonals(framePos)) {
            if (s.isPresent()) return s;
            s = find(level, p, frame);
        }
        return s;
    }

    /** Algum vizinho da peça (inclusive na diagonal, para os cantos) já é o portal aceso? */
    public static boolean touchesPortal(LevelAccessor level, BlockPos framePos, Block portal) {
        for (Direction d : Direction.values()) {
            if (level.getBlockState(framePos.relative(d)).is(portal)) return true;
        }
        for (BlockPos p : diagonals(framePos)) {
            if (level.getBlockState(p).is(portal)) return true;
        }
        return false;
    }

    /** As 12 posições diagonais (duas direções perpendiculares somadas) em volta de {@code pos}. */
    private static BlockPos[] diagonals(BlockPos pos) {
        BlockPos[] out = new BlockPos[12];
        int n = 0;
        Direction[] all = Direction.values();
        for (int i = 0; i < all.length; i++) {
            for (int j = i + 1; j < all.length; j++) {
                if (all[i].getAxis() != all[j].getAxis()) out[n++] = pos.relative(all[i]).relative(all[j]);
            }
        }
        return out;
    }

    private static boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || state.canBeReplaced();
    }

    private static FrameShape tryAxis(LevelAccessor level, BlockPos start, Direction.Axis axis, Block frame) {
        if (!isEmpty(level.getBlockState(start))) return null;
        Direction left = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
        Direction right = left.getOpposite();

        // Desce até encostar na base da moldura.
        BlockPos p = start;
        for (int i = 0; i < MAX_SIZE && isEmpty(level.getBlockState(p.below())); i++) p = p.below();
        if (!level.getBlockState(p.below()).is(frame)) return null;

        // Anda para a esquerda até a lateral.
        for (int i = 0; i < MAX_SIZE && isEmpty(level.getBlockState(p.relative(left))) && level.getBlockState(p.relative(left).below()).is(frame); i++) {
            p = p.relative(left);
        }
        if (!level.getBlockState(p.relative(left)).is(frame)) return null;
        BlockPos bottomLeft = p.immutable();

        // Largura: interior vazio com base de moldura até a lateral direita.
        int width = 0;
        while (width <= MAX_SIZE) {
            BlockPos c = bottomLeft.relative(right, width);
            if (!isEmpty(level.getBlockState(c)) || !level.getBlockState(c.below()).is(frame)) break;
            width++;
        }
        if (width < MIN_WIDTH || width > MAX_SIZE || !level.getBlockState(bottomLeft.relative(right, width)).is(frame)) return null;

        // Altura: sobe linha a linha; a primeira linha não vazia tem que ser o topo inteiro de moldura.
        int height = 0;
        while (true) {
            if (height > MAX_SIZE) return null;
            boolean rowEmpty = true;
            for (int i = 0; i < width; i++) {
                if (!isEmpty(level.getBlockState(bottomLeft.relative(right, i).above(height)))) {
                    rowEmpty = false;
                    break;
                }
            }
            if (!rowEmpty) break;
            if (!level.getBlockState(bottomLeft.relative(left).above(height)).is(frame)
                    || !level.getBlockState(bottomLeft.relative(right, width).above(height)).is(frame)) return null;
            height++;
        }
        if (height < MIN_HEIGHT) return null;
        for (int i = 0; i < width; i++) {
            if (!level.getBlockState(bottomLeft.relative(right, i).above(height)).is(frame)) return null;
        }
        return new FrameShape(bottomLeft, axis, width, height);
    }

    /** Preenche o interior com o bloco de portal (flag 18: sem atualizar a forma dos vizinhos no meio do processo). */
    public void fill(LevelAccessor level, Block portal) {
        Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        BlockState state = portal.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                level.setBlock(bottomLeft.relative(right, w).above(h), state, 18);
            }
        }
    }

    public BlockPos center() {
        Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        return bottomLeft.relative(right, width / 2).above(height / 2);
    }
}
