package com.renanpontes.isekaimahou.feature.farm.build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Histórico do Desfazer: as últimas {@link #MAX} construções de cada jogador (varinha e glifos), só na memória do
 * servidor — sai ao deslogar e ao desligar o servidor.
 */
public final class UndoHistory {
    public static final int MAX = 3;

    public record Entry(BlockPos pos, BlockState before, BlockState after) {}

    public record Record(ResourceKey<Level> dimension, List<Entry> entries) {}

    private static final Map<UUID, Deque<Record>> HISTORY = new HashMap<>();

    public static synchronized void push(UUID owner, Record r) {
        Deque<Record> d = HISTORY.computeIfAbsent(owner, k -> new ArrayDeque<>());
        d.push(r);
        while (d.size() > MAX) d.removeLast();
    }

    @Nullable
    public static synchronized Record pop(UUID owner) {
        Deque<Record> d = HISTORY.get(owner);
        if (d == null || d.isEmpty()) return null;
        Record r = d.pop();
        if (d.isEmpty()) HISTORY.remove(owner);
        return r;
    }

    public static synchronized int size(UUID owner) {
        Deque<Record> d = HISTORY.get(owner);
        return d == null ? 0 : d.size();
    }

    /** Operações de desfazer (de trás para frente). */
    public static List<BuildJob.Op> undoOps(Record r) {
        List<BuildJob.Op> ops = new ArrayList<>(r.entries().size());
        for (int i = r.entries().size() - 1; i >= 0; i--) {
            Entry e = r.entries().get(i);
            ops.add(new BuildJob.Op(e.pos(), e.before(), BuildJob.Kind.UNDO, e.after().getBlock()));
        }
        return ops;
    }

    public static synchronized void forget(UUID owner) {
        HISTORY.remove(owner);
    }

    public static synchronized void clear() {
        HISTORY.clear();
    }

    private UndoHistory() {}
}
