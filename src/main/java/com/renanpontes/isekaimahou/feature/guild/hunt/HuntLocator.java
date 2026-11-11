package com.renanpontes.isekaimahou.feature.guild.hunt;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Escolhe onde fica uma Área de Caça: um ponto a {@link #MIN_DIST}–{@link #MAX_DIST} blocos do jogador, em TERRA firme
 * (acima do nível do mar), num bioma onde as criaturas da missão vivem e longe de cidades/vilas/outras áreas.
 * <p>
 * Não carrega chunks: lê só o gerador (bioma pelo ruído + altura estimada da superfície), como o {@code /locate biome}.
 * O Y verdadeiro é medido quando o jogador chega perto ({@link HuntingGrounds}).
 * <p>
 * Ordem: anéis cada vez mais longe (170–300, 300–420, 420–500), 20 tentativas por anel; se nenhum ponto tiver o bioma
 * certo, aceita qualquer terra firme (todas as criaturas da missão podem nascer); sem terra nenhuma → null.
 */
public final class HuntLocator {
    public static final int MIN_DIST = 150, MAX_DIST = 500;
    private static final int[][] RINGS = {{170, 300}, {300, 420}, {420, MAX_DIST}};
    private static final int TRIES_PER_RING = 20;
    /** Distância mínima (blocos) da borda de cidades, vilas e outras áreas. */
    private static final int PLACE_MARGIN = 64;

    /** Ponto escolhido: centro estimado, bioma do centro e as criaturas que nascem lá. */
    public record Result(BlockPos center, Holder<Biome> biome, List<EntityType<?>> mobs) {}

    @Nullable
    public static Result find(ServerLevel level, BlockPos origin, List<EntityType<?>> mobs, RandomSource rnd) {
        if (mobs.isEmpty()) return null;
        boolean anywhere = false;
        for (EntityType<?> t : mobs) anywhere |= HuntRules.universal(level, t);
        // 1) O bioma certo de alguma criatura (se nenhuma é "de qualquer lugar").
        if (!anywhere) {
            Result r = search(level, origin, mobs, rnd, false);
            if (r != null) return r;
        }
        // 2) Qualquer terra firme.
        return search(level, origin, mobs, rnd, true);
    }

    @Nullable
    private static Result search(ServerLevel level, BlockPos origin, List<EntityType<?>> mobs, RandomSource rnd, boolean anyLand) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        int sea = gen.getSeaLevel();
        List<Places.Place> places = Places.all(level);
        for (int[] ring : RINGS) {
            for (int i = 0; i < TRIES_PER_RING; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double dist = Mth.lerp(rnd.nextDouble(), ring[0], ring[1]);
                int x = origin.getX() + Mth.floor(Math.cos(ang) * dist);
                int z = origin.getZ() + Mth.floor(Math.sin(ang) * dist);
                if (!level.getWorldBorder().isWithinBounds(x, z)) continue;
                // Filtro barato: bioma um pouco acima do mar (sem calcular a altura).
                Holder<Biome> rough = level.getUncachedNoiseBiome(x >> 2, (sea + 6) >> 2, z >> 2);
                if (!HuntRules.isLand(rough) && !anyLand) continue;
                if (!anyLand && !fitsAny(level, mobs, rough)) continue;
                int h = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
                if (h <= sea + 1 || h >= level.getMaxBuildHeight() - 8) continue;
                Holder<Biome> biome = level.getUncachedNoiseBiome(x >> 2, (h + 1) >> 2, z >> 2);
                if (!HuntRules.isLand(biome)) continue;
                List<EntityType<?>> fit = new ArrayList<>();
                for (EntityType<?> t : mobs) if (HuntRules.universal(level, t) || HuntRules.fits(level, t, biome)) fit.add(t);
                if (fit.isEmpty()) {
                    if (!anyLand) continue;
                    fit.addAll(mobs);
                }
                BlockPos pos = new BlockPos(x, h, z);
                if (nearPlace(places, pos)) continue;
                return new Result(pos, biome, fit);
            }
        }
        return null;
    }

    private static boolean fitsAny(ServerLevel level, List<EntityType<?>> mobs, Holder<Biome> b) {
        for (EntityType<?> t : mobs) if (HuntRules.fits(level, t, b)) return true;
        return false;
    }

    /** Dentro (ou perto) de uma cidade, vila, pedra de viagem ou outra Área de Caça. */
    private static boolean nearPlace(List<Places.Place> places, BlockPos pos) {
        for (Places.Place p : places) {
            int margin = "hunting_ground".equals(p.type()) ? 24 : PLACE_MARGIN;
            var box = p.box();
            if (pos.getX() >= box.minX() - margin && pos.getX() <= box.maxX() + margin
                    && pos.getZ() >= box.minZ() - margin && pos.getZ() <= box.maxZ() + margin) return true;
        }
        return false;
    }

    private HuntLocator() {}
}
