package com.renanpontes.isekaimahou.feature.settlements.build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementService;
import com.renanpontes.isekaimahou.feature.settlements.SettlementsContent;
import com.renanpontes.isekaimahou.feature.settlements.entity.BuilderNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.SiteSign;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Os construtores erguem as obras BLOCO A BLOCO, sem recursos (docs/V10_DESIGN.md §3): limpar o terreno → fundação →
 * blocos do molde (cheios de baixo para cima, depois os apoiados) → acabamento (cercas/vidros/escadas se ligam).
 * Só anda com um construtor perto do canteiro (ele martela olhando o bloco da vez); se ele empacar, é trazido à obra.
 * Custo: no máximo algumas dezenas de blocos por cidade a cada 5 ticks, só com o coração carregado.
 */
public final class ConstructionEngine {
    /** Blocos (passos) por ação de 5 ticks, antes dos multiplicadores. */
    private static final float BASE_RATE = 1.5F;
    /** A muralha é obra de todos os construtores juntos. */
    private static final float WALL_RATE = 2.5F;
    private static final int CLEAR_EXTRA = 3;
    private static final int FOUNDATION_DEPTH = 10;
    private static final int TELEPORT_AFTER = 60;

    public static void tick(ServerLevel sl, Settlement s, int age) {
        if (age % 5 == 0) build(sl, s);
        if (age % 40 == 20) signs(sl, s);
    }

    // =====================================================================================
    //  Obras
    // =====================================================================================
    private static void build(ServerLevel sl, Settlement s) {
        if (s.projects.isEmpty()) return;
        List<BuilderNpc> free = loadedBuilders(sl, s);
        int active = Math.min(s.projects.size(), Math.max(1, s.population(Settlement.Role.BUILDER)));
        Set<UUID> taken = new HashSet<>();
        for (int i = 0; i < active && i < s.projects.size(); i++) {
            Project p = s.projects.get(i);
            BuilderNpc b = pickBuilder(p, free, taken);
            if (b != null) taken.add(b.getUUID());
            if (work(sl, s, p, b)) {
                s.projects.remove(i);
                i--;
                active = Math.min(s.projects.size(), Math.max(1, s.population(Settlement.Role.BUILDER)));
                SettlementService.onProjectDone(sl, s, p);
            }
        }
        // construtores sem obra ficam livres para passear
        for (BuilderNpc b : free) if (!taken.contains(b.getUUID()) && b.site() != null) b.assign(null, null);
    }

    private static List<BuilderNpc> loadedBuilders(ServerLevel sl, Settlement s) {
        List<BuilderNpc> out = new ArrayList<>();
        for (UUID id : s.npcs.get(Settlement.Role.BUILDER)) {
            if (sl.getEntity(id) instanceof BuilderNpc b && b.isAlive()) out.add(b);
        }
        return out;
    }

    @Nullable
    private static BuilderNpc pickBuilder(Project p, List<BuilderNpc> free, Set<UUID> taken) {
        if (p.builder != null && !taken.contains(p.builder)) {
            for (BuilderNpc b : free) if (b.getUUID().equals(p.builder)) return b;
        }
        for (BuilderNpc b : free) {
            if (!taken.contains(b.getUUID())) {
                p.builder = b.getUUID();
                return b;
            }
        }
        p.builder = null;
        return null;
    }

    /** Um passo da obra. Devolve true quando ela termina. */
    private static boolean work(ServerLevel sl, Settlement s, Project p, @Nullable BuilderNpc builder) {
        Project.Piece piece = p.current();
        if (piece == null) return true;
        Templates.Tpl tpl = Templates.get(sl, piece.template);
        if (tpl == null) {
            // molde sumido (datapack quebrado): pula a peça para a obra não travar para sempre
            p.pieceIdx++;
            p.phase = Project.PHASE_CLEAR;
            p.cursor = 0;
            return p.done();
        }
        if (!piece.resolved() && !resolveBase(sl, s, piece, tpl)) return false;
        if (builder == null) return false;
        BlockPos origin = origin(piece, tpl);
        BlockPos focus = focus(p, piece, tpl, origin);
        BlockPos site = new BlockPos(piece.cx, piece.baseY + 1, piece.cz);
        builder.assign(site, focus);
        double dx = builder.getX() - (focus.getX() + 0.5), dz = builder.getZ() - (focus.getZ() + 0.5);
        double dy = builder.getY() - focus.getY();
        boolean near = dx * dx + dz * dz <= 64.0 && Math.abs(dy) <= 18.0;
        if (!near) {
            if (++p.waiting > TELEPORT_AFTER) {
                p.waiting = 0;
                bringBuilder(sl, builder, piece, tpl, origin, focus);
            }
            return false;
        }
        p.waiting = 0;
        float rate = BASE_RATE * s.style.buildSpeed * (1F + s.speedPct() / 100F) * (p.key.equals(Blueprint.WALL) ? WALL_RATE : 1F);
        p.accum += rate;
        int n = (int) p.accum;
        p.accum -= n;
        for (int k = 0; k < n && !p.done(); k++) {
            if (step(sl, s, p, tpl)) {
                piece = p.current();
                if (piece == null) return true;
                tpl = Templates.get(sl, piece.template);
                if (tpl == null || !piece.resolved() && !resolveBase(sl, s, piece, tpl)) return false;
            }
        }
        return p.done();
    }

    /** Executa um passo; devolve true quando a peça atual terminou (o projeto avançou para a próxima). */
    private static boolean step(ServerLevel sl, Settlement s, Project p, Templates.Tpl tpl) {
        Project.Piece piece = p.current();
        if (piece == null) return true;
        BlockPos origin = origin(piece, tpl);
        int w = tpl.width(piece.rotation), d = tpl.depth(piece.rotation);
        switch (p.phase) {
            case Project.PHASE_CLEAR -> {
                int total = w * d;
                for (int rep = 0; rep < 2 && p.cursor < total; rep++, p.cursor++) {
                    int x = origin.getX() + p.cursor % w, z = origin.getZ() + p.cursor / w;
                    for (int y = piece.baseY + 1; y <= piece.baseY + tpl.sy() + CLEAR_EXTRA; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (pos.equals(s.heart)) continue;
                        BlockState st = sl.getBlockState(pos);
                        if (!st.isAir() && !st.is(SettlementsContent.HEART.get())) sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
                if (p.cursor >= total) next(p, Project.PHASE_FOUNDATION);
            }
            case Project.PHASE_FOUNDATION -> {
                int total = w * d;
                BlockState found = s.style.foundation();
                for (int rep = 0; rep < 2 && p.cursor < total; rep++, p.cursor++) {
                    int x = origin.getX() + p.cursor % w, z = origin.getZ() + p.cursor / w;
                    for (int y = piece.baseY; y > piece.baseY - FOUNDATION_DEPTH; y--) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState st = sl.getBlockState(pos);
                        if (!(st.isAir() || st.canBeReplaced() || !st.getFluidState().isEmpty())) break;
                        sl.setBlock(pos, found, 2 | 16);
                    }
                }
                if (p.cursor >= total) next(p, Project.PHASE_BUILD);
            }
            case Project.PHASE_BUILD -> {
                if (p.cursor < tpl.entries().size()) {
                    Templates.Entry e = tpl.entries().get(p.cursor++);
                    place(sl, s, piece, tpl, origin, e);
                }
                if (p.cursor >= tpl.entries().size()) next(p, Project.PHASE_FINISH);
            }
            default -> {
                finish(sl, s, piece, tpl, origin);
                p.pieceIdx++;
                next(p, Project.PHASE_CLEAR);
                return true;
            }
        }
        return false;
    }

    private static void next(Project p, int phase) {
        p.phase = phase;
        p.cursor = 0;
    }

    private static void place(ServerLevel sl, Settlement s, Project.Piece piece, Templates.Tpl tpl, BlockPos origin, Templates.Entry e) {
        BlockPos pos = origin.offset(tpl.rotate(e.pos().getX(), e.pos().getY(), e.pos().getZ(), piece.rotation));
        if (pos.equals(s.heart)) return;
        BlockState st = e.state().rotate(piece.rotation);
        if (sl.getBlockState(pos) == st && e.nbt() == null) return;
        // quem estiver no lugar do bloco sobe para cima dele (ninguém fica entalado na parede)
        if (!st.getCollisionShape(sl, pos).isEmpty()) {
            for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(pos))) {
                le.teleportTo(le.getX(), pos.getY() + st.getCollisionShape(sl, pos).max(net.minecraft.core.Direction.Axis.Y), le.getZ());
            }
        }
        sl.setBlock(pos, st, 2 | 16);
        if (e.nbt() != null) {
            BlockEntity be = sl.getBlockEntity(pos);
            if (be != null) {
                CompoundTag tag = e.nbt().copy();
                tag.putInt("x", pos.getX());
                tag.putInt("y", pos.getY());
                tag.putInt("z", pos.getZ());
                be.loadWithComponents(tag, sl.registryAccess());
                be.setChanged();
            }
        }
        if (sl.random.nextInt(3) == 0) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    4, 0.25, 0.25, 0.25, 0.05);
        }
        if (sl.random.nextInt(4) == 0) {
            var sound = st.getSoundType(sl, pos, null);
            sl.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, 0.5F, sound.getPitch() * 0.9F);
        }
    }

    /** Acabamento: cercas, vidros, muros e escadas se ligam aos vizinhos; portões registram a grade. */
    private static void finish(ServerLevel sl, Settlement s, Project.Piece piece, Templates.Tpl tpl, BlockPos origin) {
        for (Templates.Entry e : tpl.entries()) {
            Block b = e.state().getBlock();
            if (!(b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock)) continue;
            BlockPos pos = origin.offset(tpl.rotate(e.pos().getX(), e.pos().getY(), e.pos().getZ(), piece.rotation));
            BlockState cur = sl.getBlockState(pos);
            BlockState upd = Block.updateFromNeighbourShapes(cur, sl, pos);
            if (upd != cur) sl.setBlock(pos, upd, 2 | 16);
        }
        if (piece.kind == Project.Piece.GATE) {
            // abertura do portão: x 2..4, y 1..altura, z 1 (no molde, antes de girar)
            for (int x = 2; x <= 4; x++) {
                for (int y = 1; y <= s.style.gateHeight; y++) {
                    s.gateBlocks.add(origin.offset(tpl.rotate(x, y, 1, piece.rotation)).immutable());
                }
            }
        }
        sl.playSound(null, piece.cx, piece.baseY + 1, piece.cz, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.3F);
    }

    /** Mede o chão da peça (mediana dos cantos e do centro). Falso se o terreno ainda não carregou. */
    private static boolean resolveBase(ServerLevel sl, Settlement s, Project.Piece piece, Templates.Tpl tpl) {
        int ground = s.heart.getY() - 1;
        if (piece.kind == Project.Piece.BUILDING && piece.cx == s.heart.getX() && piece.cz == s.heart.getZ()) {
            piece.baseY = ground;
            return true;
        }
        int w = tpl.width(piece.rotation), d = tpl.depth(piece.rotation);
        int x0 = piece.cx - w / 2, z0 = piece.cz - d / 2;
        int[][] pts = {{x0, z0}, {x0 + w - 1, z0}, {x0, z0 + d - 1}, {x0 + w - 1, z0 + d - 1}, {piece.cx, piece.cz}};
        int[] hs = new int[pts.length];
        for (int i = 0; i < pts.length; i++) {
            if (!sl.hasChunk(pts[i][0] >> 4, pts[i][1] >> 4)) return false;
            hs[i] = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pts[i][0], pts[i][1]) - 1;
        }
        Arrays.sort(hs);
        int range = piece.kind == Project.Piece.BUILDING ? 6 : 10;
        piece.baseY = Math.max(ground - range, Math.min(ground + range, hs[hs.length / 2]));
        return true;
    }

    public static BlockPos origin(Project.Piece piece, Templates.Tpl tpl) {
        int w = tpl.width(piece.rotation), d = tpl.depth(piece.rotation);
        return new BlockPos(piece.cx - w / 2, piece.baseY, piece.cz - d / 2);
    }

    private static BlockPos focus(Project p, Project.Piece piece, Templates.Tpl tpl, BlockPos origin) {
        int w = tpl.width(piece.rotation), d = tpl.depth(piece.rotation);
        if (p.phase == Project.PHASE_BUILD && p.cursor < tpl.entries().size()) {
            Templates.Entry e = tpl.entries().get(p.cursor);
            return origin.offset(tpl.rotate(e.pos().getX(), e.pos().getY(), e.pos().getZ(), piece.rotation));
        }
        if ((p.phase == Project.PHASE_CLEAR || p.phase == Project.PHASE_FOUNDATION) && p.cursor < w * d) {
            return new BlockPos(origin.getX() + p.cursor % w, piece.baseY + 1, origin.getZ() + p.cursor / w);
        }
        return new BlockPos(piece.cx, piece.baseY + 1, piece.cz);
    }

    /**
     * Construtor empacado: aparece na beira do canteiro, no ponto livre MAIS PERTO do bloco da vez (lados e cantos),
     * para ficar ao alcance da obra.
     */
    private static void bringBuilder(ServerLevel sl, BuilderNpc b, Project.Piece piece, Templates.Tpl tpl, BlockPos origin,
                                     BlockPos focus) {
        int w = tpl.width(piece.rotation), d = tpl.depth(piece.rotation);
        List<int[]> spots = new ArrayList<>(List.of(new int[] {w / 2, d + 1}, new int[] {w / 2, -2}, new int[] {-2, d / 2},
                new int[] {w + 1, d / 2}, new int[] {-2, -2}, new int[] {w + 1, -2}, new int[] {-2, d + 1},
                new int[] {w + 1, d + 1}));
        int fx = focus.getX() - origin.getX(), fz = focus.getZ() - origin.getZ();
        spots.sort((a, c) -> Integer.compare((a[0] - fx) * (a[0] - fx) + (a[1] - fz) * (a[1] - fz),
                (c[0] - fx) * (c[0] - fx) + (c[1] - fz) * (c[1] - fz)));
        for (int[] sp : spots) {
            int x = origin.getX() + sp[0], z = origin.getZ() + sp[1];
            if (!sl.hasChunk(x >> 4, z >> 4)) continue;
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (sl.getBlockState(at).isAir() && sl.getBlockState(at.above()).isAir()) {
                sl.sendParticles(ParticleTypes.CLOUD, b.getX(), b.getY() + 1, b.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                b.teleportTo(x + 0.5, y, z + 0.5);
                b.getNavigation().stop();
                sl.sendParticles(ParticleTypes.CLOUD, x + 0.5, y + 1, z + 0.5, 8, 0.3, 0.5, 0.3, 0.02);
                return;
            }
        }
    }

    // =====================================================================================
    //  Letreiros das obras
    // =====================================================================================
    private static void signs(ServerLevel sl, Settlement s) {
        int builders = s.population(Settlement.Role.BUILDER);
        int active = Math.min(s.projects.size(), Math.max(1, builders));
        for (int i = 0; i < active; i++) {
            Project p = s.projects.get(i);
            Project.Piece piece = p.current();
            if (piece == null || !piece.resolved()) continue;
            Templates.Tpl tpl = Templates.get(sl, piece.template);
            if (tpl == null) continue;
            int steps = switch (p.phase) {
                case Project.PHASE_CLEAR, Project.PHASE_FOUNDATION -> tpl.width(piece.rotation) * tpl.depth(piece.rotation);
                case Project.PHASE_BUILD -> tpl.entries().size();
                default -> 1;
            };
            Blueprint bp = Blueprints.get(s.style, p.key);
            Component name = bp == null ? Component.literal(p.key) : bp.displayName();
            Component title = p.builder == null || builders == 0
                    ? Component.translatable("settlements.isekai_mahou.sign.waiting", name)
                    : Component.translatable("settlements.isekai_mahou.sign.building", name);
            double y = piece.baseY + Math.min(tpl.sy(), 12) + 2.5;
            AABB box = new AABB(piece.cx - 1, y - 2, piece.cz - 1, piece.cx + 2, y + 2, piece.cz + 2);
            List<SiteSign> found = sl.getEntitiesOfClass(SiteSign.class, box);
            SiteSign sign = found.isEmpty() ? null : found.get(0);
            if (sign == null) {
                if (!sl.hasChunk(piece.cx >> 4, piece.cz >> 4)) continue;
                sign = SettlementsContent.SITE_SIGN.get().create(sl);
                if (sign == null) continue;
                sign.moveTo(piece.cx + 0.5, y, piece.cz + 0.5);
                sl.addFreshEntity(sign);
            }
            sign.update(title, p.progress(steps), s.style.color);
        }
    }

    /**
     * Termina na hora todas as obras (comando de op para testar). Devolve quantas terminaram; obras com terreno ainda
     * não carregado ficam na fila.
     */
    public static int finishAll(ServerLevel sl, Settlement s) {
        int done = 0;
        for (int i = 0; i < s.projects.size(); i++) {
            Project p = s.projects.get(i);
            int guard = 0;
            while (!p.done() && guard++ < 200000) {
                Project.Piece piece = p.current();
                if (piece == null) break;
                Templates.Tpl tpl = Templates.get(sl, piece.template);
                if (tpl == null) {
                    p.pieceIdx++;
                    next(p, Project.PHASE_CLEAR);
                    continue;
                }
                if (!piece.resolved() && !resolveBase(sl, s, piece, tpl)) break;
                step(sl, s, p, tpl);
            }
            if (p.done()) {
                s.projects.remove(i--);
                SettlementService.onProjectDone(sl, s, p);
                done++;
            }
        }
        clearSigns(sl, s);
        return done;
    }

    /** Some com os letreiros de uma cidade (cidade abandonada). */
    public static void clearSigns(ServerLevel sl, Settlement s) {
        AABB box = new AABB(s.heart).inflate(CityLayout.TERRITORY, 64, CityLayout.TERRITORY);
        for (Entity e : sl.getEntitiesOfClass(SiteSign.class, box)) e.discard();
    }

    private ConstructionEngine() {}
}
