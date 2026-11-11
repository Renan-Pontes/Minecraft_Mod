package com.renanpontes.isekaimahou.core.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Lugares nomeados de um nível ({@code data/isekai_mahou_places.dat}). As leituras usam uma cópia imutável
 * ({@link #snapshot}) e um cache por dimensão preenchido na thread principal: a checagem de spawn natural também roda
 * durante a geração de chunks, fora da thread principal.
 */
public final class PlaceData extends SavedData {
    public static final String NAME = "isekai_mahou_places";
    private static final Map<ResourceKey<Level>, PlaceData> CACHE = new ConcurrentHashMap<>();

    private final Map<String, Places.Place> places = new LinkedHashMap<>();
    private volatile List<Places.Place> snapshot = List.of();

    private static SavedData.Factory<PlaceData> factory() {
        return new SavedData.Factory<>(PlaceData::new, PlaceData::load, null);
    }

    /** Lugar nenhum (leituras fora da thread principal antes de o nível carregar os dados). Nunca recebe escrita. */
    private static final PlaceData EMPTY = new PlaceData();

    /**
     * Dados do nível. O armazenamento de dados do Minecraft só pode ser aberto na thread principal: fora dela (geração de
     * chunks) devolve o cache, ou um conjunto vazio se o nível ainda não carregou os lugares.
     */
    static PlaceData of(ServerLevel level) {
        PlaceData cached = CACHE.get(level.dimension());
        if (cached != null) return cached;
        if (!level.getServer().isSameThread()) return EMPTY;
        PlaceData d = level.getDataStorage().computeIfAbsent(factory(), NAME);
        CACHE.put(level.dimension(), d);
        return d;
    }

    /** Cópia em cache (qualquer thread); null se o nível ainda não carregou os lugares. */
    @Nullable
    private static PlaceData cached(ResourceKey<Level> dim) {
        return CACHE.get(dim);
    }

    static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel sl) of(sl);
    }

    static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel sl) CACHE.remove(sl.dimension());
    }

    static void clearCache() {
        CACHE.clear();
    }

    // =====================================================================================
    //  API
    // =====================================================================================
    public static Places.Place register(ServerLevel level, Places.Place place) {
        if (place == null || place.id() == null || place.id().isBlank()) throw new IllegalArgumentException("Lugar sem id");
        if (place.box() == null) throw new IllegalArgumentException("Lugar sem área: " + place.id());
        place = normalize(level, place);
        if (!level.getServer().isSameThread()) {
            // Ex.: chamado durante a geração de uma estrutura: grava no próximo tick, na thread principal
            Places.Place p = place;
            level.getServer().execute(() -> register(level, p));
            return place;
        }
        ServerLevel target = level.getServer().getLevel(place.dim());
        if (target == null) target = level;
        // Mesmo id em outro nível (lugar "mudou" de dimensão): sai de lá
        for (ServerLevel other : level.getServer().getAllLevels()) {
            if (other != target) {
                PlaceData od = of(other);
                if (od.places.containsKey(place.id())) od.removeId(place.id());
            }
        }
        PlaceData d = of(target);
        d.places.put(place.id(), place);
        d.rebuild();
        d.setDirty();
        return place;
    }

    /** Campos opcionais vazios viram valores seguros (nome = id, tipo/facção = "", centro = meio da caixa). */
    private static Places.Place normalize(ServerLevel level, Places.Place p) {
        Component name = p.name() != null ? p.name() : Component.literal(p.id());
        String type = p.type() != null ? p.type() : "";
        String faction = p.faction() != null ? p.faction() : "";
        ResourceKey<Level> dim = p.dim() != null ? p.dim() : level.dimension();
        BlockPos center = p.center() != null ? p.center() : p.box().getCenter();
        if (name == p.name() && type == p.type() && faction == p.faction() && dim == p.dim() && center == p.center()) return p;
        return new Places.Place(p.id(), name, type, faction, dim, p.box(), center, p.safeZone(), p.color());
    }

    public static void remove(ServerLevel level, String id) {
        if (id == null) return;
        if (!level.getServer().isSameThread()) {
            level.getServer().execute(() -> remove(level, id));
            return;
        }
        for (ServerLevel l : level.getServer().getAllLevels()) {
            PlaceData d = of(l);
            if (d.places.containsKey(id)) d.removeId(id);
        }
    }

    private void removeId(String id) {
        places.remove(id);
        rebuild();
        setDirty();
    }

    @Nullable
    public static Places.Place get(ServerLevel level, String id) {
        if (id == null) return null;
        Places.Place p = find(of(level).snapshot, id);
        if (p != null) return p;
        for (ServerLevel l : level.getServer().getAllLevels()) {
            p = find(of(l).snapshot, id);
            if (p != null) return p;
        }
        return null;
    }

    @Nullable
    private static Places.Place find(List<Places.Place> list, String id) {
        for (Places.Place p : list) if (p.id().equals(id)) return p;
        return null;
    }

    @Nullable
    public static Places.Place at(ServerLevel level, BlockPos pos) {
        return at(of(level).snapshot, pos);
    }

    @Nullable
    private static Places.Place at(List<Places.Place> list, BlockPos pos) {
        Places.Place best = null;
        for (Places.Place p : list) {
            if (p.contains(pos) && (best == null || p.volume() < best.volume())) best = p;
        }
        return best;
    }

    @Nullable
    public static Places.Place nearest(ServerLevel level, BlockPos pos, @Nullable String type) {
        Places.Place best = null;
        double bestD = Double.MAX_VALUE;
        for (Places.Place p : of(level).snapshot) {
            if (type != null && !type.equals(p.type())) continue;
            double d = p.center().distSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    public static List<Places.Place> all(ServerLevel level) {
        return of(level).snapshot;
    }

    /** Zona segura (lugar com safeZone e config cityProtection). Seguro em qualquer thread. */
    public static boolean safeAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !MahouConfig.CITY_PROTECTION.get()) return false;
        PlaceData d = cached(level.dimension());
        if (d == null) d = of(level);
        for (Places.Place p : d.snapshot) if (p.safeZone() && p.contains(pos)) return true;
        return false;
    }

    private void rebuild() {
        snapshot = List.copyOf(places.values());
    }

    // =====================================================================================
    //  Salvar/carregar
    // =====================================================================================
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        for (Places.Place p : places.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", p.id());
            ComponentSerialization.CODEC.encodeStart(ops, p.name()).result().ifPresent(n -> t.put("name", n));
            t.putString("type", p.type());
            t.putString("faction", p.faction());
            t.putString("dim", p.dim().location().toString());
            BoundingBox b = p.box();
            t.putIntArray("box", new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()});
            t.putLong("center", p.center().asLong());
            t.putBoolean("safe", p.safeZone());
            t.putInt("color", p.color());
            list.add(t);
        }
        tag.put("places", list);
        return tag;
    }

    private static PlaceData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlaceData d = new PlaceData();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ListTag list = tag.getList("places", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            String id = t.getString("id");
            int[] b = t.getIntArray("box");
            ResourceLocation dimId = ResourceLocation.tryParse(t.getString("dim"));
            if (id.isBlank() || b.length != 6 || dimId == null) {
                IsekaiMahou.LOGGER.warn("[places] lugar inválido ignorado: {}", t);
                continue;
            }
            Component name = t.contains("name")
                    ? ComponentSerialization.CODEC.parse(ops, t.get("name")).result().orElse(Component.literal(id))
                    : Component.literal(id);
            Places.Place p = new Places.Place(id, name, t.getString("type"), t.getString("faction"),
                    ResourceKey.create(Registries.DIMENSION, dimId), new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]),
                    BlockPos.of(t.getLong("center")), t.getBoolean("safe"), t.getInt("color"));
            d.places.put(id, p);
        }
        d.rebuild();
        return d;
    }

    /** Para comandos: todos os lugares de todos os níveis carregados. */
    static List<Places.Place> everywhere(ServerLevel any) {
        List<Places.Place> out = new ArrayList<>();
        for (ServerLevel l : any.getServer().getAllLevels()) out.addAll(of(l).snapshot);
        return out;
    }
}
