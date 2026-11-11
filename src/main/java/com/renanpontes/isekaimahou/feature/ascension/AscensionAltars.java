package com.renanpontes.isekaimahou.feature.ascension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Altares de Ascensão PÚBLICOS conhecidos (Núcleos com {@code PublicAltar:1b}, colocados por cidades, Academia e
 * Santuários). Cada Núcleo público se registra no 1º tick do servidor e sai ao ser removido. Guardado por nível
 * ({@code data/isekai_mahou_ascension_altars.dat}) e espelhado num mapa estático (só leitura em qualquer thread)
 * para a proteção de terreno e a busca do altar mais próximo. Poucos altares por mundo: listas simples bastam.
 */
public final class AscensionAltars extends SavedData {
    private static final String NAME = "isekai_mahou_ascension_altars";
    /** Espelho: dimensão → posições (conjunto concorrente). */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> MIRROR = new ConcurrentHashMap<>();

    private final Set<BlockPos> altars = ConcurrentHashMap.newKeySet();
    private ResourceKey<Level> dim;

    private AscensionAltars() {}

    private static SavedData.Factory<AscensionAltars> factory() {
        return new SavedData.Factory<>(AscensionAltars::new, AscensionAltars::load, null);
    }

    private static AscensionAltars load(CompoundTag tag, HolderLookup.Provider registries) {
        AscensionAltars d = new AscensionAltars();
        ListTag list = tag.getList("altars", Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof LongTag l) d.altars.add(BlockPos.of(l.getAsLong()));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BlockPos p : altars) list.add(LongTag.valueOf(p.asLong()));
        tag.put("altars", list);
        return tag;
    }

    /** Dados do nível (cria/carrega e atualiza o espelho). Só na thread do servidor. */
    public static AscensionAltars get(ServerLevel level) {
        AscensionAltars d = level.getDataStorage().computeIfAbsent(factory(), NAME);
        if (d.dim == null) {
            d.dim = level.dimension();
            MIRROR.put(level.dimension(), d.altars);
        }
        return d;
    }

    public static void add(ServerLevel level, BlockPos pos) {
        AscensionAltars d = get(level);
        if (d.altars.add(pos.immutable())) d.setDirty();
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        AscensionAltars d = get(level);
        if (d.altars.remove(pos)) d.setDirty();
    }

    /** Posições conhecidas nesta dimensão (cópia; vazia se o nível ainda não carregou os dados). */
    public static List<BlockPos> known(ResourceKey<Level> dim) {
        Set<BlockPos> s = MIRROR.get(dim);
        return s == null ? List.of() : new ArrayList<>(s);
    }

    /** Altar público conhecido mais próximo (horizontal) ou null. */
    @Nullable
    public static BlockPos nearest(ResourceKey<Level> dim, BlockPos from) {
        Set<BlockPos> s = MIRROR.get(dim);
        if (s == null) return null;
        BlockPos best = null;
        long bestD = Long.MAX_VALUE;
        for (BlockPos p : s) {
            long dx = p.getX() - from.getX(), dz = p.getZ() - from.getZ();
            long d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** Raio horizontal da área protegida em volta de um altar público (a maior barreira + 1 de folga). */
    public static final int AREA_RADIUS = AscensionPattern.MAX_BOUNDARY + 1;
    /** Altura protegida acima do Núcleo (o domo mais alto + 1). */
    public static final int AREA_UP = AscensionPattern.DOME_WALL + AscensionPattern.MAX_BOUNDARY + 1;

    /**
     * A posição faz parte da ÁREA de um altar público? v1.0: o círculo inteiro da maior barreira (raio
     * {@value #AREA_RADIUS}, de {@link AscensionPattern#BARRIER_BELOW} abaixo até {@value #AREA_UP} acima do Núcleo) —
     * ninguém constrói nem quebra ali fora do criativo, e explosões/magias/mobs não mexem nos blocos.
     * Qualquer thread (lê o espelho concorrente).
     */
    public static boolean covers(ResourceKey<Level> dim, BlockPos pos) {
        return coreCovering(dim, pos) != null;
    }

    /** Núcleo público cuja área contém {@code pos}, ou null. */
    @Nullable
    public static BlockPos coreCovering(ResourceKey<Level> dim, BlockPos pos) {
        Set<BlockPos> s = MIRROR.get(dim);
        if (s == null || s.isEmpty()) return null;
        long r2 = (long) AREA_RADIUS * AREA_RADIUS;
        for (BlockPos p : s) {
            long dx = p.getX() - pos.getX(), dz = p.getZ() - pos.getZ();
            if (dx * dx + dz * dz <= r2 && pos.getY() >= p.getY() - AscensionPattern.BARRIER_BELOW
                    && pos.getY() <= p.getY() + AREA_UP) return p;
        }
        return null;
    }

    /** Servidor parou: esquece o espelho (o próximo mundo carrega o seu). */
    public static void clearAll() {
        MIRROR.clear();
    }
}
