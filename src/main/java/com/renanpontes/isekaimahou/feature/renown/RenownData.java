package com.renanpontes.isekaimahou.feature.renown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Dados salvos do Renome (no Overworld, arquivo {@code isekai_mahou_renown}):
 * <ul>
 *   <li><b>fotos dos jogadores</b>: nível, chefes, torres... copiados do {@code MagicData} quando o jogador está online,
 *       para o ranking continuar mostrando quem está offline;</li>
 *   <li><b>recordes de tempo</b> por chefe: o melhor do servidor (com os nomes de quem lutou) e o melhor de cada jogador;</li>
 *   <li>cidades que já ganharam o Quadro de Renome automático (uma vez por cidade).</li>
 * </ul>
 * Tudo opcional na leitura: mundo antigo sem o arquivo começa vazio.
 */
public class RenownData extends SavedData {
    private static final String NAME = "isekai_mahou_renown";

    /** Foto de um jogador para o ranking. */
    public static final class PlayerRow {
        public String name = "";
        public int level, circle;
        public String path = "";
        /** Soma dos contadores {@code boss.*} e quantos chefes DIFERENTES. */
        public int bossKills, bossKinds;
        public int towers, towerFloors;
        public int abyss;
        /** Chave de tradução do título em uso ("" = nenhum). */
        public String title = "";
        /** Melhor tempo (ticks) por chefe (id completo do tipo, ex. {@code isekai_mahou:ignis}). */
        public final Map<String, Integer> bossBest = new TreeMap<>();
    }

    /** Recorde do servidor para um chefe. */
    public static final class BossRecord {
        public int ticks;
        public final List<UUID> ids = new ArrayList<>();
        public final List<String> names = new ArrayList<>();
        /** Dia do jogo em que o recorde foi batido. */
        public long day;
    }

    final Map<UUID, PlayerRow> players = new HashMap<>();
    final Map<String, BossRecord> bosses = new TreeMap<>();
    final Set<String> boardPlaces = new LinkedHashSet<>();

    public static RenownData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RenownData::new, RenownData::load), NAME);
    }

    PlayerRow row(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerRow());
    }

    // ------------------------------------------------------------------------------------------------ NBT
    private static RenownData load(CompoundTag tag, HolderLookup.Provider registries) {
        RenownData d = new RenownData();
        CompoundTag ps = tag.getCompound("players");
        for (String k : ps.getAllKeys()) {
            try {
                CompoundTag t = ps.getCompound(k);
                PlayerRow r = new PlayerRow();
                r.name = t.getString("name");
                r.level = t.getInt("level");
                r.circle = t.getInt("circle");
                r.path = t.getString("path");
                r.bossKills = t.getInt("boss_kills");
                r.bossKinds = t.getInt("boss_kinds");
                r.towers = t.getInt("towers");
                r.towerFloors = t.getInt("tower_floors");
                r.abyss = t.getInt("abyss");
                r.title = t.getString("title");
                CompoundTag best = t.getCompound("boss_best");
                for (String b : best.getAllKeys()) r.bossBest.put(b, best.getInt(b));
                d.players.put(UUID.fromString(k), r);
            } catch (IllegalArgumentException ignored) {
                // UUID inválido (edição manual): ignora a linha
            }
        }
        CompoundTag bs = tag.getCompound("bosses");
        for (String k : bs.getAllKeys()) {
            CompoundTag t = bs.getCompound(k);
            BossRecord r = new BossRecord();
            r.ticks = t.getInt("ticks");
            r.day = t.getLong("day");
            ListTag names = t.getList("names", Tag.TAG_STRING);
            for (int i = 0; i < names.size(); i++) r.names.add(names.getString(i));
            ListTag ids = t.getList("ids", Tag.TAG_STRING);
            for (int i = 0; i < ids.size(); i++) {
                try {
                    r.ids.add(UUID.fromString(ids.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // UUID inválido: o nome continua valendo
                }
            }
            if (r.ticks > 0) d.bosses.put(k, r);
        }
        ListTag boards = tag.getList("board_places", Tag.TAG_STRING);
        for (int i = 0; i < boards.size(); i++) d.boardPlaces.add(boards.getString(i));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag ps = new CompoundTag();
        players.forEach((id, r) -> {
            CompoundTag t = new CompoundTag();
            t.putString("name", r.name);
            t.putInt("level", r.level);
            t.putInt("circle", r.circle);
            t.putString("path", r.path);
            t.putInt("boss_kills", r.bossKills);
            t.putInt("boss_kinds", r.bossKinds);
            t.putInt("towers", r.towers);
            t.putInt("tower_floors", r.towerFloors);
            t.putInt("abyss", r.abyss);
            t.putString("title", r.title);
            CompoundTag best = new CompoundTag();
            r.bossBest.forEach(best::putInt);
            t.put("boss_best", best);
            ps.put(id.toString(), t);
        });
        tag.put("players", ps);
        CompoundTag bs = new CompoundTag();
        bosses.forEach((boss, r) -> {
            CompoundTag t = new CompoundTag();
            t.putInt("ticks", r.ticks);
            t.putLong("day", r.day);
            ListTag names = new ListTag();
            for (String n : r.names) names.add(StringTag.valueOf(n));
            t.put("names", names);
            ListTag ids = new ListTag();
            for (UUID u : r.ids) ids.add(StringTag.valueOf(u.toString()));
            t.put("ids", ids);
            bs.put(boss, t);
        });
        tag.put("bosses", bs);
        ListTag boards = new ListTag();
        for (String p : boardPlaces) boards.add(StringTag.valueOf(p));
        tag.put("board_places", boards);
        return tag;
    }
}
