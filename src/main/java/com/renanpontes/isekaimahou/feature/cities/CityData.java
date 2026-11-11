package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Dados das cidades por nível ({@code data/isekai_mahou_cities.dat}): quantos NPCs de cada tipo a cidade tinha
 * (contagem original, feita quando cada NPC do template se liga à cidade), a fila de mortos a repor (1 por dia de
 * jogo, pelo coração da cidade) e o controle diário do Cerco.
 */
public class CityData extends SavedData {
    private static final String NAME = "isekai_mahou_cities";
    /** Fila de reposição limitada (cidade sendo massacrada não cresce sem fim). */
    private static final int MAX_QUEUE = 64;

    public record Dead(String key, BlockPos home) {}

    public static final class City {
        /** Contagem original por chave ("guard", "citizen", "lord", "merchant:&lt;profissão&gt;"). */
        public final Map<String, Integer> registered = new LinkedHashMap<>();
        public final List<Dead> dead = new ArrayList<>();
        public long lastRespawnDay = -1;
        public long lastSiegeRollDay = -1;
        public int sieges;
        public int siegesWon;

        public int total() {
            int t = 0;
            for (int v : registered.values()) t += v;
            return t;
        }
    }

    private final Map<String, City> cities = new HashMap<>();

    public static CityData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(CityData::new, CityData::load, null), NAME);
    }

    public City city(String id) {
        return cities.computeIfAbsent(id, k -> new City());
    }

    @Nullable
    public City existing(String id) {
        return cities.get(id);
    }

    public Map<String, City> all() {
        return cities;
    }

    /** Um NPC do template se ligou à cidade: conta na população original. */
    public void registerNpc(String cityId, String key) {
        city(cityId).registered.merge(key, 1, Integer::sum);
        setDirty();
    }

    /**
     * Um NPC ligado morreu: entra na fila de reposição. Se a fila estava vazia, o "relógio" de reposição recomeça hoje
     * — senão um morador morto de manhã voltaria segundos depois (o último dia de reposição podia ser bem antigo).
     */
    public void recordDeath(String cityId, String key, BlockPos home, long day) {
        City c = city(cityId);
        if (c.dead.isEmpty()) c.lastRespawnDay = Math.max(c.lastRespawnDay, day);
        if (c.dead.size() < MAX_QUEUE) c.dead.add(new Dead(key, home.immutable()));
        setDirty();
    }

    @Nullable
    public Dead pollDead(String cityId) {
        City c = cities.get(cityId);
        if (c == null || c.dead.isEmpty()) return null;
        setDirty();
        return c.dead.remove(0);
    }

    // =====================================================================================
    //  NBT
    // =====================================================================================
    private static CityData load(CompoundTag tag, HolderLookup.Provider registries) {
        CityData d = new CityData();
        CompoundTag all = tag.getCompound("Cities");
        for (String id : all.getAllKeys()) {
            CompoundTag t = all.getCompound(id);
            City c = new City();
            CompoundTag reg = t.getCompound("Registered");
            for (String k : reg.getAllKeys()) c.registered.put(k, reg.getInt(k));
            ListTag dead = t.getList("Dead", Tag.TAG_COMPOUND);
            for (int i = 0; i < dead.size(); i++) {
                CompoundTag e = dead.getCompound(i);
                NbtUtils.readBlockPos(e, "Home").ifPresent(pos -> c.dead.add(new Dead(e.getString("Key"), pos)));
            }
            c.lastRespawnDay = t.getLong("LastRespawnDay");
            c.lastSiegeRollDay = t.getLong("LastSiegeRollDay");
            c.sieges = t.getInt("Sieges");
            c.siegesWon = t.getInt("SiegesWon");
            d.cities.put(id, c);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        cities.forEach((id, c) -> {
            CompoundTag t = new CompoundTag();
            CompoundTag reg = new CompoundTag();
            c.registered.forEach(reg::putInt);
            t.put("Registered", reg);
            ListTag dead = new ListTag();
            for (Dead e : c.dead) {
                CompoundTag dt = new CompoundTag();
                dt.putString("Key", e.key());
                dt.put("Home", NbtUtils.writeBlockPos(e.home()));
                dead.add(dt);
            }
            t.put("Dead", dead);
            t.putLong("LastRespawnDay", c.lastRespawnDay);
            t.putLong("LastSiegeRollDay", c.lastSiegeRollDay);
            t.putInt("Sieges", c.sieges);
            t.putInt("SiegesWon", c.siegesWon);
            all.put(id, t);
        });
        tag.put("Cities", all);
        return tag;
    }
}
