package com.renanpontes.isekaimahou.feature.auto_glyphs;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Progresso das Pedreiras (glifo Pedreira), por mundo. Chave: {@code "p:<pos do pedestal>"} para pedestais e
 * {@code "h:<x>,<z>,<raio>"} para quem conjura com a mão (a mesma coluna continua de onde parou).
 * Pedreiras de mão esquecidas somem depois de 7 dias de jogo; as de pedestal, depois de 60.
 */
public final class QuarryData extends SavedData {
    private static final String NAME = "isekai_mahou_quarries";
    private static final long HAND_EXPIRE = 24000L * 7;
    private static final long PEDESTAL_EXPIRE = 24000L * 60;

    /** Uma pedreira: área quadrada de meia-largura {@code r} em volta de (cx, cz), camada atual {@code layer}. */
    public static final class Quarry {
        public int cx, cz, r, top, layer, bottom;
        /** Conjurações seguidas em que a camada só tinha líquido voltando (água infinita do lado) */
        public int stuck;
        public boolean done, bedrock;
        public long last;
        public int mined;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Cx", cx);
            t.putInt("Cz", cz);
            t.putInt("R", r);
            t.putInt("Top", top);
            t.putInt("Layer", layer);
            t.putInt("Bottom", bottom);
            t.putInt("Stuck", stuck);
            t.putBoolean("Done", done);
            t.putBoolean("Bedrock", bedrock);
            t.putLong("Last", last);
            t.putInt("Mined", mined);
            return t;
        }

        static Quarry load(CompoundTag t) {
            Quarry q = new Quarry();
            q.cx = t.getInt("Cx");
            q.cz = t.getInt("Cz");
            q.r = t.getInt("R");
            q.top = t.getInt("Top");
            q.layer = t.getInt("Layer");
            q.bottom = t.contains("Bottom") ? t.getInt("Bottom") : q.top - 64;
            q.stuck = t.getInt("Stuck");
            q.done = t.getBoolean("Done");
            q.bedrock = t.getBoolean("Bedrock");
            q.last = t.getLong("Last");
            q.mined = t.getInt("Mined");
            return q;
        }
    }

    private final Map<String, Quarry> quarries = new HashMap<>();

    public static QuarryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(QuarryData::new, QuarryData::load), NAME);
    }

    public static String pedestalKey(BlockPos pedestal) {
        return "p:" + pedestal.asLong();
    }

    public static String handKey(int cx, int cz, int r) {
        return "h:" + cx + "," + cz + "," + r;
    }

    @Nullable
    public Quarry get(String key) {
        return quarries.get(key);
    }

    public void put(String key, Quarry q) {
        quarries.put(key, q);
        setDirty();
    }

    public boolean remove(String key) {
        boolean had = quarries.remove(key) != null;
        if (had) setDirty();
        return had;
    }

    public Map<String, Quarry> all() {
        return quarries;
    }

    /** Remove pedreiras esquecidas. */
    public void prune(long now) {
        Iterator<Map.Entry<String, Quarry>> it = quarries.entrySet().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Map.Entry<String, Quarry> e = it.next();
            long expire = e.getKey().startsWith("p:") ? PEDESTAL_EXPIRE : HAND_EXPIRE;
            if (now - e.getValue().last > expire) {
                it.remove();
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Quarry> e : quarries.entrySet()) {
            CompoundTag t = e.getValue().save();
            t.putString("Key", e.getKey());
            list.add(t);
        }
        tag.put("Quarries", list);
        return tag;
    }

    private static QuarryData load(CompoundTag tag, HolderLookup.Provider registries) {
        QuarryData d = new QuarryData();
        ListTag list = tag.getList("Quarries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            String key = t.getString("Key");
            if (!key.isEmpty()) d.quarries.put(key, Quarry.load(t));
        }
        return d;
    }
}
