package com.renanpontes.isekaimahou.feature.structures_c;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Ponte do Troll. Não é jigsaw porque o jigsaw gira a peça ao acaso e a ponte acabaria correndo AO LONGO do rio.
 * Aqui um ponto de rio perto do chunk sorteado é medido (altura do fundo, ignorando a água) nos dois eixos e a
 * ponte é girada para atravessá-lo: cabeceiras em terra firme, arco sobre a água e o covil do troll na linha d'água.
 * <pre>{"type": "isekai_mahou:troll_bridge", "template": "isekai_mahou:troll_bridge/bridge", "water_line": 6, ...}</pre>
 * {@code water_line}: camada do .nbt que fica na superfície da água (o covil fica logo acima dela).
 */
public class TrollBridgeStructure extends Structure {
    public static final MapCodec<TrollBridgeStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            ResourceLocation.CODEC.fieldOf("template").forGetter(s -> s.template),
            Codec.intRange(0, 64).fieldOf("water_line").forGetter(s -> s.waterLine)
    ).apply(i, TrollBridgeStructure::new));

    /**
     * Altura máxima da margem acima do mar. A cabeceira do tabuleiro fica 1 bloco acima do mar: com margens até
     * +2 o degrau entre a margem e a ponte é no máximo 1 bloco (dá para subir pulando); mais alto, desiste.
     */
    private static final int MAX_BANK_RISE = 2;
    /** Quantos pontos de rio no máximo são medidos por chunk sorteado (cada medida custa ~40 colunas de terreno). */
    private static final int MAX_MEASURED = 4;
    /** Deslocamentos (x, z) da busca em volta do centro do chunk: grade de 8 em 8 até ±24, do centro para fora. */
    private static final int[][] SEARCH = searchGrid(24, 8);

    private static int[][] searchGrid(int radius, int step) {
        List<int[]> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) out.add(new int[] {dx, dz});
        }
        out.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return out.toArray(new int[0][]);
    }

    private final ResourceLocation template;
    private final int waterLine;

    public TrollBridgeStructure(StructureSettings settings, ResourceLocation template, int waterLine) {
        super(settings);
        this.template = template;
        this.waterLine = waterLine;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        StructureTemplate tpl = ctx.structureTemplateManager().getOrCreate(template);
        Vec3i size = tpl.getSize();
        if (size.getX() < 8 || size.getZ() < 1) return Optional.empty();   // .nbt ausente ou quebrado
        int half = size.getX() / 2;
        int sea = ctx.chunkGenerator().getSeaLevel();
        int surface = sea - 1;
        int mx = ctx.chunkPos().getMiddleBlockX();
        int mz = ctx.chunkPos().getMiddleBlockZ();

        // Rios são faixas finas: procura um ponto de travessia numa grade em volta do chunk sorteado (do mais
        // perto para o mais longe), senão quase nenhum chunk sorteado cairia em cima da água.
        int cx = 0;
        int cz = 0;
        Direction.Axis axis = null;
        int measured = 0;
        for (int[] off : SEARCH) {
            int x = mx + off[0];
            int z = mz + off[1];
            // Bioma primeiro (barato): só pontos de rio gastam tempo medindo o terreno.
            Holder<Biome> biome = ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface),
                    QuartPos.fromBlock(z), ctx.randomState().sampler());
            if (!ctx.validBiome().test(biome)) continue;
            // O centro (o arco) precisa de água de verdade embaixo.
            if (sea - floor(ctx, x, z) < 2) continue;
            axis = crossingAxis(ctx, x, z, sea, half);
            if (axis != null) {
                cx = x;
                cz = z;
                break;
            }
            if (++measured >= MAX_MEASURED) break;
        }
        if (axis == null) return Optional.empty();

        boolean flip = ctx.random().nextBoolean();
        Rotation rotation = axis == Direction.Axis.X
                ? (flip ? Rotation.NONE : Rotation.CLOCKWISE_180)
                : (flip ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90);
        // Origem do .nbt tal que o centro do molde caia no ponto de travessia depois de girar.
        BlockPos center = StructureTemplate.transform(new BlockPos(half, 0, size.getZ() / 2), Mirror.NONE, rotation, BlockPos.ZERO);
        BlockPos origin = new BlockPos(cx - center.getX(), surface - waterLine, cz - center.getZ());
        return Optional.of(new GenerationStub(new BlockPos(cx, surface, cz),
                builder -> builder.addPiece(new TrollBridgePiece(ctx.structureTemplateManager(), template, origin, rotation))));
    }

    /**
     * Eixo em que a ponte atravessa o rio: as duas cabeceiras em terra firme (nem baixa nem alta demais) e,
     * entre os eixos que servem, o com menos água no meio (o caminho mais curto de margem a margem).
     */
    @Nullable
    private static Direction.Axis crossingAxis(GenerationContext ctx, int cx, int cz, int sea, int half) {
        Direction.Axis best = null;
        int bestWet = Integer.MAX_VALUE;
        for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
            int ax = axis == Direction.Axis.X ? 1 : 0;
            int az = 1 - ax;
            boolean banks = true;
            for (int d : new int[] {half - 2, half}) {
                if (!bank(ctx, cx + ax * d, cz + az * d, sea) || !bank(ctx, cx - ax * d, cz - az * d, sea)) {
                    banks = false;
                    break;
                }
            }
            if (!banks) continue;
            int wet = 0;
            for (int d = -(half - 4); d <= half - 4; d += 2) {
                if (floor(ctx, cx + ax * d, cz + az * d) < sea) wet++;
            }
            if (wet < bestWet) {
                bestWet = wet;
                best = axis;
            }
        }
        return best;
    }

    /** Terra firme na altura certa para a cabeceira da ponte. */
    private static boolean bank(GenerationContext ctx, int x, int z, int sea) {
        int f = floor(ctx, x, z);
        return f >= sea && f <= sea + MAX_BANK_RISE;
    }

    /** Primeira altura livre acima do chão sólido, ignorando a água. */
    private static int floor(GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
    }

    @Override
    public StructureType<?> type() {
        return ScRegistries.TROLL_BRIDGE_TYPE.get();
    }
}
