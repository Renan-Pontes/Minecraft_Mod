package com.renanpontes.isekaimahou.feature.settlements.build;

import java.util.Arrays;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Terreno da cidade (v1.0, pedido do dono: "tente aplainar as coisas, se realmente precisar, colocamos escadas").
 * <ul>
 *   <li><b>Nível da rua</b>: um lote novo fica na MESMA altura do vizinho de dentro (o que fica entre ele e a praça)
 *       sempre que o terreno natural estiver a até {@link #FLATTEN} blocos disso — a cidade inteira sai plana em
 *       terreno suave. Só em encosta de verdade o lote vira um patamar de {@link #MAX_STEP} blocos.</li>
 *   <li><b>Lote inteiro</b>: a obra corta/aterra o lote todo e a linha de rua em volta ({@link #LEVEL_HALF}), com rua
 *       contínua na borda (caminho; calçamento a partir de Cidade) e quintal no resto.</li>
 *   <li><b>Saia</b>: fora do lote o terreno natural é suavizado em degraus de 1 bloco a cada 2 ({@link #SKIRT}), sem
 *       nunca tocar em lote pronto, muralha ou construção.</li>
 *   <li><b>Escada</b>: só entre um lote e o vizinho de dentro, e só com desnível &gt; 1. A posição é sempre a mesma
 *       (no eixo da porta, na linha de rua), então refazer a obra nunca duplica a escada.</li>
 * </ul>
 * Lotes prontos de mundos antigos não mudam de altura: quem manda é o {@code plotBase} salvo.
 */
public final class CityTerrain {
    /** Meia-largura aplainada por uma obra nova: o lote (15×15) + a linha de rua de 1 bloco em volta. */
    public static final int LEVEL_HALF = 8;
    /** Largura da saia de transição em volta do lote. */
    public static final int SKIRT = 6;
    /** Até este desnível o lote é simplesmente igualado ao vizinho de dentro. */
    public static final int FLATTEN = 3;
    /** Altura de um patamar quando a encosta não deixa aplainar. */
    public static final int MAX_STEP = 2;
    /** Desnível máximo que uma escada vence (acima disso é paredão: fica muro de arrimo). */
    private static final int MAX_STAIR = 6;

    // =====================================================================================
    //  Blocos
    // =====================================================================================
    /** Terreno, mato ou piso de rua: pode ser cortado/aterrado sem estragar construção nenhuma. */
    public static boolean ground(BlockState st, BlockState foundation) {
        return st.isAir() || st.canBeReplaced() || !st.getFluidState().isEmpty() || st.is(foundation.getBlock())
                || st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.LEAVES)
                || st.is(Blocks.GRAVEL) || st.is(Blocks.DIRT_PATH) || st.is(BlockTags.SNOW) || st.is(Blocks.SANDSTONE)
                || st.is(Blocks.COBBLESTONE) || st.is(Blocks.MOSSY_COBBLESTONE) || st.is(Blocks.STONE_BRICKS)
                || st.is(Blocks.SMOOTH_SANDSTONE) || st.is(Blocks.CUT_SANDSTONE) || st.is(Blocks.CLAY)
                || st.is(BlockTags.TERRACOTTA) || st.is(Blocks.ICE) || st.is(Blocks.PACKED_ICE);
    }

    /** Piso das ruas: caminho de terra nas aldeias, calçamento do estilo a partir do nível 3 do centro. */
    public static BlockState street(Settlement s, int x, int z) {
        // onda 5: a rua muda a cada degrau (terra → cascalho → pedra → calçamento → pavimento nobre): paleta em TierLook
        // onda 8 (water_city): na Cidade das Águas a rua é passarela (tábuas; calçamento do Burgo em diante)
        if (s.water) return com.renanpontes.isekaimahou.feature.settlements.WaterCity.walk(s.style, s.level, hash(x, z));
        return TierLook.street(s.style, s.level, hash(x, z));
    }

    /** Chão do quintal (o que sobra do lote em volta do prédio). */
    public static BlockState yard(Settlement s) {
        // onda 8 (water_city): o "quintal" de um lote sobre a água é o deque do estilo
        if (s.water) return com.renanpontes.isekaimahou.feature.settlements.WaterCity.deck(s);
        return (s.style == SettlementStyle.DESERT ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState();
    }

    public static BlockState stairs(Settlement s) {
        return switch (s.style) {
            case DESERT -> Blocks.SANDSTONE_STAIRS.defaultBlockState();
            case NORDIC -> Blocks.COBBLESTONE_STAIRS.defaultBlockState();
            case ELVEN -> Blocks.MOSSY_COBBLESTONE_STAIRS.defaultBlockState();
            case GOTHIC, DWARVEN -> Blocks.COBBLED_DEEPSLATE_STAIRS.defaultBlockState();
            default -> Blocks.STONE_BRICK_STAIRS.defaultBlockState();
        };
    }

    /** Flor/mato do quintal de um estilo (3 opções; o resto é capim). */
    private static net.minecraft.world.level.block.Block yardPlant(SettlementStyle style, int h) {
        net.minecraft.world.level.block.Block[] set = switch (style) {
            case NORDIC -> new net.minecraft.world.level.block.Block[] {Blocks.FERN, Blocks.FERN, Blocks.SHORT_GRASS};
            case ORIENTAL -> new net.minecraft.world.level.block.Block[] {Blocks.PINK_TULIP, Blocks.ALLIUM, Blocks.PINK_PETALS};
            case ELVEN -> new net.minecraft.world.level.block.Block[] {Blocks.LILY_OF_THE_VALLEY, Blocks.AZURE_BLUET, Blocks.FERN};
            case GOTHIC -> new net.minecraft.world.level.block.Block[] {Blocks.FERN, Blocks.ALLIUM, Blocks.DEAD_BUSH};
            case DWARVEN -> new net.minecraft.world.level.block.Block[] {Blocks.FERN, Blocks.DANDELION, Blocks.SHORT_GRASS};
            default -> new net.minecraft.world.level.block.Block[] {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER};
        };
        return h >= 0 && h < set.length ? set[h] : Blocks.SHORT_GRASS;
    }

    /**
     * Topo de uma coluna do lote FORA do prédio: rua na borda (e a linha de rua), calçada reta da porta até a rua,
     * quintal no resto. (dx, dz) = deslocamento a partir do centro do lote; a praça e a base de terraplenagem são só rua.
     */
    public static BlockState surface(Settlement s, Project p, Project.Piece piece, int x, int z) {
        int dx = x - piece.cx, dz = z - piece.cz;
        if (p.cell < 0 || Math.max(Math.abs(dx), Math.abs(dz)) >= 7) return street(s, x, z);
        Direction front = piece.rotation.rotate(Direction.SOUTH);
        int along = dx * front.getStepX() + dz * front.getStepZ();
        int side = dx * front.getStepZ() - dz * front.getStepX();
        if (along > 0 && Math.abs(side) <= 1) return street(s, x, z);
        return yard(s);
    }

    private static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    // =====================================================================================
    //  Nível da rua
    // =====================================================================================
    /** O terreno entre este e a praça (para onde a porta olha). O anel 1 devolve a praça. */
    public static int inward(int cell) {
        int i = CityLayout.cellI(cell), j = CityLayout.cellJ(cell);
        // lote livre: o "vizinho de dentro" é o terreno da grade mais perto dele (daí em diante segue a grade)
        if (CityLayout.isFree(cell)) return CityLayout.cellIndex(i, j);
        if (Math.abs(i) >= Math.abs(j) && i != 0) i -= Integer.signum(i);
        else if (j != 0) j -= Integer.signum(j);
        // onda 9 (isles): o vizinho de dentro de uma ilha é outra ilha (ou a praça)
        return CityLayout.sameKind(cell, i, j);
    }

    /** Altura do piso de um terreno já nivelado (a praça é o chão do coração), ou null. */
    @Nullable
    public static Integer levelOf(Settlement s, int cell) {
        if (cell == CityLayout.CENTER_CELL) return s.heart.getY() - 1;
        return s.plotBase.get(cell);
    }

    /** Altura de referência de um terreno: a do primeiro lote nivelado no caminho até a praça. */
    public static int reference(Settlement s, int cell) {
        int c = cell;
        for (int k = 0; k <= 2 * CityLayout.MAX_RADIUS && c != CityLayout.CENTER_CELL; k++) {
            c = inward(c);
            Integer y = levelOf(s, c);
            if (y != null) return y;
        }
        return s.heart.getY() - 1;
    }

    /**
     * Altura em que um lote novo deve ficar (null = terreno ainda não carregado): igual ao vizinho de dentro quando dá
     * para aplainar; senão um patamar de {@link #MAX_STEP} blocos na direção do terreno natural.
     */
    @Nullable
    public static Integer streetLevel(ServerLevel sl, Settlement s, int cell) {
        // onda 7 (sky_island): na Ilha Celeste todo lote fica no topo plano da ilha
        if (s.sky) return s.heart.getY() - 1;
        // onda 8 (water_city): a Cidade das Águas também — todo lote na altura do piso da praça, logo acima da água
        if (s.water) return s.heart.getY() - 1;
        BlockPos c = CityLayout.cellCenter(s.heart, cell);
        int[] hs = new int[9];
        int n = 0;
        for (int ox = -6; ox <= 6; ox += 6) {
            for (int oz = -6; oz <= 6; oz += 6) {
                int x = c.getX() + ox, z = c.getZ() + oz;
                if (!sl.hasChunk(x >> 4, z >> 4)) return null;
                hs[n++] = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            }
        }
        Arrays.sort(hs);
        int nat = hs[4];
        int ref = CityLayout.isFree(cell) ? freeReference(s, cell) : reference(s, cell);
        int d = nat - ref;
        int base = Math.abs(d) <= FLATTEN ? ref : ref + Integer.signum(d) * MAX_STEP;
        // vizinho de lado 1 bloco acima/abaixo: iguala (nada de degrauzinho entre dois lotes lado a lado)
        int i = CityLayout.cellI(cell), j = CityLayout.cellJ(cell);
        int[][] around = {{i + 1, j}, {i - 1, j}, {i, j + 1}, {i, j - 1}};
        for (int[] a : around) {
            if (Math.max(Math.abs(a[0]), Math.abs(a[1])) > CityLayout.MAX_RADIUS) continue;
            Integer y = levelOf(s, CityLayout.cellAt(s, a[0], a[1]));
            if (y != null && Math.abs(y - base) == 1 && Math.abs(y - ref) <= MAX_STEP && Math.abs(nat - y) <= FLATTEN + 1) {
                base = y;
                break;
            }
        }
        int ground = s.heart.getY() - 1;
        return Math.max(ground - CityLayout.BOX_DOWN + 4, Math.min(ground + CityLayout.BOX_UP - 26, base));
    }

    /**
     * Referência de um lote LIVRE: a altura do lote nivelado mais perto dele (até 2 terrenos de distância) — o prédio
     * solto fica no nível da rua que já existe ao lado; sem vizinho por perto, vale a regra da grade.
     */
    private static int freeReference(Settlement s, int cell) {
        int x = CityLayout.offsetX(cell), z = CityLayout.offsetZ(cell);
        int best = Integer.MAX_VALUE, y = 0;
        for (java.util.Map.Entry<Integer, Integer> e : s.plotBase.entrySet()) {
            int c = e.getKey();
            if (c == cell) continue;
            int d = Math.max(Math.abs(CityLayout.offsetX(c) - x), Math.abs(CityLayout.offsetZ(c) - z));
            if (d < best) {
                best = d;
                y = e.getValue();
            }
        }
        int plaza = Math.max(Math.abs(x), Math.abs(z));
        if (plaza <= best && plaza <= 2 * CityLayout.CELL + 8) return s.heart.getY() - 1;
        return best <= 2 * CityLayout.CELL + 8 ? y : reference(s, cell);
    }

    // =====================================================================================
    //  Saia (transição suave em volta do lote)
    // =====================================================================================
    /** Quantas colunas tem a saia de um lote de meia-largura {@code half}. */
    public static int skirtColumns(int half) {
        int t = 0;
        for (int k = 1; k <= SKIRT; k++) t += 8 * (half + k);
        return t;
    }

    /**
     * Suaviza UMA coluna da saia (a de número {@code cursor}): o terreno natural fica a no máximo 1 bloco do lote nas
     * duas primeiras voltas, 2 nas seguintes, 3 nas últimas. Devolve quantos blocos mudou.
     */
    public static int skirtColumn(ServerLevel sl, Settlement s, Project self, int cx, int cz, int base, int half, int cursor) {
        int k = 1, c = cursor;
        while (k <= SKIRT && c >= 8 * (half + k)) {
            c -= 8 * (half + k);
            k++;
        }
        if (k > SKIRT) return 0;
        // onda 7 (sky_island): a Ilha Celeste é plana — nada a suavizar (e medir o chão acharia o vazio)
        if (s.sky) return 0;
        int r = half + k, side = c / (2 * r), off = c % (2 * r);
        int x = cx + switch (side) {
            case 0 -> -r + off;
            case 1 -> r;
            case 2 -> r - off;
            default -> -r;
        };
        int z = cz + switch (side) {
            case 0 -> -r;
            case 1 -> -r + off;
            case 2 -> r;
            default -> r - off;
        };
        if (!sl.hasChunk(x >> 4, z >> 4)) return 0;
        int dx = x - s.heart.getX(), dz = z - s.heart.getZ();
        if (Math.max(Math.abs(dx), Math.abs(dz)) > CityLayout.half(s)) return 0;
        if (CityLayout.inWallBand(s, dx, dz) || leveledAt(s, dx, dz, self)) return 0;
        int step = (k + 1) / 2;
        int hi = base + step, lo = base - step;
        int g = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockState found = s.style.foundation();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, g, z);
        BlockState surface = sl.getBlockState(m);
        if (!surface.getFluidState().isEmpty()) return 0;   // margem de rio/lago fica como está
        int changed = 0;
        if (g > hi) {
            if (g - hi > 12) return 0;                      // paredão: vira fundo de paisagem, não se mexe
            for (int y = g; y > hi; y--) {
                if (!ground(sl.getBlockState(m.setY(y)), found)) return 0;
            }
            // mato/neve em cima do bloco cortado vai junto
            BlockState above = sl.getBlockState(m.setY(g + 1));
            if (!above.isAir() && above.canBeReplaced() && above.getFluidState().isEmpty()) {
                sl.setBlock(m, Blocks.AIR.defaultBlockState(), 2 | 16);
                changed++;
            }
            for (int y = g; y > hi; y--) {
                sl.setBlock(m.setY(y), Blocks.AIR.defaultBlockState(), 2 | 16);
                changed++;
            }
            BlockState top = sl.getBlockState(m.setY(hi));
            if (soil(surface) && ground(top, found) && !top.isAir() && top.getFluidState().isEmpty() && !top.is(surface.getBlock())) {
                sl.setBlock(m, surface, 2 | 16);
                changed++;
            }
        } else if (g < lo) {
            if (lo - g > 6 || !ground(surface, found)) return 0;   // buraco fundo ou construção: não aterra
            BlockState cap = soil(surface) ? surface : yard(s);
            BlockState fill = cap.is(BlockTags.SAND) ? cap : Blocks.DIRT.defaultBlockState();
            sl.setBlock(m.setY(g), fill, 2 | 16);
            for (int y = g + 1; y <= lo; y++) {
                BlockState cur = sl.getBlockState(m.setY(y));
                if (!cur.isAir() && !cur.canBeReplaced()) break;
                sl.setBlock(m, y == lo ? cap : fill, 2 | 16);
                changed++;
            }
        }
        return changed;
    }

    /** Bloco de superfície que vale repetir em cima de um corte/aterro (grama, areia, cascalho, neve...). */
    private static boolean soil(BlockState st) {
        return st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(Blocks.GRAVEL) || st.is(Blocks.SNOW_BLOCK);
    }

    /** A coluna (dx, dz a partir do coração) fica num lote já nivelado, na praça ou no canteiro de outra obra? */
    private static boolean leveledAt(Settlement s, int dx, int dz, @Nullable Project self) {
        // lotes livres (v1.0): prédio pronto, lote nivelado ou canteiro de outra obra
        for (int cell : s.buildings.keySet()) if (freeAt(cell, dx, dz)) return true;
        for (int cell : s.plotBase.keySet()) if (freeAt(cell, dx, dz)) return true;
        for (Project q : s.projects) if (q != self && freeAt(q.cell, dx, dz)) return true;
        for (int i = -CityLayout.MAX_RADIUS; i <= CityLayout.MAX_RADIUS; i++) {
            if (Math.abs(dx - CityLayout.axis(s, i)) > LEVEL_HALF) continue;
            for (int j = -CityLayout.MAX_RADIUS; j <= CityLayout.MAX_RADIUS; j++) {
                if (Math.abs(dz - CityLayout.axis(s, j)) > LEVEL_HALF) continue;
                if (i == 0 && j == 0) return true;
                int cell = CityLayout.cellAt(s, i, j);
                if (s.buildings.containsKey(cell) || s.plotBase.containsKey(cell)) return true;
                Project q = s.projectAt(cell);
                if (q != null && q != self) return true;
            }
        }
        return false;
    }

    private static boolean freeAt(int cell, int dx, int dz) {
        return CityLayout.isFree(cell) && Math.abs(dx - CityLayout.freeDx(cell)) <= LEVEL_HALF
                && Math.abs(dz - CityLayout.freeDz(cell)) <= LEVEL_HALF;
    }

    // =====================================================================================
    //  Escadas (só onde o desnível é inevitável)
    // =====================================================================================
    /**
     * Liga o lote ao vizinho de dentro com uma escada de 3 de largura na linha de rua, se os dois estão nivelados, são
     * colados (sem vão de muralha) e o desnível passa de 1. Nunca entra na planta de um prédio; se não couber, não
     * faz nada. Devolve quantos blocos colocou.
     */
    public static int link(ServerLevel sl, Settlement s, int cell) {
        if (cell < 0 || cell == CityLayout.CENTER_CELL) return 0;
        int in = inward(cell);
        Integer b = levelOf(s, cell), n = levelOf(s, in);
        if (b == null || n == null) return 0;
        int d = Math.abs(b - n);
        if (d < 2 || d > MAX_STAIR) return 0;
        BlockPos cc = CityLayout.cellCenter(s.heart, cell), nc = CityLayout.cellCenter(s.heart, in);
        if (Math.abs(nc.getX() - cc.getX()) + Math.abs(nc.getZ() - cc.getZ()) != CityLayout.CELL) return 0;
        int ax = Integer.signum(nc.getX() - cc.getX()), az = Integer.signum(nc.getZ() - cc.getZ());
        boolean cellHigh = b > n;
        int low = Math.min(b, n), high = Math.max(b, n);
        // eixo t ao longo da ligação: 0 = linha de rua entre os dois lotes; positivo = para dentro do lote ALTO
        int gx = cc.getX() + 8 * ax, gz = cc.getZ() + 8 * az;
        int hx = cellHigh ? -ax : ax, hz = cellHigh ? -az : az;
        boolean alongX = ax != 0;
        int freeHi = free(sl, s, cellHigh ? cell : in, alongX, cellHigh);
        int freeLo = free(sl, s, cellHigh ? in : cell, alongX, !cellHigh);
        int tTop = Math.min(freeHi, d - 1);
        int t0 = tTop - (d - 1);
        if (t0 < -freeLo) return 0;
        Direction up = Direction.fromDelta(hx, 0, hz);
        if (up == null) return 0;
        BlockState found = s.style.foundation();
        BlockState stair = stairs(s).setValue(StairBlock.FACING, up);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // passo 0 confere tudo antes de pôr o primeiro bloco (ou a escada inteira, ou nada); o passo 1 constrói.
        // t0 <= 0 <= tTop: a linha de rua sempre tem um degrau, e o último degrau já fica na altura do lote alto.
        for (int pass = 0; pass < 2; pass++) {
            int placed = 0;
            for (int t = t0; t <= tTop; t++) {
                for (int l = -1; l <= 1; l++) {
                    int x = gx + hx * t + hz * l, z = gz + hz * t + hx * l;
                    if (!sl.hasChunk(x >> 4, z >> 4)) return 0;
                    int y = low + 1 + (t - t0);
                    for (int yy = y; yy <= high + 3; yy++) {
                        BlockState cur = sl.getBlockState(m.set(x, yy, z));
                        if (pass == 0) {
                            // piso de rua de qualquer degrau também cede lugar à escada (onda 5)
                            if (!ground(cur, found) && !cur.is(stair.getBlock()) && !TierLook.isStreet(cur)) return 0;
                        } else if (yy == y) {
                            if (cur != stair) {
                                sl.setBlock(m, stair, 3);
                                placed++;
                            }
                        } else if (!cur.isAir()) {
                            sl.setBlock(m, Blocks.AIR.defaultBlockState(), 3);
                            placed++;
                        }
                    }
                    if (pass == 1) {
                        for (int yy = y - 1; yy > y - 9; yy--) {
                            BlockState cur = sl.getBlockState(m.set(x, yy, z));
                            if (!(cur.isAir() || cur.canBeReplaced() || !cur.getFluidState().isEmpty())) break;
                            sl.setBlock(m, found, 2 | 16);
                            placed++;
                        }
                    }
                }
            }
            if (pass == 1) return placed;
        }
        return 0;
    }

    /**
     * Quantas colunas do lote, a partir da borda, estão livres de prédio no eixo da ligação (0..7). Lote vazio = 7.
     * {@code front} = a escada chega pela frente do prédio (onde nunca há enfeite); nos outros lados conta 1 a mais.
     */
    private static int free(ServerLevel sl, Settlement s, int cell, boolean alongX, boolean front) {
        Templates.Tpl tpl = null;
        Rotation rot = Rotation.NONE;
        if (cell == CityLayout.CENTER_CELL) {
            tpl = Templates.get(sl, Blueprint.template(s.style, Blueprint.PLAZA));
            rot = s.plazaRotation();
            front = false;
        } else {
            Blueprint bp = s.blueprintAt(cell);
            Project q = s.projectAt(cell);
            rot = CityLayout.facingHeart(cell);
            if (q != null && !q.pieces.isEmpty() && !Project.KIND_TERRAIN.equals(q.kind)) {
                tpl = Templates.get(sl, q.pieces.get(0).template);
            } else if (bp != null) {
                tpl = Templates.get(sl, com.renanpontes.isekaimahou.feature.settlements.CityLooks.standing(s, cell, bp));
            } else {
                return 7;
            }
        }
        if (tpl == null) return 0;
        int size = alongX ? tpl.width(rot) : tpl.depth(rot);
        // onda 8 (city_looks): no visual novo o molde ocupa o lote inteiro (prédio + quintal), mas o caminho do portão
        // até a porta é livre — conta como espaço para a escada de ligação chegar pela frente
        int yard = front ? LookBuildings.frontClear(tpl) : 0;
        return Math.max(0, Math.min(7, 7 - size / 2 - (front ? 0 : 1) + yard));
    }

    // =====================================================================================
    //  Mobília da rua: postes, bandeira do estilo, mato do quintal
    // =====================================================================================
    /**
     * Enfeita a frente de um lote pronto: 2 postes de lanterna nos cantos da frente e a bandeira do estilo da cidade
     * ao lado da calçada; flores/mato no quintal. Só em bloco de ar sobre chão firme, sempre nas mesmas posições (a
     * próxima obra do lote limpa tudo junto). Devolve quantos blocos colocou.
     */
    public static int furnish(ServerLevel sl, Settlement s, Project.Piece piece, Templates.Tpl tpl, BlockPos origin) {
        int sx = tpl.sx(), sz = tpl.sz(), n = 0;
        if (sx > 13 || sz > 13) return 0;
        // onda 5: poste do degrau e estandarte do estilo (TierLook) — o mastro do estandarte é o poste simples do estilo
        TierLook.Look look = TierLook.of(s.style);
        BlockState[] lamp = TierLook.lamp(s.style, s.level);
        BlockState post = look.lamp[2][0];
        for (int x : new int[] {-1, sx}) {
            n += pole(sl, piece, origin.offset(tpl.rotate(x, 1, sz, piece.rotation)), lamp);
        }
        if (sx / 2 + 3 <= sx - 1) {
            BlockState banner = look.banner.defaultBlockState().setValue(BannerBlock.ROTATION, 0).rotate(piece.rotation);
            n += pole(sl, piece, origin.offset(tpl.rotate(sx / 2 + 3, 1, sz, piece.rotation)), new BlockState[] {post, post, banner});
        }
        // quintal: um pouco de mato e flores (determinístico: a mesma cidade fica sempre igual)
        int hw = tpl.width(piece.rotation) / 2, hd = tpl.depth(piece.rotation) / 2;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (Math.abs(dx) <= hw && Math.abs(dz) <= hd) continue;
                int x = piece.cx + dx, z = piece.cz + dz, h = hash(x, z) % 23;
                if (h > 5) continue;
                BlockState soil = sl.getBlockState(m.set(x, piece.baseY, z));
                if (!sl.getBlockState(m.setY(piece.baseY + 1)).isAir()) continue;
                BlockState plant;
                if (soil.is(Blocks.GRASS_BLOCK)) {
                    plant = yardPlant(s.style, h).defaultBlockState();
                } else if (soil.is(Blocks.SAND) && h == 0) {
                    plant = Blocks.DEAD_BUSH.defaultBlockState();
                } else {
                    continue;
                }
                sl.setBlock(m, plant, 2 | 16);
                n++;
            }
        }
        return n;
    }

    /** Poste: a coluna {@code col} (de baixo para cima; o último é a luz/estandarte) — só se ela toda estiver livre. */
    private static int pole(ServerLevel sl, Project.Piece piece, BlockPos at, BlockState[] col) {
        if (Math.abs(at.getX() - piece.cx) > 7 || Math.abs(at.getZ() - piece.cz) > 7) return 0;
        if (!sl.hasChunk(at.getX() >> 4, at.getZ() >> 4)) return 0;
        BlockState below = sl.getBlockState(at.below());
        if (below.isAir() || !below.isFaceSturdy(sl, at.below(), Direction.UP) && !below.is(Blocks.DIRT_PATH)) return 0;
        for (int y = 0; y < col.length; y++) {
            if (!free(sl.getBlockState(at.above(y)), y == 0)) return 0;
        }
        for (int y = 0; y < col.length; y++) sl.setBlock(at.above(y), col[y], 2 | 16);
        return col.length;
    }

    /** Dá para pôr um enfeite aqui? Ar; no pé do poste vale também neve/mato (cidade nevada não fica sem poste). */
    public static boolean free(BlockState st, boolean foot) {
        return st.isAir() || foot && st.canBeReplaced() && st.getFluidState().isEmpty();
    }

    private CityTerrain() {}
}
