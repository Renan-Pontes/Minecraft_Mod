package com.renanpontes.isekaimahou.feature.end_library.world;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.end_library.ElRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

/**
 * Tipo de estrutura "isekai_mahou:end_library" — a Biblioteca do Vazio. Um único molde grande
 * ({@code end_library/library}, 47×56×47) colocado como peça de jigsaw, mas SÓ onde há ilha de verdade: mede a altura
 * do terreno no centro, nos 4 cantos e no meio de cada lado da área girada; qualquer ponto sobre o vazio (ou um desnível
 * grande) cancela. Assim a biblioteca nunca nasce pendurada no nada das ilhas externas do End.
 * <pre>{"type": "isekai_mahou:end_library", "biomes": "#isekai_mahou:has_structure/end_library", ...}</pre>
 * {@code /place structure} (ou o comando do módulo) força: sem ilha, o piso fica na altura medida no centro (mín. 64).
 */
public class EndLibraryStructure extends Structure {
    public static final MapCodec<EndLibraryStructure> CODEC = simpleCodec(EndLibraryStructure::new);
    public static final String TEMPLATE = "isekai_mahou:end_library/library";
    /** Altura do alicerce dentro do molde (o piso do salão fica em y+3). */
    private static final int FOUNDATION = 3;
    /** Abaixo disto é vazio (as ilhas externas ficam por volta de y 50–70). */
    private static final int MIN_GROUND = 40;
    private static final int MAX_SPREAD = 10;

    public EndLibraryStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        Rotation rot = Rotation.getRandom(ctx.random());
        StructurePoolElement element = StructurePoolElement.single(TEMPLATE).apply(StructureTemplatePool.Projection.RIGID);
        // Caixa do molde girado com o canto na origem → desloca para centralizar no meio do chunk.
        BoundingBox local = element.getBoundingBox(ctx.structureTemplateManager(), BlockPos.ZERO, rot);
        int mx = ctx.chunkPos().getMiddleBlockX();
        int mz = ctx.chunkPos().getMiddleBlockZ();
        int ox = mx - (local.minX() + local.maxX()) / 2;
        int oz = mz - (local.minZ() + local.maxZ()) / 2;
        int x0 = local.minX() + ox + 4, x1 = local.maxX() + ox - 4;
        int z0 = local.minZ() + oz + 4, z1 = local.maxZ() + oz - 4;
        int[][] probes = {{mx, mz}, {x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {mx, z0}, {mx, z1}, {x0, mz}, {x1, mz}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] p : probes) {
            int h = ctx.chunkGenerator().getFirstOccupiedHeight(p[0], p[1], Heightmap.Types.WORLD_SURFACE_WG,
                    ctx.heightAccessor(), ctx.randomState());
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        boolean forced = isForcedPlacement(ctx);
        int ground;
        if (min < MIN_GROUND || max - min > MAX_SPREAD) {
            if (!forced) return Optional.empty();
            int center = ctx.chunkGenerator().getFirstOccupiedHeight(mx, mz, Heightmap.Types.WORLD_SURFACE_WG,
                    ctx.heightAccessor(), ctx.randomState());
            ground = Math.max(center, 64);
        } else {
            // Mediana grosseira: o alicerce fica um pouco enterrado no lado alto e a adaptação de terreno cobre o baixo.
            ground = (min + max) / 2;
        }
        BlockPos pos = new BlockPos(ox, ground - FOUNDATION, oz);
        BoundingBox box = element.getBoundingBox(ctx.structureTemplateManager(), pos, rot);
        PoolElementStructurePiece piece = new PoolElementStructurePiece(ctx.structureTemplateManager(), element, pos,
                element.getGroundLevelDelta(), rot, box, LiquidSettings.IGNORE_WATERLOGGING);
        return Optional.of(new GenerationStub(new BlockPos(mx, ground, mz), builder -> builder.addPiece(piece)));
    }

    /** "/place structure" e o comando do módulo aceitam qualquer bioma (o teste de bioma recebe até o The Void). */
    private static boolean isForcedPlacement(GenerationContext ctx) {
        return ctx.registryAccess().registry(Registries.BIOME)
                .flatMap(r -> r.getHolder(Biomes.THE_VOID))
                .map(h -> ctx.validBiome().test(h))
                .orElse(false);
    }

    @Override
    public StructureType<?> type() {
        return ElRegistries.END_LIBRARY_STRUCTURE.get();
    }
}
