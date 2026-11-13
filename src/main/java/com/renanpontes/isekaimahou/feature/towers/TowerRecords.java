package com.renanpontes.isekaimahou.feature.towers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * <b>Recordes de tempo por andar</b> das Torres de Desafio (v1.0), guardados com o mundo (Overworld).
 * Uma tabela por (variante da torre, andar): melhor tempo de cada jogador, com quantos afixos o andar tinha.
 * O andar {@value TowerData#FLOORS} é o pináculo (tempo da luta com o guardião).
 * <p>Leitura para outros módulos (ranking): {@link #best}, {@link #personalBest}, {@link #top} — todos estáticos e
 * seguros com "ainda não existe". Contadores no {@code MagicData}: {@code towers.records} (+1 a cada recorde pessoal),
 * {@code towers.world_records} (+1 ao bater o recorde do mundo de outra marca), {@code towers.affix_floor} (+1 por andar
 * com afixo vencido) e {@code towers.affixes} (soma dos afixos vencidos).
 */
public final class TowerRecords extends SavedData {
    public static final String COUNTER_RECORDS = "towers.records";
    public static final String COUNTER_WORLD_RECORDS = "towers.world_records";
    public static final String COUNTER_AFFIX_FLOOR = "towers.affix_floor";
    public static final String COUNTER_AFFIXES = "towers.affixes";
    private static final String NAME = "isekai_mahou_tower_records";
    /** Teto de marcas guardadas por tabela (as piores saem). */
    private static final int MAX_PER_TABLE = 100;

    /** Uma marca: quem, em quantos ticks, com quantos afixos. */
    public record Entry(UUID player, String name, int ticks, int affixes) {}

    /** Resultado de registrar um tempo. {@code previousBest} = recorde do mundo antes (-1 se não havia). */
    public record Result(boolean personalBest, boolean worldBest, int previousPersonal, int previousBest) {}

    private final Map<String, Map<UUID, Entry>> tables = new HashMap<>();

    public static TowerRecords get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TowerRecords::new, TowerRecords::load), NAME);
    }

    public static String key(String variant, int floor) {
        return variant + "/" + floor;
    }

    // ------------------------------------------------------------------ leitura (API)

    /** Melhor tempo do mundo (ticks) nesse andar dessa variante, ou -1. */
    public static int best(MinecraftServer server, String variant, int floor) {
        Entry e = get(server).bestEntry(key(variant, floor));
        return e == null ? -1 : e.ticks();
    }

    /** Melhor tempo do jogador (ticks), ou -1. */
    public static int personalBest(MinecraftServer server, UUID player, String variant, int floor) {
        Map<UUID, Entry> t = get(server).tables.get(key(variant, floor));
        Entry e = t == null ? null : t.get(player);
        return e == null ? -1 : e.ticks();
    }

    /** As {@code n} melhores marcas (mais rápidas primeiro). */
    public static List<Entry> top(MinecraftServer server, String variant, int floor, int n) {
        Map<UUID, Entry> t = get(server).tables.get(key(variant, floor));
        if (t == null) return List.of();
        List<Entry> list = new ArrayList<>(t.values());
        list.sort(Comparator.comparingInt(Entry::ticks));
        return list.size() > n ? List.copyOf(list.subList(0, n)) : list;
    }

    /** Todas as tabelas que têm alguma marca (chaves "variante/andar"). */
    public List<String> keys() {
        List<String> k = new ArrayList<>(tables.keySet());
        k.sort(Comparator.naturalOrder());
        return k;
    }

    @Nullable
    public Entry bestEntry(String key) {
        Map<UUID, Entry> t = tables.get(key);
        if (t == null) return null;
        Entry best = null;
        for (Entry e : t.values()) {
            if (best == null || e.ticks() < best.ticks()) best = e;
        }
        return best;
    }

    // ------------------------------------------------------------------ escrita

    /** Registra o tempo se for o melhor do jogador. */
    public Result submit(String variant, int floor, UUID player, String name, int ticks, int affixes) {
        String key = key(variant, floor);
        Map<UUID, Entry> t = tables.computeIfAbsent(key, k -> new HashMap<>());
        Entry world = bestEntry(key);
        Entry mine = t.get(player);
        int prevWorld = world == null ? -1 : world.ticks();
        int prevMine = mine == null ? -1 : mine.ticks();
        boolean pb = mine == null || ticks < mine.ticks();
        if (pb) {
            t.put(player, new Entry(player, name, ticks, affixes));
            trim(t);
            setDirty();
        }
        return new Result(pb, pb && (world == null || ticks < world.ticks()), prevMine, prevWorld);
    }

    private static void trim(Map<UUID, Entry> t) {
        if (t.size() <= MAX_PER_TABLE) return;
        List<Entry> list = new ArrayList<>(t.values());
        list.sort(Comparator.comparingInt(Entry::ticks));
        for (int i = MAX_PER_TABLE; i < list.size(); i++) t.remove(list.get(i).player());
    }

    /** Debug: apaga tudo. */
    public void clear() {
        tables.clear();
        setDirty();
    }

    /** "1:07.4" */
    public static String format(int ticks) {
        int tenths = Math.max(0, ticks) / 2;
        return String.format(java.util.Locale.ROOT, "%d:%02d.%d", tenths / 600, (tenths / 10) % 60, tenths % 10);
    }

    // ------------------------------------------------------------------ salvar

    private static TowerRecords load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerRecords r = new TowerRecords();
        for (Tag t : tag.getList("Tables", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Map<UUID, Entry> table = new HashMap<>();
            for (Tag et : c.getList("Entries", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) et;
                if (!e.hasUUID("Id")) continue;
                UUID id = e.getUUID("Id");
                table.put(id, new Entry(id, e.getString("Name"), e.getInt("Ticks"), e.getInt("Affixes")));
            }
            if (!table.isEmpty()) r.tables.put(c.getString("Key"), table);
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Map<UUID, Entry>> t : tables.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("Key", t.getKey());
            ListTag entries = new ListTag();
            for (Entry e : t.getValue().values()) {
                CompoundTag ec = new CompoundTag();
                ec.putUUID("Id", e.player());
                ec.putString("Name", e.name());
                ec.putInt("Ticks", e.ticks());
                ec.putInt("Affixes", e.affixes());
                entries.add(ec);
            }
            c.put("Entries", entries);
            list.add(c);
        }
        tag.put("Tables", list);
        return tag;
    }
}
