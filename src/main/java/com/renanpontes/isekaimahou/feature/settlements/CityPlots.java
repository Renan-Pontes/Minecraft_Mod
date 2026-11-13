package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.settlements.build.Templates;
import com.renanpontes.isekaimahou.feature.settlements.network.SettlementsNetwork;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * <b>Planta livre da cidade</b> (v1.0, onda 4 — pedido do dono: "Prévia antes de fundar" e "Posição livre dos prédios").
 * <ul>
 *   <li><b>Lote livre</b>: um prédio em qualquer ponto da cidade, virado para qualquer lado. O id do lote guarda a
 *       posição ({@link CityLayout#freeCell}), então TODO o resto da cidade (obras, níveis, consertos, enfeites, defesa)
 *       funciona igual ao de um terreno da grade. A grade continua sendo o jeito rápido/padrão.</li>
 *   <li><b>Regras de onde cabe</b> ({@link #spotProblem}): dentro da área da cidade, fora da praça, fora da faixa das
 *       muralhas e a um terreno de distância ({@link #SPACING}) de qualquer outro prédio/obra — sobra sempre a linha de
 *       rua entre dois lotes. Um terreno da grade coberto por um lote livre fica bloqueado ({@link #gridBlocked}).</li>
 *   <li><b>Mudar/girar um prédio pronto</b> ({@link #move}): custa moedas do cofre; os construtores demolem o lote
 *       antigo e reerguem o prédio (mesmo nível e estilo) no lugar novo.</li>
 *   <li><b>Prévias</b> ({@link #probe}): o cliente pergunta "pode aqui?" enquanto o jogador mexe o holograma; a
 *       resposta traz o motivo, o tamanho do prédio e o estado do terreno. Nada é gasto antes de confirmar.</li>
 * </ul>
 */
public final class CityPlots {
    /** Distância mínima (em blocos, no maior eixo) entre os centros de dois lotes: 15 de lote + 1 de rua. */
    public static final int SPACING = CityLayout.CELL;
    /** Alcance da prévia (o jogador planeja de longe, olhando o terreno). */
    public static final int REACH = 72;

    /** Estados de terreno das prévias. */
    public static final int TERRAIN_OK = 0, TERRAIN_STEEP = 1, TERRAIN_WATER = 2, TERRAIN_UNKNOWN = -1;

    /** Último pedido de prévia de cada jogador (tick do servidor) — a prévia não pode virar enxurrada de pacotes. */
    private static final Map<UUID, Integer> LAST_PROBE = new HashMap<>();

    // =====================================================================================
    //  Onde cabe
    // =====================================================================================
    /** Lotes ocupados: prédio pronto ou obra em andamento (grade e livres). */
    public static Set<Integer> occupied(Settlement s) {
        Set<Integer> out = new LinkedHashSet<>(s.buildings.keySet());
        out.remove(CityLayout.CENTER_CELL);
        for (Project p : s.projects) if (p.cell >= 0) out.add(p.cell);
        return out;
    }

    /** Só os lotes LIVRES ocupados. */
    public static List<Integer> freeOccupied(Settlement s) {
        List<Integer> out = new ArrayList<>();
        for (int c : occupied(s)) if (CityLayout.isFree(c)) out.add(c);
        return out;
    }

    private static int gap(int cell, int dx, int dz) {
        return Math.max(Math.abs(CityLayout.offsetX(cell) - dx), Math.abs(CityLayout.offsetZ(cell) - dz));
    }

    /**
     * Por que um lote com centro em (dx, dz) a partir do coração NÃO cabe (chave de texto {@code free.*}/{@code plan.*}),
     * ou null se cabe. {@code ignore} = lote que não conta (o próprio, ou o prédio que está sendo mudado de lugar).
     */
    @Nullable
    public static String spotProblem(Settlement s, int dx, int dz, int ignore) {
        // onda 9 (isles): no arquipélago cada prédio ganha a SUA ilha/plataforma num terreno da planta — posição livre
        // cairia no vão entre as ilhas (no céu, no vazio)
        if (s.isles) return "isle.no_free";
        int m = Math.max(Math.abs(dx), Math.abs(dz));
        if (m < SPACING) return "free.plaza";
        if (m > CityLayout.axis(s.radius)) return "free.outside";
        // faixa das muralhas (as prontas e as que ainda podem ser erguidas): mesma folga que os terrenos da grade têm
        int lo = m - CityPlanning.PLOT_HALF, hi = m + CityPlanning.PLOT_HALF;
        for (int k = 1; k <= CityLayout.wallSlots(CityLayout.MAX_RADIUS); k++) {
            int d = CityLayout.wallDist(k);
            if (lo <= d + 6 && hi >= d - 2) return "free.wall";
        }
        // "sempre obedecendo o sistema de criação": a área só vale quando o terreno da grade dali já está liberado
        int near = CityLayout.cellIndex(CityLayout.nearestIndex(dx), CityLayout.nearestIndex(dz));
        if (near != CityLayout.CENTER_CELL && !CityLayout.unlocked(s, near)) return "plan.locked";
        for (int c : occupied(s)) {
            if (c != ignore && gap(c, dx, dz) < SPACING) return "free.overlap";
        }
        return null;
    }

    /** O terreno da grade está coberto por um lote livre (prédio ou obra)? */
    public static boolean gridBlocked(Settlement s, int cell) {
        return gridBlocked(freeOccupied(s), cell, Integer.MIN_VALUE);
    }

    public static boolean gridBlocked(List<Integer> freeCells, int cell, int ignore) {
        int dx = CityLayout.offsetX(cell), dz = CityLayout.offsetZ(cell);
        for (int f : freeCells) if (f != ignore && gap(f, dx, dz) < SPACING) return true;
        return false;
    }

    /**
     * O lote (livre ou da grade) que contém a coluna (x, z) do mundo, ou -1 (praça, rua larga, vão de muralha, fora da
     * cidade). Lotes livres ganham dos terrenos da grade que cobrem.
     */
    public static int plotAt(Settlement s, int x, int z) {
        int dx = x - s.heart.getX(), dz = z - s.heart.getZ();
        for (int f : freeOccupied(s)) {
            if (gap(f, dx, dz) <= CityPlanning.PLOT_HALF) return f;
        }
        // onda 9 (isles): terrenos espaçados (o vão entre as ilhas não é de lote nenhum)
        if (s.isles) return CityLayout.cellAtOffset(s, dx, dz);
        int i = CityLayout.indexAt(dx), j = CityLayout.indexAt(dz);
        if (i == Integer.MIN_VALUE || j == Integer.MIN_VALUE || Math.max(Math.abs(i), Math.abs(j)) > s.radius) return -1;
        int cell = CityLayout.cellIndex(i, j);
        return cell == CityLayout.CENTER_CELL ? -1 : cell;
    }

    /**
     * Id do lote para um centro e uma rotação: se cair EXATAMENTE num terreno da grade com a rotação padrão dele, é o
     * próprio terreno da grade (a grade continua sendo o padrão); senão, um lote livre.
     */
    public static int cellFor(int dx, int dz, Rotation rot) {
        int i = CityLayout.nearestIndex(dx), j = CityLayout.nearestIndex(dz);
        if (CityLayout.axis(i) == dx && CityLayout.axis(j) == dz && (i != 0 || j != 0)) {
            int grid = CityLayout.cellIndex(i, j);
            if (CityLayout.facingHeart(grid) == rot) return grid;
        }
        return CityLayout.freeCell(dx, dz, rot);
    }

    /** Por que o lote (grade ou livre) não pode receber uma obra nova — só a parte de POSIÇÃO. */
    @Nullable
    public static String cellProblem(Settlement s, int cell, int ignore) {
        if (CityLayout.isFree(cell)) return spotProblem(s, CityLayout.freeDx(cell), CityLayout.freeDz(cell), ignore);
        if (!CityPlanning.validCell(cell)) return "plan.bad_cell";
        // onda 9 (isles): id de ilha só vale no arquipélago, e id da grade só fora dele (nada de prédio no lugar errado)
        if (s.isles != CityLayout.isIsle(cell)) return "plan.bad_cell";
        if (CityLayout.ring(cell) > s.radius) return "plan.outside";
        if (!CityLayout.unlocked(s, cell)) return "plan.locked";
        if (gridBlocked(freeOccupied(s), cell, ignore)) return "free.blocked";
        return null;
    }

    /** 0 = bom, 1 = desnível grande, 2 = água, -1 = não carregado. {@code refY} = piso de referência (ou MIN = só o desnível do lote). */
    public static int terrainAt(ServerLevel sl, int cx, int cz, int refY) {
        int[][] pts = {{0, 0}, {-6, -6}, {6, -6}, {-6, 6}, {6, 6}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0;
        for (int[] o : pts) {
            int x = cx + o[0], z = cz + o[1];
            if (!sl.hasChunk(x >> 4, z >> 4)) return TERRAIN_UNKNOWN;
            int h = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (!sl.getFluidState(new BlockPos(x, h, z)).isEmpty()) wet++;
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        if (wet >= 2) return TERRAIN_WATER;
        if (max - min > 5) return TERRAIN_STEEP;
        if (refY != Integer.MIN_VALUE && (max - refY > 6 || refY - min > 6)) return TERRAIN_STEEP;
        return TERRAIN_OK;
    }

    // =====================================================================================
    //  Ações
    // =====================================================================================
    /** Constrói {@code key} com o centro em (dx, dz) a partir do coração e a entrada virada por {@code rot}. */
    public static boolean build(ServerPlayer sp, Settlement s, String key, String styleId, int dx, int dz, Rotation rot) {
        return CityPlanning.build(sp, s, key, cellFor(dx, dz, rot), styleId);
    }

    /** Onda 7: o mesmo, com a altura escolhida pelo jogador (−4..+4 da praça; null = automática). */
    public static boolean build(ServerPlayer sp, Settlement s, String key, String styleId, int dx, int dz, Rotation rot, @Nullable Integer offset) {
        return CityPlanning.build(sp, s, key, cellFor(dx, dz, rot), styleId, offset);
    }

    /** Moedas do cofre para mudar/girar um prédio pronto do nível {@code level}. */
    public static int moveCost(int level) {
        return 10 + 10 * Math.max(1, level);
    }

    /** Por que o prédio do lote {@code from} não pode ir para {@code to} (null = pode). Não gasta nem muda nada. */
    @Nullable
    public static Component moveProblem(Settlement s, int from, int to) {
        if (!s.own()) return SettlementsLang.tr("plan.own_only");
        Blueprint bp = s.blueprintAt(from);
        if (bp == null || from == CityLayout.CENTER_CELL) return SettlementsLang.tr("plan.empty");
        if (s.projectAt(from) != null) return SettlementsLang.tr("plan.busy");
        if (s.damaged.contains(from)) return SettlementsLang.tr("plan.repair_first");
        if (to == from) return SettlementsLang.tr("free.same");
        String pr = cellProblem(s, to, from);
        if (pr == null && !CityLayout.isFree(to) && (s.buildings.containsKey(to) || s.projectAt(to) != null)) pr = "plan.busy";
        if (pr != null) return SettlementsLang.tr(pr, CityLayout.unlockLevel(to));
        if (s.projects.size() + 2 > SettlementService.MAX_QUEUE) return SettlementsLang.tr("plan.queue_full", SettlementService.MAX_QUEUE);
        int cost = moveCost(s.levelOf(from));
        if (s.treasury < cost) return Component.translatable("settlements.isekai_mahou.treasury.poor", cost);
        return null;
    }

    /**
     * Muda (ou só gira) um prédio pronto: paga do cofre, demole o lote antigo e reergue o prédio no lugar novo com o
     * mesmo nível e estilo. Baús do prédio precisam estar vazios (a demolição derrubaria os itens).
     */
    public static boolean move(ServerPlayer sp, Settlement s, int from, int dx, int dz, Rotation rot) {
        int to = cellFor(dx, dz, rot);
        Component pr = moveProblem(s, from, to);
        if (pr != null) {
            sp.displayClientMessage(pr.copy().withColor(0xE8735E), true);
            sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
            return false;
        }
        if (!CityPlanning.checkContainers(sp, s, from)) return false;
        Blueprint bp = s.blueprintAt(from);
        int level = s.levelOf(from);
        SettlementStyle style = s.styleOf(from);
        Integer oldBase = s.plotBase.get(from);
        s.treasury -= moveCost(level);
        // a obra nova devolve a prosperidade do prédio quando terminar
        s.points = Math.max(0, s.points - bp.points());
        CityPlanning.clearCell(s, from);
        CityPlanning.queueTerrain(s, from, oldBase != null ? oldBase : s.heart.getY() - 1);

        BlockPos c = CityLayout.cellCenter(s.heart, to);
        Integer toBase = s.plotBase.get(to);
        // lugar novo encostado no antigo (ou só girou): fica na mesma altura, sem refazer o terreno
        boolean near = gap(from, CityLayout.offsetX(to), CityLayout.offsetZ(to)) <= 2 * CityTerrain.LEVEL_HALF;
        int y = toBase != null ? toBase : near && oldBase != null ? oldBase : Integer.MIN_VALUE;
        int variant = Math.floorMod(to, Blueprints.variants(bp.key()));
        Project p = new Project(bp.key(), to, List.of(new Project.Piece(bp.template(variant, level), c.getX(), c.getZ(),
                CityLayout.facingHeart(to), Project.Piece.BUILDING, y)));
        p.kind = Project.KIND_BUILD;
        p.level = level;
        p.style = style.id;
        p.clearHalf = CityTerrain.LEVEL_HALF;
        p.after = from;
        // onda 7 (city_pause): marca a reconstrução — ela só se pausa, não se cancela (mesmo depois de a demolição acabar)
        p.moved = true;
        s.projects.add(p);
        sp.displayClientMessage(SettlementsLang.tr("free.moving", bp.displayName(), moveCost(level)).withColor(s.style.color), true);
        sp.playNotifySound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    // =====================================================================================
    //  Prévias (cliente pergunta, servidor responde)
    // =====================================================================================
    /**
     * Pedido de prévia. {@code kind}: {@code found} (holograma da fundação), {@code found_open} (confirmou: abre a
     * tela de estilo/nome), {@code build} ({@code arg} = "chave|estilo") ou {@code move} ({@code arg} = id do lote).
     */
    public static void probe(ServerPlayer sp, String kind, String cityId, String arg, BlockPos pos, int rot) {
        if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > (REACH + 24) * (REACH + 24)) return;
        if ("found_open".equals(kind)) {
            // onda 7 (sky_ui): com a Ilha Celeste escolhida na prévia, quem confere é a regra do céu ({@code arg} = terreno)
            // onda 8 (water_city): com "Sobre a Água" escolhido, quem confere é a regra da Cidade das Águas
            if (!WaterCity.open(sp, pos, arg)) SkyFounding.open(sp, pos, arg);
            return;
        }
        int now = sp.server.getTickCount();
        Integer last = LAST_PROBE.put(sp.getUUID(), now);
        if (last != null && now - last >= 0 && now - last < 2) return;
        CompoundTag out = new CompoundTag();
        out.putString("Kind", kind);
        out.putInt("X", pos.getX());
        out.putInt("Y", pos.getY());
        out.putInt("Z", pos.getZ());
        out.putInt("Rot", rot);
        Rotation r = Rotation.values()[Math.floorMod(rot, 4)];
        if ("found".equals(kind)) {
            // onda 7 (sky_ui): prévia da Ilha Celeste ({@code arg} = "skyland") — a resposta traz a altitude da ilha
            // onda 8 (water_city): prévia sobre a água ({@code arg} = "water") — a resposta traz a altura do Coração e a parte de água
            if (!WaterCity.probe(sp, pos, arg, out) && !SkyFounding.probe(sp, pos, arg, out)) probeFound(sp, pos, out);
            Hints.show(sp, "city_preview");
        } else {
            Settlement s = SettlementService.city(sp.server, cityId);
            if (s == null || !s.canPlan(sp.getUUID()) || sp.level().dimension() != s.dim) return;
            probePlot(sp, s, kind, arg, pos, r, out);
            Hints.show(sp, "city_free_plot");
        }
        PacketDistributor.sendToPlayer(sp, new SettlementsNetwork.ProbeResult(out));
    }

    private static void problem(CompoundTag out, ServerPlayer sp, @Nullable Component pr) {
        out.putBoolean("Ok", pr == null);
        if (pr != null) out.putString("Problem", SettlementView.json(pr, sp.registryAccess()));
    }

    /** Fundação: o motivo que impede (ou nada) e o estado do terreno da praça e dos 24 lotes do começo. */
    private static void probeFound(ServerPlayer sp, BlockPos pos, CompoundTag out) {
        ServerLevel sl = sp.serverLevel();
        problem(out, sp, SettlementService.foundingProblem(sp, pos));
        int[] cells = new int[CityLayout.CELLS];
        for (int c = 0; c < CityLayout.CELLS; c++) {
            BlockPos cc = CityLayout.cellCenter(pos, c);
            cells[c] = terrainAt(sl, cc.getX(), cc.getZ(), pos.getY() - 1);
        }
        out.putIntArray("Cells", cells);
    }

    /** Obra em posição livre (ou mudança de um prédio): pode?, tamanho do prédio girado, altura do piso e terreno. */
    private static void probePlot(ServerPlayer sp, Settlement s, String kind, String arg, BlockPos pos, Rotation rot, CompoundTag out) {
        ServerLevel sl = sp.serverLevel();
        int dx = pos.getX() - s.heart.getX(), dz = pos.getZ() - s.heart.getZ();
        int to = cellFor(dx, dz, rot);
        Blueprint bp;
        int level = 1;
        Component pr;
        if ("move".equals(kind)) {
            int from = parse(arg, -1);
            bp = s.blueprintAt(from);
            level = s.levelOf(from);
            pr = moveProblem(s, from, to);
            out.putInt("Cost", moveCost(level));
        } else {
            String[] a = arg.split("\\|");
            SettlementStyle style = a.length > 1 ? SettlementStyle.byId(a[1]) : null;
            bp = Blueprints.get(style == null ? s.style : style, a[0]);
            pr = bp == null ? SettlementsLang.tr("plan.no_style") : buildProblem(s, bp, to);
        }
        problem(out, sp, pr);
        if (bp != null) {
            Templates.Tpl tpl = Templates.get(sl, bp.template(Math.floorMod(to, Blueprints.variants(bp.key())), level));
            if (tpl != null) {
                out.putInt("W", tpl.width(rot));
                out.putInt("D", tpl.depth(rot));
                out.putInt("H", tpl.sy());
            }
        }
        Integer base = s.plotBase.get(to);
        if (base == null && Math.abs(dx) <= CityLayout.half(s) && Math.abs(dz) <= CityLayout.half(s)) base = CityTerrain.streetLevel(sl, s, to);
        // onda 7: altura escolhida pelo jogador no painel (3º campo do pedido de prédio novo) — a prévia mostra nela
        if ("build".equals(kind)) {
            String[] a = arg.split("\\|");
            int off = a.length > 2 ? parse(a[2], Integer.MIN_VALUE) : Integer.MIN_VALUE;
            if (off != Integer.MIN_VALUE) {
                base = s.heart.getY() - 1 + WaterCity.offset(s, Math.max(-CityPlanning.MAX_TERRAIN_OFFSET, Math.min(CityPlanning.MAX_TERRAIN_OFFSET, off)));
            }
        }
        if (base != null) out.putInt("Base", base);
        // onda 8 (water_city): na Cidade das Águas, água debaixo do lote não é aviso — é o normal
        out.putInt("Terrain", s.water ? TERRAIN_OK : terrainAt(sl, pos.getX(), pos.getZ(), Integer.MIN_VALUE));
    }

    /** As mesmas checagens de {@link CityPlanning#build}, sem pôr nada na fila (para a prévia ficar verde/vermelha). */
    @Nullable
    private static Component buildProblem(Settlement s, Blueprint bp, int cell) {
        if (!s.own()) return SettlementsLang.tr("plan.own_only");
        if (!s.has(Blueprint.PLAZA)) return SettlementsLang.tr("bp.need_plaza");
        if (!bp.usesCell()) return SettlementsLang.tr("plan.bad_cell");
        String pr = cellProblem(s, cell, cell);
        if (pr == null && (s.buildings.containsKey(cell) || s.projectAt(cell) != null)) pr = "plan.busy";
        if (pr != null) return SettlementsLang.tr(pr, CityLayout.unlockLevel(cell));
        if (bp.minLevel() > s.level) return SettlementsLang.tr("bp.need_level", bp.minLevel());
        if (s.count(bp.key()) + s.inProgress(bp.key()) >= CityGrowth.maxCount(s, bp)) return SettlementsLang.tr("bp.maxed");
        if (s.projects.size() >= SettlementService.MAX_QUEUE) return SettlementsLang.tr("plan.queue_full", SettlementService.MAX_QUEUE);
        return null;
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static void forget(UUID player) {
        LAST_PROBE.remove(player);
    }

    public static void clear() {
        LAST_PROBE.clear();
    }

    private CityPlots() {}
}
