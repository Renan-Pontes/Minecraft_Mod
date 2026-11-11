package com.renanpontes.isekaimahou.feature.class_shinigami.inner;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Dados salvos do Mundo Interior ({@code data/isekai_mahou_inner_world.dat}, no Overworld): a faixa de cada jogador na
 * dimensão, a paisagem já construída, o espírito da espada e o ponto de volta (onde ele meditou).
 */
public final class InnerWorldData extends SavedData {
    private static final String NAME = "isekai_mahou_inner_world";

    /** Um Mundo Interior. {@code built} = paisagem construída (-1 nenhuma, 0 sem nome, 1..5 arquétipo). */
    public static final class Entry {
        public final int index;
        public int built = -1;
        @Nullable
        public UUID spirit;
        @Nullable
        public ResourceLocation returnDim;
        @Nullable
        public Vec3 returnPos;
        public float returnYaw;

        Entry(int index) {
            this.index = index;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private int next;

    public static InnerWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(InnerWorldData::new, InnerWorldData::load, null), NAME);
    }

    /** O Mundo Interior do jogador (cria uma faixa nova na primeira vez). */
    public Entry entry(UUID player) {
        Entry e = entries.get(player);
        if (e == null) {
            e = new Entry(next++);
            entries.put(player, e);
            setDirty();
        }
        return e;
    }

    @Nullable
    public Entry peek(UUID player) {
        return entries.get(player);
    }

    /** Dono da faixa {@code index} (ou null). */
    @Nullable
    public UUID ownerOf(int index) {
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) if (e.getValue().index == index) return e.getKey();
        return null;
    }

    private static InnerWorldData load(CompoundTag tag, HolderLookup.Provider lookup) {
        InnerWorldData d = new InnerWorldData();
        d.next = tag.getInt("Next");
        for (Tag t : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.hasUUID("Player")) continue;
            Entry e = new Entry(c.getInt("Index"));
            e.built = c.contains("Built") ? c.getInt("Built") : -1;
            if (c.hasUUID("Spirit")) e.spirit = c.getUUID("Spirit");
            if (c.contains("ReturnDim")) {
                e.returnDim = ResourceLocation.tryParse(c.getString("ReturnDim"));
                e.returnPos = new Vec3(c.getDouble("RX"), c.getDouble("RY"), c.getDouble("RZ"));
                e.returnYaw = c.getFloat("RYaw");
            }
            d.entries.put(c.getUUID("Player"), e);
            d.next = Math.max(d.next, e.index + 1);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("Next", next);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> me : entries.entrySet()) {
            Entry e = me.getValue();
            CompoundTag c = new CompoundTag();
            c.putUUID("Player", me.getKey());
            c.putInt("Index", e.index);
            c.putInt("Built", e.built);
            if (e.spirit != null) c.putUUID("Spirit", e.spirit);
            if (e.returnDim != null && e.returnPos != null) {
                c.putString("ReturnDim", e.returnDim.toString());
                c.putDouble("RX", e.returnPos.x);
                c.putDouble("RY", e.returnPos.y);
                c.putDouble("RZ", e.returnPos.z);
                c.putFloat("RYaw", e.returnYaw);
            }
            list.add(c);
        }
        tag.put("Entries", list);
        return tag;
    }
}
