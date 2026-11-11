package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Dados do módulo no Overworld ({@code data/isekai_mahou_settlements.dat}): as cidades governadas por jogadores
 * (próprias e conquistadas) e os Juramentos em andamento.
 */
public class SettlementData extends SavedData {
    private static final String NAME = "isekai_mahou_settlements";

    /** Juramento de um jogador para uma cidade de reino (missão antes do desafio no coração). */
    public static final class Oath {
        public final String city;
        public int kills;
        public int tribute;
        public int vigil;
        public boolean sealGiven;
        /** Tempo do jogo da última derrota no desafio (espera para tentar de novo). */
        public long lastFail = -1;

        public Oath(String city) {
            this.city = city;
        }

        public boolean complete() {
            return kills >= Conquest.KILLS_NEEDED && tribute >= Conquest.TRIBUTE_NEEDED && vigil >= Conquest.VIGIL_NEEDED;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("City", city);
            t.putInt("Kills", kills);
            t.putInt("Tribute", tribute);
            t.putInt("Vigil", vigil);
            t.putBoolean("Seal", sealGiven);
            t.putLong("LastFail", lastFail);
            return t;
        }

        static Oath load(CompoundTag t) {
            Oath o = new Oath(t.getString("City"));
            o.kills = t.getInt("Kills");
            o.tribute = t.getInt("Tribute");
            o.vigil = t.getInt("Vigil");
            o.sealGiven = t.getBoolean("Seal");
            o.lastFail = t.contains("LastFail") ? t.getLong("LastFail") : -1;
            return o;
        }
    }

    private final Map<String, Settlement> cities = new LinkedHashMap<>();
    private final Map<UUID, Oath> oaths = new HashMap<>();

    public static SettlementData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(SettlementData::new, SettlementData::load, null), NAME);
    }

    @Nullable
    public Settlement city(String id) {
        return cities.get(id);
    }

    public List<Settlement> all() {
        return new ArrayList<>(cities.values());
    }

    public void put(Settlement s) {
        cities.put(s.id, s);
        setDirty();
    }

    public void remove(String id) {
        if (cities.remove(id) != null) setDirty();
    }

    /** A cidade PRÓPRIA (fundada) do jogador, se houver. */
    @Nullable
    public Settlement ownedBy(UUID player) {
        for (Settlement s : cities.values()) if (s.own() && player.equals(s.owner)) return s;
        return null;
    }

    /** Cidades que o jogador governa (própria e conquistadas). */
    public List<Settlement> ruledBy(UUID player) {
        List<Settlement> out = new ArrayList<>();
        for (Settlement s : cities.values()) if (player.equals(s.owner)) out.add(s);
        return out;
    }

    @Nullable
    public Oath oath(UUID player) {
        return oaths.get(player);
    }

    public Oath startOath(UUID player, String city) {
        Oath o = new Oath(city);
        oaths.put(player, o);
        setDirty();
        return o;
    }

    public void endOath(UUID player) {
        if (oaths.remove(player) != null) setDirty();
    }

    public Map<UUID, Oath> oaths() {
        return oaths;
    }

    private static SettlementData load(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementData d = new SettlementData();
        ListTag l = tag.getList("Cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            Settlement s = Settlement.load(l.getCompound(i));
            if (s != null) d.cities.put(s.id, s);
        }
        CompoundTag o = tag.getCompound("Oaths");
        for (String k : o.getAllKeys()) {
            try {
                d.oaths.put(UUID.fromString(k), Oath.load(o.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
                // chave inválida: o juramento se perde (o jogador pode fazer de novo)
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (Settlement s : cities.values()) l.add(s.save());
        tag.put("Cities", l);
        CompoundTag o = new CompoundTag();
        oaths.forEach((u, oath) -> o.put(u.toString(), oath.save()));
        tag.put("Oaths", o);
        return tag;
    }

    /** As cidades mudam por dentro (campos públicos): sempre salvar. */
    @Override
    public boolean isDirty() {
        return true;
    }
}
