package com.renanpontes.isekaimahou.feature.las_noches;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Estado de Las Noches no Mundo Oco (SavedData do reino): construção em andamento (para continuar depois de um reinício),
 * centro da cúpula, versão da planta e as Espada derrotadas (voltam um dia de jogo depois).
 */
public final class LnData extends SavedData {
    /** Sobe quando a planta muda de um jeito que pede reconstrução nos mundos antigos. */
    public static final int LAYOUT_VERSION = 1;
    public static final int NONE = 0, BUILDING = 1, DONE = 2;
    private static final String NAME = "isekai_mahou_las_noches";

    int stage = NONE, version, chunkIndex, columnIndex;
    @Nullable BlockPos center;
    final Map<String, Long> defeatedAt = new HashMap<>();
    final Map<String, UUID> espada = new HashMap<>();

    static LnData load(CompoundTag tag, HolderLookup.Provider registries) {
        LnData d = new LnData();
        d.stage = tag.getInt("stage");
        d.version = tag.getInt("version");
        d.chunkIndex = tag.getInt("chunk");
        d.columnIndex = tag.getInt("column");
        d.center = NbtUtils.readBlockPos(tag, "center").orElse(null);
        CompoundTag def = tag.getCompound("defeated");
        for (String k : def.getAllKeys()) d.defeatedAt.put(k, def.getLong(k));
        CompoundTag ids = tag.getCompound("espada");
        for (String k : ids.getAllKeys()) {
            if (ids.hasUUID(k)) d.espada.put(k, ids.getUUID(k));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("stage", stage);
        tag.putInt("version", version);
        tag.putInt("chunk", chunkIndex);
        tag.putInt("column", columnIndex);
        if (center != null) tag.put("center", NbtUtils.writeBlockPos(center));
        CompoundTag def = new CompoundTag();
        defeatedAt.forEach(def::putLong);
        tag.put("defeated", def);
        CompoundTag ids = new CompoundTag();
        espada.forEach(ids::putUUID);
        tag.put("espada", ids);
        return tag;
    }

    private static final SavedData.Factory<LnData> FACTORY = new SavedData.Factory<>(LnData::new, LnData::load, null);

    public static LnData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public boolean built() {
        return stage == DONE && center != null;
    }

    @Nullable
    public BlockPos center() {
        return center;
    }
}
