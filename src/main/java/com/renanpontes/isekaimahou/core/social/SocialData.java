package com.renanpontes.isekaimahou.core.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Dados sociais do mundo (salvos no Overworld): grupos (party), lealdade dos jogadores a cidades, líderes das cidades e
 * guerras entre cidades. Tudo na thread do servidor.
 */
public final class SocialData extends SavedData {
    private static final String NAME = "isekai_mahou_social";

    /** Um grupo. {@code members} na ordem de entrada (o líder também está lá). {@code names}: último nome visto. */
    public static final class PartyRec {
        public final UUID id;
        public UUID leader;
        public final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        public final Map<UUID, String> names = new HashMap<>();

        PartyRec(UUID id, UUID leader) {
            this.id = id;
            this.leader = leader;
        }

        public String name(UUID member) {
            return names.getOrDefault(member, "?");
        }
    }

    /** Guerra entre duas cidades (ids em ordem alfabética), quem declarou e quando (tempo de jogo). */
    public record WarRec(String a, String b, @Nullable UUID declaredBy, String declaredByName, long since) {
        public boolean involves(String city) {
            return a.equals(city) || b.equals(city);
        }

        public String other(String city) {
            return a.equals(city) ? b : a;
        }
    }

    final Map<UUID, PartyRec> parties = new LinkedHashMap<>();
    final Map<UUID, UUID> partyOf = new HashMap<>();
    final Map<UUID, String> allegiance = new HashMap<>();
    final Map<String, UUID> leaders = new HashMap<>();
    final Map<String, WarRec> wars = new LinkedHashMap<>();

    @Nullable private static SocialData cached;

    static SavedData.Factory<SocialData> factory() {
        return new SavedData.Factory<>(SocialData::new, SocialData::load, null);
    }

    public static SocialData get(MinecraftServer server) {
        SocialData d = cached;
        if (d == null) {
            d = server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
            cached = d;
        }
        return d;
    }

    static void clearCache() {
        cached = null;
    }

    // =====================================================================================
    //  Grupos
    // =====================================================================================
    @Nullable
    public PartyRec partyOf(UUID player) {
        UUID id = partyOf.get(player);
        return id == null ? null : parties.get(id);
    }

    PartyRec create(UUID leader, String leaderName) {
        PartyRec p = new PartyRec(UUID.randomUUID(), leader);
        p.members.add(leader);
        p.names.put(leader, leaderName);
        parties.put(p.id, p);
        partyOf.put(leader, p.id);
        setDirty();
        return p;
    }

    void addMember(PartyRec p, UUID member, String name) {
        p.members.add(member);
        p.names.put(member, name);
        partyOf.put(member, p.id);
        setDirty();
    }

    void removeMember(PartyRec p, UUID member) {
        p.members.remove(member);
        p.names.remove(member);
        partyOf.remove(member);
        setDirty();
    }

    void disband(PartyRec p) {
        for (UUID m : p.members) partyOf.remove(m);
        p.members.clear();
        parties.remove(p.id);
        setDirty();
    }

    public List<PartyRec> parties() {
        return new ArrayList<>(parties.values());
    }

    // =====================================================================================
    //  Lealdade, líderes, guerras
    // =====================================================================================
    static String warKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    // =====================================================================================
    //  Salvar / carregar
    // =====================================================================================
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag ps = new ListTag();
        for (PartyRec p : parties.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", p.id);
            t.putUUID("leader", p.leader);
            ListTag ms = new ListTag();
            for (UUID m : p.members) {
                CompoundTag mt = new CompoundTag();
                mt.putUUID("id", m);
                mt.putString("name", p.name(m));
                ms.add(mt);
            }
            t.put("members", ms);
            ps.add(t);
        }
        tag.put("parties", ps);

        CompoundTag al = new CompoundTag();
        allegiance.forEach((id, city) -> al.putString(id.toString(), city));
        tag.put("allegiance", al);

        CompoundTag ld = new CompoundTag();
        leaders.forEach((city, id) -> ld.putUUID(city, id));
        tag.put("leaders", ld);

        ListTag ws = new ListTag();
        for (WarRec w : wars.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("a", w.a());
            t.putString("b", w.b());
            if (w.declaredBy() != null) t.putUUID("by", w.declaredBy());
            t.putString("by_name", w.declaredByName());
            t.putLong("since", w.since());
            ws.add(t);
        }
        tag.put("wars", ws);
        return tag;
    }

    static SocialData load(CompoundTag tag, HolderLookup.Provider registries) {
        SocialData d = new SocialData();
        for (Tag raw : tag.getList("parties", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("id") || !t.hasUUID("leader")) continue;
            PartyRec p = new PartyRec(t.getUUID("id"), t.getUUID("leader"));
            for (Tag mr : t.getList("members", Tag.TAG_COMPOUND)) {
                CompoundTag mt = (CompoundTag) mr;
                if (!mt.hasUUID("id")) continue;
                UUID m = mt.getUUID("id");
                // Um jogador só pode estar num grupo (save corrompido: fica no primeiro)
                if (d.partyOf.containsKey(m)) continue;
                p.members.add(m);
                p.names.put(m, mt.getString("name"));
                d.partyOf.put(m, p.id);
            }
            if (p.members.size() < 2) {
                for (UUID m : p.members) d.partyOf.remove(m);
                continue;
            }
            if (!p.members.contains(p.leader)) p.leader = p.members.iterator().next();
            d.parties.put(p.id, p);
        }
        CompoundTag al = tag.getCompound("allegiance");
        for (String k : al.getAllKeys()) {
            try {
                d.allegiance.put(UUID.fromString(k), al.getString(k));
            } catch (IllegalArgumentException ignored) {
                // chave inválida: descarta
            }
        }
        CompoundTag ld = tag.getCompound("leaders");
        for (String k : ld.getAllKeys()) {
            if (ld.hasUUID(k)) d.leaders.put(k, ld.getUUID(k));
        }
        for (Tag raw : tag.getList("wars", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            String a = t.getString("a"), b = t.getString("b");
            if (a.isEmpty() || b.isEmpty() || a.equals(b)) continue;
            WarRec w = new WarRec(a, b, t.hasUUID("by") ? t.getUUID("by") : null, t.getString("by_name"), t.getLong("since"));
            d.wars.put(warKey(a, b), w);
        }
        return d;
    }

    /** Conjunto de cidades em guerra com {@code city}. */
    Set<String> enemiesOf(String city) {
        Set<String> out = new LinkedHashSet<>();
        for (WarRec w : wars.values()) if (w.involves(city)) out.add(w.other(city));
        return out;
    }
}
