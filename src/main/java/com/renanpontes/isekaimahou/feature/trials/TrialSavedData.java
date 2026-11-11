package com.renanpontes.isekaimahou.feature.trials;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Estado das Câmaras que precisa sobreviver a um reinício/queda do servidor (guardado no Overworld):
 * <ul>
 *   <li>{@code active}: instâncias abertas (índice → elemento/rank). No boot, todas viram "sujas" (abortadas).</li>
 *   <li>{@code dirty}: índices cujos blocos ainda precisam ser apagados antes de reusar.</li>
 *   <li>{@code returns}: ponto de retorno de cada jogador que entrou (dimensão, posição, olhar) e o modo de jogo a
 *       restaurar se ele estava "Abatido" (espectador) — usado na evacuação ao entrar no mundo.</li>
 * </ul>
 */
public class TrialSavedData extends SavedData {
    private static final String NAME = "isekai_mahou_trials";

    /** Ponto de retorno: {@code prevMode} = id do GameType a restaurar (−1 = nenhum). */
    public record ReturnPoint(ResourceKey<Level> dim, Vec3 pos, float yaw, int prevMode) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("dim", dim.location().toString());
            t.putDouble("x", pos.x);
            t.putDouble("y", pos.y);
            t.putDouble("z", pos.z);
            t.putFloat("yaw", yaw);
            t.putInt("mode", prevMode);
            return t;
        }

        @Nullable
        static ReturnPoint load(CompoundTag t) {
            ResourceLocation rl = ResourceLocation.tryParse(t.getString("dim"));
            if (rl == null) return null;
            return new ReturnPoint(ResourceKey.create(Registries.DIMENSION, rl), new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z")),
                    t.getFloat("yaw"), t.contains("mode") ? t.getInt("mode") : -1);
        }

        public ReturnPoint withMode(int mode) {
            return new ReturnPoint(dim, pos, yaw, mode);
        }
    }

    final Map<Integer, CompoundTag> active = new TreeMap<>();
    final TreeSet<Integer> dirty = new TreeSet<>();
    final Map<UUID, ReturnPoint> returns = new HashMap<>();

    public static TrialSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TrialSavedData::new, TrialSavedData::load), NAME);
    }

    private static TrialSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TrialSavedData d = new TrialSavedData();
        CompoundTag act = tag.getCompound("active");
        for (String k : act.getAllKeys()) {
            try {
                d.active.put(Integer.parseInt(k), act.getCompound(k));
            } catch (NumberFormatException ignored) {
                // chave estranha (edição manual): ignora
            }
        }
        for (Tag t : tag.getList("dirty", Tag.TAG_INT)) d.dirty.add(((IntTag) t).getAsInt());
        CompoundTag ret = tag.getCompound("returns");
        for (String k : ret.getAllKeys()) {
            try {
                ReturnPoint rp = ReturnPoint.load(ret.getCompound(k));
                if (rp != null) d.returns.put(UUID.fromString(k), rp);
            } catch (IllegalArgumentException ignored) {
                // UUID inválido: ignora
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag act = new CompoundTag();
        active.forEach((k, v) -> act.put(String.valueOf(k), v.copy()));
        tag.put("active", act);
        ListTag dl = new ListTag();
        for (int i : dirty) dl.add(IntTag.valueOf(i));
        tag.put("dirty", dl);
        CompoundTag ret = new CompoundTag();
        returns.forEach((k, v) -> ret.put(k.toString(), v.save()));
        tag.put("returns", ret);
        return tag;
    }

    // ------------------------------------------------------------------------------------------------ instâncias
    void putActive(int index, CompoundTag info) {
        active.put(index, info);
        setDirty();
    }

    /** A instância fechou: os blocos dela ainda precisam ser apagados. */
    void retire(int index) {
        active.remove(index);
        dirty.add(index);
        setDirty();
    }

    void cleaned(int index) {
        if (dirty.remove(index)) setDirty();
    }

    boolean isFree(int index) {
        return !active.containsKey(index) && !dirty.contains(index);
    }

    /** Menor índice livre (nem ativo nem esperando limpeza). */
    int freeIndex() {
        int i = 0;
        while (!isFree(i)) i++;
        return i;
    }

    // ------------------------------------------------------------------------------------------------ retornos
    void putReturn(UUID player, ReturnPoint rp) {
        returns.put(player, rp);
        setDirty();
    }

    @Nullable
    ReturnPoint takeReturn(UUID player) {
        ReturnPoint rp = returns.remove(player);
        if (rp != null) setDirty();
        return rp;
    }

    /** Grava (ou limpa com −1) o modo de jogo a restaurar, mantendo o ponto de retorno. */
    void setPrevMode(UUID player, int mode) {
        ReturnPoint rp = returns.get(player);
        if (rp != null && rp.prevMode() != mode) {
            returns.put(player, rp.withMode(mode));
            setDirty();
        }
    }
}
