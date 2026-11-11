package com.renanpontes.isekaimahou.core.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Destruction.Mode;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.boss.BossKit;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Fila GLOBAL de destruição de terreno (implementação de {@code core/api/Destruction}).
 *
 * <p>Cada pedido vira um "trabalho" com posições já ordenadas do centro para fora. No fim de cada tick do servidor os
 * trabalhos dividem o orçamento ({@code DESTRUCTION_BLOCKS_PER_TICK}) em rodízio; checar um bloco que não muda custa
 * 1/8 de um bloco alterado. As regras (proteções, chunk carregado...) são checadas NA HORA de mexer no bloco.
 *
 * <p>Os blocos são trocados SEM atualizar vizinhos e sem drops (senão tochas/flores vizinhas cairiam como item); no fim
 * de cada trabalho os vizinhos são acomodados (água volta a escorrer, flores sem apoio somem, cercas reconectam).
 */
public final class DestructionQueue {
    public static final int MAX_QUEUED = 250_000;
    public static final float MAX_RADIUS = 64F;
    private static final int CARVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final int COST_CHANGE = 8;
    private static final int COST_SKIP = 1;
    private static final int COST_SETTLE = 2;
    private static final int MIN_SHARE = 256 * COST_CHANGE;
    /** ~1 escombro a cada 40 blocos removidos; no máx. 24 novos por tick no servidor. */
    private static final int DEBRIS_EVERY = 40;
    private static final int DEBRIS_PER_TICK = 24;

    private static final List<Job> JOBS = new ArrayList<>();
    private static int queued;
    private static int rotate;
    private static int debrisThisTick;

    // =====================================================================================
    //  Formas
    // =====================================================================================
    public static int sphere(ServerLevel l, Vec3 center, float radius, @Nullable Entity caster, Mode mode) {
        if (!valid(l, center, mode) || !DestructionRules.allowed(l, caster)) return 0;
        float r = Mth.clamp(radius, 0.5F, MAX_RADIUS);
        int R = Mth.ceil(r);
        BlockPos c = BlockPos.containing(center);
        Sorter s = new Sorter(l);
        double r2 = (double) r * r;
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    double px = c.getX() + dx + 0.5 - center.x, py = c.getY() + dy + 0.5 - center.y, pz = c.getZ() + dz + 0.5 - center.z;
                    double d2 = px * px + py * py + pz * pz;
                    if (d2 <= r2) s.add(c.getX() + dx, c.getY() + dy, c.getZ() + dz, d2);
                }
            }
        }
        return submit(l, caster, center, new Segment(mode, s.sorted(l), mode == Mode.SCORCH ? 0.12F : 0F));
    }

    public static int crater(ServerLevel l, Vec3 center, float radius, @Nullable Entity caster) {
        if (!valid(l, center, Mode.ERASE) || !DestructionRules.allowed(l, caster)) return 0;
        // O raio até o chão não pode atravessar chunk descarregado (carregaria o chunk na hora)
        if (!l.isLoaded(BlockPos.containing(center))) return 0;
        float R = Mth.clamp(radius, 1.0F, 48.0F);
        BlockPos ground = groundBelow(l, center, R + 3.0);
        if (ground == null) ground = BlockPos.containing(center);
        double depth = Math.max(1.5, R * 0.6);
        double up = R * 0.85;
        int r = Mth.ceil(R) + 2;
        RandomSource rnd = RandomSource.create(ground.asLong() ^ l.getGameTime());
        Sorter inner = new Sorter(l);
        Sorter shell = new Sorter(l);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double h2 = dx * dx + dz * dz;
                for (int dy = -Mth.ceil(depth) - 2; dy <= Mth.ceil(up) + 1; dy++) {
                    // Tigela achatada embaixo; acima do chão, uma meia-esfera mais baixa leva morros/árvores junto
                    double f = dy <= 0 ? h2 / (R * R) + (dy * dy) / (depth * depth) : (h2 + dy * dy * 1.3) / (up * up);
                    double jitter = (rnd.nextDouble() - 0.5) * 0.22;
                    int x = ground.getX() + dx, y = ground.getY() + dy, z = ground.getZ() + dz;
                    double d2 = h2 + dy * dy;
                    if (f <= 1.0 + jitter) inner.add(x, y, z, d2);
                    else if (dy <= 1 && f <= 1.42 + jitter) shell.add(x, y, z, d2);
                }
            }
        }
        // Borda externa: o chão em volta também fica chamuscado (só a superfície)
        int ring = Mth.ceil(R * 1.6F);
        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < R || d > R * 1.6F) continue;
                double chance = 0.75 * (1.0 - (d - R) / (R * 0.6F));
                if (rnd.nextDouble() > chance) continue;
                BlockPos top = surfaceAt(l, ground.getX() + dx, ground.getZ() + dz, ground.getY() + 3, ground.getY() - 3, false);
                if (top != null) shell.add(top.getX(), top.getY(), top.getZ(), d * d);
            }
        }
        Vec3 c = Vec3.atBottomCenterOf(ground.above());
        int n = submit(l, caster, c, new Segment(Mode.ERASE, inner.sorted(l), 0F), new Segment(Mode.SCORCH, shell.sorted(l), 0.15F));
        if (n > 0) {
            l.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0F + R * 0.2F, 0.5F);
            BossKit.shake(l, c, 40 + R * 5, Math.min(3.0F, 0.9F + R * 0.16F), 30 + (int) (R * 2));
        }
        return n;
    }

    public static int cylinder(ServerLevel l, Vec3 base, float radius, float height, @Nullable Entity caster, Mode mode) {
        if (!valid(l, base, mode) || !DestructionRules.allowed(l, caster)) return 0;
        float r = Mth.clamp(radius, 0.5F, 48F);
        if (Math.abs(height) < 1.0F) height = height < 0 ? -1.0F : 1.0F;
        int y0 = Mth.floor(Math.min(base.y, base.y + height));
        int y1 = Mth.floor(Math.max(base.y, base.y + height));
        y0 = Math.max(y0, l.getMinBuildHeight());
        y1 = Math.min(y1, l.getMaxBuildHeight() - 1);
        if (y1 < y0) return 0;
        int R = Mth.ceil(r);
        BlockPos c = BlockPos.containing(base);
        Sorter s = new Sorter(l);
        double r2 = (double) r * r;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double px = c.getX() + dx + 0.5 - base.x, pz = c.getZ() + dz + 0.5 - base.z;
                double h2 = px * px + pz * pz;
                if (h2 > r2) continue;
                for (int y = y0; y <= y1; y++) {
                    double py = y + 0.5 - base.y;
                    s.add(c.getX() + dx, y, c.getZ() + dz, h2 + py * py);
                }
            }
        }
        return submit(l, caster, base, new Segment(mode, s.sorted(l), mode == Mode.SCORCH ? 0.12F : 0F));
    }

    public static int line(ServerLevel l, Vec3 from, Vec3 to, float width, float height, @Nullable Entity caster, Mode mode) {
        if (!valid(l, from, mode) || to == null || !DestructionRules.allowed(l, caster)) return 0;
        float w = Mth.clamp(width, 0.3F, 32F);
        float h = Mth.clamp(height, 0.3F, 64F);
        Vec3 d = to.subtract(from);
        double len = Math.min(256.0, d.length());
        if (len < 0.05) return sphere(l, from, Math.max(w, h) * 0.5F, caster, mode);
        Vec3 dir = d.normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 upv = side.cross(dir).normalize();
        int nu = Math.max(1, Mth.ceil(w / 0.5F));
        int nv = Math.max(1, Mth.ceil(h / 0.5F));
        int ns = Mth.ceil(len / 0.5) + 1;
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList out = new LongArrayList();
        Probe probe = new Probe(l);
        for (int si = 0; si <= ns; si++) {
            Vec3 c = from.add(dir.scale(Math.min(len, si * 0.5)));
            for (int ui = 0; ui < nu; ui++) {
                double u = -w / 2.0 + (ui + 0.5) * w / nu;
                for (int vi = 0; vi < nv; vi++) {
                    double v = -h / 2.0 + (vi + 0.5) * h / nv;
                    int x = Mth.floor(c.x + side.x * u + upv.x * v), y = Mth.floor(c.y + side.y * u + upv.y * v),
                            z = Mth.floor(c.z + side.z * u + upv.z * v);
                    long k = BlockPos.asLong(x, y, z);
                    // Ar e chunk não carregado não ocupam a fila
                    if (seen.add(k) && probe.solid(x, y, z)) out.add(k);
                }
            }
        }
        return submit(l, caster, from, new Segment(mode, out.toLongArray(), mode == Mode.SCORCH ? 0.12F : 0F));
    }

    public static int surface(ServerLevel l, Vec3 center, float radius, @Nullable Entity caster, Mode mode) {
        if (!valid(l, center, mode) || !DestructionRules.allowed(l, caster)) return 0;
        float r = Mth.clamp(radius, 0.5F, MAX_RADIUS);
        int R = Mth.ceil(r);
        BlockPos c = BlockPos.containing(center);
        int span = Math.max(6, Math.min(24, R / 2 + 4));
        Sorter s = new Sorter(l);
        double r2 = (double) r * r;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double px = c.getX() + dx + 0.5 - center.x, pz = c.getZ() + dz + 0.5 - center.z;
                double h2 = px * px + pz * pz;
                if (h2 > r2) continue;
                BlockPos top = surfaceAt(l, c.getX() + dx, c.getZ() + dz, c.getY() + span, c.getY() - span, mode == Mode.FREEZE);
                if (top == null) continue;
                s.add(top.getX(), top.getY(), top.getZ(), h2);
                // Planta em cima do chão: o chão embaixo dela também muda (queimar/vitrificar)
                if (mode != Mode.FREEZE && l.getBlockState(top).canBeReplaced()) s.add(top.getX(), top.getY() - 1, top.getZ(), h2 + 0.01);
            }
        }
        return submit(l, caster, center, new Segment(mode, s.sorted(l), mode == Mode.SCORCH ? 0.12F : 0F));
    }

    public static int queued() {
        return queued;
    }

    // =====================================================================================
    //  Fila
    // =====================================================================================
    private static boolean valid(ServerLevel l, Vec3 v, Mode mode) {
        if (l == null || v == null || mode == null || !Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z)) return false;
        // A fila e a leitura de chunks são da thread do servidor (magias rodam nela; geração de mundo NÃO pode chamar)
        if (!l.getServer().isSameThread()) {
            IsekaiMahou.LOGGER.warn("[destruction] pedido fora da thread do servidor ignorado", new IllegalStateException());
            return false;
        }
        return true;
    }

    private static int submit(ServerLevel l, @Nullable Entity caster, Vec3 center, Segment... segments) {
        int total = 0;
        for (Segment s : segments) total += s.pos.length;
        if (total == 0) return 0;
        int room = MAX_QUEUED - queued;
        if (room <= 0) {
            IsekaiMahou.LOGGER.warn("[destruction] fila cheia ({} blocos): pedido de {} blocos descartado", queued, total);
            return 0;
        }
        if (total > room) {
            IsekaiMahou.LOGGER.warn("[destruction] fila quase cheia: {} de {} blocos do pedido descartados (os mais longe do centro)",
                    total - room, total);
            int left = room;
            for (int i = 0; i < segments.length; i++) {
                Segment s = segments[i];
                int keep = Math.min(left, s.pos.length);
                if (keep < s.pos.length) segments[i] = new Segment(s.mode, Arrays.copyOf(s.pos, keep), s.fireChance);
                left -= keep;
            }
            total = room;
        }
        Job job = new Job(l, caster, center, segments);
        JOBS.add(job);
        queued += total;
        return total;
    }

    static void tick(ServerTickEvent.Post event) {
        debrisThisTick = 0;
        if (JOBS.isEmpty()) return;
        int budget = MahouConfig.DESTRUCTION_BLOCKS_PER_TICK.get() * COST_CHANGE;
        int n = JOBS.size();
        int share = Math.max(MIN_SHARE, budget / n);
        int start = Math.floorMod(rotate++, n);
        for (int i = 0; i < n && budget > 0; i++) {
            Job job = JOBS.get((start + i) % n);
            budget -= job.step(Math.min(share, budget));
        }
        JOBS.removeIf(j -> {
            if (!j.done()) return false;
            queued -= j.remainingPositions();
            return true;
        });
        if (queued < 0) queued = 0;
    }

    static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        JOBS.removeIf(j -> {
            if (j.level != sl) return false;
            queued -= j.remainingPositions();
            return true;
        });
        if (queued < 0) queued = 0;
    }

    static void clear() {
        JOBS.clear();
        queued = 0;
        rotate = 0;
        debrisThisTick = 0;
    }

    // =====================================================================================
    //  Trabalho
    // =====================================================================================
    private record Segment(Mode mode, long[] pos, float fireChance) {}

    private static final class Job {
        final ServerLevel level;
        @Nullable final UUID casterId;
        final boolean playerCaster;
        @Nullable final Entity casterRef;
        final Vec3 center;
        final Segment[] segments;
        final List<BoundingBox> safeBoxes;
        final boolean checkCustom;
        final RandomSource rnd;
        int seg;
        int index;
        final LongOpenHashSet changed = new LongOpenHashSet();
        final LongArrayList changedList = new LongArrayList();
        int settleIndex;
        int removedSinceDebris;
        int age;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // Efeitos do tick atual
        long fxPos = Long.MIN_VALUE;
        BlockState fxState;
        int fxCount;

        Job(ServerLevel level, @Nullable Entity caster, Vec3 center, Segment[] segments) {
            this.level = level;
            this.casterId = caster == null ? null : caster.getUUID();
            this.playerCaster = caster instanceof Player;
            this.casterRef = caster;
            this.center = center;
            this.segments = segments;
            this.rnd = RandomSource.create(level.getGameTime() ^ Double.doubleToLongBits(center.x * 31 + center.z));
            this.safeBoxes = safeBoxesAround(level, segments);
            this.checkCustom = DestructionRules.hasCustomProtections();
        }

        int remainingPositions() {
            int r = 0;
            for (int i = seg; i < segments.length; i++) r += segments[i].pos.length - (i == seg ? index : 0);
            return Math.max(0, r);
        }

        boolean done() {
            return seg >= segments.length && settleIndex >= changedList.size();
        }

        /** Processa até {@code budget} unidades; devolve as usadas. */
        int step(int budget) {
            age++;
            int used = 0;
            fxCount = 0;
            fxState = null;
            Player player = player();
            Entity casterEntity = casterRef != null && !casterRef.isRemoved() ? casterRef : player;
            while (used < budget && seg < segments.length) {
                Segment s = segments[seg];
                if (index >= s.pos.length) {
                    seg++;
                    index = 0;
                    continue;
                }
                long packed = s.pos[index++];
                queued--;
                cursor.set(packed);
                used += process(s, cursor, player, casterEntity) ? COST_CHANGE : COST_SKIP;
            }
            // Acomodar vizinhos (depois que todas as posições foram processadas)
            if (seg >= segments.length) {
                while (used < budget && settleIndex < changedList.size()) {
                    settle(changedList.getLong(settleIndex++));
                    used += COST_SETTLE;
                }
            }
            effects();
            return used;
        }

        @Nullable
        private Player player() {
            if (!playerCaster || casterId == null) return null;
            ServerPlayer sp = level.getServer().getPlayerList().getPlayer(casterId);
            return sp != null && sp.level() == level ? sp : null;
        }

        private boolean process(Segment s, BlockPos pos, @Nullable Player player, @Nullable Entity casterEntity) {
            if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) return false;
            BlockState st = level.getBlockState(pos);
            if (st.isAir()) return false;
            if (!canTouch(pos, st, player)) return false;
            if (s.mode == Mode.FREEZE) return freeze(pos, st, player);
            // Líquidos só mudam no modo Congelar
            if (st.getBlock() instanceof LiquidBlock) return false;
            BlockState next = switch (s.mode) {
                case ERASE, BREAK -> Blocks.AIR.defaultBlockState();
                case SCORCH -> scorch(st);
                case VITRIFY -> vitrify(st);
                case CRUSH -> crush(pos, st);
                case FREEZE -> null;
            };
            if (next == null || next == st) return false;
            BlockEntity be = st.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            if (s.mode == Mode.BREAK && next.isAir() && rnd.nextFloat() < 0.10F) {
                Block.dropResources(st, level, pos, be, casterEntity, ItemStack.EMPTY);
            } else if (be != null) {
                // Apagar/transformar não espalha o conteúdo (só "quebrar" deixa cair)
                Clearable.tryClear(be);
            }
            if (!next.isCollisionShapeFullBlock(level, pos) || next.isAir()) clearPlantsAbove(pos, player);
            level.setBlock(pos, next, CARVE_FLAGS);
            recordChange(pos, st);
            if (next.isAir()) maybeDebris(pos, st);
            if (s.fireChance > 0 && !next.isAir()) maybeFire(pos, s.fireChance, player);
            return true;
        }

        private boolean canTouch(BlockPos pos, BlockState st, @Nullable Player player) {
            if (!level.getWorldBorder().isWithinBounds(pos)) return false;
            if (DestructionRules.blockProtected(level, pos, st)) return false;
            for (BoundingBox b : safeBoxes) if (b.isInside(pos)) return false;
            if (checkCustom && DestructionRules.customProtected(level, pos)) return false;
            return player == null || level.mayInteract(player, pos);
        }

        // ---------------------------------------------------------------- Modos
        @Nullable
        private BlockState scorch(BlockState st) {
            if (burnsAway(st)) return Blocks.AIR.defaultBlockState();
            if (st.is(Blocks.COARSE_DIRT)) return rnd.nextFloat() < 0.10F ? Blocks.MAGMA_BLOCK.defaultBlockState() : null;
            if (st.is(BlockTags.DIRT) || st.is(Blocks.FARMLAND) || st.is(Blocks.DIRT_PATH)) {
                return rnd.nextFloat() < 0.10F ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
            }
            if (st.is(Blocks.BLACKSTONE) || st.is(Blocks.BASALT) || st.is(Blocks.MAGMA_BLOCK)) return null;
            if (stoneLike(st)) {
                float r = rnd.nextFloat();
                return r < 0.45F ? Blocks.BLACKSTONE.defaultBlockState() : r < 0.88F ? Blocks.BASALT.defaultBlockState()
                        : Blocks.MAGMA_BLOCK.defaultBlockState();
            }
            if (st.is(BlockTags.SAND)) return rnd.nextFloat() < 0.35F ? Blocks.GLASS.defaultBlockState() : null;
            if (st.is(Blocks.GRAVEL)) return rnd.nextFloat() < 0.4F ? Blocks.BLACKSTONE.defaultBlockState() : null;
            if (st.is(Blocks.CLAY)) return Blocks.TERRACOTTA.defaultBlockState();
            return null;
        }

        @Nullable
        private BlockState vitrify(BlockState st) {
            if (burnsAway(st)) return Blocks.AIR.defaultBlockState();
            if (st.is(Blocks.RED_SAND)) return Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
            if (st.is(BlockTags.SAND)) return Blocks.GLASS.defaultBlockState();
            if (stoneLike(st)) {
                float r = rnd.nextFloat();
                return r < 0.25F ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : r < 0.65F ? Blocks.BLACK_STAINED_GLASS.defaultBlockState()
                        : r < 0.85F ? Blocks.PURPLE_STAINED_GLASS.defaultBlockState() : Blocks.GRAY_STAINED_GLASS.defaultBlockState();
            }
            if (st.is(BlockTags.DIRT) || st.is(Blocks.FARMLAND) || st.is(Blocks.DIRT_PATH)) {
                return rnd.nextBoolean() ? Blocks.BROWN_STAINED_GLASS.defaultBlockState() : Blocks.TERRACOTTA.defaultBlockState();
            }
            if (st.is(Blocks.GRAVEL)) return Blocks.GRAY_STAINED_GLASS.defaultBlockState();
            if (st.is(Blocks.CLAY)) return Blocks.TERRACOTTA.defaultBlockState();
            return null;
        }

        @Nullable
        private BlockState crush(BlockPos pos, BlockState st) {
            if (burnsAway(st) || st.is(Tags.Blocks.GLASS_BLOCKS) || st.is(Tags.Blocks.GLASS_PANES)) return Blocks.AIR.defaultBlockState();
            // A camada de cima (original) afunda 1 bloco
            BlockPos above = pos.above();
            if (!changed.contains(above.asLong())) {
                BlockState up = level.getBlockState(above);
                if (up.isAir() || (up.canBeReplaced() && up.getFluidState().isEmpty())) return Blocks.AIR.defaultBlockState();
            }
            if (st.is(Blocks.COBBLESTONE) || st.is(Blocks.GRAVEL) || st.is(Blocks.COBBLED_DEEPSLATE) || st.is(BlockTags.SAND)) return null;
            if (st.is(Blocks.DEEPSLATE) || st.is(Tags.Blocks.COBBLESTONES_DEEPSLATE)) return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            if (stoneLike(st)) return rnd.nextFloat() < 0.55F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
            if (st.is(BlockTags.DIRT)) return rnd.nextFloat() < 0.6F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
            return Blocks.GRAVEL.defaultBlockState();
        }

        /** Congelar: água vira gelo, lava vira obsidiana, fogo apaga, chão exposto ganha neve. */
        private boolean freeze(BlockPos pos, BlockState st, @Nullable Player player) {
            FluidState fluid = st.getFluidState();
            BlockState next = null;
            if (st.getBlock() instanceof LiquidBlock) {
                if (fluid.is(FluidTags.WATER) && fluid.isSource()) {
                    next = rnd.nextFloat() < 0.3F ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.ICE.defaultBlockState();
                } else if (fluid.is(FluidTags.LAVA)) {
                    next = fluid.isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                }
            } else if (st.is(BlockTags.FIRE)) {
                next = Blocks.AIR.defaultBlockState();
            }
            if (next != null) {
                level.setBlock(pos, next, CARVE_FLAGS);
                recordChange(pos, st);
                return true;
            }
            // Superfície: neve por cima
            BlockPos above = pos.above();
            if (level.isOutsideBuildHeight(above) || !level.getBlockState(above).isAir()) return false;
            if (!canTouch(above, Blocks.AIR.defaultBlockState(), player)) return false;
            BlockState snow = Blocks.SNOW.defaultBlockState();
            if (!snow.canSurvive(level, above)) return false;
            level.setBlock(above, snow, CARVE_FLAGS);
            recordChange(above, Blocks.AIR.defaultBlockState());
            return true;
        }

        // ---------------------------------------------------------------- Auxiliares
        private boolean burnsAway(BlockState st) {
            return st.canBeReplaced() || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.is(BlockTags.PLANKS)
                    || st.is(BlockTags.WOOL) || st.is(BlockTags.SNOW) || st.is(BlockTags.ICE) || st.is(BlockTags.FLOWERS)
                    || st.is(BlockTags.SAPLINGS) || st.is(BlockTags.CROPS) || st.is(Blocks.HAY_BLOCK) || st.is(Blocks.CACTUS)
                    || st.is(Blocks.BAMBOO) || st.is(Blocks.SUGAR_CANE) || st.is(Blocks.VINE) || st.is(Blocks.MOSS_CARPET);
        }

        private boolean stoneLike(BlockState st) {
            return st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(Tags.Blocks.STONES) || st.is(Tags.Blocks.COBBLESTONES)
                    || st.is(BlockTags.STONE_BRICKS) || st.is(Tags.Blocks.SANDSTONE_BLOCKS) || st.is(Blocks.TERRACOTTA)
                    || st.is(BlockTags.TERRACOTTA);
        }

        /** Plantas/neve em cima de um bloco que deixa de ser cheio somem SEM drops (senão cairiam como item). */
        private void clearPlantsAbove(BlockPos pos, @Nullable Player player) {
            BlockPos.MutableBlockPos q = pos.mutable();
            for (int k = 1; k <= 2; k++) {
                q.move(Direction.UP);
                if (level.isOutsideBuildHeight(q)) return;
                BlockState top = level.getBlockState(q);
                if (top.isAir() || !top.canBeReplaced() && !top.is(BlockTags.FLOWERS) && !top.is(BlockTags.SAPLINGS)
                        && !top.is(BlockTags.CROPS)) return;
                if (!top.getFluidState().isEmpty() || top.hasBlockEntity() || !canTouch(q, top, player)) return;
                BlockPos at = q.immutable();
                level.setBlock(at, Blocks.AIR.defaultBlockState(), CARVE_FLAGS);
                recordChange(at, top);
            }
        }

        private void recordChange(BlockPos pos, BlockState old) {
            long k = pos.asLong();
            if (changed.add(k)) changedList.add(k);
            if (fxCount++ % 37 == 0 || fxState == null) {
                fxPos = k;
                fxState = old;
            }
        }

        private void maybeFire(BlockPos pos, float chance, @Nullable Player player) {
            if (rnd.nextFloat() >= chance || !level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return;
            BlockState here = level.getBlockState(pos);
            // Netherrack e magma queimariam para sempre
            if (here.is(Blocks.NETHERRACK) || here.is(Blocks.MAGMA_BLOCK)) return;
            BlockPos above = pos.above();
            if (level.isOutsideBuildHeight(above) || !level.getBlockState(above).isAir()) return;
            if (!canTouch(above, Blocks.AIR.defaultBlockState(), player)) return;
            BlockState flame = BaseFireBlock.getState(level, above);
            if (flame.canSurvive(level, above)) {
                level.setBlock(above, flame, Block.UPDATE_ALL);
                long k = above.asLong();
                if (changed.add(k)) changedList.add(k);
            }
        }

        private void maybeDebris(BlockPos pos, BlockState st) {
            if (++removedSinceDebris < DEBRIS_EVERY || debrisThisTick >= DEBRIS_PER_TICK) return;
            removedSinceDebris = rnd.nextInt(8);
            Vec3 at = Vec3.atCenterOf(pos);
            Vec3 out = at.subtract(center).multiply(1, 0, 1);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5).normalize() : out.normalize();
            double speed = 0.25 + rnd.nextDouble() * 0.45;
            Vec3 vel = new Vec3(out.x * speed, 0.45 + rnd.nextDouble() * 0.5, out.z * speed);
            if (DebrisEntity.spawn(level, at, st, vel)) debrisThisTick++;
        }

        /** Acomoda os vizinhos de um bloco alterado (sem drops). */
        private void settle(long packed) {
            int x = BlockPos.getX(packed), y = BlockPos.getY(packed), z = BlockPos.getZ(packed);
            BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
            for (Direction dir : Direction.values()) {
                n.set(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
                long key = n.asLong();
                if (changed.contains(key) || level.isOutsideBuildHeight(n) || !level.isLoaded(n)) continue;
                BlockState st = level.getBlockState(n);
                if (st.isAir()) continue;
                BlockPos at = n.immutable();
                FluidState fluid = st.getFluidState();
                if (!fluid.isEmpty()) level.scheduleTick(at, fluid.getType(), fluid.getType().getTickDelay(level));
                BlockState updated = Block.updateFromNeighbourShapes(st, level, at);
                if (updated != st) Block.updateOrDestroy(st, updated, level, at, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
            // O próprio bloco novo (gelo, neve, cascalho) também se ajusta aos vizinhos
            BlockPos self = BlockPos.of(packed);
            BlockState own = level.getBlockState(self);
            if (!own.isAir()) {
                BlockState upd = Block.updateFromNeighbourShapes(own, level, self);
                if (upd != own) Block.updateOrDestroy(own, upd, level, self, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                if (own.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) level.scheduleTick(self, own.getBlock(), 2);
            }
        }

        /** Poeira/fumaça e som de desmoronamento, em lotes (1 por tick por trabalho). */
        private void effects() {
            if (fxState == null || fxPos == Long.MIN_VALUE) return;
            BlockPos p = BlockPos.of(fxPos);
            double x = p.getX() + 0.5, y = p.getY() + 0.5, z = p.getZ() + 0.5;
            Mode mode = seg < segments.length ? segments[seg].mode : segments[segments.length - 1].mode;
            if (!fxState.isAir() && fxState.getFluidState().isEmpty()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, fxState), x, y, z, 10, 0.8, 0.6, 0.8, 0.15);
            }
            switch (mode) {
                case SCORCH -> {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 3, 0.8, 0.3, 0.8, 0.02);
                    level.sendParticles(ParticleTypes.FLAME, x, y + 0.5, z, 4, 0.8, 0.3, 0.8, 0.02);
                }
                case VITRIFY -> level.sendParticles(ParticleTypes.END_ROD, x, y + 0.5, z, 4, 0.8, 0.3, 0.8, 0.02);
                case FREEZE -> level.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 0.5, z, 6, 0.8, 0.3, 0.8, 0.02);
                default -> level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.5, z, 2, 0.6, 0.3, 0.6, 0.01);
            }
            if (age % 6 == 1) {
                var sound = switch (mode) {
                    case SCORCH -> SoundEvents.FIRE_AMBIENT;
                    case VITRIFY -> SoundEvents.AMETHYST_BLOCK_CHIME;
                    case FREEZE -> SoundEvents.GLASS_PLACE;
                    default -> fxState.getSoundType().getBreakSound();
                };
                level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 1.6F, 0.55F + rnd.nextFloat() * 0.25F);
            }
            if (age % 20 == 1 && changedList.size() > 1500 && (mode == Mode.ERASE || mode == Mode.BREAK || mode == Mode.CRUSH)) {
                level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.8F, 0.4F);
            }
        }
    }

    /** Caixas de lugares seguros que encostam na área do pedido (checadas por bloco sem consultar o SavedData). */
    private static List<BoundingBox> safeBoxesAround(ServerLevel l, Segment[] segments) {
        if (!MahouConfig.CITY_PROTECTION.get()) return List.of();
        List<Places.Place> places = PlaceData.all(l);
        if (places.isEmpty()) return List.of();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Segment s : segments) {
            for (long k : s.pos) {
                int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (z < minZ) minZ = z;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
                if (z > maxZ) maxZ = z;
            }
        }
        // +2: plantas/neve acima do topo também são tocadas
        BoundingBox area = new BoundingBox(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 2, maxZ + 1);
        List<BoundingBox> out = new ArrayList<>();
        for (Places.Place p : places) if (p.safeZone() && p.box().intersects(area)) out.add(p.box());
        return out;
    }

    /** Topo do chão na coluna (bloco não-ar com ar em cima), procurando de {@code fromY} até {@code toY}. */
    @Nullable
    private static BlockPos surfaceAt(ServerLevel l, int x, int z, int fromY, int toY, boolean liquidsCount) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, fromY, z);
        if (!l.isLoaded(p)) return null;
        boolean airAbove = false;
        for (int y = fromY; y >= toY; y--) {
            p.setY(y);
            if (l.isOutsideBuildHeight(p)) continue;
            BlockState st = l.getBlockState(p);
            if (st.isAir()) {
                airAbove = true;
                continue;
            }
            boolean liquid = st.getBlock() instanceof LiquidBlock;
            if (airAbove && (!liquid || liquidsCount)) return p.immutable();
            airAbove = false;
            if (liquid && !liquidsCount) return null; // superfície é água: nada a queimar
        }
        return null;
    }

    @Nullable
    private static BlockPos groundBelow(ServerLevel l, Vec3 at, double maxDown) {
        Vec3 from = at.add(0, 0.6, 0);
        Vec3 to = at.subtract(0, maxDown, 0);
        BlockHitResult hit = l.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return BlockPos.containing(hit.getLocation().x, hit.getLocation().y - 0.05, hit.getLocation().z);
    }

    /** Junta posições com a distância² e devolve ordenadas do centro para fora (sem caixas de objetos). */
    private static final class Sorter {
        private final Probe probe;
        private long[] keys = new long[1024];
        private long[] pos = new long[1024];
        private int n;

        Sorter(ServerLevel l) {
            this.probe = new Probe(l);
        }

        /** Junta a posição se ali houver bloco (ar e chunks não carregados não ocupam a fila: nunca mudariam). */
        void add(int x, int y, int z, double d2) {
            if (!probe.solid(x, y, z)) return;
            if (n == keys.length) {
                keys = Arrays.copyOf(keys, n * 2);
                pos = Arrays.copyOf(pos, n * 2);
            }
            // chave = distância² quantizada (alto) + índice (baixo): ordenar longs é rápido e estável
            long q = Math.min((long) (d2 * 16.0), (1L << 31) - 1);
            keys[n] = (q << 32) | n;
            pos[n] = BlockPos.asLong(x, y, z);
            n++;
        }

        long[] sorted(ServerLevel l) {
            long[] k = Arrays.copyOf(keys, n);
            Arrays.sort(k);
            long[] out = new long[n];
            int m = 0;
            for (long key : k) {
                long p = pos[(int) (key & 0xFFFFFFFFL)];
                int y = BlockPos.getY(p);
                if (y < l.getMinBuildHeight() || y >= l.getMaxBuildHeight()) continue;
                out[m++] = p;
            }
            return m == n ? out : Arrays.copyOf(out, m);
        }
    }

    /**
     * "Tem bloco aqui?" rápido para montar os pedidos: lê o chunk JÁ carregado (nunca carrega/gera chunk) com cache do
     * último chunk consultado. Ar, fora da altura do mundo e chunk não carregado = false.
     */
    private static final class Probe {
        private final ServerLevel level;
        private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        private long lastKey = Long.MIN_VALUE;
        @Nullable private LevelChunk chunk;

        Probe(ServerLevel level) {
            this.level = level;
        }

        boolean solid(int x, int y, int z) {
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return false;
            int cx = x >> 4, cz = z >> 4;
            long key = ChunkPos.asLong(cx, cz);
            if (key != lastKey) {
                lastKey = key;
                chunk = level.getChunkSource().getChunkNow(cx, cz);
            }
            return chunk != null && !chunk.getBlockState(at.set(x, y, z)).isAir();
        }
    }

    private DestructionQueue() {}
}
