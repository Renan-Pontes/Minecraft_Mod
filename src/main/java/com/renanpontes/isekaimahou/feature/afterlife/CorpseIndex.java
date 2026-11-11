package com.renanpontes.isekaimahou.feature.afterlife;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Índice dos Corpos do mundo (SavedData no Overworld): só para o rastreador/marcador saber onde está o corpo de cada
 * jogador mesmo com o chunk descarregado. Os ITENS ficam no próprio Corpo (salvo com o chunk), nunca aqui.
 */
public final class CorpseIndex extends SavedData {
    private static final String NAME = "isekai_mahou_afterlife";

    public record Entry(UUID corpse, UUID owner, ResourceKey<Level> dim, BlockPos pos, long created, boolean charred) {}

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    private static CorpseIndex load(CompoundTag tag, HolderLookup.Provider provider) {
        CorpseIndex idx = new CorpseIndex();
        ListTag list = tag.getList("corpses", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(c.getString("dim"));
            if (!c.hasUUID("id") || !c.hasUUID("owner") || dim == null) continue;
            Entry e = new Entry(c.getUUID("id"), c.getUUID("owner"), ResourceKey.create(Registries.DIMENSION, dim),
                    BlockPos.of(c.getLong("pos")), c.getLong("created"), c.getBoolean("charred"));
            idx.entries.put(e.corpse(), e);
        }
        return idx;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.corpse());
            c.putUUID("owner", e.owner());
            c.putString("dim", e.dim().location().toString());
            c.putLong("pos", e.pos().asLong());
            c.putLong("created", e.created());
            c.putBoolean("charred", e.charred());
            list.add(c);
        }
        tag.put("corpses", list);
        return tag;
    }

    public static CorpseIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CorpseIndex::new, CorpseIndex::load, null), NAME);
    }

    public static void put(MinecraftServer server, Entry e) {
        CorpseIndex idx = get(server);
        idx.entries.put(e.corpse(), e);
        idx.setDirty();
        ServerPlayer owner = server.getPlayerList().getPlayer(e.owner());
        if (owner != null) sendMark(owner);
    }

    public static void remove(MinecraftServer server, UUID corpse) {
        CorpseIndex idx = get(server);
        Entry e = idx.entries.remove(corpse);
        if (e == null) return;
        idx.setDirty();
        ServerPlayer owner = server.getPlayerList().getPlayer(e.owner());
        if (owner != null) sendMark(owner);
    }

    /** Corpo mais recente do jogador (null = nenhum). */
    @Nullable
    public static Entry latest(MinecraftServer server, UUID owner) {
        Entry best = null;
        for (Entry e : get(server).entries.values()) {
            if (e.owner().equals(owner) && (best == null || e.created() >= best.created())) best = e;
        }
        return best;
    }

    public static int count(MinecraftServer server, UUID owner) {
        int n = 0;
        for (Entry e : get(server).entries.values()) if (e.owner().equals(owner)) n++;
        return n;
    }

    public static void sendMark(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Entry e = latest(server, player.getUUID());
        AfterlifeNet.Mark mark = e == null ? AfterlifeNet.Mark.NONE
                : new AfterlifeNet.Mark(true, e.dim().location(), e.pos(), e.charred(), count(server, player.getUUID()));
        PacketDistributor.sendToPlayer(player, mark);
    }

    /**
     * Limpa entradas órfãs (o corpo sumiu por fora: edição do mundo, outro mod) quando o dono está perto do lugar, com as
     * entidades daquele chunk já carregadas, e o corpo não existe.
     */
    public static void pruneNear(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel level = player.serverLevel();
        List<UUID> gone = new ArrayList<>();
        for (Entry e : get(server).entries.values()) {
            if (!e.owner().equals(player.getUUID()) || !e.dim().equals(level.dimension())) continue;
            if (e.pos().distSqr(player.blockPosition()) > 12 * 12) continue;
            if (!level.areEntitiesLoaded(ChunkPos.asLong(e.pos()))) continue;
            if (level.getEntity(e.corpse()) == null) gone.add(e.corpse());
        }
        for (UUID id : gone) remove(server, id);
    }

    /** Atualiza a posição guardada (o corpo é fixo, mas um /tp de admin pode movê-lo). */
    public static void move(MinecraftServer server, UUID corpse, BlockPos pos) {
        CorpseIndex idx = get(server);
        Entry e = idx.entries.get(corpse);
        if (e == null || e.pos().equals(pos)) return;
        idx.entries.put(corpse, new Entry(e.corpse(), e.owner(), e.dim(), pos, e.created(), e.charred()));
        idx.setDirty();
    }

    public static boolean contains(MinecraftServer server, UUID corpse) {
        return get(server).entries.containsKey(corpse);
    }
}
