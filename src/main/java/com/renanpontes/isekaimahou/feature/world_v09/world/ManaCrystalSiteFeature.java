package com.renanpontes.isekaimahou.feature.world_v09.world;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.world_v09.V09Registry;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Aglomerados de Cristal de Mana "perto de santuários": roda uma vez por chunk e só faz algo se o chunk tiver
 * referência a uma estrutura da tag {@code #isekai_mahou:mana_crystal_sites} (santuários elementais, Santuário de
 * Ascensão, portais...). Coloca 2–4 tufos em chão NATURAL (terra, areia, pedra, neve) — nunca no piso da estrutura.
 * Custo: um mapa vazio na imensa maioria dos chunks.
 */
public class ManaCrystalSiteFeature extends Feature<NoneFeatureConfiguration> {
    public static final TagKey<Structure> SITES = TagKey.create(Registries.STRUCTURE, IsekaiMahou.id("mana_crystal_sites"));

    public ManaCrystalSiteFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        ChunkAccess chunk = level.getChunk(ctx.origin());
        Map<Structure, LongSet> refs = chunk.getAllReferences();
        if (refs.isEmpty() || !touchesSite(level, refs)) return false;

        RandomSource r = ctx.random();
        ChunkPos cp = chunk.getPos();
        int placed = 0;
        int patches = 2 + r.nextInt(3);
        for (int i = 0; i < patches; i++) {
            int x = cp.getMinBlockX() + 2 + r.nextInt(12);
            int z = cp.getMinBlockZ() + 2 + r.nextInt(12);
            placed += patch(level, cp, x, z, r);
        }
        return placed > 0;
    }

    private static boolean touchesSite(WorldGenLevel level, Map<Structure, LongSet> refs) {
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Structure s : refs.keySet()) {
            if (reg.wrapAsHolder(s).is(SITES)) return true;
        }
        return false;
    }

    /** Um tufo de 2–5 aglomerados em volta de (x, z), dentro do chunk. */
    private static int patch(WorldGenLevel level, ChunkPos cp, int x, int z, RandomSource r) {
        BlockState cluster = V09Registry.MANA_CRYSTAL_CLUSTER.get().defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, Direction.UP);
        int want = 2 + r.nextInt(4);
        int placed = 0;
        for (int tries = 0; tries < 10 && placed < want; tries++) {
            int px = x + r.nextInt(5) - 2;
            int pz = z + r.nextInt(5) - 2;
            if (px < cp.getMinBlockX() || px > cp.getMaxBlockX() || pz < cp.getMinBlockZ() || pz > cp.getMaxBlockZ()) continue;
            // Nas features o *_WG não é mais atualizado (não vê a estrutura nem as árvores): usa o mapa final, sem folhas.
            BlockPos pos = new BlockPos(px, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz), pz);
            BlockPos below = pos.below();
            BlockState ground = level.getBlockState(below);
            BlockState here = level.getBlockState(pos);
            // Ar, ou capim/flor/neve fina (substituível e sem água) — o cristal toma o lugar
            boolean free = here.isAir() || (here.canBeReplaced() && here.getFluidState().isEmpty() && !here.hasBlockEntity());
            if (!free || !natural(ground) || !ground.isFaceSturdy(level, below, Direction.UP)) continue;
            if (!here.isAir() && here.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) continue;
            // "O veio aflorando": às vezes o chão vira calcita sob o cristal.
            if (r.nextInt(3) == 0) level.setBlock(below, Blocks.CALCITE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(pos, cluster, Block.UPDATE_CLIENTS);
            placed++;
        }
        return placed;
    }

    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CALCITE) || s.is(Blocks.COARSE_DIRT);
    }
}
