package com.renanpontes.isekaimahou.feature.farm.wand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Prancheta da varinha (Copiar/Colar): a área copiada fica nos dados persistentes do jogador (sobrevive a relogar),
 * em paleta + índices. As contas de girar/posicionar a colagem são comuns ao servidor e à prévia do cliente.
 */
public final class Clipboard {
    private static final String KEY = "isekai_mahou_farm_clipboard";
    /** Lado máximo da área copiada ("área pequena"). */
    public static final int MAX_SIDE = 16;
    public static final int MAX_VOLUME = 4096;

    /** Área copiada: tamanho, direção do olhar na cópia e os estados (x mais rápido, depois z, depois y). */
    public record Loaded(int w, int h, int d, Direction facing, List<BlockState> states) {
        public BlockState at(int x, int y, int z) {
            return states.get((y * d + z) * w + x);
        }
    }

    public static void save(ServerPlayer player, int w, int h, int d, Direction facing, List<BlockState> states) {
        Map<BlockState, Integer> index = new HashMap<>();
        ListTag palette = new ListTag();
        int[] data = new int[states.size()];
        for (int i = 0; i < states.size(); i++) {
            BlockState s = states.get(i);
            Integer id = index.get(s);
            if (id == null) {
                id = palette.size();
                index.put(s, id);
                palette.add(NbtUtils.writeBlockState(s));
            }
            data[i] = id;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("w", w);
        tag.putInt("h", h);
        tag.putInt("d", d);
        tag.putInt("facing", facing.get2DDataValue());
        tag.put("palette", palette);
        tag.putIntArray("data", data);
        player.getPersistentData().put(KEY, tag);
    }

    @Nullable
    public static Loaded load(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(KEY);
        int w = tag.getInt("w"), h = tag.getInt("h"), d = tag.getInt("d");
        int[] data = tag.getIntArray("data");
        if (w <= 0 || h <= 0 || d <= 0 || data.length != w * h * d) return null;
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        var blocks = player.level().holderLookup(Registries.BLOCK);
        List<BlockState> pal = new ArrayList<>(palette.size());
        for (int i = 0; i < palette.size(); i++) pal.add(NbtUtils.readBlockState(blocks, palette.getCompound(i)));
        List<BlockState> states = new ArrayList<>(data.length);
        for (int v : data) states.add(v >= 0 && v < pal.size() ? pal.get(v) : Blocks.AIR.defaultBlockState());
        return new Loaded(w, h, d, Direction.from2DDataValue(tag.getInt("facing")), states);
    }

    /** Rotação que leva a direção da cópia para a direção atual. */
    public static Rotation rotation(Direction from, Direction to) {
        for (Rotation r : Rotation.values()) if (r.rotate(from) == to) return r;
        return Rotation.NONE;
    }

    private static int[] rot(int x, int z, Rotation r) {
        return switch (r) {
            case NONE -> new int[]{x, z};
            case CLOCKWISE_90 -> new int[]{-z, x};
            case CLOCKWISE_180 -> new int[]{-x, -z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, -x};
        };
    }

    /**
     * Deslocamento (a partir do bloco clicado) de cada ponto local: a colagem gira com você, fica CENTRADA na sua frente
     * e começa no bloco clicado, indo para longe de você.
     */
    public static BlockPos place(int x, int y, int z, int w, int d, Rotation r, Direction facing) {
        int[] p = rot(x, z, r);
        int[] c0 = rot(0, 0, r), c1 = rot(w - 1, d - 1, r);
        int minX = Math.min(c0[0], c1[0]), maxX = Math.max(c0[0], c1[0]);
        int minZ = Math.min(c0[1], c1[1]), maxZ = Math.max(c0[1], c1[1]);
        int dx, dz;
        switch (facing) {
            case NORTH -> { dz = -maxZ; dx = -Math.floorDiv(minX + maxX, 2); }
            case SOUTH -> { dz = -minZ; dx = -Math.floorDiv(minX + maxX, 2); }
            case EAST -> { dx = -minX; dz = -Math.floorDiv(minZ + maxZ, 2); }
            default -> { dx = -maxX; dz = -Math.floorDiv(minZ + maxZ, 2); }
        }
        return new BlockPos(p[0] + dx, y, p[1] + dz);
    }

    /** Caixa ocupada pela colagem (prévia do cliente). */
    public static AABB pasteBox(BlockPos origin, BlockPos size, Direction copyFacing, Direction facing) {
        Rotation r = rotation(copyFacing, facing);
        int w = size.getX(), h = size.getY(), d = size.getZ();
        BlockPos a = origin.offset(place(0, 0, 0, w, d, r, facing));
        BlockPos b = origin.offset(place(w - 1, h - 1, d - 1, w, d, r, facing));
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    private Clipboard() {}
}
