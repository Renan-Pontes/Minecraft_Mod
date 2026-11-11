package com.renanpontes.isekaimahou.feature.cities.world;

import java.util.Locale;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * Paleta da cidade, escolhida pelo bioma do centro. O id é a pasta dos templates (structure/city/&lt;id&gt;/) e o
 * valor de "Palette" no city_heart. Os blocos daqui são os da parte feita em código (muralha, ruas, fundação) e
 * combinam com as paletas de tools/gen/city_build.py.
 */
public enum CityPalette {
    PLAINS("plains",
            new Weighted(Blocks.STONE_BRICKS, 70, Blocks.MOSSY_STONE_BRICKS, 12, Blocks.CRACKED_STONE_BRICKS, 10,
                    Blocks.COBBLESTONE, 8),
            Blocks.STONE_BRICKS, Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICK_STAIRS,
            Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.COBBLESTONE,
            new Weighted(Blocks.STONE_BRICKS, 50, Blocks.COBBLESTONE, 20, Blocks.ANDESITE, 15,
                    Blocks.MOSSY_STONE_BRICKS, 8, Blocks.CRACKED_STONE_BRICKS, 7),
            Blocks.SPRUCE_FENCE, Blocks.DIRT, Blocks.STONE, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH),
    DESERT("desert",
            new Weighted(Blocks.SANDSTONE, 40, Blocks.CUT_SANDSTONE, 35, Blocks.SMOOTH_SANDSTONE, 20,
                    Blocks.CHISELED_SANDSTONE, 5),
            Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE_WALL, Blocks.SANDSTONE_STAIRS,
            Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE,
            new Weighted(Blocks.SMOOTH_SANDSTONE, 42, Blocks.SANDSTONE, 25, Blocks.CUT_SANDSTONE, 25,
                    Blocks.CHISELED_SANDSTONE, 3, Blocks.SMOOTH_RED_SANDSTONE, 5),
            Blocks.JUNGLE_FENCE, Blocks.SAND, Blocks.SANDSTONE, Blocks.SAND, Blocks.SMOOTH_SANDSTONE),
    SNOWY("snowy",
            new Weighted(Blocks.STONE_BRICKS, 50, Blocks.COBBLESTONE, 25, Blocks.MOSSY_COBBLESTONE, 10,
                    Blocks.CRACKED_STONE_BRICKS, 10, Blocks.ANDESITE, 5),
            Blocks.STONE_BRICKS, Blocks.COBBLESTONE_WALL, Blocks.STONE_BRICK_STAIRS,
            Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.COBBLESTONE,
            new Weighted(Blocks.COBBLESTONE, 35, Blocks.STONE_BRICKS, 35, Blocks.ANDESITE, 10,
                    Blocks.MOSSY_COBBLESTONE, 10, Blocks.CRACKED_STONE_BRICKS, 10),
            Blocks.SPRUCE_FENCE, Blocks.DIRT, Blocks.STONE, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH);

    public final String id;
    /** Corpo da muralha (variado) e topo/ameias/fundação da muralha. */
    final Weighted wall;
    final BlockState wallTop;
    final BlockState parapet;
    final BlockState buttressCap;
    /** Avenida: faixa do meio, faixa de enfeite e meio-fio. */
    final BlockState avenueMain;
    final BlockState avenueAccent;
    final BlockState curb;
    final Weighted street;
    final BlockState lampPost;
    /** Terra logo abaixo do piso, enchimento fundo, superfície natural (taludes) e a estrada que sai do portão. */
    final BlockState subsoil;
    final BlockState core;
    final BlockState topsoil;
    final BlockState road;

    CityPalette(String id, Weighted wall, Block wallTop, Block parapet, Block buttressCap, Block avenueMain,
                Block avenueAccent, Block curb, Weighted street, Block lampPost, Block subsoil, Block core,
                Block topsoil, Block road) {
        this.id = id;
        this.wall = wall;
        this.wallTop = wallTop.defaultBlockState();
        this.parapet = parapet.defaultBlockState();
        this.buttressCap = buttressCap.defaultBlockState();
        this.avenueMain = avenueMain.defaultBlockState();
        this.avenueAccent = avenueAccent.defaultBlockState();
        this.curb = curb.defaultBlockState();
        this.street = street;
        this.lampPost = lampPost.defaultBlockState();
        this.subsoil = subsoil.defaultBlockState();
        this.core = core.defaultBlockState();
        this.topsoil = topsoil.defaultBlockState();
        this.road = road.defaultBlockState();
    }

    /**
     * Deserto/terras áridas (inclusive badlands arborizadas) → deserto; neve, taiga e frio → neve; o resto (planície,
     * floresta, savana e savana ventosa, prado, bosque de cerejeiras, bétulas antigas, selva esparsa) → planície.
     * O reino da cidade é o da paleta ({@link CityKingdom#forPalette}); as capitais fixam a paleta pelo reino.
     */
    public static CityPalette forBiome(Holder<Biome> biome) {
        if (biome.is(Tags.Biomes.IS_DESERT) || biome.is(Tags.Biomes.IS_BADLANDS)) return DESERT;
        if (biome.is(Tags.Biomes.IS_SNOWY) || biome.is(Tags.Biomes.IS_TAIGA) || biome.is(Tags.Biomes.IS_COLD_OVERWORLD)) {
            return SNOWY;
        }
        if (biome.is(Tags.Biomes.IS_SAVANNA)) return PLAINS;
        float t = biome.value().getBaseTemperature();
        if (t < 0.3F) return SNOWY;
        if (t >= 1.5F && !biome.value().hasPrecipitation()) return DESERT;
        return PLAINS;
    }

    public static CityPalette byId(String id) {
        for (CityPalette p : values()) {
            if (p.id.equals(id.toLowerCase(Locale.ROOT))) return p;
        }
        return PLAINS;
    }

    /** Sorteio com pesos usando um hash da posição (o mesmo bloco sai igual em qualquer chunk). */
    static final class Weighted {
        private final BlockState[] states;
        private final int[] cumulative;
        private final int total;

        Weighted(Object... blockWeightPairs) {
            int n = blockWeightPairs.length / 2;
            states = new BlockState[n];
            cumulative = new int[n];
            int sum = 0;
            for (int i = 0; i < n; i++) {
                states[i] = ((Block) blockWeightPairs[2 * i]).defaultBlockState();
                sum += (Integer) blockWeightPairs[2 * i + 1];
                cumulative[i] = sum;
            }
            total = sum;
        }

        BlockState pick(long hash) {
            int v = (int) Math.floorMod(hash, (long) total);
            for (int i = 0; i < states.length; i++) {
                if (v < cumulative[i]) return states[i];
            }
            return states[states.length - 1];
        }
    }
}
