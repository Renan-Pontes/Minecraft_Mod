package com.renanpontes.isekaimahou.feature.lore;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Onde ficam os lugares de que os fragmentos falam:
 * <ul>
 *   <li><b>estruturas</b>: busca no mapa igual ao {@code /locate}, num alcance limitado (só quando o jogador lê um
 *       fragmento ou pede "procurar de novo" — nunca por tick);</li>
 *   <li><b>tesouros</b>: um ponto no ermo, longe de lugares protegidos; o barril só é enterrado quando o jogador
 *       chega (chunk carregado), em chão natural — nunca em cidade nem em construção de jogador.</li>
 * </ul>
 */
final class LoreLocator {
    /** Alcance desejado da busca de estruturas, em chunks. */
    private static final int SEARCH_CHUNKS = 160;
    /** Folga em volta dos lugares protegidos onde nenhum tesouro é marcado. */
    private static final int PLACE_MARGIN = 32;
    static final ResourceKey<LootTable> TREASURE_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            IsekaiMahou.id("chests/lore_treasure"));

    /** @param exactY a altura é a do chão de verdade (chunk carregado)? */
    record Found(BlockPos pos, boolean exactY) {}

    /** Estrutura mais próxima da lista (ids do mod em texto). Nunca derruba o servidor: sem resultado = null. */
    @Nullable
    static Found structure(ServerLevel level, List<String> structures, BlockPos from) {
        try {
            Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
            List<Holder<Structure>> usable = new ArrayList<>();
            int minSpacing = Integer.MAX_VALUE;
            for (String s : structures) {
                Holder<Structure> h = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, IsekaiMahou.id(s))).orElse(null);
                if (h == null) continue;
                List<StructurePlacement> placements = state.getPlacementsForStructure(h);
                if (placements.isEmpty()) continue;
                usable.add(h);
                for (StructurePlacement sp : placements) {
                    if (sp instanceof RandomSpreadStructurePlacement spread) minSpacing = Math.min(minSpacing, Math.max(1, spread.spacing()));
                }
            }
            if (usable.isEmpty()) return null;
            int spacing = minSpacing == Integer.MAX_VALUE ? 32 : minSpacing;
            int rings = Mth.clamp(Mth.ceil(SEARCH_CHUNKS / (float) spacing), 2, 8);
            Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, HolderSet.direct(usable), from, rings, false);
            if (result == null) return null;
            BlockPos pos = result.getFirst();
            return refine(level, new BlockPos(pos.getX(), from.getY(), pos.getZ()));
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.debug("Lore: falha ao procurar {}", structures, e);
            return null;
        }
    }

    /** Põe a altura do chão quando o chunk está carregado (senão a posição fica como veio). */
    static Found refine(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return new Found(pos, false);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return new Found(new BlockPos(pos.getX(), Math.max(level.getMinBuildHeight() + 1, y), pos.getZ()), true);
    }

    // ------------------------------------------------------------------ tesouros
    /**
     * Sorteia o ponto de um tesouro entre {@code min} e {@code max} blocos de {@code origin}, longe de lugares
     * registrados (só X/Z valem; a altura sai ao chegar).
     */
    @Nullable
    static BlockPos treasureSpot(ServerLevel level, BlockPos origin, RandomSource random, int min, int max) {
        List<Places.Place> places = Places.all(level);
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = min + random.nextInt(Math.max(1, max - min + 1));
            BlockPos pos = new BlockPos(origin.getX() + (int) Math.round(Math.cos(angle) * dist), origin.getY(),
                    origin.getZ() + (int) Math.round(Math.sin(angle) * dist));
            if (!level.getWorldBorder().isWithinBounds(pos)) continue;
            if (!nearPlace(places, pos)) return pos;
        }
        return null;
    }

    /** Dentro (ou colado) de algum lugar registrado — cidade, vila, covil, portal? Compara só X/Z. */
    private static boolean nearPlace(List<Places.Place> places, BlockPos pos) {
        for (Places.Place p : places) {
            var b = p.box();
            if (pos.getX() >= b.minX() - PLACE_MARGIN && pos.getX() <= b.maxX() + PLACE_MARGIN
                    && pos.getZ() >= b.minZ() - PLACE_MARGIN && pos.getZ() <= b.maxZ() + PLACE_MARGIN) return true;
        }
        return false;
    }

    /**
     * Enterra o barril do tesouro perto de (x, z): procura em espiral uma coluna de chão NATURAL (terra, pedra, areia…)
     * fora de zona segura. O barril troca o bloco logo ABAIXO do chão — a superfície fica intacta.
     *
     * @return onde ficou, ou null se não há chão natural por perto (o chamador sorteia outro ponto)
     */
    @Nullable
    static BlockPos bury(ServerLevel level, int x, int z, RandomSource random) {
        if (LoreService.blocksForbidden(level)) return null;
        for (int r = 0; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos spot = buryAt(level, x + dx, z + dz, random);
                    if (spot != null) return spot;
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos buryAt(ServerLevel level, int x, int z, RandomSource random) {
        BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
        if (!level.isLoaded(probe)) return null;
        int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        BlockPos ground = new BlockPos(x, top - 1, z);
        BlockPos chest = ground.below();
        if (chest.getY() <= level.getMinBuildHeight() + 2) return null;
        if (!natural(level.getBlockState(ground)) || !natural(level.getBlockState(chest))) return null;
        if (Places.isSafe(level, ground) || Places.at(level, ground) != null) return null;
        level.setBlock(chest, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), 3);
        if (level.getBlockEntity(chest) instanceof RandomizableContainerBlockEntity be) {
            be.setLootTable(TREASURE_LOOT);
            be.setLootTableSeed(random.nextLong());
        }
        return chest;
    }

    /** Chão que o mundo gerou sozinho (nunca tábua, tijolo, vidro… de alguém). */
    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
                || s.is(Blocks.CLAY) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.CALCITE);
    }

    private LoreLocator() {}
}
