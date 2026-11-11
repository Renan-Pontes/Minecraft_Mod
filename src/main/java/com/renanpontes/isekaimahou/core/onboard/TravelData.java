package com.renanpontes.isekaimahou.core.onboard;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Pedras de Teleporte registradas por lugar ({@code data/isekai_mahou_travel.dat} do Overworld): id do lugar →
 * posição da pedra (ponto de chegada). Só na thread principal.
 */
final class TravelData extends SavedData {
    private static final String NAME = "isekai_mahou_travel";
    private final Map<String, GlobalPos> stones = new LinkedHashMap<>();

    private static SavedData.Factory<TravelData> factory() {
        return new SavedData.Factory<>(TravelData::new, TravelData::load, null);
    }

    static TravelData of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    void put(String placeId, GlobalPos pos) {
        if (pos.equals(stones.get(placeId))) return;
        stones.put(placeId, pos);
        setDirty();
    }

    void removeAt(GlobalPos pos) {
        if (stones.values().removeIf(pos::equals)) setDirty();
    }

    @Nullable
    GlobalPos get(String placeId) {
        return stones.get(placeId);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        stones.forEach((id, pos) -> {
            CompoundTag t = new CompoundTag();
            t.putString("place", id);
            t.putString("dim", pos.dimension().location().toString());
            t.putLong("pos", pos.pos().asLong());
            list.add(t);
        });
        tag.put("stones", list);
        return tag;
    }

    private static TravelData load(CompoundTag tag, HolderLookup.Provider registries) {
        TravelData d = new TravelData();
        ListTag list = tag.getList("stones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            String id = t.getString("place");
            if (dim == null || id.isBlank()) {
                IsekaiMahou.LOGGER.warn("[travel] pedra inválida ignorada: {}", t);
                continue;
            }
            d.stones.put(id, GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(t.getLong("pos"))));
        }
        return d;
    }
}
