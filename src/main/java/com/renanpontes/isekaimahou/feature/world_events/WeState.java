package com.renanpontes.isekaimahou.feature.world_events;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Estado salvo dos eventos do mundo (no overworld): evento ativo, quando acaba, o lugar, os sorteios já feitos,
 * as opções do admin e os chunks onde jogadores já colocaram blocos (nenhum evento mexe no terreno ali).
 */
public final class WeState extends SavedData {
    private static final String NAME = "isekai_mahou_world_events";
    /** Teto de chunks lembrados (cada um custa 8 bytes): mais que isso e paramos de anotar. */
    private static final int MAX_BUILT = 200_000;

    /** Id do evento ativo ("" = nenhum). */
    public String active = "";
    public long startedAt;
    public long endsAt;
    @Nullable public BlockPos site;
    /** Dados próprios do evento ativo (fase, fendas...). */
    public CompoundTag extra = new CompoundTag();

    /** Último dia em que o sorteio do dia/da noite foi feito (um por dia cada). */
    long rolledDay = -1;
    long rolledNight = -1;
    /** Antes deste dia nenhum evento começa sozinho (descanso entre eventos). */
    long nextAllowedDay;
    /** Sorteio automático ligado? */
    boolean auto = true;
    /** Multiplicador das chances, em % (100 = normal). */
    int chancePct = 100;

    private final LongOpenHashSet builtChunks = new LongOpenHashSet();

    public static WeState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WeState::new, WeState::load, null), NAME);
    }

    @Nullable
    public WorldEventType type() {
        return active.isEmpty() ? null : WorldEventType.byId(active);
    }

    void begin(WorldEventType type, long now, long endsAt, @Nullable BlockPos site) {
        this.active = type.id;
        this.startedAt = now;
        this.endsAt = endsAt;
        this.site = site;
        this.extra = new CompoundTag();
        setDirty();
    }

    void clear() {
        this.active = "";
        this.site = null;
        this.extra = new CompoundTag();
        setDirty();
    }

    // ------------------------------------------------------------------ chunks com blocos de jogador
    void markBuilt(long chunk) {
        if (builtChunks.size() >= MAX_BUILT) return;
        if (builtChunks.add(chunk)) setDirty();
    }

    public boolean isBuilt(long chunk) {
        return builtChunks.contains(chunk);
    }

    int builtCount() {
        return builtChunks.size();
    }

    // ------------------------------------------------------------------ NBT
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("active", active);
        tag.putLong("startedAt", startedAt);
        tag.putLong("endsAt", endsAt);
        if (site != null) tag.putLong("site", site.asLong());
        tag.put("extra", extra.copy());
        tag.putLong("rolledDay", rolledDay);
        tag.putLong("rolledNight", rolledNight);
        tag.putLong("nextAllowedDay", nextAllowedDay);
        tag.putBoolean("auto", auto);
        tag.putInt("chancePct", chancePct);
        tag.putLongArray("built", builtChunks.toLongArray());
        return tag;
    }

    private static WeState load(CompoundTag tag, HolderLookup.Provider registries) {
        WeState s = new WeState();
        s.active = tag.getString("active");
        s.startedAt = tag.getLong("startedAt");
        s.endsAt = tag.getLong("endsAt");
        s.site = tag.contains("site") ? BlockPos.of(tag.getLong("site")) : null;
        s.extra = tag.getCompound("extra").copy();
        s.rolledDay = tag.contains("rolledDay") ? tag.getLong("rolledDay") : -1;
        s.rolledNight = tag.contains("rolledNight") ? tag.getLong("rolledNight") : -1;
        s.nextAllowedDay = tag.getLong("nextAllowedDay");
        s.auto = !tag.contains("auto") || tag.getBoolean("auto");
        s.chancePct = tag.contains("chancePct") ? tag.getInt("chancePct") : 100;
        for (long c : tag.getLongArray("built")) s.builtChunks.add(c);
        if (s.type() == null) s.active = "";
        return s;
    }
}
