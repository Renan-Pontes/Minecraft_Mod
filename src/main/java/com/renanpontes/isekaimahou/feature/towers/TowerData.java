package com.renanpontes.isekaimahou.feature.towers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Progresso salvo de cada Torre de Desafio do mundo (chave = chunk de início da estrutura): andares vencidos (bits) e se o
 * pináculo já foi conquistado. Usado pela barreira do pináculo e pela proteção das paredes.
 */
public final class TowerData extends SavedData {
    public static final TagKey<Structure> TOWERS = TagKey.create(Registries.STRUCTURE, IsekaiMahou.id("towers"));
    /** Sem torre (núcleo colocado fora de uma estrutura). */
    public static final long NONE = Long.MIN_VALUE;
    /** Andares internos (0..6); o 8º (7) é o pináculo. */
    public static final int FLOORS = 7;
    private static final String NAME = "isekai_mahou_towers";
    /** Camadas de ilha embaixo da Torre Flutuante (igual ao ISLAND_H de tools/gen/towers_struct.py). */
    public static final int FLOATING_BASE = 14;

    private final Map<Long, Integer> masks = new HashMap<>();
    private final Set<Long> conquered = new HashSet<>();

    public static TowerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TowerData::new, TowerData::load), NAME);
    }

    /** Estrutura de torre que contém a posição (pode ser inválida). */
    public static StructureStart towerAt(ServerLevel level, BlockPos pos) {
        return level.structureManager().getStructureWithPieceAt(pos, TOWERS);
    }

    public static long keyOf(StructureStart start) {
        return start.isValid() ? start.getChunkPos().toLong() : NONE;
    }

    /** Caminho do id da estrutura ("tower_stone", "tower_floating"...), ou "" se não der para saber. */
    public static String pathOf(ServerLevel level, StructureStart start) {
        if (!start.isValid()) return "";
        ResourceLocation key = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
        return key == null ? "" : key.getPath();
    }

    /**
     * Entrada da torre: em cima do Núcleo do térreo. Nas torres do chão o térreo começa na base da caixa; a Torre
     * Flutuante tem a ilha por baixo ({@link #FLOATING_BASE} camadas).
     */
    public static BlockPos entrance(ServerLevel level, StructureStart start) {
        BoundingBox bb = start.getBoundingBox();
        int base = "tower_floating".equals(pathOf(level, start)) ? FLOATING_BASE : 0;
        return new BlockPos(bb.getCenter().getX(), bb.minY() + base + 4, bb.getCenter().getZ());
    }

    public int mask(long key) {
        return masks.getOrDefault(key, 0);
    }

    /** Todos os andares abaixo de {@code floor} vencidos? */
    public boolean floorsBelowCleared(long key, int floor) {
        int need = (1 << floor) - 1;
        return (mask(key) & need) == need;
    }

    public void markFloor(long key, int floor) {
        if (key == NONE) return;
        masks.merge(key, 1 << floor, (a, b) -> a | b);
        setDirty();
    }

    public boolean isConquered(long key) {
        return conquered.contains(key);
    }

    public void setConquered(long key) {
        if (key != NONE && conquered.add(key)) setDirty();
    }

    /** Debug: esquece o progresso de uma torre. */
    public void forget(long key) {
        masks.remove(key);
        conquered.remove(key);
        setDirty();
    }

    private static TowerData load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerData d = new TowerData();
        for (Tag t : tag.getList("Towers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            long k = c.getLong("Key");
            if (c.getInt("Mask") != 0) d.masks.put(k, c.getInt("Mask"));
            if (c.getBoolean("Conquered")) d.conquered.add(k);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Set<Long> keys = new HashSet<>(masks.keySet());
        keys.addAll(conquered);
        ListTag list = new ListTag();
        for (long k : keys) {
            CompoundTag c = new CompoundTag();
            c.putLong("Key", k);
            c.putInt("Mask", mask(k));
            c.putBoolean("Conquered", conquered.contains(k));
            list.add(c);
        }
        tag.put("Towers", list);
        return tag;
    }
}
