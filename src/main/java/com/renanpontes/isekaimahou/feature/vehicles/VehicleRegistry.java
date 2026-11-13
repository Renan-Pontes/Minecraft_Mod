package com.renanpontes.isekaimahou.feature.vehicles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.vehicles.entity.MagicVehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Onda 10: o caderno do Mecânico — onde está cada veículo de cada dono (SavedData no Overworld,
 * {@code isekai_mahou_vehicles}). Serve para o Mecânico RESGATAR um veículo perdido (deixado longe, num lugar
 * descarregado, ou cujo item sumiu) e RECONSTRUIR um destruído.
 * <ul>
 *   <li>Cada veículo tem uma identidade ({@code VehicleId}) e uma geração. O resgate entrega um item de geração maior;
 *       a cópia antiga — entidade num chunk distante ou item esquecido num baú — vê que ficou para trás e some (a carga
 *       a bordo cai no chão onde ela estava). Assim nunca existem dois do mesmo veículo.</li>
 *   <li>Estados: {@link #OUT} (no mundo), {@link #PACKED} (guardado num item), {@link #WRECKED} (destruído).</li>
 *   <li>Só guarda dados pequenos (tipo, melhorias, carga, vida, último lugar); a carga do baú fica na entidade.</li>
 * </ul>
 */
public final class VehicleRegistry extends SavedData {
    private static final String NAME = "isekai_mahou_vehicles";
    public static final byte OUT = 0, PACKED = 1, WRECKED = 2;
    /** Teto de registros por dono (o mais antigo guardado/destruído sai primeiro). */
    private static final int MAX_PER_OWNER = 24;

    /** Um veículo de alguém. */
    public static final class Entry {
        public final UUID id;
        public final UUID owner;
        public String kind;
        public int gen;
        public byte state;
        public int upg;
        public int fuel;
        /** Vida em fração (1 = cheia). */
        public float hp = 1F;
        /** A entidade no mundo (só no estado {@link #OUT}). */
        @Nullable
        public UUID entity;
        @Nullable
        public ResourceKey<Level> dim;
        @Nullable
        public BlockPos pos;
        public long time;

        Entry(UUID id, UUID owner, String kind) {
            this.id = id;
            this.owner = owner;
            this.kind = kind;
        }

        @Nullable
        public VehicleKind kind() {
            return VehicleKind.byId(kind);
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static VehicleRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VehicleRegistry::new, VehicleRegistry::load, null), NAME);
    }

    @Nullable
    public Entry get(@Nullable UUID id) {
        return id == null ? null : entries.get(id);
    }

    /** Os veículos de um dono, do mais novo para o mais antigo. */
    public List<Entry> of(UUID owner) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.owner.equals(owner)) out.add(0, e);
        return out;
    }

    private Entry entry(UUID id, UUID owner, VehicleKind kind) {
        Entry e = entries.get(id);
        if (e == null || !e.owner.equals(owner)) {
            e = new Entry(id, owner, kind.id);
            entries.put(id, e);
            trim(owner);
        }
        e.kind = kind.id;
        return e;
    }

    /** O veículo está no mundo (acabou de ser colocado ou foi visto agora). */
    public void out(MinecraftServer server, MagicVehicle v) {
        if (v.ownerId() == null || v.vehicleId() == null) return;
        Entry e = entry(v.vehicleId(), v.ownerId(), v.kind());
        e.gen = v.gen();
        e.state = OUT;
        e.entity = v.getUUID();
        fill(e, v, server);
        setDirty();
    }

    /** Atualiza o último lugar e o estado do veículo (chamado de tempos em tempos pela entidade). */
    public void touch(MinecraftServer server, MagicVehicle v) {
        Entry e = get(v.vehicleId());
        if (e == null || e.state != OUT) return;
        fill(e, v, server);
        setDirty();
    }

    private static void fill(Entry e, MagicVehicle v, MinecraftServer server) {
        e.upg = v.upgrades();
        e.fuel = v.getFuel();
        e.hp = v.healthFraction();
        e.dim = v.level().dimension();
        e.pos = v.blockPosition();
        e.time = server.overworld().getGameTime();
    }

    /** O dono guardou o veículo num item. */
    public void packed(MinecraftServer server, UUID owner, VehicleKind kind, VehicleStacks.Data d) {
        if (d.id() == null) return;
        Entry e = entry(d.id(), owner, kind);
        e.gen = d.gen();
        e.state = PACKED;
        e.entity = null;
        e.upg = d.upg();
        e.fuel = d.fuel();
        e.hp = d.hp();
        e.time = server.overworld().getGameTime();
        setDirty();
    }

    /** O veículo foi destruído (o Mecânico pode reconstruí-lo). */
    public void wrecked(MinecraftServer server, MagicVehicle v) {
        if (v.ownerId() == null || v.vehicleId() == null) return;
        Entry e = entry(v.vehicleId(), v.ownerId(), v.kind());
        e.gen = v.gen();
        e.state = WRECKED;
        e.entity = null;
        e.upg = v.upgrades();
        e.fuel = 0;
        e.hp = 0F;
        e.dim = v.level().dimension();
        e.pos = v.blockPosition();
        e.time = server.overworld().getGameTime();
        setDirty();
    }

    /** Esquece o veículo (desmontado no criativo). */
    public void forget(@Nullable UUID id) {
        if (id != null && entries.remove(id) != null) setDirty();
    }

    /** O Mecânico entregou um item novo: a cópia antiga fica para trás. Devolve a geração nova. */
    public int reissue(MinecraftServer server, Entry e, float hp, int fuel) {
        e.gen++;
        e.state = PACKED;
        e.entity = null;
        e.hp = hp;
        e.fuel = fuel;
        e.time = server.overworld().getGameTime();
        setDirty();
        return e.gen;
    }

    /** Muitos registros do mesmo dono: tira os guardados/destruídos mais antigos (os que estão no mundo ficam). */
    private void trim(UUID owner) {
        List<Entry> mine = new ArrayList<>();
        for (Entry e : entries.values()) if (e.owner.equals(owner)) mine.add(e);
        int extra = mine.size() - MAX_PER_OWNER;
        for (Entry e : mine) {
            if (extra <= 0) break;
            if (e.state == OUT) continue;
            entries.remove(e.id);
            extra--;
        }
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    private static VehicleRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        VehicleRegistry r = new VehicleRegistry();
        ListTag list = tag.getList("vehicles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (!c.hasUUID("id") || !c.hasUUID("owner")) continue;
            Entry e = new Entry(c.getUUID("id"), c.getUUID("owner"), c.getString("kind"));
            e.gen = c.getInt("gen");
            e.state = c.getByte("state");
            e.upg = c.getInt("upg");
            e.fuel = c.getInt("fuel");
            e.hp = c.contains("hp") ? c.getFloat("hp") : 1F;
            e.entity = c.hasUUID("entity") ? c.getUUID("entity") : null;
            ResourceLocation dim = c.contains("dim") ? ResourceLocation.tryParse(c.getString("dim")) : null;
            e.dim = dim == null ? null : ResourceKey.create(Registries.DIMENSION, dim);
            e.pos = c.contains("pos") ? BlockPos.of(c.getLong("pos")) : null;
            e.time = c.getLong("time");
            r.entries.put(e.id, e);
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.id);
            c.putUUID("owner", e.owner);
            c.putString("kind", e.kind);
            c.putInt("gen", e.gen);
            c.putByte("state", e.state);
            c.putInt("upg", e.upg);
            c.putInt("fuel", e.fuel);
            c.putFloat("hp", e.hp);
            if (e.entity != null) c.putUUID("entity", e.entity);
            if (e.dim != null) c.putString("dim", e.dim.location().toString());
            if (e.pos != null) c.putLong("pos", e.pos.asLong());
            c.putLong("time", e.time);
            list.add(c);
        }
        tag.put("vehicles", list);
        return tag;
    }
}
