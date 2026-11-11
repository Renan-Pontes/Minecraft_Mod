package com.renanpontes.isekaimahou.feature.bosses_e.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.bosses_e.entity.DragonPhase;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Obras de terreno da arena (todas TEMPORÁRIAS, via {@link ArenaTerraform}): o chão de cada fase, manchas de lava,
 * crateras de verdade, pilares de pedra e a prisão de gelo. Nada aqui destrói terreno para sempre.
 */
public final class ArenaWorks {
    /** Terreno de fase: dura até ser liberado (morte, troca de fase) — o dragão estica o prazo enquanto luta. */
    public static final int PHASE_TICKS = 6000;

    /** Topo sólido da coluna (x,z) perto de {@code y} (procura de y+6 até y−10), ou null. */
    @Nullable
    public static BlockPos surface(ServerLevel l, int x, int z, int y) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y + 6, z);
        for (int i = 0; i < 17; i++) {
            BlockState st = l.getBlockState(p);
            if (st.isFaceSturdy(l, p, Direction.UP)) {
                BlockState above = l.getBlockState(p.above());
                if (!above.isFaceSturdy(l, p.above(), Direction.DOWN) && above.getFluidState().isEmpty()) return p.immutable();
            }
            p.move(0, -1, 0);
        }
        return null;
    }

    /** Colunas de um disco (anel entre {@code rMin} e {@code rMax}) em volta de {@code c}. */
    private static List<int[]> columns(Vec3 c, double rMin, double rMax) {
        List<int[]> out = new ArrayList<>();
        int r = (int) Math.ceil(rMax);
        int cx = (int) Math.floor(c.x);
        int cz = (int) Math.floor(c.z);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt((cx + dx + 0.5 - c.x) * (cx + dx + 0.5 - c.x) + (cz + dz + 0.5 - c.z) * (cz + dz + 0.5 - c.z));
                if (d >= rMin && d < rMax) out.add(new int[]{cx + dx, cz + dz});
            }
        }
        return out;
    }

    private static double noise(int x, int z, int seed) {
        long n = x * 374761393L + z * 668265263L + seed * 2147483647L;
        n = (n ^ (n >> 13)) * 1274126177L;
        return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0;
    }

    // =====================================================================================
    //  Chão de cada fase
    // =====================================================================================

    /**
     * Pinta o anel {@code rMin..rMax} do chão no tema da fase (chamado anel a anel na transição, como uma onda).
     * @return blocos trocados
     */
    public static int themeRing(ServerLevel l, Vec3 c, double rMin, double rMax, DragonPhase phase, UUID owner) {
        ArenaTerraform t = ArenaTerraform.get(l);
        int n = 0;
        int y = (int) Math.floor(c.y);
        for (int[] col : columns(c, rMin, rMax)) {
            BlockPos top = surface(l, col[0], col[1], y);
            if (top == null) continue;
            double k = noise(col[0], col[1], phase.ordinal() * 17 + 3);
            BlockState st = themed(phase, k, col[0] < c.x);
            if (st == null || !l.getBlockState(top).isCollisionShapeFullBlock(l, top)) continue;
            if (t.place(l, top, st, PHASE_TICKS, owner)) n++;
            BlockPos up = top.above();
            if (phase == DragonPhase.WATER && k > 0.7 && l.getBlockState(up).isAir()) {
                t.place(l, up, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + (int) (k * 10) % 2), PHASE_TICKS, owner);
            }
            if (n % 6 == 0) {
                l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), top.getX() + 0.5, top.getY() + 1.1, top.getZ() + 0.5,
                        2, 0.3, 0.1, 0.3, 0.05);
            }
        }
        return n;
    }

    @Nullable
    private static BlockState themed(DragonPhase phase, double k, boolean westSide) {
        return switch (phase) {
            case FIRE -> k < 0.42 ? Blocks.BLACKSTONE.defaultBlockState() : k < 0.66 ? Blocks.SMOOTH_BASALT.defaultBlockState()
                    : k < 0.84 ? Blocks.NETHERRACK.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            case WATER -> k < 0.4 ? Blocks.PACKED_ICE.defaultBlockState() : k < 0.9 ? Blocks.SNOW_BLOCK.defaultBlockState()
                    : Blocks.BLUE_ICE.defaultBlockState();
            case EARTH -> k < 0.3 ? Blocks.COARSE_DIRT.defaultBlockState() : k < 0.45 ? Blocks.ROOTED_DIRT.defaultBlockState()
                    : k < 0.6 ? Blocks.COBBLESTONE.defaultBlockState() : k < 0.75 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                    : k < 0.9 ? Blocks.TUFF.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
            case STORM -> k < 0.35 ? Blocks.CALCITE.defaultBlockState() : k < 0.5 ? Blocks.SMOOTH_BASALT.defaultBlockState()
                    : k < 0.65 ? Blocks.DIORITE.defaultBlockState() : k < 0.8 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                    : Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState();
            case TWILIGHT -> westSide
                    ? (k < 0.5 ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : k < 0.8 ? Blocks.QUARTZ_BRICKS.defaultBlockState()
                    : Blocks.END_STONE_BRICKS.defaultBlockState())
                    : (k < 0.4 ? Blocks.BLACKSTONE.defaultBlockState() : k < 0.7 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                    : k < 0.88 ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.CRYING_OBSIDIAN.defaultBlockState());
        };
    }

    // =====================================================================================
    //  Perigos
    // =====================================================================================

    /** Chão de lava: o topo vira magma por {@code ticks} (quem pisa queima — o dragão aplica Em Chamas). */
    public static int magmaPatch(ServerLevel l, Vec3 c, double r, UUID owner, int ticks) {
        ArenaTerraform t = ArenaTerraform.get(l);
        int n = 0;
        for (int[] col : columns(c, 0, r)) {
            BlockPos top = surface(l, col[0], col[1], (int) Math.floor(c.y));
            if (top == null || !l.getBlockState(top).isCollisionShapeFullBlock(l, top)) continue;
            if (t.place(l, top, Blocks.MAGMA_BLOCK.defaultBlockState(), ticks, owner)) n++;
        }
        return n;
    }

    /** Setor (fatia de pizza) do disco em magma: yaw do centro da fatia em graus (padrão do Minecraft), abertura total. */
    public static int magmaSector(ServerLevel l, Vec3 c, float yawDeg, float angleDeg, double r, UUID owner, int ticks) {
        ArenaTerraform t = ArenaTerraform.get(l);
        Vec3 dir = Vec3.directionFromRotation(0, yawDeg);
        double cos = Math.cos(Math.toRadians(angleDeg / 2));
        int n = 0;
        for (int[] col : columns(c, 1.5, r)) {
            Vec3 v = new Vec3(col[0] + 0.5 - c.x, 0, col[1] + 0.5 - c.z).normalize();
            if (v.dot(dir) < cos) continue;
            BlockPos top = surface(l, col[0], col[1], (int) Math.floor(c.y));
            if (top == null || !l.getBlockState(top).isCollisionShapeFullBlock(l, top)) continue;
            if (t.place(l, top, Blocks.MAGMA_BLOCK.defaultBlockState(), ticks, owner)) n++;
        }
        return n;
    }

    /**
     * Cratera de verdade (temporária): tigela de raio {@code r}; nunca abre a coluna até o vazio (sobram 2 blocos
     * sólidos embaixo — a arena flutuante da Fenda não vira um buraco para o nada). Borda chamuscada.
     */
    public static int crater(ServerLevel l, Vec3 c, double r, UUID owner, int ticks) {
        ArenaTerraform t = ArenaTerraform.get(l);
        int n = 0;
        int y = (int) Math.floor(c.y);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int[] col : columns(c, 0, r + 1.5)) {
            BlockPos top = surface(l, col[0], col[1], y);
            if (top == null) continue;
            double d = Math.sqrt((col[0] + 0.5 - c.x) * (col[0] + 0.5 - c.x) + (col[1] + 0.5 - c.z) * (col[1] + 0.5 - c.z));
            if (d >= r) {
                BlockState rim = noise(col[0], col[1], 5) < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                if (l.getBlockState(top).isCollisionShapeFullBlock(l, top) && t.place(l, top, rim, ticks, owner)) n++;
                continue;
            }
            int depth = Math.max(1, (int) Math.round((1 - (d / r) * (d / r)) * r * 0.6));
            while (depth > 0 && !solidBelow(l, top.below(depth), 2)) depth--;
            for (int i = 0; i < depth; i++) {
                BlockPos p = top.below(i);
                // Também tira plantas/neve em cima do bloco removido.
                if (i == 0 && !l.getBlockState(p.above()).isAir() && l.getBlockState(p.above()).getCollisionShape(l, p.above()).isEmpty()) {
                    t.place(l, p.above(), air, ticks, owner);
                }
                if (t.place(l, p, air, ticks, owner)) n++;
            }
            if (depth > 0) {
                BlockPos floor = top.below(depth);
                BlockState f = noise(col[0], col[1], 9) < 0.35 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                if (l.getBlockState(floor).isCollisionShapeFullBlock(l, floor)) t.place(l, floor, f, ticks, owner);
            }
        }
        return n;
    }

    private static boolean solidBelow(ServerLevel l, BlockPos from, int layers) {
        for (int i = 1; i <= layers; i++) {
            BlockPos p = from.below(i);
            if (!l.getBlockState(p).isFaceSturdy(l, p, Direction.UP)) return false;
        }
        return true;
    }

    /**
     * Pilar de pedra que irrompe do chão (cruz de 5 colunas, altura {@code height}): lança quem está em cima e fica
     * como cobertura por {@code ticks}. Não coloca bloco onde há alguém. @return posições trocadas
     */
    public static List<BlockPos> pillar(ServerLevel l, Vec3 c, int height, UUID owner, int ticks, BlockState body, BlockState cap) {
        List<BlockPos> placed = new ArrayList<>();
        ArenaTerraform t = ArenaTerraform.get(l);
        int cx = (int) Math.floor(c.x);
        int cz = (int) Math.floor(c.z);
        int[][] cross = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : cross) {
            BlockPos top = surface(l, cx + o[0], cz + o[1], (int) Math.floor(c.y));
            if (top == null) continue;
            int h = (o[0] == 0 && o[1] == 0) ? height : Math.max(1, height - 2);
            for (int i = 1; i <= h; i++) {
                BlockPos p = top.above(i);
                BlockState cur = l.getBlockState(p);
                if (!cur.isAir() && !cur.canBeReplaced()) break;
                if (!l.getEntitiesOfClass(LivingEntity.class, new AABB(p), Entity::isAlive).isEmpty()) continue;
                if (t.place(l, p, i == h ? cap : body, ticks, owner)) placed.add(p);
            }
        }
        return placed;
    }

    /** Prisão de gelo em volta de quem foi pego (casca 3×3×4 de gelo compacto, só no ar). @return posições trocadas */
    public static List<BlockPos> iceCage(ServerLevel l, LivingEntity e, UUID owner, int ticks) {
        List<BlockPos> placed = new ArrayList<>();
        ArenaTerraform t = ArenaTerraform.get(l);
        BlockPos base = e.blockPosition();
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean shell = dy == 3 || Math.abs(dx) == 1 || Math.abs(dz) == 1;
                    if (!shell) continue;
                    BlockPos p = base.offset(dx, dy, dz);
                    BlockState cur = l.getBlockState(p);
                    if (!cur.isAir() && !cur.canBeReplaced()) continue;
                    if (dy < 3 && p.getX() == base.getX() && p.getZ() == base.getZ()) continue;
                    // Não emparedar quem está do lado (só o preso fica dentro da jaula).
                    if (!l.getEntitiesOfClass(LivingEntity.class, new AABB(p), x -> x != e && x.isAlive()).isEmpty()) continue;
                    BlockState ice = (dx + dz + dy) % 3 == 0 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
                    if (t.place(l, p, ice, ticks, owner)) placed.add(p);
                }
            }
        }
        return placed;
    }

    private ArenaWorks() {}
}
