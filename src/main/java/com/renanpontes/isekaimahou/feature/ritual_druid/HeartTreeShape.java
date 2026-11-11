package com.renanpontes.isekaimahou.feature.ritual_druid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.renanpontes.isekaimahou.feature.ritual_druid.block.HeartTreeLeavesBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Forma procedural da Árvore Coração em cada estágio (0..10), relativa ao Núcleo (0,0,0).
 * É determinística (mesma semente = mesma árvore), e as direções dos galhos/raízes não mudam entre estágios,
 * então a árvore parece CRESCER em vez de ser trocada. A ordem do mapa é a ordem de crescimento:
 * tronco/raízes de baixo para cima, depois a copa do centro para fora.
 * <p>
 * A face {@code opening} do Núcleo fica sempre livre (uma porta de 2 blocos de altura no pé do tronco, com corredor):
 * quando o tronco engrossa, sem isso o Núcleo ficaria emparedado e o druida não conseguiria mais clicar nele.
 */
public final class HeartTreeShape {
    public enum Part { LOG_Y, LOG_X, LOG_Z, LEAVES, GOLDEN }

    private static final Map<Long, Map<Integer, Map<BlockPos, Part>>> CACHE = new HashMap<>();

    /** Altura do tronco no estágio. */
    public static int trunkHeight(int stage) {
        return 3 + Math.round(stage * 1.3F);
    }

    /** Raio da copa no estágio. */
    public static float canopyRadius(int stage) {
        return 1.8F + stage * 0.58F;
    }

    public static synchronized Map<BlockPos, Part> build(int stage, long seed, Direction opening) {
        if (stage < 0) return Map.of();
        Direction open = opening.getAxis().isHorizontal() ? opening : Direction.NORTH;
        // Poucas árvores existem ao mesmo tempo; o limite só evita crescer sem fim num servidor muito antigo.
        if (CACHE.size() > 64) CACHE.clear();
        long key = seed * 31L + open.get2DDataValue();
        Map<Integer, Map<BlockPos, Part>> bySeed = CACHE.computeIfAbsent(key, k -> new HashMap<>());
        return bySeed.computeIfAbsent(stage, s -> Collections.unmodifiableMap(generate(s, seed, open)));
    }

    /** As 2 casas na frente da face aberta do Núcleo (2 de altura: cabe o druida) nunca recebem blocos da árvore. */
    private static boolean inCorridor(BlockPos p, Direction open) {
        if (p.getY() != 0 && p.getY() != 1) return false;
        for (int t = 1; t <= 2; t++) {
            if (p.getX() == open.getStepX() * t && p.getZ() == open.getStepZ() * t) return true;
        }
        return false;
    }

    private static Map<BlockPos, Part> generate(int s, long seed, Direction open) {
        Map<BlockPos, Part> logs = new LinkedHashMap<>();
        Map<BlockPos, Part> leaves = new HashMap<>();
        Random rnd = new Random(seed);
        // Ângulos fixos (independentes do estágio): os galhos e raízes de estágios altos continuam os de antes.
        double rootBase = rnd.nextDouble() * Math.PI * 2;
        double branchBase = rnd.nextDouble() * Math.PI * 2;

        int h = trunkHeight(s);
        // ---------------- Tronco ----------------
        for (int y = 1; y <= h; y++) logs.put(new BlockPos(0, y, 0), Part.LOG_Y);
        if (s >= 4) {
            int thick = Math.round(h * (s >= 8 ? 0.5F : 0.35F));
            for (int y = 0; y <= thick; y++) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (y <= 1 && d == open) continue; // porta da alcova do coração
                    logs.put(new BlockPos(d.getStepX(), y, d.getStepZ()), Part.LOG_Y);
                }
            }
        }
        if (s >= 8) {
            int corner = Math.round(h * 0.2F);
            for (int y = 0; y <= corner; y++) {
                for (int cx = -1; cx <= 1; cx += 2) for (int cz = -1; cz <= 1; cz += 2) logs.put(new BlockPos(cx, y, cz), Part.LOG_Y);
            }
        }

        // ---------------- Raízes (no nível do chão, saindo do tronco) ----------------
        if (s >= 2) {
            int roots = Math.min(8, 2 + s / 2);
            int len = 1 + s / 2;
            int start = s >= 4 ? 2 : 1;
            double openAngle = Math.atan2(open.getStepZ(), open.getStepX());
            for (int i = 0; i < roots; i++) {
                double a = rootBase + i * Math.PI * 2 / roots;
                // Nenhuma raiz cresce na frente da alcova (o caminho até o Núcleo fica livre)
                double diff = Math.abs(Math.IEEEremainder(a - openAngle, Math.PI * 2));
                if (diff < Math.toRadians(28)) continue;
                double cos = Math.cos(a), sin = Math.sin(a);
                Part axis = Math.abs(cos) > Math.abs(sin) ? Part.LOG_X : Part.LOG_Z;
                for (int t = start; t < start + len; t++) {
                    int y = t >= start + len - 1 && len > 2 ? -1 : 0; // a ponta mergulha no chão
                    logs.putIfAbsent(new BlockPos((int) Math.round(cos * t), y, (int) Math.round(sin * t)), axis);
                }
            }
        }

        // ---------------- Copa principal ----------------
        float R = canopyRadius(s);
        float Rv = R * 0.72F;
        float cy = h - R * 0.15F;
        double goldenY = s >= 6 ? cy + Rv * (0.4 - (s - 6) * 0.1) : Double.MAX_VALUE;
        blob(leaves, 0, cy, 0, R, Rv, seed, goldenY);

        // ---------------- Galhos com tufos de folhas ----------------
        if (s >= 3) {
            int n = Math.min(6, 1 + (s - 1) / 2);
            int len = 2 + s / 3;
            float tuft = 1.6F + s * 0.16F;
            for (int i = 0; i < n; i++) {
                double a = branchBase + i * 2.39996; // ângulo áureo: galhos bem distribuídos
                double cos = Math.cos(a), sin = Math.sin(a);
                int y0 = Math.round(h * (0.5F + 0.35F * i / Math.max(1, n - 1)));
                Part axis = Math.abs(cos) > Math.abs(sin) ? Part.LOG_X : Part.LOG_Z;
                int ex = 0, ey = y0, ez = 0;
                for (int t = 1; t <= len; t++) {
                    ex = (int) Math.round(cos * t);
                    ez = (int) Math.round(sin * t);
                    ey = y0 + Math.round(t * 0.6F);
                    logs.putIfAbsent(new BlockPos(ex, ey, ez), axis);
                }
                blob(leaves, ex, ey + 0.5F, ez, tuft, tuft * 0.75F, seed + i * 31L, goldenY);
            }
        }

        // ---------------- Monta na ordem de crescimento ----------------
        Map<BlockPos, Part> out = new LinkedHashMap<>();
        logs.keySet().removeIf(p -> inCorridor(p, open));
        List<BlockPos> logOrder = new ArrayList<>(logs.keySet());
        logOrder.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(p -> p.getX() * p.getX() + p.getZ() * p.getZ()));
        for (BlockPos p : logOrder) out.put(p, logs.get(p));
        List<BlockPos> leafOrder = new ArrayList<>();
        for (BlockPos p : leaves.keySet()) {
            if (!logs.containsKey(p) && !(p.getX() == 0 && p.getZ() == 0 && p.getY() <= 0) && !inCorridor(p, open)) leafOrder.add(p);
        }
        final float ccy = cy;
        leafOrder.sort(Comparator.comparingDouble(p -> p.getX() * p.getX() + (p.getY() - ccy) * (p.getY() - ccy) + p.getZ() * p.getZ()));
        for (BlockPos p : leafOrder) out.put(p, leaves.get(p));
        return out;
    }

    /** Elipsoide de folhas com borda irregular (ruído estável pela posição). */
    private static void blob(Map<BlockPos, Part> leaves, float cx, float cy, float cz, float r, float rv, long seed, double goldenY) {
        int ri = (int) Math.ceil(r), rvi = (int) Math.ceil(rv);
        for (int x = (int) Math.floor(cx) - ri; x <= (int) Math.ceil(cx) + ri; x++) {
            for (int y = (int) Math.floor(cy) - rvi; y <= (int) Math.ceil(cy) + rvi; y++) {
                for (int z = (int) Math.floor(cz) - ri; z <= (int) Math.ceil(cz) + ri; z++) {
                    double dx = (x - cx) / r, dy = (y - cy) / rv, dz = (z - cz) / r;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d > 1.0) continue;
                    if (d > 0.7 && noise(x, y, z, seed) < 0.33) continue;
                    BlockPos p = new BlockPos(x, y, z);
                    Part part = y >= goldenY ? Part.GOLDEN : Part.LEAVES;
                    Part old = leaves.get(p);
                    if (old == null || part == Part.GOLDEN) leaves.put(p, part);
                }
            }
        }
    }

    private static double noise(int x, int y, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Estado do bloco para uma parte da árvore. */
    public static BlockState state(Part part) {
        return switch (part) {
            case LOG_Y -> DruidContent.HEART_TREE_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
            case LOG_X -> DruidContent.HEART_TREE_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
            case LOG_Z -> DruidContent.HEART_TREE_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
            case LEAVES -> DruidContent.HEART_TREE_LEAVES.get().defaultBlockState().setValue(HeartTreeLeavesBlock.GOLDEN, false);
            case GOLDEN -> DruidContent.HEART_TREE_LEAVES.get().defaultBlockState().setValue(HeartTreeLeavesBlock.GOLDEN, true);
        };
    }

    private HeartTreeShape() {}
}
