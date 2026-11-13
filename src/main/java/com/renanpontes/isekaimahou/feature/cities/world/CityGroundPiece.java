package com.renanpontes.isekaimahou.feature.cities.world;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.renanpontes.isekaimahou.feature.cities.world.CityGeometry.Zone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

/**
 * A peça "chão" da cidade, feita em código (vem antes dos templates na lista de peças, então roda primeiro em cada
 * chunk): aplaina o terreno (fundação até o chão firme por baixo, limpa morros/árvores/restos de vila por cima),
 * calça avenidas e ruas, ergue a muralha (com passeio, ameias, lanternas e contrafortes), põe os postes nas esquinas
 * e faz o talude que liga a cidade ao terreno em volta (com a estrada saindo de cada portão).
 * v1.4: o piso é em terraços ({@link CityTerrace}) — ruas inclinadas com meias lajes, cada quarteirão num terraço
 * plano com muro de arrimo de pedra onde fica mais alto que a rua, degraus na frente das entradas, muralha seguindo o
 * meio-fio de dentro.
 * Só escreve dentro do BoundingBox do chunk recebido em {@link #postProcess}.
 */
public class CityGroundPiece extends StructurePiece implements PieceBeardifierModifier {
    /** Profundidade máxima da fundação (abismos/cavernas abaixo disso ficam como estão). */
    private static final int FOUNDATION_MAX = 64;
    /** Degraus no máximo por entrada de lote (a rua tem 5 de largura: dois de cada lado nunca se cruzam). */
    private static final int MAX_STEPS = 2;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();

    private final CityGeometry geo;
    private final CityFrame frame;
    private final CityTerrace terrace;
    private final CityPalette palette;
    private final int gates;
    private final int clearHeight;
    private final long seed;
    private final BoundingBox beardBox;
    /** Degraus na rua em frente às entradas dos lotes (coluna canônica → degrau); vazio na cidade plana. */
    private final Map<Long, Step> steps;

    /** Um degrau (escada de pedra) numa coluna de rua: altura e para onde sobe (no plano canônico). */
    private record Step(int y, Direction facing) {}

    public CityGroundPiece(CityGeometry geo, CityFrame frame, CityTerrace terrace, CityPalette palette, int gates,
                           int clearHeight, long seed) {
        super(CityWorldgen.GROUND_PIECE.get(), 0, makeBox(geo, frame, terrace, clearHeight));
        this.geo = geo;
        this.frame = frame;
        this.terrace = terrace;
        this.palette = palette;
        this.gates = gates;
        this.clearHeight = clearHeight;
        this.seed = seed;
        this.beardBox = makeBeardBox(geo, frame, terrace);
        this.steps = makeSteps();
    }

    /**
     * Recarregada do chunk de início salvo (cidade ainda não colocada por inteiro). Peças salvas antes da v1.4 não têm
     * "GX"/"GZ"/"Sea": gradiente zero = piso único em floorY, exatamente como eram geradas.
     */
    public CityGroundPiece(CompoundTag tag) {
        super(CityWorldgen.GROUND_PIECE.get(), tag);
        this.geo = new CityGeometry(tag.getInt("Rings"), tag.getBoolean("Capital"));
        this.frame = new CityFrame(tag.getInt("CX"), tag.getInt("CZ"), tag.getInt("Floor"), readRotation(tag.getString("Rot")));
        this.palette = CityPalette.byId(tag.getString("Palette"));
        this.gates = tag.getInt("Gates");
        this.clearHeight = tag.getInt("Clear");
        this.seed = tag.getLong("Seed");
        int gx = tag.contains("GX", Tag.TAG_INT) ? tag.getInt("GX") : 0;
        int gz = tag.contains("GZ", Tag.TAG_INT) ? tag.getInt("GZ") : 0;
        int sea = tag.contains("Sea", Tag.TAG_INT) ? tag.getInt("Sea") : frame.floorY();
        this.terrace = new CityTerrace(geo, frame, gates, gx, gz, sea);
        this.beardBox = makeBeardBox(geo, frame, terrace);
        this.steps = makeSteps();
    }

    /** Caixa da peça: do ponto mais baixo do piso −16 até o mais alto + a altura de limpeza (plano = como antes). */
    private static BoundingBox makeBox(CityGeometry geo, CityFrame frame, CityTerrace terrace, int clearHeight) {
        int e = geo.extent;
        return new BoundingBox(frame.centerX() - e, terrace.lowest(e) - 16, frame.centerZ() - e,
                frame.centerX() + e, terrace.highest(e) + clearHeight, frame.centerZ() + e);
    }

    /**
     * Caixa do aplainamento no ruído: começa no nível MAIS BAIXO do piso inclinado (senão o beardifier aterraria a
     * parte de baixo até floorY e ela teria de ser cortada de novo) e vai até 12 acima do mais alto. Cada coluna
     * termina o serviço no seu próprio nível (fundação por baixo, limpeza por cima).
     */
    private static BoundingBox makeBeardBox(CityGeometry geo, CityFrame frame, CityTerrace terrace) {
        int r = geo.towerOut;
        return new BoundingBox(frame.centerX() - r, terrace.lowest(r), frame.centerZ() - r,
                frame.centerX() + r, terrace.highest(r) + 12, frame.centerZ() + r);
    }

    /**
     * Degraus em frente ao meio da fachada de cada lote (e do castelo) quando o terraço do quarteirão ficou mais alto
     * ou mais baixo que a rua: subindo (escada na rua encostada no terraço, com enchimento por baixo) ou descendo
     * (a escada é rebaixada na rua). Só depende do seed e do relevo salvos — sai igual em qualquer chunk.
     */
    private Map<Long, Step> makeSteps() {
        if (terrace.isFlat()) return Map.of();
        Map<Long, Step> out = new HashMap<>();
        for (CityPlanner.Lot lot : CityPlanner.fronts(geo, seed)) {
            Direction f = lot.facing();
            int lotLevel = terrace.surface(lot.x0(), lot.z0());
            boolean ns = f.getAxis() == Direction.Axis.Z;
            int lo = ns ? lot.x0() : lot.z0();
            int hi = ns ? lot.x1() : lot.z1();
            int w = hi - lo + 1;
            for (int mid = lo + (w - 1) / 2; mid <= lo + w / 2; mid++) {
                for (int k = 1; k <= MAX_STEPS; k++) {
                    int x = switch (f) {
                        case EAST -> lot.x1() + k;
                        case WEST -> lot.x0() - k;
                        default -> mid;
                    };
                    int z = switch (f) {
                        case SOUTH -> lot.z1() + k;
                        case NORTH -> lot.z0() - k;
                        default -> mid;
                    };
                    if (!isPaved(geo.zone(x, z, gates))) break;
                    int street = terrace.level(x, z);
                    int y;
                    Direction up;
                    if (lotLevel > street) {
                        // Terraço mais alto: a escada fica em cima da rua e sobe em direção ao lote.
                        y = lotLevel - k + 1;
                        if (y <= street) break;
                        up = f.getOpposite();
                    } else if (lotLevel < street) {
                        // Terraço mais baixo: a escada é rebaixada na rua e sobe saindo do lote.
                        y = lotLevel + k;
                        if (y > street) break;
                        up = f;
                    } else {
                        break;
                    }
                    out.put(key(x, z), new Step(y, up));
                }
            }
        }
        return out;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static boolean isPaved(Zone zone) {
        return zone == Zone.STREET || zone == Zone.AVENUE || zone == Zone.RING;
    }

    private static Rotation readRotation(String name) {
        try {
            return Rotation.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Rotation.NONE;
        }
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("Rings", geo.rings);
        tag.putBoolean("Capital", geo.capital);
        tag.putInt("CX", frame.centerX());
        tag.putInt("CZ", frame.centerZ());
        tag.putInt("Floor", frame.floorY());
        tag.putString("Rot", frame.rotation().name());
        tag.putString("Palette", palette.id);
        tag.putInt("Gates", gates);
        tag.putInt("Clear", clearHeight);
        tag.putLong("Seed", seed);
        // v1.4: relevo do piso (gradientes em milésimos, eixos do mundo) e o nível mínimo (mar).
        tag.putInt("GX", terrace.gxMilli());
        tag.putInt("GZ", terrace.gzMilli());
        tag.putInt("Sea", terrace.minLevel());
    }

    // O terreno é aplainado no ruído para o nível do piso dentro da cidade (como as vilas fazem), e a peça termina
    // o serviço bloco a bloco.
    @Override
    public BoundingBox getBeardifierBox() {
        return beardBox;
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return TerrainAdjustment.BEARD_BOX;
    }

    @Override
    public int getGroundLevelDelta() {
        return 1;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        removeStrayEntities(level, chunkPos);
        int x0 = Math.max(box.minX(), boundingBox.minX());
        int x1 = Math.min(box.maxX(), boundingBox.maxX());
        int z0 = Math.max(box.minZ(), boundingBox.minZ());
        int z1 = Math.min(box.maxZ(), boundingBox.maxZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int wx = x0; wx <= x1; wx++) {
            for (int wz = z0; wz <= z1; wz++) {
                int lx = frame.localX(wx, wz);
                int lz = frame.localZ(wx, wz);
                Zone zone = geo.zone(lx, lz, gates);
                if (zone == Zone.OUTSIDE) continue;
                if (zone == Zone.MARGIN) {
                    margin(level, box, pos, wx, wz, lx, lz);
                } else {
                    column(level, box, pos, wx, wz, lx, lz, zone);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ miolo da cidade
    private void column(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int wx, int wz, int lx,
                        int lz, Zone zone) {
        // Nível desta coluna no piso em terraços (rua: a própria coluna; lote/praça/castelo/torre: o do seu terraço).
        int floor = terrace.surface(lx, lz, zone);
        boolean paved = isPaved(zone);
        boolean half = paved && terrace.half(lx, lz);
        boolean masonry = zone == Zone.WALL || zone == Zone.STRUCTURE || zone == Zone.BUTTRESS;
        // Lado exposto (vizinho mais baixo): vira muro de arrimo de pedra em vez de terra.
        int face = masonry ? floor : retainingFrom(lx, lz, floor);
        // Fundação: enche o que for ar/água/planta debaixo do piso até achar chão firme.
        int bottom = Math.max(level.getMinBuildHeight() + 1, floor - FOUNDATION_MAX);
        for (int y = floor - 1; y >= bottom; y--) {
            pos.set(wx, y, wz);
            boolean wallFace = y >= face;
            if (!wallFace && !isSoft(level.getBlockState(pos))) break;
            int depth = floor - y;
            put(level, box, pos, masonry || wallFace ? palette.wall.pick(hash(lx, lz, y))
                    : depth <= 2 ? palette.subsoil : palette.core);
        }
        // Limpa por cima: morros, árvores e o que outra estrutura tenha deixado no lugar da cidade — até a superfície
        // real da coluna (heightmap), para não sobrar topo de morro nem copa de árvore flutuando acima do volume
        // medido na escolha do lugar.
        int top = Math.min(level.getMaxBuildHeight() - 1, Math.max(floor + clearHeight, surfaceTop(level, wx, wz)));
        for (int y = floor + 1; y <= top; y++) {
            pos.set(wx, y, wz);
            if (!level.getBlockState(pos).isAir()) put(level, box, pos, AIR);
        }
        long h = hash(lx, lz, 0);
        switch (zone) {
            case WALL -> wall(level, box, pos, wx, wz, lx, lz, floor);
            case BUTTRESS -> buttress(level, box, pos, wx, wz, lx, lz, floor);
            case AVENUE, STREET, RING -> {
                BlockState pave;
                if (zone == Zone.AVENUE) {
                    int w = Math.abs(lx) <= CityGeometry.AVENUE ? Math.abs(lx) : Math.abs(lz);
                    pave = w <= 1 ? palette.avenueMain : w == 2 ? palette.avenueAccent : palette.curb;
                } else if (zone == Zone.RING) {
                    boolean kerb = Math.max(Math.abs(lx), Math.abs(lz)) == geo.ringEnd;
                    pave = kerb ? palette.curb : palette.street.pick(h);
                } else {
                    pave = palette.street.pick(h);
                }
                set(level, box, pos, wx, floor, wz, pave);
                // Rua inclinada: meia laje do mesmo material por cima quando o relevo pede meio bloco a mais (vizinhos
                // diferem no máximo meio bloco → anda-se sem pular).
                if (half) set(level, box, pos, wx, floor + 1, wz, palette.slabOf(pave));
                lamp(level, box, pos, wx, wz, lx, lz, floor, half, pave);
                Step step = steps.get(key(lx, lz));
                if (step != null) step(level, box, pos, wx, wz, floor, pave, step);
            }
            // Praça, castelo, lotes, torres e portões: chão provisório, o template por cima define o piso de verdade.
            default -> set(level, box, pos, wx, floor, wz, masonry ? palette.wallTop : palette.topsoil);
        }
    }

    /**
     * Primeiro y (de baixo) da face exposta desta coluna: o vizinho (4 lados) de piso mais baixo deixa à mostra os
     * blocos entre o piso dele e o desta coluna; ali vai pedra (muro de arrimo do terraço ou da rua elevada). Muralha,
     * torres e talude não contam (já são de pedra / tratados à parte). Sem vizinho mais baixo = {@code floor} (nada).
     */
    private int retainingFrom(int lx, int lz, int floor) {
        if (terrace.isFlat()) return floor;
        int low = floor;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = lx + d.getStepX();
            int nz = lz + d.getStepZ();
            Zone nz2 = geo.zone(nx, nz, gates);
            if (!isPaved(nz2) && nz2 != Zone.LOT && nz2 != Zone.PLAZA && nz2 != Zone.CASTLE) continue;
            low = Math.min(low, terrace.surface(nx, nz, nz2));
        }
        return low + 1;
    }

    /** Degrau em frente à entrada de um lote (ver {@link #makeSteps}): enche por baixo ou rebaixa a rua. */
    private void step(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int wx, int wz, int floor,
                      BlockState pave, Step step) {
        for (int y = floor + 1; y < step.y(); y++) set(level, box, pos, wx, y, wz, pave);
        for (int y = step.y() + 1; y <= floor + 1; y++) set(level, box, pos, wx, y, wz, AIR);
        pos.set(wx, step.y(), wz);
        put(level, box, pos, palette.buttressCap.setValue(StairBlock.FACING, step.facing()).rotate(level, pos, frame.rotation()));
    }

    /** Muralha: corpo variado, passeio no topo, ameias por fora (com lanternas) e parapeito baixo por dentro. O nível
     *  é o do meio-fio de dentro na mesma posição (igual nas 3 colunas de espessura). */
    private void wall(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int wx, int wz, int lx, int lz,
                      int floor) {
        boolean ns = Math.abs(lz) >= geo.wallIn;
        int along = ns ? lx : lz;
        int depth = (ns ? Math.abs(lz) : Math.abs(lx)) - geo.wallIn;
        set(level, box, pos, wx, floor, wz, palette.wallTop);
        for (int y = 1; y <= CityGeometry.WALL_HEIGHT; y++) {
            set(level, box, pos, wx, floor + y, wz,
                    y == CityGeometry.WALL_HEIGHT ? palette.wallTop : palette.wall.pick(hash(lx, lz, y)));
        }
        int top = floor + CityGeometry.WALL_HEIGHT + 1;
        if (depth == CityGeometry.WALL - 1) {
            if ((along & 1) == 0) {
                set(level, box, pos, wx, top, wz, palette.wallTop);
                if (Math.floorMod(along, 8) == 0) set(level, box, pos, wx, top + 1, wz, LANTERN);
            }
        } else if (depth == 0) {
            BlockState parapet = palette.parapet
                    .setValue(WallBlock.EAST_WALL, ns ? WallSide.LOW : WallSide.NONE)
                    .setValue(WallBlock.WEST_WALL, ns ? WallSide.LOW : WallSide.NONE)
                    .setValue(WallBlock.NORTH_WALL, ns ? WallSide.NONE : WallSide.LOW)
                    .setValue(WallBlock.SOUTH_WALL, ns ? WallSide.NONE : WallSide.LOW)
                    .setValue(WallBlock.UP, false);
            pos.set(wx, top, wz);
            put(level, box, pos, parapet.rotate(level, pos, frame.rotation()));
        } else if (!terrace.isFlat()) {
            // Meio do passeio: onde o trecho vizinho da muralha sobe um bloco, um degrau de escada.
            for (int s = 1; s >= -1; s -= 2) {
                int nx = ns ? lx + s : lx;
                int nz = ns ? lz : lz + s;
                if (geo.zone(nx, nz, gates) != Zone.WALL || terrace.wallLevel(nx, nz) <= floor) continue;
                Direction up = ns ? (s > 0 ? Direction.EAST : Direction.WEST) : (s > 0 ? Direction.SOUTH : Direction.NORTH);
                pos.set(wx, top, wz);
                put(level, box, pos, palette.buttressCap.setValue(StairBlock.FACING, up).rotate(level, pos, frame.rotation()));
                break;
            }
        }
    }

    /** Contraforte encostado na face de fora da muralha, com escada de pedra de remate. */
    private void buttress(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int wx, int wz, int lx, int lz,
                          int floor) {
        set(level, box, pos, wx, floor, wz, palette.wallTop);
        for (int y = 1; y <= 4; y++) set(level, box, pos, wx, floor + y, wz, palette.wall.pick(hash(lx, lz, y)));
        Direction towardWall = switch (CityGeometry.side(lx, lz)) {
            case CityGeometry.GATE_N -> Direction.SOUTH;
            case CityGeometry.GATE_S -> Direction.NORTH;
            case CityGeometry.GATE_E -> Direction.WEST;
            default -> Direction.EAST;
        };
        pos.set(wx, floor + 5, wz);
        put(level, box, pos, palette.buttressCap.setValue(StairBlock.FACING, towardWall).rotate(level, pos, frame.rotation()));
    }

    /** Poste com lanterna nas esquinas dos quarteirões (em cima da meia laje, ela vira bloco inteiro de base). */
    private void lamp(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int wx, int wz, int lx, int lz,
                      int floor, boolean half, BlockState pave) {
        if (!geo.isLampSpot(lx, lz)) return;
        int base = floor;
        if (half) {
            base = floor + 1;
            set(level, box, pos, wx, base, wz, pave);
        }
        for (int y = 1; y <= 3; y++) set(level, box, pos, wx, base + y, wz, palette.lampPost);
        set(level, box, pos, wx, base + 4, wz, LANTERN);
    }

    // ------------------------------------------------------------------------------------------ talude em volta
    /**
     * Liga o piso da cidade ao terreno natural: enche ou corta suavemente até a borda da faixa de transição.
     * Colunas com construção de outra estrutura por cima do chão (casa de vila etc.) ficam como estão — cortar a
     * terra embaixo dela deixaria a casa flutuando, e encher o entorno enterraria o interior.
     */
    private void margin(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int wx, int wz, int lx, int lz) {
        // Parte do nível do pé da muralha logo ali dentro (a estrada do portão, do nível do portão) e vai até o chão
        // natural.
        int floor = isGateRoad(lx, lz) ? terrace.gateLevel(CityGeometry.side(lx, lz)) : terrace.surface(lx, lz, Zone.MARGIN);
        int m = Math.max(Math.abs(lx), Math.abs(lz));
        float t = Mth.clamp((m - geo.wallOut) / (float) (geo.extent - geo.wallOut), 0F, 1F);
        float s = t * t * (3F - 2F * t);
        int top = Math.min(level.getMaxBuildHeight() - 1, surfaceTop(level, wx, wz));
        int bottom = Math.max(level.getMinBuildHeight() + 1, floor - 48);
        // Chão natural: o 1º bloco firme de cima para baixo, pulando ar, água, plantas, folhas e troncos.
        int ground = bottom;
        for (int y = top; y >= bottom; y--) {
            pos.set(wx, y, wz);
            BlockState st = level.getBlockState(pos);
            if (isSoft(st) || st.is(BlockTags.LOGS)) continue;
            if (!isNatural(st)) return;
            ground = y;
            break;
        }
        int target = floor + Math.round((ground - floor) * s);
        if (ground < target) {
            for (int y = ground + 1; y <= target; y++) {
                pos.set(wx, y, wz);
                if (isSoft(level.getBlockState(pos))) put(level, box, pos, y == target ? palette.topsoil : palette.subsoil);
            }
        } else if (ground > target) {
            // Corta a terra E o que estava em cima dela (árvores, plantas, neve), senão sobra árvore flutuando.
            for (int y = top; y > target; y--) {
                pos.set(wx, y, wz);
                if (isNatural(level.getBlockState(pos))) put(level, box, pos, AIR);
            }
        }
        pos.set(wx, target, wz);
        BlockState surface = level.getBlockState(pos);
        if (isGateRoad(lx, lz) && (isNatural(surface) || surface.isAir())) {
            put(level, box, pos, palette.road);
            for (int y = target + 1; y <= target + 3; y++) {
                pos.set(wx, y, wz);
                BlockState above = level.getBlockState(pos);
                // Debaixo d'água a estrada fica no fundo: não abre um "túnel" de ar com paredes de água.
                if (isNatural(above) && above.getFluidState().isEmpty()) put(level, box, pos, AIR);
            }
        } else if (target != ground && isNatural(surface) && !isSoft(surface)) {
            put(level, box, pos, palette.topsoil);
        }
    }

    /** Estrada saindo dos portões pela faixa de transição. */
    private boolean isGateRoad(int lx, int lz) {
        int m = Math.max(Math.abs(lx), Math.abs(lz));
        return m > geo.towerOut && (gates & CityGeometry.side(lx, lz)) != 0 && Math.abs(CityGeometry.along(lx, lz)) <= 2;
    }

    /** Bloco não-ar mais alto da coluna (heightmap WORLD_SURFACE do chunk que está sendo gerado). */
    private static int surfaceTop(WorldGenLevel level, int wx, int wz) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
    }

    // ------------------------------------------------------------------------------------------ utilidades
    /** Mobs do vanilla deixados por outra estrutura (vila sobreposta) dentro da muralha somem antes das peças. */
    private void removeStrayEntities(WorldGenLevel level, ChunkPos chunkPos) {
        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z);
        if (!(chunk instanceof ProtoChunk proto) || chunk instanceof ImposterProtoChunk) return;
        int floor = terrace.lowest(geo.towerOut);
        proto.getEntities().removeIf(tag -> {
            if (!tag.getString("id").startsWith("minecraft:")) return false;
            ListTag p = tag.getList("Pos", Tag.TAG_DOUBLE);
            if (p.size() < 3) return false;
            // Tudo acima do piso é limpo (até a superfície real), então qualquer altura acima conta.
            if (p.getDouble(1) < floor - 4) return false;
            int ex = Mth.floor(p.getDouble(0));
            int ez = Mth.floor(p.getDouble(2));
            return Math.max(Math.abs(frame.localX(ex, ez)), Math.abs(frame.localZ(ex, ez))) <= geo.towerOut;
        });
    }

    private static boolean isSoft(BlockState st) {
        return st.isAir() || !st.getFluidState().isEmpty() || st.canBeReplaced() || st.is(BlockTags.LEAVES);
    }

    /** Terreno e vegetação (o que pode ser cortado no talude sem destruir construções). */
    private static boolean isNatural(BlockState st) {
        return isSoft(st) || st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(BlockTags.BASE_STONE_OVERWORLD)
                || st.is(BlockTags.LOGS) || st.is(BlockTags.SNOW) || st.is(BlockTags.ICE) || st.is(BlockTags.TERRACOTTA)
                || st.is(Tags.Blocks.ORES) || st.is(Tags.Blocks.GRAVELS) || st.is(Tags.Blocks.SANDSTONE_BLOCKS)
                || st.is(Tags.Blocks.STONES) || st.is(Blocks.CLAY) || st.is(Blocks.CALCITE)
                || st.is(Blocks.PUMPKIN) || st.is(Blocks.MELON) || st.is(Blocks.MUSHROOM_STEM)
                || st.is(Blocks.BROWN_MUSHROOM_BLOCK) || st.is(Blocks.RED_MUSHROOM_BLOCK) || st.is(Blocks.BEE_NEST)
                || st.is(Blocks.DRIPSTONE_BLOCK) || st.is(Blocks.POINTED_DRIPSTONE) || st.is(Blocks.MOSS_CARPET)
                || st.is(Blocks.COBWEB) || st.is(Blocks.CACTUS) || st.is(Blocks.SUGAR_CANE) || st.is(Blocks.BAMBOO);
    }

    private void set(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState st) {
        pos.set(x, y, z);
        put(level, box, pos, st);
    }

    private static void put(WorldGenLevel level, BoundingBox box, BlockPos pos, BlockState st) {
        if (box.isInside(pos)) level.setBlock(pos, st, 2);
    }

    /** Hash estável por posição canônica (o mesmo bloco sai igual seja qual for o chunk que o desenha). */
    private long hash(int x, int z, int y) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (y * 0x632BE59BD9B4E019L);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }
}
