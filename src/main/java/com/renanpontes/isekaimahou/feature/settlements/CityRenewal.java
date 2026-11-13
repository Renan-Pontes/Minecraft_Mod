package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.settlements.build.Templates;
import com.renanpontes.isekaimahou.feature.settlements.build.TierLook;
import com.renanpontes.isekaimahou.feature.settlements.build.TierWalls;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;

/**
 * REFORMA DO DEGRAU (onda 5): "subir de nível tem que ser um acontecimento visível na cidade INTEIRA". Quando o degrau
 * da cidade muda (ou num mundo antigo, que nunca passou por aqui), os construtores percorrem a cidade lote a lote, da
 * praça para fora, e deixam tudo com a cara do degrau ({@link TierLook}):
 * <ul>
 *   <li><b>ruas</b> — a borda de cada lote e a calçada da porta são repavimentadas (só troca o que JÁ é piso de rua);</li>
 *   <li><b>postes</b> — os dois da frente de cada prédio viram o poste do degrau; do Burgo em diante entram postes
 *       nas esquinas;</li>
 *   <li><b>fachadas e jardins</b> — flores/sebes sob as janelas, estandartes ao lado da porta, capacho, canteiros;</li>
 *   <li><b>praça</b> — meio-fio novo, o Monumento do Coração (4 pilares que crescem a cada degrau) e, do Burgo em
 *       diante, fontes no lugar dos canteiros das diagonais;</li>
 *   <li><b>muralha</b> — cada muralha pronta entra na fila de obras para ser REFORMADA para o grau do degrau, uma por
 *       vez ({@code Project.KIND_TIER}; peças em {@code TierWalls}).</li>
 * </ul>
 * <b>Nunca apaga o que o jogador pôs</b>: enfeite só entra em bloco de ar; cada um fica anotado
 * ({@link Settlement#tierBlocks}) e na troca de degrau só sai o que está anotado e ainda é enfeite nosso; poste só é
 * trocado se ainda for um poste; a fonte só tira do canteiro o que ainda for o bloco do molde da praça.
 * <p><b>Aos poucos</b>: um lote a cada 0,5–2 s (mais construtores = mais rápido), só com o coração carregado, tirando
 * do MESMO orçamento de blocos por tick das obras ({@link ConstructionEngine#claim}). Construtores sem obra vão até o
 * lote da vez. Lotes em chunks descarregados ficam para a próxima volta.
 */
public final class CityRenewal {
    /** Versão do visual de degrau que esta classe aplica (onda 8 = 2: desenho das ruas, mobília e praça por degrau). */
    public static final int LOOK_GEN = 2;
    /** Blocos reservados do orçamento do tick para reformar um lote. */
    private static final int SITE_BUDGET = 220;

    public static boolean active(Settlement s) {
        return s.own() && s.lookGoal > 0;
    }

    /** Recomeça a reforma da cidade inteira (mudou de degrau; comando). */
    public static void restart(Settlement s) {
        s.look = 0;
        s.lookGoal = 0;
        s.lookCursor = 0;
        s.lookWait = 0;
    }

    /** Lote que os construtores estão reformando agora (para onde os desocupados andam), ou null. */
    @Nullable
    public static BlockPos site(Settlement s) {
        if (!active(s)) return null;
        List<Integer> sites = sites(s);
        if (s.lookCursor >= sites.size()) return null;
        int cell = sites.get(s.lookCursor);
        BlockPos c = cell == CityLayout.CENTER_CELL ? s.heart : CityLayout.cellCenter(s.heart, cell);
        Integer y = CityTerrain.levelOf(s, cell);
        Direction front = (cell == CityLayout.CENTER_CELL ? Rotation.NONE : CityLayout.facingHeart(cell)).rotate(Direction.SOUTH);
        return new BlockPos(c.getX() + 7 * front.getStepX(), (y == null ? s.heart.getY() - 1 : y) + 1, c.getZ() + 7 * front.getStepZ());
    }

    /** Andamento da reforma em % (-1 = não há reforma). */
    public static int percent(Settlement s) {
        if (!active(s)) return -1;
        return Math.min(99, s.lookCursor * 100 / Math.max(1, sites(s).size()));
    }

    /** Praça primeiro; depois todo lote com prédio ou aplainado, do mais perto para o mais longe. */
    private static List<Integer> sites(Settlement s) {
        Set<Integer> set = new LinkedHashSet<>();
        for (int c : s.buildings.keySet()) if (c >= 0 && c != CityLayout.CENTER_CELL) set.add(c);
        for (int c : s.plotBase.keySet()) if (c >= 0 && c != CityLayout.CENTER_CELL) set.add(c);
        List<Integer> out = new ArrayList<>(set);
        out.sort(Comparator.comparingInt((Integer c) -> {
            int x = CityLayout.offsetX(c), z = CityLayout.offsetZ(c);
            return x * x + z * z;
        }).thenComparingInt(c -> c));
        out.add(0, CityLayout.CENTER_CELL);
        return out;
    }

    // =====================================================================================
    //  Tick (chamado pelo coração da cidade a cada 5 ticks)
    // =====================================================================================
    public static void tick(ServerLevel sl, Settlement s, int age) {
        if (!s.own() || !s.has(Blueprint.PLAZA)) return;
        int tier = TierLook.clamp(s.level);
        // onda 8 (city_looks): cidade reformada antes do visual novo dos degraus passa pela reforma mais uma vez
        if (s.lookGen < LOOK_GEN) {
            s.lookGen = LOOK_GEN;
            if (s.lookGoal == 0) s.look = 0;
        }
        if (s.lookGoal == 0) {
            if (s.look == tier) {
                // cidade em dia: só confere, de vez em quando, se sobrou muralha para reformar
                if (age % 200 == 7) queueWall(s, tier);
                return;
            }
        }
        // onda 8: durante a reforma da cidade a muralha também é conferida (antes só no começo: se houvesse outra muralha
        // em obra naquela hora, a troca do muro ficava esquecida até a reforma inteira acabar)
        if (s.lookGoal != 0 && age % 100 == 7) queueWall(s, tier);
        if (s.lookGoal == 0) {
            s.lookGoal = tier;
            s.lookCursor = 0;
            s.lookWait = 0;
            s.lookSkipped = 0;
            queueWall(s, tier);
        } else if (s.lookGoal != tier) {
            s.lookGoal = tier;
            s.lookCursor = 0;
            s.lookSkipped = 0;
        }
        if (s.lookWait > 0) {
            s.lookWait -= 5;
            return;
        }
        List<Integer> sites = sites(s);
        if (s.lookCursor >= sites.size()) {
            finishPass(sl, s);
            return;
        }
        int cell = sites.get(s.lookCursor);
        BlockPos c = cell == CityLayout.CENTER_CELL ? s.heart : CityLayout.cellCenter(s.heart, cell);
        if (!loaded(sl, c)) {
            s.lookSkipped++;
            s.lookCursor++;
            return;
        }
        if (ConstructionEngine.claim(sl, SITE_BUDGET) < SITE_BUDGET) return;
        renew(sl, s, cell, tier);
        s.lookCursor++;
        s.lookWait = Math.max(10, 40 - 2 * s.population(Settlement.Role.BUILDER));
    }

    private static void finishPass(ServerLevel sl, Settlement s) {
        if (s.lookSkipped > 0) {
            // ficou lote para trás (chunk descarregado): outra volta daqui a pouco — o que já foi feito não muda de novo
            s.lookSkipped = 0;
            s.lookCursor = 0;
            s.lookWait = 600;
            return;
        }
        boolean first = s.look == 0 && s.tierDay < 0;
        s.look = s.lookGoal;
        s.lookGoal = 0;
        s.lookCursor = 0;
        // onda 8 (city_looks): cidade com prédios erguidos antes do visual novo — dica única de como refazê-los
        if (CityLooks.pending(s) > 0) {
            for (ServerPlayer g : CityEconomy.governors(sl.getServer(), s)) {
                com.renanpontes.isekaimahou.feature.ui_hud.Hints.show(g, "city_looks");
            }
        }
        // mundo antigo ganhando o visual pela primeira vez: sem aviso (nada "aconteceu" para o jogador)
        if (first) return;
        for (ServerPlayer g : CityEconomy.governors(sl.getServer(), s)) {
            g.displayClientMessage(SettlementsLang.tr("rank.renew.done", SettlementService.cityName(sl.getServer(), s), CityTier.name(s.look))
                    .withColor(s.style.color), false);
        }
    }

    private static boolean loaded(ServerLevel sl, BlockPos c) {
        return sl.hasChunk((c.getX() - 9) >> 4, (c.getZ() - 9) >> 4) && sl.hasChunk((c.getX() + 9) >> 4, (c.getZ() - 9) >> 4)
                && sl.hasChunk((c.getX() - 9) >> 4, (c.getZ() + 9) >> 4) && sl.hasChunk((c.getX() + 9) >> 4, (c.getZ() + 9) >> 4);
    }

    /** Uma obra vai limpar esta área: os enfeites do degrau dali deixam de ser nossos (a limpeza leva os blocos). */
    public static void forgetArea(Settlement s, int x0, int z0, int x1, int z1) {
        if (s.tierBlocks.isEmpty()) return;
        s.tierBlocks.removeIf(p -> p.getX() >= x0 && p.getX() <= x1 && p.getZ() >= z0 && p.getZ() <= z1);
    }

    /** Prédio novo, melhorado ou consertado: o lote já sai com a rua, os postes e a fachada do degrau de hoje. */
    public static void onBuilt(ServerLevel sl, Settlement s, int cell) {
        if (!s.own() || cell < 0 || cell == CityLayout.CENTER_CELL) return;
        if (!loaded(sl, CityLayout.cellCenter(s.heart, cell))) return;
        renew(sl, s, cell, TierLook.clamp(s.level));
    }

    /** Reforma a cidade inteira AGORA (comando de teste). Devolve quantos lotes passaram. */
    public static int runNow(ServerLevel sl, Settlement s) {
        restart(s);
        s.lookGen = LOOK_GEN;
        int tier = TierLook.clamp(s.level), n = 0;
        for (int cell : sites(s)) {
            BlockPos c = cell == CityLayout.CENTER_CELL ? s.heart : CityLayout.cellCenter(s.heart, cell);
            if (!loaded(sl, c)) continue;
            renew(sl, s, cell, tier);
            n++;
        }
        s.look = tier;
        queueWall(s, tier);
        return n;
    }

    // =====================================================================================
    //  Muralhas: uma reforma por vez, na fila de obras
    // =====================================================================================
    /** Grau e estilo com que a muralha k foi construída ("estilo:grau"; muralha antiga = moldes .nbt, grau 0). */
    public static int wallTierOf(Settlement s, int k) {
        String v = s.wallLooks.get(k);
        if (v == null) return 0;
        try {
            return Integer.parseInt(v.substring(v.indexOf(':') + 1));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static SettlementStyle wallStyleOf(Settlement s, int k) {
        String v = s.wallLooks.get(k);
        SettlementStyle st = v == null ? null : SettlementStyle.byId(v.substring(0, Math.max(0, v.indexOf(':'))));
        return st == null ? s.style : st;
    }

    /**
     * Onda 8 (city_looks): com que visual a muralha desta obra ficou — "estilo:grau" se todas as peças saíram iguais,
     * senão "mixed:0" (a cidade mudou de estilo/degrau com a muralha em obra: a reforma confere peça por peça).
     */
    public static String wallLookOf(Settlement s, Project p) {
        SettlementStyle pst = SettlementStyle.byId(p.style);
        String first = null;
        for (Project.Piece piece : p.pieces) {
            SettlementStyle st = TierWalls.styleOf(piece.template);
            String v = (st != null ? st : pst != null ? pst : s.style).id + ":" + TierWalls.tierOf(piece.template);
            if (first == null) first = v;
            else if (!first.equals(v)) return "mixed:0";
        }
        return first != null ? first : s.style.id + ":0";
    }

    /**
     * Onda 8 (city_looks): muralha AINDA EM OBRA quando a cidade muda de estilo ou de degrau — as peças que faltam já
     * saem no estilo/grau novo (as já erguidas entram na troca quando a obra terminar: veja {@link #wallLookOf}).
     */
    static void retarget(Settlement s, int tier) {
        int want = TierLook.wallTier(tier);
        for (Project p : s.projects) {
            if (!Blueprint.WALL.equals(p.key) || !Project.KIND_BUILD.equals(p.kind) || p.undo) continue;
            boolean changed = false;
            for (int i = p.pieceIdx; i < p.pieces.size(); i++) {
                Project.Piece piece = p.pieces.get(i);
                // a peça da vez só muda se os construtores ainda não puseram a mão nela
                if (i == p.pieceIdx && (p.phase != Project.PHASE_CLEAR || p.cursor > 0)) continue;
                String name = piece.kind == Project.Piece.TOWER ? TierWalls.TOWER : piece.kind == Project.Piece.GATE ? TierWalls.GATE : TierWalls.SEGMENT;
                net.minecraft.resources.ResourceLocation id = TierWalls.id(s.style, want, name);
                if (piece.kind == Project.Piece.BUILDING || id.equals(piece.template)) continue;
                try {
                    // a altura já medida vale para a peça nova (mesma planta, mesmo lugar)
                    p.pieces.set(i, new Project.Piece(id, piece.cx, piece.cz, piece.rotation, piece.kind, piece.baseY));
                    changed = true;
                } catch (UnsupportedOperationException e) {
                    return;
                }
            }
            if (changed) p.style = s.style.id;
        }
    }

    /** Põe na fila a reforma da primeira muralha que não está no grau/estilo de hoje (se nenhuma muralha está em obra). */
    static void queueWall(Settlement s, int tier) {
        retarget(s, tier);
        if (s.walls.isEmpty()) return;
        for (Project p : s.projects) if (Blueprint.WALL.equals(p.key)) return;
        int want = TierLook.wallTier(tier);
        for (int k : s.walls) {
            int have = wallTierOf(s, k);
            SettlementStyle old = wallStyleOf(s, k);
            if (have == want && old == s.style) continue;
            Project p = new Project(Blueprint.WALL, CityLayout.wallCell(k), CityLayout.wallSwapPieces(s, old, have, s.style, want, k));
            p.kind = Project.KIND_TIER;
            p.level = want;
            p.style = s.style.id;
            p.phase = Project.PHASE_BUILD;
            // onda 8: a reforma da muralha entra logo depois das obras que já estão andando (antes ia para o fim da fila
            // e, numa cidade com muita obra, o muro ficava no estilo velho por dias)
            s.projects.add(Math.min(s.projects.size(), Math.max(1, s.population(Settlement.Role.BUILDER))), p);
            return;
        }
    }

    // =====================================================================================
    //  Um lote
    // =====================================================================================
    private static void renew(ServerLevel sl, Settlement s, int cell, int tier) {
        boolean plaza = cell == CityLayout.CENTER_CELL;
        BlockPos c = plaza ? s.heart : CityLayout.cellCenter(s.heart, cell);
        Integer base = CityTerrain.levelOf(s, cell);
        if (base == null) return;
        clearFurniture(sl, s, c.getX() - 8, c.getZ() - 8, c.getX() + 8, c.getZ() + 8);
        Rotation rot = plaza ? s.plazaRotation() : CityLayout.facingHeart(cell);
        Templates.Tpl tpl = template(sl, s, cell);
        int hw = tpl == null ? 0 : tpl.width(rot) / 2, hd = tpl == null ? 0 : tpl.depth(rot) / 2;
        streets(sl, s, c, base, tier, plaza, rot, hw, hd);
        if (plaza) {
            plaza(sl, s, base, tier, tpl);
            return;
        }
        BlockPos origin = tpl == null ? null : new BlockPos(c.getX() - tpl.width(rot) / 2, base, c.getZ() - tpl.depth(rot) / 2);
        BlockState[] lamp = TierLook.lamp(s.style, tier);
        if (tpl != null && tpl.sx() <= 13 && tpl.sz() <= 13) {
            for (int x : new int[] {-1, tpl.sx()}) lamp(sl, c, origin.offset(tpl.rotate(x, 1, tpl.sz(), rot)), lamp);
        }
        if (tier >= 3) {
            lamp(sl, c, new BlockPos(c.getX() + 7, base + 1, c.getZ() + 7), lamp);
            lamp(sl, c, new BlockPos(c.getX() - 7, base + 1, c.getZ() - 7), lamp);
        }
        if (tier >= 4) {
            lamp(sl, c, new BlockPos(c.getX() + 7, base + 1, c.getZ() - 7), lamp);
            lamp(sl, c, new BlockPos(c.getX() - 7, base + 1, c.getZ() + 7), lamp);
        }
        TierLook.Look look = TierLook.of(s.styleOf(cell));
        if (tpl != null && tier >= 2) facade(sl, s, tpl, origin, rot, tier, look);
        if (tier >= 3) garden(sl, s, c, base, tier, look, hw, hd);
        // onda 8 (city_looks): mobília da rua do degrau (barris e carroças → bancos e canteiros → avenida arborizada)
        CityScenery.lot(sl, s, cell, c, base, tier);
    }

    /** Molde do prédio do lote (para saber a planta, a porta e as janelas), ou null em lote vazio. */
    @Nullable
    private static Templates.Tpl template(ServerLevel sl, Settlement s, int cell) {
        if (cell == CityLayout.CENTER_CELL) return Templates.get(sl, Blueprint.template(s.style, Blueprint.PLAZA));
        Blueprint bp = s.blueprintAt(cell);
        if (bp == null) return null;
        return Templates.get(sl, CityLooks.standing(s, cell, bp));
    }

    static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /** Põe um enfeite anotado (sai sozinho na próxima troca de degrau). Só em ar/mato. */
    static boolean put(ServerLevel sl, Settlement s, BlockPos pos, BlockState st) {
        if (pos.equals(s.heart) || !CityTerrain.free(sl.getBlockState(pos), true)) return false;
        sl.setBlock(pos, st, 2 | 16);
        s.tierBlocks.add(pos.immutable());
        return true;
    }

    static boolean firm(ServerLevel sl, BlockPos below) {
        BlockState st = sl.getBlockState(below);
        return !st.isAir() && (st.isFaceSturdy(sl, below, Direction.UP) || st.is(Blocks.DIRT_PATH));
    }

    /** Tira os enfeites anotados da área (os de cima primeiro). Só sai o que ainda é enfeite nosso. */
    private static void clearFurniture(ServerLevel sl, Settlement s, int x0, int z0, int x1, int z1) {
        for (int i = s.tierBlocks.size() - 1; i >= 0; i--) {
            BlockPos p = s.tierBlocks.get(i);
            if (p.getX() < x0 || p.getX() > x1 || p.getZ() < z0 || p.getZ() > z1) continue;
            if (TierLook.isFurniture(sl.getBlockState(p))) sl.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
            s.tierBlocks.remove(i);
        }
    }

    // ---------------------------------------------------------------- ruas
    private static void streets(ServerLevel sl, Settlement s, BlockPos c, int base, int tier, boolean plaza, Rotation rot, int hw, int hd) {
        Direction front = rot.rotate(Direction.SOUTH);
        TierLook.Look look = TierLook.of(s.style);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring < 7) {
                    // dentro do lote só a calçada da porta é rua (a praça inteira é do molde dela)
                    if (plaza || Math.abs(dx) <= hw && Math.abs(dz) <= hd) continue;
                    int along = dx * front.getStepX() + dz * front.getStepZ();
                    int side = dx * front.getStepZ() - dz * front.getStepX();
                    if (along <= 0 || Math.abs(side) > 1) continue;
                }
                int x = c.getX() + dx, z = c.getZ() + dz;
                if (CityLayout.inWallBand(s, x - s.heart.getX(), z - s.heart.getZ())) continue;
                BlockState cur = sl.getBlockState(m.set(x, base, z));
                // onda 8 (water_city): na Cidade das Águas a "rua" é passarela — a reforma troca as tábuas pelo piso do degrau
                if (!TierLook.isStreet(cur) && !(s.water && WaterCity.isDeck(cur))) continue;
                BlockState want = plaza && ring == 7 ? look.plazaEdge[tier] : s.water ? WaterCity.walk(s.style, tier, hash(x, z))
                        // onda 8 (city_looks): o DESENHO da rua muda com o degrau (trilha → meio-fio → calçada → avenida → mosaico)
                        : TierLook.pave(s.style, tier, ring, dx, dz, hash(x, z));
                // caminho de terra vira terra comum debaixo de bloco sólido (poste): usa terra batida ali
                if (want.is(Blocks.DIRT_PATH) && sl.getBlockState(m.setY(base + 1)).isSolid()) want = Blocks.COARSE_DIRT.defaultBlockState();
                if (!cur.is(want.getBlock())) sl.setBlock(m.setY(base), want, 2 | 16);
            }
        }
    }

    // ---------------------------------------------------------------- postes
    /**
     * Troca (ou põe) o poste do degrau em {@code at} (primeiro bloco acima do chão). Só mexe se ali estiver vazio ou se
     * a coluna inteira ainda for um poste (peças de poste com uma luz no topo) — qualquer outra coisa fica.
     */
    private static void lamp(ServerLevel sl, BlockPos lot, BlockPos at, BlockState[] col) {
        if (Math.abs(at.getX() - lot.getX()) > 7 || Math.abs(at.getZ() - lot.getZ()) > 7) return;
        int n = 0;
        while (n < 7 && TierLook.isLampPart(sl.getBlockState(at.above(n)))) n++;
        if (n > 0 && !TierLook.isLampLight(sl.getBlockState(at.above(n - 1)))) return;
        if (n == 0 && !firm(sl, at.below())) return;
        for (int i = n; i < col.length; i++) if (!CityTerrain.free(sl.getBlockState(at.above(i)), i == 0)) return;
        for (int i = 0; i < Math.max(n, col.length); i++) {
            BlockState want = i < col.length ? col[i] : Blocks.AIR.defaultBlockState();
            if (sl.getBlockState(at.above(i)) != want) sl.setBlock(at.above(i), want, 2 | 16);
        }
    }

    // ---------------------------------------------------------------- fachadas
    private static boolean window(BlockState st) {
        return st.is(Blocks.GLASS_PANE) || st.is(Blocks.GLASS) || st.getBlock().getDescriptionId().contains("stained_glass");
    }

    /**
     * Acabamento da fachada: sob as janelas do térreo, flores (Vila) ou sebe (Burgo+, com vaso em cima na Cidade+); ao
     * lado de cada porta de fora, estandartes do estilo (Burgo+) e um capacho (Cidade+). Confere no mundo se a janela e
     * a porta do molde ainda estão lá — prédio mexido pelo jogador não ganha enfeite fora de lugar.
     */
    private static void facade(ServerLevel sl, Settlement s, Templates.Tpl tpl, BlockPos origin, Rotation rot, int tier, TierLook.Look look) {
        int sx = tpl.sx(), sz = tpl.sz();
        List<BlockPos> doors = new ArrayList<>();
        Set<Long> windows = new LinkedHashSet<>();
        for (Templates.Entry e : tpl.entries()) {
            BlockPos p = e.pos();
            if (p.getX() != 0 && p.getZ() != 0 && p.getX() != sx - 1 && p.getZ() != sz - 1) continue;
            BlockState st = e.state();
            if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && p.getY() <= 2) doors.add(p);
            else if (p.getY() == 2 && window(st)) windows.add(BlockPos.asLong(p.getX(), 0, p.getZ()));
        }
        int done = 0;
        for (long w : windows) {
            if (done >= 12) break;
            int x = BlockPos.getX(w), z = BlockPos.getZ(w);
            int ox = x == 0 ? -1 : x == sx - 1 ? 1 : 0, oz = ox != 0 ? 0 : z == 0 ? -1 : 1;
            boolean nearDoor = false;
            for (BlockPos d : doors) nearDoor |= Math.abs(d.getX() - x) + Math.abs(d.getZ() - z) <= 1;
            if (nearDoor || !window(sl.getBlockState(origin.offset(tpl.rotate(x, 2, z, rot))))) continue;
            BlockPos at = origin.offset(tpl.rotate(x + ox, 1, z + oz, rot));
            if (tier == 2 || look.hedge == null) {
                BlockState flower = look.flowers[Math.floorMod(hash(at.getX(), at.getZ()), look.flowers.length)];
                if (flower.canSurvive(sl, at) && put(sl, s, at, flower)) done++;
            } else if (firm(sl, at.below()) && put(sl, s, at, look.hedge)) {
                done++;
                if (tier >= 4 && done % 2 == 0 && look.hedge.isCollisionShapeFullBlock(sl, at)) {
                    put(sl, s, at.above(), look.pots[Math.floorMod(hash(at.getX(), at.getZ()), look.pots.length)]);
                }
            }
        }
        if (tier < 3) return;
        for (BlockPos d : doors) {
            int x = d.getX(), y = d.getY(), z = d.getZ();
            int ox = z == sz - 1 ? 0 : z == 0 ? 0 : x == 0 ? -1 : 1, oz = z == sz - 1 ? 1 : z == 0 ? -1 : 0;
            if (!(sl.getBlockState(origin.offset(tpl.rotate(x, y, z, rot))).getBlock() instanceof DoorBlock)) continue;
            BlockPos out = origin.offset(tpl.rotate(x + ox, y, z + oz, rot));
            BlockPos doorPos = origin.offset(tpl.rotate(x, y, z, rot));
            Direction face = Direction.fromDelta(out.getX() - doorPos.getX(), 0, out.getZ() - doorPos.getZ());
            if (face == null) continue;
            for (int side = -1; side <= 1; side += 2) {
                // parede ao lado da porta, 2 blocos acima da soleira: o estandarte fica pendurado do lado de fora
                BlockPos wall = origin.offset(tpl.rotate(x + side * Math.abs(oz), y + 2, z + side * Math.abs(ox), rot));
                BlockPos banner = wall.relative(face);
                if (!sl.getBlockState(wall).isFaceSturdy(sl, wall, face) || !sl.getBlockState(banner).isAir()
                        || !sl.getBlockState(banner.below()).isAir()) {
                    continue;
                }
                put(sl, s, banner, look.wallBanner.defaultBlockState().setValue(WallBannerBlock.FACING, face));
            }
            if (tier >= 4 && firm(sl, out.below()) && sl.getBlockState(out).isAir()) put(sl, s, out, look.carpet.defaultBlockState());
        }
    }

    // ---------------------------------------------------------------- jardins
    /** Canteiros nos 4 cantos do quintal (fora do prédio): flores no Burgo, arbusto na Cidade, arbusto com vaso na Capital. */
    private static void garden(ServerLevel sl, Settlement s, BlockPos c, int base, int tier, TierLook.Look look, int hw, int hd) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                if (5 <= hw && 5 <= hd) continue;
                BlockPos at = new BlockPos(c.getX() + 5 * sx, base + 1, c.getZ() + 5 * sz);
                BlockState soil = sl.getBlockState(at.below());
                if (!soil.is(Blocks.GRASS_BLOCK) && !soil.is(Blocks.SAND) && !soil.is(Blocks.DIRT)) continue;
                int h = hash(at.getX(), at.getZ());
                if (tier == 3 || look.hedge == null || !look.hedge.isCollisionShapeFullBlock(sl, at)) {
                    BlockState flower = look.flowers[Math.floorMod(h, look.flowers.length)];
                    if (flower.canSurvive(sl, at)) put(sl, s, at, flower);
                    else put(sl, s, at, look.pots[Math.floorMod(h, look.pots.length)]);
                } else if (put(sl, s, at, look.hedge) && tier >= 5) {
                    put(sl, s, at.above(), look.pots[Math.floorMod(h, look.pots.length)]);
                }
            }
        }
    }

    // ---------------------------------------------------------------- praça
    /**
     * A praça do degrau: o MONUMENTO DO CORAÇÃO — 4 pilares a 2 blocos do coração, mais altos a cada degrau, com luz
     * no topo e estandartes na Cidade/Capital — e, do Burgo em diante, FONTES no lugar dos canteiros das diagonais.
     */
    private static void plaza(ServerLevel sl, Settlement s, int base, int tier, @Nullable Templates.Tpl tpl) {
        TierLook.Look look = TierLook.of(s.style);
        int h = TierLook.pillarHeight(tier);
        if (h > 0) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    BlockPos foot = new BlockPos(s.heart.getX() + 2 * sx, base + 1, s.heart.getZ() + 2 * sz);
                    if (!firm(sl, foot.below())) continue;
                    boolean free = true;
                    for (int i = 0; i <= h; i++) free &= CityTerrain.free(sl.getBlockState(foot.above(i)), i == 0);
                    if (!free) continue;
                    for (int i = 0; i < h; i++) put(sl, s, foot.above(i), i == h - 1 ? look.cap[tier] : look.pillar[tier]);
                    put(sl, s, foot.above(h), look.light[tier]);
                    if (tier >= 4) {
                        Direction out = sx > 0 ? Direction.EAST : Direction.WEST;
                        BlockPos b = foot.above(h - 2).relative(out);
                        if (sl.getBlockState(b).isAir() && sl.getBlockState(b.below()).isAir()) {
                            put(sl, s, b, look.wallBanner.defaultBlockState().setValue(WallBannerBlock.FACING, out));
                        }
                    }
                }
            }
        }
        // onda 8 (city_looks): a praça de cada degrau (fogueira e barracas → feira e poço → estátua e arcadas → coroa e mosaico)
        CityScenery.plaza(sl, s, base, tier, tpl);
        if (tier < 3 || tpl == null || look.fountainRim == null || look.fountainPost == null) return;
        // 1) canteiros das diagonais: sai só o que ainda é o bloco do molde da praça (o resto é do jogador e fica)
        Rotation rot = s.plazaRotation();
        BlockPos origin = new BlockPos(s.heart.getX() - tpl.width(rot) / 2, base, s.heart.getZ() - tpl.depth(rot) / 2);
        for (int i = tpl.entries().size() - 1; i >= 0; i--) {
            Templates.Entry e = tpl.entries().get(i);
            if (!Templates.plazaCorner(tpl, e)) continue;
            BlockPos p = origin.offset(tpl.rotate(e.pos().getX(), e.pos().getY(), e.pos().getZ(), rot));
            if (sl.getBlockState(p).is(e.state().getBlock())) sl.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        // 2) fontes: 4 degraus em volta e a coluna do meio com água (a água só entra se a borda fechou os 4 lados)
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                BlockPos mid = new BlockPos(s.heart.getX() + 3 * sx, base + 1, s.heart.getZ() + 3 * sz);
                if (!firm(sl, mid.below())) continue;
                boolean free = true;
                for (int i = 0; i < 3; i++) free &= CityTerrain.free(sl.getBlockState(mid.above(i)), i == 0);
                if (!free) continue;
                boolean closed = true;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos rim = mid.relative(d);
                    if (firm(sl, rim.below())) put(sl, s, rim, look.fountainRim.defaultBlockState().setValue(StairBlock.FACING, d));
                    BlockState now = sl.getBlockState(rim);
                    closed &= !now.isAir() && !now.canBeReplaced(Fluids.WATER) && !now.canBeReplaced();
                }
                BlockState post = look.fountainPost.defaultBlockState();
                BlockState wet = closed && post.hasProperty(BlockStateProperties.WATERLOGGED) ? post.setValue(BlockStateProperties.WATERLOGGED, true) : post;
                put(sl, s, mid, wet);
                put(sl, s, mid.above(), post);
                put(sl, s, mid.above(2), look.light[tier]);
            }
        }
    }

    private CityRenewal() {}
}
