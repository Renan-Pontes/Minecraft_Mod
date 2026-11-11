package com.renanpontes.isekaimahou.feature.trials;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.trials.TrialTheme.Palette;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Constrói a recepção e a arena com blocos (flag 18: sem atualizar vizinhos — rápido e sem física).
 * A recepção sai inteira na hora (é pequena); a arena é montada em fatias, uma por tick, enquanto o grupo se reúne.
 */
public final class TrialBuilder {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Onde ficam as peças da recepção. */
    public record LobbyLayout(BlockPos altar, BlockPos exit, Vec3 spawn, Vec3 panel, Vec3 exitLabel) {}

    // =====================================================================================
    //  Recepção (~21×21)
    // =====================================================================================
    public static LobbyLayout lobby(ServerLevel level, int index, Element element) {
        BlockPos o = TrialDimension.origin(index);
        Palette p = TrialTheme.palette(element);
        BlockState barrier = TrialsRegistry.BARRIER.get().defaultBlockState();
        BlockState rune = TrialsRegistry.RUNE_STONE.get().defaultBlockState();
        int h = TrialDimension.LOBBY_HALF;

        for (int dx = -h; dx <= h; dx++) {
            for (int dz = -h; dz <= h; dz++) {
                BlockPos col = o.offset(dx, 0, dz);
                boolean edge = Math.abs(dx) == h || Math.abs(dz) == h;
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                BlockState floor;
                if (edge) floor = p.trim();
                else if (ring == h - 1) floor = p.accent();
                else if (ring == 2) floor = rune;                                   // mosaico rúnico no centro
                else if (dx == 0 && dz == 0) floor = p.light();
                else if (Math.abs(dx) == 6 && Math.abs(dz) == 6) floor = p.light(); // luzes embutidas nos cantos
                else floor = ((dx + dz) & 1) == 0 ? p.floorA() : p.floorB();
                set(level, col, floor);
                set(level, col.below(), p.wall());
                if (edge) {
                    boolean pillar = (Math.abs(dx) == h && Math.abs(dz) % 5 == 0) || (Math.abs(dz) == h && Math.abs(dx) % 5 == 0);
                    boolean window = (Math.abs(dz) == h && Math.abs(dx) >= 2 && Math.abs(dx) <= 3)
                            || (Math.abs(dx) == h && Math.abs(dz) >= 2 && Math.abs(dz) <= 3);
                    for (int y = 1; y <= 5; y++) {
                        BlockState s;
                        if (pillar) s = y == 5 ? p.light() : p.pillar();
                        else if (window && (y == 2 || y == 3)) s = p.glass();
                        else s = y == 5 ? p.accent() : p.wall();
                        set(level, col.above(y), s);
                    }
                    for (int y = 6; y < TrialDimension.LOBBY_ROOF; y++) set(level, col.above(y), barrier);
                }
                set(level, col.above(TrialDimension.LOBBY_ROOF), barrier);
            }
        }
        // Tapete do ponto de chegada até o altar (interrompido pelo mosaico).
        for (int dz = 3; dz <= 7; dz++) set(level, o.offset(0, 1, dz), p.carpet());
        for (int dz = -5; dz <= -3; dz++) set(level, o.offset(0, 1, dz), p.carpet());

        // Estrado do altar, colunas e braseiros.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -8; dz <= -6; dz++) {
                boolean corner = Math.abs(dx) == 2 && dz != -7;
                set(level, o.offset(dx, 1, dz), corner ? p.trim() : p.accent());
            }
        }
        BlockPos altar = o.offset(0, 2, -7);
        set(level, altar, TrialsRegistry.ALTAR.get().defaultBlockState());
        for (int sx : new int[] {-3, 3}) {
            for (int y = 1; y <= 3; y++) set(level, o.offset(sx, y, -9), p.pillar());
            set(level, o.offset(sx, 4, -9), p.light());
            set(level, o.offset(sx + Integer.signum(sx), 1, -7), p.brazier());
        }

        // Arco do Portal de Saída (encostado na parede sul).
        BlockPos exit = o.offset(0, 1, 8);
        for (int sx : new int[] {-2, 2}) for (int y = 1; y <= 3; y++) set(level, o.offset(sx, y, 8), p.pillar());
        for (int dx = -2; dx <= 2; dx++) set(level, o.offset(dx, 4, 8), dx == 0 ? p.light() : p.accent());
        set(level, exit, TrialsRegistry.EXIT.get().defaultBlockState());

        Vec3 spawn = TrialDimension.lobbySpawn(index);
        Vec3 panel = new Vec3(o.getX() + 0.5, o.getY() + 3.45, o.getZ() - 6.6);
        Vec3 exitLabel = new Vec3(o.getX() + 0.5, o.getY() + 2.25, o.getZ() + 7.6);
        return new LobbyLayout(altar, exit, spawn, panel, exitLabel);
    }

    // =====================================================================================
    //  Arena circular (raio 18–24)
    // =====================================================================================
    /** Passos de construção (cada um é uma fatia de colunas; o último monta as colunas de cobertura). */
    public static List<Runnable> arenaSteps(ServerLevel level, int index, Element element, int rank) {
        BlockPos c = TrialDimension.arenaCenter(index);
        int r = TrialDimension.arenaRadius(rank);
        Palette p = TrialTheme.palette(element);
        int ext = r + 4;
        List<Runnable> steps = new ArrayList<>();
        for (int x0 = -ext; x0 <= ext; x0 += 8) {
            int a = x0, b = Math.min(ext, x0 + 7);
            steps.add(() -> columns(level, c, r, p, a, b));
        }
        steps.add(() -> pillars(level, c, r, p, rank, index));
        return steps;
    }

    private static void columns(ServerLevel level, BlockPos c, int r, Palette p, int x0, int x1) {
        BlockState barrier = TrialsRegistry.BARRIER.get().defaultBlockState();
        int ext = r + 4;
        for (int dx = x0; dx <= x1; dx++) {
            for (int dz = -ext; dz <= ext; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + TrialDimension.WALL_OUTER + 1.0) continue;
                BlockPos col = c.offset(dx, 0, dz);
                if (d > r + TrialDimension.WALL_OUTER) {   // aba decorativa por fora da parede
                    set(level, col, p.trim());
                    continue;
                }
                set(level, col, floorBlock(dx, dz, d, r, p));
                set(level, col.below(), p.wall());
                double deg = Math.toDegrees(Math.atan2(dz, dx));
                if (d > r + 0.5) {
                    boolean inner = d <= r + 1.5;
                    boolean crownLight = inner && Math.abs(Mth.positiveModulo(deg, 15.0) - 7.5) > 6.4;
                    boolean window = inner && Mth.positiveModulo(Math.floor(deg / 7.5), 4) == 1;
                    for (int y = 1; y <= 6; y++) {
                        BlockState s;
                        if (y == 1) s = p.trim();
                        else if (y == 6) s = crownLight ? p.light() : p.accent();
                        else if (window && (y == 3 || y == 4)) s = p.glass();
                        else s = p.wall();
                        set(level, col.above(y), s);
                    }
                    // Barreira sobre a parede INTEIRA (não só a metade de dentro): ninguém fica em cima do muro, além da borda.
                    for (int y = 7; y < TrialDimension.ARENA_ROOF; y++) set(level, col.above(y), barrier);
                }
                set(level, col.above(TrialDimension.ARENA_ROOF), barrier);
            }
        }
    }

    /** Piso: medalhão rúnico no centro, 8 raios, anéis e faixas alternadas; luzes embutidas nos raios e no anel do meio. */
    private static BlockState floorBlock(int dx, int dz, double d, int r, Palette p) {
        if (d > r + 0.5) return p.wall();
        if (d < 0.8) return p.light();
        if (d < 2.6) return TrialsRegistry.RUNE_STONE.get().defaultBlockState();
        if (d < 3.5) return p.trim();
        if (Math.abs(d - (r - 0.5)) < 0.5) return p.accent();
        double ang = Math.atan2(dz, dx);
        double step = Math.PI / 4;
        double spoke = Math.abs(Mth.wrapDegrees(Math.toDegrees(ang - Math.round(ang / step) * step))) * Math.PI / 180 * d;
        if (spoke < 0.55) return Math.abs(d - (r - 2.5)) < 0.5 ? p.light() : p.accent();
        double mid = r * 0.5;
        if (Math.abs(d - mid) < 0.5) {
            double half = ang - step / 2;
            double between = Math.abs(Mth.wrapDegrees(Math.toDegrees(half - Math.round(half / step) * step))) * Math.PI / 180 * d;
            return between < 0.6 ? p.light() : p.trim();
        }
        boolean band = ((int) (d / 3)) % 2 == 0;
        boolean speck = Math.floorMod(dx * 73856093 ^ dz * 19349663, 7) == 0;
        return band ^ speck ? p.floorA() : p.floorB();
    }

    /** Colunas de cobertura (4–7 pelo rank) a 60% do raio, entre os raios do piso; algumas quebradas (baixas). */
    private static void pillars(ServerLevel level, BlockPos c, int r, Palette p, int rank, int index) {
        int count = 4 + rank / 2;
        double rp = r * 0.6;
        for (int k = 0; k < count; k++) {
            double ang = Math.toRadians(22.5 + k * 360.0 / count);
            BlockPos base = c.offset((int) Math.round(Math.cos(ang) * rp), 0, (int) Math.round(Math.sin(ang) * rp));
            boolean broken = k % 3 == 2;
            int h = broken ? 2 : 4 + Math.floorMod(k * 7 + index * 3 + rank, 3);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, base.offset(dx, 1, dz), p.accent());
            for (int y = 2; y <= h; y++) {
                set(level, base.above(y), p.pillar());
                if (y < h) {
                    set(level, base.offset(1, y, 0), p.pillar());
                    set(level, base.offset(-1, y, 0), p.pillar());
                    set(level, base.offset(0, y, 1), p.pillar());
                    set(level, base.offset(0, y, -1), p.pillar());
                }
            }
            if (!broken) {
                set(level, base.above(h + 1), p.light());
                set(level, base.offset(1, h, 0), p.trim());
                set(level, base.offset(-1, h, 0), p.trim());
                set(level, base.offset(0, h, 1), p.trim());
                set(level, base.offset(0, h, -1), p.trim());
            }
        }
    }

    /** Pontos das colunas (para nada nascer dentro delas). */
    public static List<Vec3> pillarCenters(int index, int rank) {
        BlockPos c = TrialDimension.arenaCenter(index);
        int r = TrialDimension.arenaRadius(rank);
        int count = 4 + rank / 2;
        double rp = r * 0.6;
        List<Vec3> out = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            double ang = Math.toRadians(22.5 + k * 360.0 / count);
            out.add(new Vec3(c.getX() + Math.round(Math.cos(ang) * rp) + 0.5, c.getY() + 1, c.getZ() + Math.round(Math.sin(ang) * rp) + 0.5));
        }
        return out;
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, FLAGS);
    }

    private TrialBuilder() {}
}
