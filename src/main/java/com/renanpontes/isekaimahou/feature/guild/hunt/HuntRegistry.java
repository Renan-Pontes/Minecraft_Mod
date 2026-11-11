package com.renanpontes.isekaimahou.feature.guild.hunt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Registro das Áreas de Caça vivas no mundo ({@code data/isekai_mahou_hunting_grounds.dat} do Overworld): id → dono,
 * dimensão, centro e validade. É a fonte da verdade para a limpeza: mobs marcados e estandartes cujo id não está aqui
 * somem ao carregar; áreas vencidas (ou de missões que o dono já não tem) são apagadas mesmo com o dono offline.
 * Só na thread principal do servidor.
 */
public final class HuntRegistry extends SavedData {
    private static final String NAME = "isekai_mahou_hunting_grounds";

    public record Entry(UUID owner, ResourceKey<Level> dim, BlockPos center, long expires) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private static SavedData.Factory<HuntRegistry> factory() {
        return new SavedData.Factory<>(HuntRegistry::new, HuntRegistry::load, null);
    }

    public static HuntRegistry of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public void put(String id, Entry e) {
        entries.put(id, e);
        setDirty();
    }

    public void remove(String id) {
        if (entries.remove(id) != null) setDirty();
    }

    @Nullable
    public Entry get(String id) {
        return entries.get(id);
    }

    public boolean has(String id) {
        return entries.containsKey(id);
    }

    public void updateCenter(String id, BlockPos center) {
        Entry e = entries.get(id);
        if (e != null && !e.center().equals(center)) {
            entries.put(id, new Entry(e.owner(), e.dim(), center, e.expires()));
            setDirty();
        }
    }

    public List<Map.Entry<String, Entry>> snapshot() {
        return List.copyOf(entries.entrySet());
    }

    public int size() {
        return entries.size();
    }

    public int count(UUID owner) {
        int n = 0;
        for (Entry e : entries.values()) if (e.owner().equals(owner)) n++;
        return n;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        entries.forEach((id, e) -> {
            CompoundTag t = new CompoundTag();
            t.putString("id", id);
            t.putUUID("owner", e.owner());
            t.putString("dim", e.dim().location().toString());
            t.putLong("center", e.center().asLong());
            t.putLong("expires", e.expires());
            list.add(t);
        });
        tag.put("grounds", list);
        return tag;
    }

    private static HuntRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        HuntRegistry r = new HuntRegistry();
        ListTag list = tag.getList("grounds", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            String id = t.getString("id");
            if (dim == null || id.isBlank() || !t.hasUUID("owner")) {
                IsekaiMahou.LOGGER.warn("[guild] area de caca invalida ignorada: {}", t);
                continue;
            }
            r.entries.put(id, new Entry(t.getUUID("owner"), ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(t.getLong("center")),
                    t.getLong("expires")));
        }
        return r;
    }
}
