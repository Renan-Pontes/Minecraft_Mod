package com.renanpontes.isekaimahou.feature.farm.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * Molde de uma Planta de Casa em coordenadas LOCAIS: x = da esquerda para a direita (olhando a fachada), y = andar
 * (0 = chão, que fica no nível do terreno), z = de trás (0) para a frente ({@code depth - 1} = fachada com a porta).
 * Direções locais: SOUTH = para fora da fachada, NORTH = para dentro.
 *
 * <p>No mundo, a obra fica NA FRENTE de quem colocou o canteiro: a fachada vira para o jogador, a 1 bloco do canteiro.
 */
public final class Template {
    /** Uma posição do molde. {@code facing}/{@code axis} locais; {@code variant}: laje/degrau de cima, lanterna pendurada. */
    public record Entry(int x, int y, int z, Mat mat, @Nullable Direction facing, @Nullable Direction.Axis axis, int variant) {}

    public final int width;
    public final int depth;
    private final List<Entry> entries;
    private final int minX, maxX, minY, maxY, minZ, maxZ;

    private Template(int width, int depth, List<Entry> entries) {
        this.width = width;
        this.depth = depth;
        this.entries = entries;
        int a = Integer.MAX_VALUE, b = Integer.MIN_VALUE, c = Integer.MAX_VALUE, d = Integer.MIN_VALUE, e = Integer.MAX_VALUE, f = Integer.MIN_VALUE;
        for (Entry en : entries) {
            a = Math.min(a, en.x); b = Math.max(b, en.x);
            c = Math.min(c, en.y); d = Math.max(d, en.y);
            e = Math.min(e, en.z); f = Math.max(f, en.z);
        }
        minX = a; maxX = b; minY = c; maxY = d; minZ = e; maxZ = f;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    /** Quantos itens de cada material (sem o "limpar"). */
    public Map<Mat, Integer> counts() {
        Map<Mat, Integer> out = new EnumMap<>(Mat.class);
        for (Entry e : entries) if (e.mat != Mat.AIR) out.merge(e.mat, 1, Integer::sum);
        return out;
    }

    // =====================================================================================
    //  Local → mundo
    // =====================================================================================

    /** Rotação das direções locais: a fachada (SOUTH local) vira para o jogador. */
    public static Rotation rotation(Direction look) {
        for (Rotation r : Rotation.values()) if (r.rotate(Direction.SOUTH) == look.getOpposite()) return r;
        return Rotation.NONE;
    }

    /** Posição no mundo de um ponto local, com o canteiro em {@code site} e o jogador olhando para {@code look}. */
    public BlockPos world(BlockPos site, Direction look, int x, int y, int z) {
        Direction right = look.getClockWise();
        return site.relative(look, depth - z).relative(right, x - width / 2).above(y - 1);
    }

    @Nullable
    public static Direction.Axis worldAxis(@Nullable Direction.Axis local, Direction look) {
        if (local == null || local == Direction.Axis.Y) return local;
        // x local acompanha a "direita" do jogador; z local acompanha o olhar
        return local == Direction.Axis.X ? look.getClockWise().getAxis() : look.getAxis();
    }

    /** Caixa ocupada no mundo (prévia e contorno do canteiro). */
    public AABB bounds(BlockPos site, Direction look) {
        BlockPos a = world(site, look, minX, minY, minZ), b = world(site, look, maxX, maxY, maxZ);
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    // =====================================================================================
    //  Construtor do molde
    // =====================================================================================
    static final class Builder {
        private final int width, depth;
        private final Map<Long, Entry> map = new HashMap<>();

        Builder(int width, int depth) {
            this.width = width;
            this.depth = depth;
        }

        private static long key(int x, int y, int z) {
            return BlockPos.asLong(x, y, z);
        }

        Builder put(int x, int y, int z, Mat m) {
            return put(x, y, z, m, null, null, 0);
        }

        Builder put(int x, int y, int z, Mat m, @Nullable Direction facing) {
            return put(x, y, z, m, facing, null, 0);
        }

        Builder put(int x, int y, int z, Mat m, @Nullable Direction facing, @Nullable Direction.Axis axis, int variant) {
            map.put(key(x, y, z), new Entry(x, y, z, m, facing, axis, variant));
            return this;
        }

        /** Tira a posição do molde (ex.: metade de cima da porta, posta junto com a de baixo). */
        Builder none(int x, int y, int z) {
            map.remove(key(x, y, z));
            return this;
        }

        Builder log(int x, int y, int z, Direction.Axis axis) {
            return put(x, y, z, Mat.LOG, null, axis, 0);
        }

        /** Caixa cheia (inclusiva). */
        Builder box(int x0, int y0, int z0, int x1, int y1, int z1, Mat m) {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) put(x, y, z, m);
            return this;
        }

        /** Só o contorno de cada andar (paredes). */
        Builder ring(int x0, int y0, int z0, int x1, int y1, int z1, Mat m) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                    if (x == x0 || x == x1 || z == z0 || z == z1) put(x, y, z, m);
                }
            }
            return this;
        }

        /** Ar só onde ainda não há nada no molde. */
        Builder clear(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                map.putIfAbsent(key(x, y, z), new Entry(x, y, z, Mat.AIR, null, null, 0));
            }
            return this;
        }

        /** Telhado de duas águas com a cumeeira de trás para a frente (z), de {@code x0} a {@code x1}. */
        Builder gableRoof(int x0, int x1, int baseY, int z0, int z1, Mat fill) {
            int k = 0;
            while (x0 + k <= x1 - k) {
                int y = baseY + k, l = x0 + k, r = x1 - k;
                for (int z = z0; z <= z1; z++) {
                    if (l == r) {
                        put(l, y, z, Mat.SLAB);
                    } else {
                        put(l, y, z, Mat.STAIRS, Direction.EAST);
                        put(r, y, z, Mat.STAIRS, Direction.WEST);
                    }
                }
                // Empena (frente e fundos) preenchida por dentro
                for (int x = l + 1; x <= r - 1; x++) {
                    put(x, y, z0 + 1, fill);
                    put(x, y, z1 - 1, fill);
                    for (int z = z0 + 2; z <= z1 - 2; z++) map.putIfAbsent(key(x, y, z), new Entry(x, y, z, Mat.AIR, null, null, 0));
                }
                k++;
            }
            return this;
        }

        Template build() {
            List<Entry> list = new ArrayList<>(map.values());
            // Andar por andar; em cada andar: limpar, depois blocos, depois o que se apoia (tochas, portas...)
            list.sort(Comparator.comparingInt(Entry::y)
                    .thenComparingInt(e -> e.mat == Mat.AIR ? 0 : e.mat.attachable() ? 2 : 1)
                    .thenComparingInt(Entry::z).thenComparingInt(Entry::x));
            return new Template(width, depth, List.copyOf(list));
        }
    }
}
