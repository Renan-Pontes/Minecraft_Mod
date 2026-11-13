package com.renanpontes.isekaimahou.feature.settlements.build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.CityDecor;
import com.renanpontes.isekaimahou.feature.settlements.CityPace;
import com.renanpontes.isekaimahou.feature.settlements.CityPlanning;
import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementService;
import com.renanpontes.isekaimahou.feature.settlements.SettlementsContent;
import com.renanpontes.isekaimahou.feature.settlements.SkyCity;
import com.renanpontes.isekaimahou.feature.settlements.entity.BuilderNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.SiteSign;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
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
 * (saia do terreno) → blocos do molde (cheios de baixo para cima, depois os apoiados) → acabamento (cercas/vidros/
 * escadas se ligam, escada de ligação, postes e bandeira).
 * <p><b>v1.0 — "expandir a rodo"</b>: cada construtor toca uma obra (obras em paralelo = nº de construtores) e os que
 * sobram AJUDAM nas obras em andamento (+{@link #HELPER_BONUS} da velocidade por ajudante perto do canteiro). A obra
 * só anda com alguém da equipe perto (eles martelam olhando o bloco da vez); quem empaca é trazido ao canteiro.
 * <p><b>Orçamento</b>: no máximo {@link #CITY_BUDGET} blocos mudados por cidade a cada ação (5 ticks) e
 * {@link #TICK_BUDGET} por tick no servidor inteiro — uma cidade 15×15 com 20 construtores não trava o servidor; o que
 * não coube fica para a próxima ação. Só roda com o coração carregado.
 * <p>O terreno (aplainar, ruas, saia, escadas) fica em {@link CityTerrain}.
 */
public final class ConstructionEngine {
    /** Blocos (passos) por ação de 5 ticks de UM construtor, antes dos multiplicadores. */
    private static final float BASE_RATE = 4.0F;
    /** A muralha é obra de todos os construtores juntos. */
    private static final float WALL_RATE = 2.5F;
    /** Cada ajudante perto da obra soma isto à velocidade dela. */
    public static final float HELPER_BONUS = 0.5F;
    /** Blocos mudados por cidade em cada ação (5 ticks). */
    public static final int CITY_BUDGET = 260;
    /** Blocos mudados por tick somando todas as cidades. */
    public static final int TICK_BUDGET = 520;
    /** Colunas limpas/fundadas por passo. */
    private static final int COLUMNS_PER_STEP = 4;
    /** Colunas da saia por passo (quase todas não mudam nada). */
    private static final int SKIRT_PER_STEP = 12;
    private static final int CLEAR_EXTRA = 3;
    private static final int FOUNDATION_DEPTH = 10;
    private static final int TELEPORT_AFTER = 60;
    /** Até quantos blocos acima do piso a limpeza corta morros. */
    private static final int MAX_CUT = 40;

    /** Blocos que a ação atual ainda pode mudar (ver {@link #CITY_BUDGET}). */
    private static int budget = Integer.MAX_VALUE;
    private static long budgetTick = -1;
    private static int tickLeft;

    public static void tick(ServerLevel sl, Settlement s, int age) {
        if (age % 5 == 0) build(sl, s);
        if (age % 40 == 20) signs(sl, s);
    }

    /**
     * Onda 5: a reforma do degrau ({@code CityRenewal}) pede blocos do MESMO orçamento por tick das obras. Devolve
     * quantos pode mudar agora (0 = o servidor já gastou o tick; tente na próxima).
     */
    public static int claim(ServerLevel sl, int want) {
        long now = sl.getServer().getTickCount();
        if (now != budgetTick) {
            budgetTick = now;
            tickLeft = TICK_BUDGET;
        }
        int got = Math.max(0, Math.min(want, tickLeft));
        tickLeft -= got;
        return got;
    }

    /** Obra de reforma de degrau (moldes de troca: sem limpeza nem fundação, e sem passar por cima de nada). */
    public static boolean tier(Project p) {
        return Project.KIND_TIER.equals(p.kind);
    }

    /** Todo bloco que a obra muda passa por aqui (conta no orçamento). */
    private static void set(ServerLevel sl, BlockPos pos, BlockState st, int flags) {
        sl.setBlock(pos, st, flags);
        budget--;
    }

    /** Uma obra nova, que aplaina o lote inteiro com a linha de rua (obras antigas em andamento seguem como eram). */
    public static boolean flat(Project p) {
        return p.clearHalf >= CityTerrain.LEVEL_HALF;
    }

    // =====================================================================================
    //  Obras
    // =====================================================================================
    private static void build(ServerLevel sl, Settlement s) {
        List<BuilderNpc> all = loadedBuilders(sl, s);
        if (s.projects.isEmpty()) {
            // construtores sem obra: ajudam na reforma do degrau (onda 5) ou ficam livres para passear
            BlockPos renew = com.renanpontes.isekaimahou.feature.settlements.CityRenewal.site(s);
            for (BuilderNpc b : all) {
                if (renew != null) b.assign(renew, renew);
                else if (b.site() != null) b.assign(null, null);
            }
            return;
        }
        int active = Math.min(s.projects.size(), Math.max(1, s.population(Settlement.Role.BUILDER)));
        List<Project> act;
        if (s.sky || com.renanpontes.isekaimahou.feature.settlements.CityPause.any(s)) {
            // onda 7 (sky_island): obra esperando a terra do lote não segura construtor — a equipe vai para a próxima
            // onda 7 (city_pause): obra pausada também não — os construtores dela vão para as outras obras
            act = new ArrayList<>(active);
            for (Project p : s.projects) {
                if (act.size() >= active) break;
                if (!p.paused && !SkyCity.waitingLand(s, p)) act.add(p);
            }
            active = act.size();
            if (active == 0) {
                for (BuilderNpc b : all) if (b.site() != null) b.assign(null, null);
                return;
            }
        } else {
            act = new ArrayList<>(s.projects.subList(0, active));
        }
        // 1) um construtor responsável por obra (o mesmo de antes, se ainda existir)
        List<List<BuilderNpc>> crews = new ArrayList<>(active);
        Set<UUID> taken = new HashSet<>();
        for (Project p : act) {
            List<BuilderNpc> crew = new ArrayList<>(2);
            BuilderNpc b = pickBuilder(p, all, taken);
            if (b != null) {
                taken.add(b.getUUID());
                crew.add(b);
            }
            crews.add(crew);
        }
        // 2) quem sobrou ajuda nas obras em andamento, em rodízio (a ordem é estável: o ajudante não troca de obra)
        int turn = 0;
        for (BuilderNpc b : all) {
            if (!taken.add(b.getUUID())) continue;
            crews.get(turn++ % active).add(b);
        }
        // 3) orçamento de blocos desta ação
        long now = sl.getServer().getTickCount();
        if (now != budgetTick) {
            budgetTick = now;
            tickLeft = TICK_BUDGET;
        }
        int granted = Math.min(CITY_BUDGET, tickLeft);
        budget = granted;
        for (int i = 0; i < act.size(); i++) {
            Project p = act.get(i);
            // onda 7 (city_pause): obra cancelada no meio — a equipe desmonta o canteiro em vez de construir
            if (p.undo) {
                budget -= com.renanpontes.isekaimahou.feature.settlements.CityPause.undoStep(sl, s, p, crews.get(i),
                        Math.max(0, Math.min(budget, com.renanpontes.isekaimahou.feature.settlements.CityPause.UNDO_RATE)));
                continue;
            }
            if (work(sl, s, p, crews.get(i))) {
                s.projects.remove(p);
                SettlementService.onProjectDone(sl, s, p);
            }
        }
        tickLeft -= granted - Math.max(0, budget);
        budget = Integer.MAX_VALUE;
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

    /** Um passo da obra com a equipe dela (responsável + ajudantes). Devolve true quando ela termina. */
    private static boolean work(ServerLevel sl, Settlement s, Project p, List<BuilderNpc> crew) {
        Project.Piece piece = p.current();
        if (piece == null) return true;
        // v1.0 (city_plan): prédio mudando de lugar — espera a demolição do lote antigo terminar
        if (waiting(s, p)) return false;
        Templates.Tpl tpl = Templates.get(sl, piece.template);
        if (tpl == null) {
            // molde sumido (datapack quebrado): pula a peça para a obra não travar para sempre
            p.pieceIdx++;
            p.phase = Project.PHASE_CLEAR;
            p.cursor = 0;
            return p.done();
        }
        if (!piece.resolved() && !resolveBase(sl, s, p, piece, tpl)) return false;
        // onda 7 (sky_island): numa Ilha Celeste a obra espera a terra do lote existir (ninguém é mandado para o vazio)
        if (SkyCity.waitingLand(s, p)) return false;
        if (crew.isEmpty()) return false;
        BlockPos origin = origin(piece, tpl);
        BlockPos site = new BlockPos(piece.cx, piece.baseY + 1, piece.cz);
        int near = 0;
        for (int i = 0; i < crew.size(); i++) {
            BuilderNpc b = crew.get(i);
            BlockPos focus = focus(p, piece, tpl, origin, i);
            b.assign(site, focus);
            double dx = b.getX() - (focus.getX() + 0.5), dz = b.getZ() - (focus.getZ() + 0.5);
            double dy = b.getY() - focus.getY();
            if (dx * dx + dz * dz <= 100.0 && Math.abs(dy) <= 18.0) {
                near++;
                b.lag = 0;
            } else if (++b.lag > TELEPORT_AFTER) {
                b.lag = 0;
                // onda 8 (water_city): sobre a água o construtor aparece no piso firme mais perto do canteiro (não dentro d'água)
                if (!(s.water && com.renanpontes.isekaimahou.feature.settlements.WaterCity.bring(sl, s, b, focus))) {
                    bringBuilder(sl, b, piece, tpl, origin, focus);
                }
            }
        }
        if (near == 0 || budget <= 0) return false;
        float rate = BASE_RATE * s.style.buildSpeed * (1F + s.speedPct() / 100F) * (p.key.equals(Blueprint.WALL) ? WALL_RATE : 1F)
                * (1F + HELPER_BONUS * (near - 1));
        // v1.0 (city_magic): mana canalizada na obra — os construtores trabalham mais rápido enquanto a magia dura
        if (p.magicUntil > sl.getGameTime()) {
            rate *= Math.max(1F, p.magicMult);
            // brilho discreto sobre a obra (lote de FX: respeita a qualidade de efeitos do cliente)
            if (sl.random.nextInt(4) == 0) {
                com.renanpontes.isekaimahou.core.ElementFx.burst(sl, new net.minecraft.world.phys.Vec3(piece.cx + 0.5, piece.baseY + 2.5,
                        piece.cz + 0.5), com.renanpontes.isekaimahou.core.Element.ARCANE, 5, 2.2);
            }
        }
        // onda 6 (city_pace): a obra leva DIAS — os blocos só sobem até a fração de dias já trabalhada. No dia a dia
        // isso dá um bloco de vez em quando (os construtores martelam o tempo todo); depois de dias longe, a equipe
        // recupera no ritmo de antes o que "já estava feito".
        CityPace.ensure(sl.getServer(), s, p);
        float target = CityPace.target(p);
        boolean all = CityPace.timeDone(p);
        p.accum = Math.min(p.accum + rate, 512F);
        int n = (int) p.accum;
        p.accum -= n;
        for (int k = 0; k < n && !p.done() && budget > 0; k++) {
            if (!all && p.progress(phaseSteps(p, piece, tpl)) >= target) {
                p.accum = 0F;
                break;
            }
            if (step(sl, s, p, tpl)) {
                piece = p.current();
                if (piece == null) return true;
                tpl = Templates.get(sl, piece.template);
                if (tpl == null || !piece.resolved() && !resolveBase(sl, s, p, piece, tpl)) return false;
            }
        }
        return p.done();
    }

    /** A obra ainda espera outra terminar ({@link Project#after})? Se a outra sumiu (cancelada), deixa de esperar. */
    public static boolean waiting(Settlement s, Project p) {
        if (p.after == Integer.MIN_VALUE) return false;
        Project other = s.projectAt(p.after);
        if (other != null && other != p) return true;
        p.after = Integer.MIN_VALUE;
        return false;
    }

    /** Área (x0, z0, largura, profundidade) que a obra limpa e aplaina: o molde + 1 bloco de margem (ou o terreno todo). */
    public static int[] area(Project p, Project.Piece piece, Templates.Tpl tpl, BlockPos origin) {
        int w = tpl.width(piece.rotation), d = tpl.depth(piece.rotation);
        if (piece.kind != Project.Piece.BUILDING) return new int[] {origin.getX(), origin.getZ(), w, d};
        if (p.clearHalf > 0) {
            int h = p.clearHalf;
            return new int[] {piece.cx - h, piece.cz - h, 2 * h + 1, 2 * h + 1};
        }
        return new int[] {origin.getX() - 1, origin.getZ() - 1, w + 2, d + 2};
    }

    /** Passos da fase atual (para a barra de progresso). */
    public static int phaseSteps(Project p, Project.Piece piece, Templates.Tpl tpl) {
        return switch (p.phase) {
            case Project.PHASE_CLEAR, Project.PHASE_FOUNDATION -> {
                int[] a = area(p, piece, tpl, origin(piece, tpl));
                yield a[2] * a[3];
            }
            case Project.PHASE_BUILD -> tpl.entries().size();
            case Project.PHASE_SKIRT -> CityTerrain.skirtColumns(Math.max(1, p.clearHalf));
            default -> 1;
        };
    }

    /** Executa um passo; devolve true quando a peça atual terminou (o projeto avançou para a próxima). */
    private static boolean step(ServerLevel sl, Settlement s, Project p, Templates.Tpl tpl) {
        Project.Piece piece = p.current();
        if (piece == null) return true;
        BlockPos origin = origin(piece, tpl);
        int[] a = area(p, piece, tpl, origin);
        int w = a[2], d = a[3];
        boolean building = piece.kind == Project.Piece.BUILDING;
        boolean flat = building && flat(p);
        boolean tier = tier(p);
        if (tier && (p.phase == Project.PHASE_CLEAR || p.phase == Project.PHASE_FOUNDATION)) next(p, Project.PHASE_BUILD);
        switch (p.phase) {
            case Project.PHASE_CLEAR -> {
                int total = w * d;
                // obra nova começando num lote: os enfeites de festival dali são esquecidos (a limpeza leva todos)
                if (p.cursor == 0 && flat) {
                    CityDecor.forgetArea(s, a[0], a[1], a[0] + w - 1, a[1] + d - 1);
                    com.renanpontes.isekaimahou.feature.settlements.CityRenewal.forgetArea(s, a[0], a[1], a[0] + w - 1, a[1] + d - 1);
                }
                for (int rep = 0; rep < COLUMNS_PER_STEP && p.cursor < total && budget > 0; rep++, p.cursor++) {
                    int x = a[0] + p.cursor % w, z = a[1] + p.cursor / w;
                    if (flat && CityLayout.inWallBand(s, x - s.heart.getX(), z - s.heart.getZ())) continue;
                    int top = piece.baseY + tpl.sy() + CLEAR_EXTRA;
                    // morro em cima do terreno: corta até a superfície (nada de pedaço de morro flutuando sobre o telhado)
                    if (building) top = Math.min(piece.baseY + MAX_CUT, Math.max(top, sl.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1));
                    for (int y = piece.baseY + 1; y <= top; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (pos.equals(s.heart)) continue;
                        BlockState st = sl.getBlockState(pos);
                        if (st.isAir() || st.is(SettlementsContent.HEART.get())) continue;
                        // tronco/folha avisa os vizinhos (as folhas que sobram fora do lote murcham sozinhas)
                        set(sl, pos, Blocks.AIR.defaultBlockState(), st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES) ? 3 : 2 | 16);
                    }
                }
                if (p.cursor >= total) next(p, Project.PHASE_FOUNDATION);
            }
            case Project.PHASE_FOUNDATION -> {
                int total = w * d;
                BlockState found = s.style.foundation();
                int fw = tpl.width(piece.rotation), fd = tpl.depth(piece.rotation);
                for (int rep = 0; rep < COLUMNS_PER_STEP && p.cursor < total && budget > 0; rep++, p.cursor++) {
                    int x = a[0] + p.cursor % w, z = a[1] + p.cursor / w;
                    if (flat && CityLayout.inWallBand(s, x - s.heart.getX(), z - s.heart.getZ())) continue;
                    // onda 8 (water_city): coluna sobre a água ganha o piso sobre estacas (deque do lote, passarela da linha
                    // de rua, base da muralha) — a obra nunca aterra o mar. Coluna de terra firme segue as regras de sempre.
                    if (s.water && com.renanpontes.isekaimahou.feature.settlements.WaterCity.wet(sl, s, x, z, piece.baseY)) {
                        boolean edge = x < origin.getX() || z < origin.getZ() || x >= origin.getX() + fw || z >= origin.getZ() + fd;
                        budget -= com.renanpontes.isekaimahou.feature.settlements.WaterCity.deckColumn(sl, s, piece, x, z,
                                building && edge && flat ? CityTerrain.surface(s, p, piece, x, z) : null, !building);
                        continue;
                    }
                    // onda 7 (sky_island): coluna sem terra fica como está, e a fundação nunca passa do fundo da ilha
                    // (nada de pilar descendo pelo vazio)
                    int skyFloor = s.sky ? SkyCity.floorY(s, x, z) : Integer.MIN_VALUE;
                    if (skyFloor == Integer.MAX_VALUE) continue;
                    for (int y = piece.baseY; y > piece.baseY - FOUNDATION_DEPTH; y--) {
                        if (y < skyFloor && y < piece.baseY) break;
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState st = sl.getBlockState(pos);
                        if (!(st.isAir() || st.canBeReplaced() || !st.getFluidState().isEmpty())) break;
                        set(sl, pos, found, 2 | 16);
                    }
                    // em volta do prédio: rua na borda do lote, calçada até a porta e quintal (as bordas de um patamar
                    // mostram a fundação = muro de arrimo)
                    boolean margin = x < origin.getX() || z < origin.getZ() || x >= origin.getX() + fw || z >= origin.getZ() + fd;
                    if (building && margin) {
                        BlockPos top = new BlockPos(x, piece.baseY, z);
                        BlockState cur = sl.getBlockState(top);
                        BlockState want = flat ? CityTerrain.surface(s, p, piece, x, z) : terraceTop(s);
                        // degrau antigo que ficou no piso do lote (escadas de versões anteriores) vira piso também
                        boolean oldStep = flat && cur.is(stairs(s).getBlock());
                        // onda 8: numa cidade das águas o deque/passarela de outro degrau ou estilo também é "chão do lote"
                        boolean deck = s.water && com.renanpontes.isekaimahou.feature.settlements.WaterCity.isFloor(cur);
                        if (!top.equals(s.heart) && cur != want && (oldStep || deck || natural(cur, found))) set(sl, top, want, 2 | 16);
                    }
                }
                // onda 7: na Ilha Celeste o terreno é plano — não há encosta para suavizar em volta do lote
                if (p.cursor >= total) next(p, flat && !s.sky ? Project.PHASE_SKIRT : Project.PHASE_BUILD);
            }
            case Project.PHASE_BUILD -> {
                // reforma de um portão: a grade velha sai antes (o vão novo pode ser maior ou menor)
                if (tier && p.cursor == 0 && piece.kind == Project.Piece.GATE) openGate(sl, s, piece, tpl, origin);
                // onda 6: os andaimes sobem quando os blocos começam (e saem no acabamento)
                if (p.cursor == 0 && building && p.scaffold.isEmpty()) raiseScaffold(sl, s, p, piece, tpl, origin);
                if (p.cursor < tpl.entries().size()) {
                    Templates.Entry e = tpl.entries().get(p.cursor++);
                    place(sl, s, piece, tpl, origin, e, tier);
                }
                if (p.cursor >= tpl.entries().size()) next(p, Project.PHASE_FINISH);
            }
            case Project.PHASE_SKIRT -> {
                int total = CityTerrain.skirtColumns(p.clearHalf);
                for (int rep = 0; rep < SKIRT_PER_STEP && p.cursor < total && budget > 0; rep++, p.cursor++) {
                    budget -= CityTerrain.skirtColumn(sl, s, p, piece.cx, piece.cz, piece.baseY, p.clearHalf, p.cursor);
                }
                if (p.cursor >= total) next(p, Project.PHASE_BUILD);
            }
            default -> {
                dropScaffold(sl, p);
                finish(sl, s, piece, tpl, origin);
                if (flat) {
                    budget -= connect(sl, s, p, piece);
                    if (p.cell >= 0 && !Project.KIND_TERRAIN.equals(p.kind)) budget -= CityTerrain.furnish(sl, s, piece, tpl, origin);
                } else if (building && p.cell >= 0 && !Project.KIND_TERRAIN.equals(p.kind)) {
                    frontSteps(sl, s, piece, tpl, origin);
                }
                p.pieceIdx++;
                next(p, Project.PHASE_CLEAR);
                return true;
            }
        }
        return false;
    }

    /**
     * Lote aplainado pronto: grava a altura dele e faz as escadas de ligação que forem INEVITÁVEIS — a dele com o
     * vizinho de dentro e a dos vizinhos de fora que dependem dele (desnível &gt; 1; ver {@link CityTerrain#link}).
     */
    private static int connect(ServerLevel sl, Settlement s, Project p, Project.Piece piece) {
        int cell = p.cell >= 0 ? p.cell : Blueprint.PLAZA.equals(p.key) ? CityLayout.CENTER_CELL : -1;
        if (cell < 0) return 0;
        int n = 0;
        if (cell != CityLayout.CENTER_CELL) {
            s.plotBase.put(cell, piece.baseY);
            n += CityTerrain.link(sl, s, cell);
        }
        int i = CityLayout.cellI(cell), j = CityLayout.cellJ(cell);
        int[][] around = {{i + 1, j}, {i - 1, j}, {i, j + 1}, {i, j - 1}};
        for (int[] a : around) {
            if (Math.max(Math.abs(a[0]), Math.abs(a[1])) > s.radius) continue;
            int other = CityLayout.cellAt(s, a[0], a[1]);
            if (other != CityLayout.CENTER_CELL && CityTerrain.inward(other) == cell) n += CityTerrain.link(sl, s, other);
        }
        return n;
    }

    /** Topo da margem dos terraços. */
    private static BlockState terraceTop(Settlement s) {
        return switch (s.style) {
            case DESERT -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            case NORDIC, GOTHIC -> Blocks.COARSE_DIRT.defaultBlockState();
            case DWARVEN -> Blocks.GRAVEL.defaultBlockState();
            default -> Blocks.DIRT_PATH.defaultBlockState();
        };
    }

    private static BlockState stairs(Settlement s) {
        return switch (s.style) {
            case DESERT -> Blocks.SANDSTONE_STAIRS.defaultBlockState();
            case NORDIC -> Blocks.COBBLESTONE_STAIRS.defaultBlockState();
            case ELVEN -> Blocks.MOSSY_COBBLESTONE_STAIRS.defaultBlockState();
            case GOTHIC, DWARVEN -> Blocks.COBBLED_DEEPSLATE_STAIRS.defaultBlockState();
            default -> Blocks.STONE_BRICK_STAIRS.defaultBlockState();
        };
    }

    /** Terreno ou mato (pode ser mexido nos degraus/terraços sem estragar construções). */
    private static boolean natural(BlockState st, BlockState foundation) {
        // onda 5: o piso de rua de qualquer degrau (calçamento, pavimento nobre) também é "chão do lote"
        return CityTerrain.ground(st, foundation) || TierLook.isStreet(st);
    }

    /**
     * Degraus na frente da entrada: do terraço do prédio até o chão (descendo ou subindo), no máximo 6, só em terreno
     * natural (nunca em cima de outra construção).
     */
    private static void frontSteps(ServerLevel sl, Settlement s, Project.Piece piece, Templates.Tpl tpl, BlockPos origin) {
        Direction out = piece.rotation.rotate(Direction.SOUTH);
        BlockPos edge = origin.offset(tpl.rotate(tpl.sx() / 2, 0, tpl.sz(), piece.rotation));   // margem em frente à porta
        int level = piece.baseY;
        BlockState found = s.style.foundation();
        // anda pelo terraço (mesma altura) até a borda, e os degraus começam onde o chão muda
        int g = level;
        for (int skip = 0; skip < 10; skip++) {
            BlockPos q = edge.relative(out);
            if (!sl.hasChunk(q.getX() >> 4, q.getZ() >> 4)) return;
            // degraus de uma obra anterior (melhoria/troca): o terraço novo cresceu por cima deles — viram piso (senão
            // a obra nova põe OUTRA escada na frente da velha a cada nível)
            BlockPos atLevel = new BlockPos(q.getX(), level, q.getZ());
            boolean inPlot = Math.abs(q.getX() - piece.cx) <= CityPlanning.PLOT_HALF && Math.abs(q.getZ() - piece.cz) <= CityPlanning.PLOT_HALF;
            if (inPlot && sl.getBlockState(atLevel).is(stairs(s).getBlock())) sl.setBlock(atLevel, terraceTop(s), 3);
            if (inPlot && sl.getBlockState(atLevel.above()).is(stairs(s).getBlock())) sl.setBlock(atLevel.above(), Blocks.AIR.defaultBlockState(), 3);
            g =sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.getX(), q.getZ()) - 1;
            if (g != level) break;
            edge = q;
        }
        if (g == level) return;
        boolean down = g < level;
        int y = down ? level : level + 1;
        for (int k = 1; k <= 6; k++) {
            BlockPos at = edge.relative(out, k);
            if (!sl.hasChunk(at.getX() >> 4, at.getZ() >> 4)) return;
            if (Math.abs(at.getX() - s.heart.getX()) > CityLayout.half(s) || Math.abs(at.getZ() - s.heart.getZ()) > CityLayout.half(s)) return;
            int ground = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 1;
            if (down ? ground >= y : ground <= y - 1) return;
            BlockPos stairPos = new BlockPos(at.getX(), y, at.getZ());
            if (!natural(sl.getBlockState(stairPos), found)) return;
            BlockState st = stairs(s).setValue(StairBlock.FACING, down ? out.getOpposite() : out);
            sl.setBlock(stairPos, st, 3);
            for (int up = 1; up <= 3; up++) {
                BlockPos ab = stairPos.above(up);
                if (natural(sl.getBlockState(ab), found) && !sl.getBlockState(ab).isAir()) sl.setBlock(ab, Blocks.AIR.defaultBlockState(), 3);
            }
            for (int dn = 1; dn <= 8; dn++) {
                BlockPos bl = stairPos.below(dn);
                BlockState b = sl.getBlockState(bl);
                if (!(b.isAir() || b.canBeReplaced() || !b.getFluidState().isEmpty())) break;
                sl.setBlock(bl, found, 2 | 16);
            }
            if (down && ground >= y - 1) return;
            y += down ? -1 : 1;
        }
    }

    private static void next(Project p, int phase) {
        p.phase = phase;
        p.cursor = 0;
    }

    // =====================================================================================
    //  Andaimes (onda 6, city_pace): a obra de dias tem cara de obra
    // =====================================================================================
    /** Altura máxima dos andaimes. */
    private static final int SCAFFOLD_MAX = 9;

    /**
     * Andaimes nos quatro cantos de fora do prédio, do piso do lote até a altura dele. Só em ar, só dentro do lote e
     * só sobre chão firme (andaime sem apoio cairia); cada bloco fica anotado na obra para sair no acabamento.
     */
    private static void raiseScaffold(ServerLevel sl, Settlement s, Project p, Project.Piece piece, Templates.Tpl tpl, BlockPos origin) {
        if (p.pieces.size() != 1 || p.cell < 0 || !flat(p) || tpl.sy() < 4) return;
        if (!(Project.KIND_BUILD.equals(p.kind) || Project.KIND_UPGRADE.equals(p.kind) || Project.KIND_RESTYLE.equals(p.kind))) return;
        int w = tpl.width(piece.rotation), d = tpl.depth(piece.rotation);
        int h = Math.min(tpl.sy(), SCAFFOLD_MAX);
        int[][] corners = {{origin.getX() - 1, origin.getZ() - 1}, {origin.getX() + w, origin.getZ() - 1},
                {origin.getX() - 1, origin.getZ() + d}, {origin.getX() + w, origin.getZ() + d}};
        BlockState pole = Blocks.SCAFFOLDING.defaultBlockState().setValue(net.minecraft.world.level.block.ScaffoldingBlock.DISTANCE, 0);
        for (int[] c : corners) {
            if (Math.abs(c[0] - piece.cx) > p.clearHalf || Math.abs(c[1] - piece.cz) > p.clearHalf) continue;
            if (CityLayout.inWallBand(s, c[0] - s.heart.getX(), c[1] - s.heart.getZ())) continue;
            BlockPos base = new BlockPos(c[0], piece.baseY, c[1]);
            if (!sl.hasChunk(c[0] >> 4, c[1] >> 4) || !sl.getBlockState(base).isFaceSturdy(sl, base, Direction.UP)) continue;
            for (int y = 1; y <= h; y++) {
                BlockPos pos = base.above(y);
                if (pos.equals(s.heart) || !sl.getBlockState(pos).isAir()) break;
                set(sl, pos, pole, 2 | 16);
                p.scaffold.add(pos);
            }
        }
    }

    /** Tira os andaimes da obra (de cima para baixo; só o que ainda é andaime — nada do jogador é tocado). */
    public static void dropScaffold(ServerLevel sl, Project p) {
        for (int i = p.scaffold.size() - 1; i >= 0; i--) {
            BlockPos pos = p.scaffold.get(i);
            if (sl.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && sl.getBlockState(pos).is(Blocks.SCAFFOLDING)) {
                sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        p.scaffold.clear();
    }

    /**
     * Põe um bloco do molde. {@code polite} (reforma de degrau): entradas "tirar" só tiram o bloco que os construtores
     * tinham posto, e bloco novo só entra onde está livre (ar, mato, terreno) — nada do jogador é apagado.
     */
    /** Chão de quintal dos moldes (grama, terra, areia...) — na água vira deque. */
    private static boolean yardSoil(BlockState st) {
        return st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.SAND) || st.is(Blocks.RED_SAND) || st.is(Blocks.DIRT) || st.is(Blocks.COARSE_DIRT)
                || st.is(Blocks.PODZOL) || st.is(Blocks.ROOTED_DIRT) || st.is(Blocks.MOSS_BLOCK) || st.is(Blocks.MYCELIUM);
    }

    /** Mato e flor de quintal (precisam de terra embaixo). */
    private static boolean yardPlant(BlockState st) {
        return st.is(BlockTags.SMALL_FLOWERS) || st.is(BlockTags.TALL_FLOWERS) || st.is(Blocks.SHORT_GRASS) || st.is(Blocks.TALL_GRASS)
                || st.is(Blocks.FERN) || st.is(Blocks.LARGE_FERN) || st.is(Blocks.DEAD_BUSH) || st.is(Blocks.PINK_PETALS)
                || st.is(Blocks.SWEET_BERRY_BUSH);
    }

    private static void place(ServerLevel sl, Settlement s, Project.Piece piece, Templates.Tpl tpl, BlockPos origin, Templates.Entry e,
                              boolean polite) {
        BlockPos pos = origin.offset(tpl.rotate(e.pos().getX(), e.pos().getY(), e.pos().getZ(), piece.rotation));
        if (pos.equals(s.heart)) return;
        if (e.state().is(Blocks.STRUCTURE_VOID)) {
            // onda 8: entrada "varrer a coluna" da reforma de muralha — sai todo bloco de muralha dali para cima
            if (e.nbt() != null && TierWalls.SWEEP.equals(e.nbt().getString(TierWalls.OLD))) {
                budget -= TierWalls.sweep(sl, pos, e.nbt().getInt(TierWalls.SWEEP_H));
                return;
            }
            BlockState cur = sl.getBlockState(pos);
            if (!cur.isAir() && e.nbt() != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cur.getBlock()).toString()
                    .equals(e.nbt().getString(TierWalls.OLD))) {
                set(sl, pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            return;
        }
        BlockState st = e.state().rotate(piece.rotation);
        // onda 9 (conserto do visual novo na água): o quintal dos moldes é grama/areia — sobre o deque da Cidade das
        // Águas ele vira o piso do deque (areia cairia no mar), e o mato/flor do quintal, que não vive em tábua, fica de fora
        if (s.water && piece.kind == Project.Piece.BUILDING) {
            int ty = e.pos().getY();
            if (ty == 0 && yardSoil(st)) {
                st = com.renanpontes.isekaimahou.feature.settlements.WaterCity.deck(s);
            } else if ((ty == 1 || ty == 2) && yardPlant(st) && !yardSoil(sl.getBlockState(pos.below()))) {
                return;
            }
        }
        if (sl.getBlockState(pos) == st && e.nbt() == null) return;
        if (polite) {
            BlockState cur = sl.getBlockState(pos);
            // onda 8: bloco de muralha velho no caminho (base de outro estilo, resto de outra forma) é coberto pelo novo
            if (!cur.isAir() && !cur.canBeReplaced() && !cur.is(st.getBlock()) && !CityTerrain.ground(cur, s.style.foundation())
                    && !TierWalls.isWallBlock(cur)) {
                return;
            }
        }
        // quem estiver no lugar do bloco sobe para cima dele (ninguém fica entalado na parede)
        if (!st.getCollisionShape(sl, pos).isEmpty()) {
            for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(pos))) {
                le.teleportTo(le.getX(), pos.getY() + st.getCollisionShape(sl, pos).max(net.minecraft.core.Direction.Axis.Y), le.getZ());
            }
        }
        set(sl, pos, st, 2 | 16);
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
            // abertura do portão no molde, antes de girar: x0..x1, y 1..altura, z 1 (muda com o degrau: TierWalls)
            int[] o = TierWalls.opening(piece.template, s.style);
            for (int x = o[0]; x <= o[1]; x++) {
                for (int y = 1; y <= o[2]; y++) {
                    BlockPos g = origin.offset(tpl.rotate(x, y, 1, piece.rotation)).immutable();
                    if (!s.gateBlocks.contains(g)) s.gateBlocks.add(g);
                }
            }
        }
        sl.playSound(null, piece.cx, piece.baseY + 1, piece.cz, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.3F);
    }

    /**
     * Reforma de um portão: esquece a grade velha desta peça (e tira as barras, se o portão estava fechado). O vão
     * novo é registrado no acabamento; o decreto dos portões fecha de novo sozinho.
     */
    private static void openGate(ServerLevel sl, Settlement s, Project.Piece piece, Templates.Tpl tpl, BlockPos origin) {
        int w = tpl.width(piece.rotation), d = tpl.depth(piece.rotation);
        BlockState bars = s.style.portcullis();
        boolean any = s.gateBlocks.removeIf(g -> {
            if (g.getX() < origin.getX() || g.getX() >= origin.getX() + w || g.getZ() < origin.getZ() || g.getZ() >= origin.getZ() + d) return false;
            if (sl.getBlockState(g).is(bars.getBlock())) set(sl, g, Blocks.AIR.defaultBlockState(), 3);
            return true;
        });
        if (any) s.gatesShut = false;
    }

    /** Mede o chão da peça (mediana dos cantos e do centro). Falso se o terreno ainda não carregou. */
    private static boolean resolveBase(ServerLevel sl, Settlement s, Project p, Project.Piece piece, Templates.Tpl tpl) {
        int ground = s.heart.getY() - 1;
        // onda 5: reforma de muralha — a peça nova fica exatamente onde a velha está (achada comparando com o mundo)
        if (tier(p) && piece.kind != Project.Piece.BUILDING) {
            // onda 8 (city_looks, "RENOVATION tem que renovar o muro tbm"): a reforma não pula mais peça em silêncio —
            // acha a peça velha seja qual for o estilo/grau em que está de pé e, se não achar, reconstrói a peça do zero
            // no lugar dela (TierWalls.resolveSwap troca o molde da peça; a obra segue na próxima passada)
            int r = TierWalls.resolveSwap(sl, p, p.pieceIdx, ground, s.sky || s.water);
            if (r == TierWalls.SWAP_DONE) {
                // esta peça já está no visual novo (muralha que estava em obra na hora da reforma): nada a trocar
                p.pieceIdx++;
                next(p, Project.PHASE_BUILD);
            }
            return r == TierWalls.SWAP_READY;
        }
        // terreno já aplainado (ou prédio antigo) neste lote: usa a mesma altura
        Integer plot = p.cell >= 0 ? s.plotBase.get(p.cell) : null;
        if (plot != null && piece.kind == Project.Piece.BUILDING) {
            piece.baseY = plot;
            return true;
        }
        if (piece.kind == Project.Piece.BUILDING && piece.cx == s.heart.getX() && piece.cz == s.heart.getZ()) {
            piece.baseY = ground;
            return true;
        }
        // onda 7 (sky_island): a ilha é plana na altura da praça — prédio ou muralha, o chão é sempre o topo dela
        // (medir o terreno aqui acharia o chão lá embaixo, ou o vazio)
        // onda 8 (water_city): a Cidade das Águas também é plana — tudo na altura do piso da praça, logo acima da água
        if (s.sky || s.water && (piece.kind != Project.Piece.BUILDING || p.cell >= 0)) {
            piece.baseY = ground;
            return true;
        }
        // v1.0: lote novo fica no nível da rua (igual ao vizinho de dentro sempre que der para aplainar)
        if (piece.kind == Project.Piece.BUILDING && p.cell >= 0 && flat(p) && !Project.KIND_REPAIR.equals(p.kind)) {
            Integer level = CityTerrain.streetLevel(sl, s, p.cell);
            if (level == null) return false;
            piece.baseY = level;
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
        int range = piece.kind == Project.Piece.BUILDING ? 4 : 10;
        piece.baseY = Math.max(ground - range, Math.min(ground + range, hs[hs.length / 2]));
        return true;
    }

    public static BlockPos origin(Project.Piece piece, Templates.Tpl tpl) {
        int w = tpl.width(piece.rotation), d = tpl.depth(piece.rotation);
        return new BlockPos(piece.cx - w / 2, piece.baseY, piece.cz - d / 2);
    }

    /**
     * Para onde o membro {@code member} da equipe olha/anda (0 = o responsável). Nos blocos: o bloco da vez (os
     * ajudantes, alguns blocos adiante). No terreno: o meio da fileira da vez — o alvo anda devagar, ninguém fica
     * correndo atrás dele.
     */
    private static BlockPos focus(Project p, Project.Piece piece, Templates.Tpl tpl, BlockPos origin, int member) {
        int size = tpl.entries().size();
        if (p.phase == Project.PHASE_BUILD && p.cursor < size) {
            Templates.Entry e = tpl.entries().get(Math.min(size - 1, p.cursor + member * 11));
            return origin.offset(tpl.rotate(e.pos().getX(), e.pos().getY(), e.pos().getZ(), piece.rotation));
        }
        int spread = (member + 1) / 2 * 3 * (member % 2 == 0 ? 1 : -1);
        if (p.phase == Project.PHASE_CLEAR || p.phase == Project.PHASE_FOUNDATION) {
            int[] a = area(p, piece, tpl, origin);
            if (p.cursor < a[2] * a[3]) {
                int x = Math.max(a[0], Math.min(a[0] + a[2] - 1, a[0] + a[2] / 2 + spread));
                return new BlockPos(x, piece.baseY + 1, a[1] + p.cursor / a[2]);
            }
        }
        return new BlockPos(piece.cx + spread, piece.baseY + 1, piece.cz);
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
        // onda 7 (sky_island): obra esperando a terra não conta como "em andamento" (a equipe foi para a próxima); só as
        // primeiras da fila mostram a placa "aguardando a terra". No chão isto é o mesmo laço de sempre.
        // onda 7 (city_pause): obra pausada não ocupa equipe, mas o canteiro que já começou mantém a placa ("Pausada · dia X de Y")
        boolean pauses = com.renanpontes.isekaimahou.feature.settlements.CityPause.any(s);
        for (int i = 0, shown = 0; i < s.projects.size() && (shown < active || pauses); i++) {
            Project p = s.projects.get(i);
            boolean noLand = SkyCity.waitingLand(s, p);
            if (p.paused) {
                if (p.stage == 0) continue;
            } else {
                if (shown >= active || noLand && i >= active) continue;
                if (!noLand) shown++;
            }
            Project.Piece piece = p.current();
            if (piece == null || !piece.resolved()) continue;
            Templates.Tpl tpl = Templates.get(sl, piece.template);
            if (tpl == null) continue;
            Component name = CityPlanning.projectName(s, p);
            // onda 6: a placa do canteiro conta os dias ("Mercado — Paredes · dia 2 de 3")
            CityPace.ensure(sl.getServer(), s, p);
            // onda 7 (sky_island): obra parada porque o chão do lote ainda está se formando
            Component title = p.paused || p.undo
                    ? com.renanpontes.isekaimahou.feature.settlements.CityPause.signTitle(s, p, name)
                    : noLand
                    ? com.renanpontes.isekaimahou.feature.settlements.SettlementsLang.tr("sky.sign.land", name)
                    : p.builder == null || builders == 0
                    ? Component.translatable("settlements.isekai_mahou.sign.waiting", name)
                    : com.renanpontes.isekaimahou.feature.settlements.SettlementsLang.tr("pace.sign", name, CityPace.stageText(p),
                            CityPace.dayNumber(p), CityPace.totalDays(p));
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
            sign.update(title, CityPace.fraction(p), s.style.color);
        }
    }

    /**
     * Termina na hora todas as obras (comando de op para testar). Devolve quantas terminaram; obras com terreno ainda
     * não carregado ficam na fila.
     */
    public static int finishAll(ServerLevel sl, Settlement s) {
        budget = Integer.MAX_VALUE;
        int done = 0;
        // onda 7 (sky_island): terminar as obras de uma Ilha Celeste termina antes a terra que elas esperam
        if (s.sky) SkyCity.finishNow(sl, s);
        // onda 8 (water_city): e as passarelas, pontes e o cais da Cidade das Águas
        if (s.water) com.renanpontes.isekaimahou.feature.settlements.WaterWorks.finishNow(sl, s);
        for (int i = 0; i < s.projects.size(); i++) {
            Project p = s.projects.get(i);
            // onda 7 (city_pause): "terminar tudo" retoma as pausadas; obra cancelada no meio termina de ser DESMONTADA
            // (se virou o conserto que devolve o prédio de antes, ele é terminado na volta seguinte do laço)
            p.paused = false;
            if (p.undo) {
                if (com.renanpontes.isekaimahou.feature.settlements.CityPause.undoNow(sl, s, p)) i--;
                continue;
            }
            CityPace.complete(sl.getServer(), s, p);
            int guard = 0;
            while (!p.done() && guard++ < 200000) {
                Project.Piece piece = p.current();
                if (piece == null) break;
                // sem terra (chunk descarregado): a obra fica na fila, nada é posto no vazio
                if (SkyCity.waitingLand(s, p)) break;
                Templates.Tpl tpl = Templates.get(sl, piece.template);
                if (tpl == null) {
                    p.pieceIdx++;
                    next(p, Project.PHASE_CLEAR);
                    continue;
                }
                if (!piece.resolved()) {
                    int idx = p.pieceIdx;
                    if (!resolveBase(sl, s, p, piece, tpl)) {
                        if (p.pieceIdx != idx || p.current() != piece) continue;   // peça pulada ou com o molde trocado (reforma)
                        break;
                    }
                }
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

    /** Termina na hora UMA obra (pagar para consertar logo, comandos). Devolve true se terminou. */
    public static boolean finishOne(ServerLevel sl, Settlement s, Project p) {
        budget = Integer.MAX_VALUE;
        // onda 7 (sky_island): sem a terra do lote a obra não termina na hora (espera a ilha chegar lá)
        if (SkyCity.waitingLand(s, p)) return false;
        // onda 7 (city_pause): obra pausada ou sendo desmontada não é terminada na hora (nem com magia nem pagando)
        if (p.paused || p.undo) return false;
        CityPace.complete(sl.getServer(), s, p);
        int guard = 0;
        while (!p.done() && guard++ < 200000) {
            Project.Piece piece = p.current();
            if (piece == null) break;
            if (SkyCity.waitingLand(s, p)) return false;
            Templates.Tpl tpl = Templates.get(sl, piece.template);
            if (tpl == null) {
                p.pieceIdx++;
                next(p, Project.PHASE_CLEAR);
                continue;
            }
            if (!piece.resolved()) {
                int idx = p.pieceIdx;
                if (!resolveBase(sl, s, p, piece, tpl)) {
                    if (p.pieceIdx != idx || p.current() != piece) continue;
                    return false;
                }
            }
            step(sl, s, p, tpl);
        }
        if (!p.done()) return false;
        s.projects.remove(p);
        SettlementService.onProjectDone(sl, s, p);
        clearSigns(sl, s);
        return true;
    }

    /** Some com os letreiros de uma cidade (cidade abandonada). */
    public static void clearSigns(ServerLevel sl, Settlement s) {
        AABB box = new AABB(s.heart).inflate(CityLayout.half(s), 64, CityLayout.half(s));
        for (Entity e : sl.getEntitiesOfClass(SiteSign.class, box)) e.discard();
    }

    private ConstructionEngine() {}
}
