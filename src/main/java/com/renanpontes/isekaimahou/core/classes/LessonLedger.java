package com.renanpontes.isekaimahou.core.classes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * XP de professor guardado para quem estava OFFLINE quando um aluno aprendeu com a Lição dele
 * ({@code data/isekai_mahou_lessons.dat} do Overworld). Entregue no próximo login. Só na thread principal.
 */
final class LessonLedger extends SavedData {
    private static final String NAME = "isekai_mahou_lessons";

    /** Professor → [XP pendente, quantos alunos]. */
    private final Map<UUID, int[]> pending = new HashMap<>();

    private static SavedData.Factory<LessonLedger> factory() {
        return new SavedData.Factory<>(LessonLedger::new, LessonLedger::load, null);
    }

    static LessonLedger of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    void add(UUID teacher, int xp) {
        int[] v = pending.computeIfAbsent(teacher, k -> new int[2]);
        v[0] = (int) Math.min(Integer.MAX_VALUE, (long) v[0] + Math.max(0, xp));
        v[1]++;
        setDirty();
    }

    /** Tira o pendente do professor: [XP, alunos], ou null se não há nada. */
    int[] take(UUID teacher) {
        int[] v = pending.remove(teacher);
        if (v != null) setDirty();
        return v;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        pending.forEach((id, v) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("teacher", id);
            t.putInt("xp", v[0]);
            t.putInt("students", v[1]);
            list.add(t);
        });
        tag.put("pending", list);
        return tag;
    }

    private static LessonLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        LessonLedger d = new LessonLedger();
        ListTag list = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("teacher")) continue;
            d.pending.put(t.getUUID("teacher"), new int[]{Math.max(0, t.getInt("xp")), Math.max(0, t.getInt("students"))});
        }
        return d;
    }
}
