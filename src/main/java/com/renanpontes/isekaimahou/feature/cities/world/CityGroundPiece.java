package com.renanpontes.isekaimahou.feature.cities.world;

import java.util.Locale;

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
 * Só escreve dentro do BoundingBox do chunk recebido em {@link #postProcess}.
 */
public class CityGroundPiece extends StructurePiece implements PieceBeardifierModifier {
    /** Profundidade máxima da fundação (abismos/cavernas abaixo disso ficam como estão). */
    private static final int FOUNDATION_MAX = 64;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();

    private final CityGeometry geo;
    private final CityFrame frame;
    private final CityPalette palette;
    private final int gates;
    private final int clearHeight;
    private final long seed;
    private final BoundingBox beardBox;

    public CityGroundPiece(CityGeometry geo, CityFrame frame, CityPalette palette, int gates, int clearHeight, long seed) {
        super(CityWorldgen.GROUND_PIECE.get(), 0, makeBox(geo, frame, clearHeight));
        this.geo = geo;
        this.frame = frame;
        this.palette = palette;
        this.gates = gates;
        this.clearHeight = clearHeight;
        this.seed = seed;
        this.beardBox = makeBeardBox(geo, frame);
    }

    /** Recarregada do chunk de início salvo (cidade ainda não colocada por inteiro). */
    public CityGroundPiece(CompoundTag tag) {
        super(CityWorldgen.GROUND_PIECE.get(), tag);
        this.geo = new CityGeometry(tag.getInt("Rings"), tag.getBoolean("Capital"));
        this.frame = new CityFrame(tag.getInt("CX"), tag.getInt("CZ"), tag.getInt("Floor"), readRotation(tag.getString("Rot")));
        this.palette = CityPalette.byId(tag.getString("Palette"));
        this.gates = tag.getInt("Gates");
        this.clearHeight = tag.getInt("Clear");
        this.seed = tag.getLong("Seed");
        this.beardBox = makeBeardBox(geo, frame);
    }

    private static BoundingBox makeBox(CityGeometry geo, CityFrame frame, int clearHeight) {
        int e = geo.extent;
        return new BoundingBox(frame.centerX() - e, frame.floorY() - 16, frame.centerZ() - e,
                frame.centerX() + e, frame.floorY() + clearHeight, frame.centerZ() + e);
    }

    private static BoundingBox makeBeardBox(CityGeometry geo, CityFrame frame) {
        int r = geo.towerOut;
        return new BoundingBox(frame.centerX() - r, frame.floorY(), frame.centerZ() - r,
                frame.centerX() + r, frame.floorY() + 12, frame.centerZ() + r);
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
        int floor = frame.floorY();
        boolean masonry = zone == Zone.WALL || zone == Zone.STRUCTURE || zone == Zone.BUTTRESS;
        // Fundação: enche o que for ar/água/planta debaixo do piso até achar chão firme.
        int bottom = Math.max(level.getMinBuildHeight() + 1, floor - FOUNDATION_MAX);
        for (int y = floor - 1; y >= bottom; y--) {
            pos.set(wx, y, wz);
            if (!isSoft(level.getBlockState(pos))) break;
            int depth = floor - y;
            put(level, box, pos, masonry ? palette.wall.pick(hash(lx, lz, y)) : depth <= 2 ? palette.subsoil : palette.core);
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
            case WALL -> wall(level, box, pos, wx, wz, lx, lz);
            case BUTTRESS -> buttress(level, box, pos, wx, wz, lx, lz);
            case AVENUE -> {
                int w = Math.abs(lx) <= CityGeometry.AVENUE ? Math.abs(lx) : Math.abs(lz);
                set(level, box, pos, wx, floor, wz, w <= 1 ? palette.avenueMain : w == 2 ? palette.avenueAccent : palette.curb);
                lamp(level, box, pos, wx, wz, lx, lz);
            }
            case STREET -> {
                set(level, box, pos, wx, floor, wz, palette.street.pick(h));
                lamp(level, box, pos, wx, wz, lx, lz);
            }
            case RING -> {
                boolean kerb = Math.max(Math.abs(lx), Math.abs(lz)) == geo.ringEnd;
                set(level, box, pos, wx, floor, wz, kerb ? palette.curb : palette.street.pick(h));
                lamp(level, box, pos, wx, wz, lx, lz);
            }
            // Praça, castelo, lotes, torres e portões: chão provisório, o template por cima define o piso de verdade.
            default -> set(level, box, pos, wx, floor, wz, masonry ? palette.wallTop : palette.topsoil);
        }
    }

    /** Muralha: corpo variado, passeio no topo, ameias por fora (com lanternas) e parapeito baixo por dentro. */
    private void wall(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int wx, int wz, int lx, int lz) {
        int floor = frame.floorY();
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
        }
    }

    /** Contraforte encostado na face de fora da muralha, com escada de pedra de remate. */
    private void buttress(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int wx, int wz, int lx, int lz) {
        int floor = frame.floorY();
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

    /** Poste com lanterna nas esquinas dos quarteirões. */
    private void lamp(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int wx, int wz, int lx, int lz) {
        if (!geo.isLampSpot(lx, lz)) return;
        int floor = frame.floorY();
        for (int y = 1; y <= 3; y++) set(level, box, pos, wx, floor + y, wz, palette.lampPost);
        set(level, box, pos, wx, floor + 4, wz, LANTERN);
    }

    // ------------------------------------------------------------------------------------------ talude em volta
    /**
     * Liga o piso da cidade ao terreno natural: enche ou corta suavemente até a borda da faixa de transição.
     * Colunas com construção de outra estrutura por cima do chão (casa de vila etc.) ficam como estão — cortar a
     * terra embaixo dela deixaria a casa flutuando, e encher o entorno enterraria o interior.
     */
    private void margin(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int wx, int wz, int lx, int lz) {
        int floor = frame.floorY();
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
        int floor = frame.floorY();
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
