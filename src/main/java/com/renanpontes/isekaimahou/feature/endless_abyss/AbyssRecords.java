package com.renanpontes.isekaimahou.feature.endless_abyss;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Dados salvos do Abismo Infinito (guardados no Overworld, arquivo {@code isekai_mahou_endless_abyss}):
 * <ul>
 *   <li><b>recordes</b> por jogador (maior andar vencido, quando, vitórias contra Sombras) — sobrevivem com o jogador
 *       offline, por isso servem de RANKING: use {@link #bestFloor}, {@link #shadowWins} e {@link #top};</li>
 *   <li><b>retornos</b>: de onde cada jogador entrou (para devolver com segurança, mesmo depois de queda do servidor);</li>
 *   <li><b>vagas construídas</b>: índices cuja arena já existe no mundo (e com qual versão do desenho).</li>
 * </ul>
 * Tudo opcional na leitura: mundos sem o arquivo começam vazios.
 */
public class AbyssRecords extends SavedData {
    private static final String NAME = "isekai_mahou_endless_abyss";

    /** Uma linha do ranking. {@code gameTime} = tick do mundo em que o recorde foi batido (desempate: quem chegou antes). */
    public record Entry(UUID player, String name, int bestFloor, long gameTime, int shadowWins) {}

    /** Ponto de retorno do jogador. */
    public record ReturnPoint(ResourceKey<Level> dim, Vec3 pos, float yaw) {}

    private static final class Row {
        String name = "";
        int best;
        long time;
        int shadowWins;
        final Map<String, Integer> shadows = new TreeMap<>();
    }

    private final Map<UUID, Row> rows = new HashMap<>();
    private final Map<UUID, ReturnPoint> returns = new HashMap<>();
    private final Map<Integer, Integer> built = new TreeMap<>();

    public static AbyssRecords get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(AbyssRecords::new, AbyssRecords::load), NAME);
    }

    // ------------------------------------------------------------------------------------------------ leitura (ranking)
    /** Maior andar já vencido pelo jogador (0 = nunca entrou). */
    public static int bestFloor(MinecraftServer server, UUID player) {
        Row r = get(server).rows.get(player);
        return r == null ? 0 : r.best;
    }

    /** Quantas Lutas das Sombras o jogador venceu (todas somadas). */
    public static int shadowWins(MinecraftServer server, UUID player) {
        Row r = get(server).rows.get(player);
        return r == null ? 0 : r.shadowWins;
    }

    /** Vitórias contra a Sombra de um chefe (path do id da entidade, ex. {@code "ignis"}). */
    public static int shadowWins(MinecraftServer server, UUID player, String bossPath) {
        Row r = get(server).rows.get(player);
        return r == null ? 0 : r.shadows.getOrDefault(bossPath, 0);
    }

    /** Os {@code limit} melhores (maior andar; empate = quem bateu o recorde primeiro). Inclui jogadores offline. */
    public static List<Entry> top(MinecraftServer server, int limit) {
        List<Entry> out = new ArrayList<>();
        get(server).rows.forEach((id, r) -> {
            if (r.best > 0) out.add(new Entry(id, r.name, r.best, r.time, r.shadowWins));
        });
        out.sort(Comparator.comparingInt(Entry::bestFloor).reversed().thenComparingLong(Entry::gameTime));
        return out.size() > limit ? new ArrayList<>(out.subList(0, Math.max(0, limit))) : out;
    }

    // ------------------------------------------------------------------------------------------------ escrita (módulo)
    private Row row(UUID id, String name) {
        Row r = rows.computeIfAbsent(id, k -> new Row());
        if (name != null && !name.isEmpty()) r.name = name;
        return r;
    }

    /** Registra um andar vencido. Devolve true se bateu o recorde do jogador. */
    boolean recordFloor(UUID id, String name, int floor, long gameTime) {
        Row r = row(id, name);
        if (floor <= r.best) return false;
        r.best = floor;
        r.time = gameTime;
        setDirty();
        return true;
    }

    /** Ajuste direto do recorde (comando de teste). */
    void setBest(UUID id, String name, int floor, long gameTime) {
        Row r = row(id, name);
        r.best = Math.max(0, floor);
        r.time = gameTime;
        setDirty();
    }

    /** +1 vitória contra a Sombra do chefe; devolve o total contra ESSE chefe. */
    int recordShadow(UUID id, String name, String bossPath) {
        Row r = row(id, name);
        r.shadowWins++;
        int v = r.shadows.merge(bossPath, 1, Integer::sum);
        setDirty();
        return v;
    }

    void putReturn(UUID id, ReturnPoint rp) {
        returns.put(id, rp);
        setDirty();
    }

    @Nullable
    ReturnPoint takeReturn(UUID id) {
        ReturnPoint rp = returns.remove(id);
        if (rp != null) setDirty();
        return rp;
    }

    boolean isBuilt(int index) {
        return built.getOrDefault(index, 0) == AbyssDimension.LAYOUT_VERSION;
    }

    void markBuilt(int index) {
        built.put(index, AbyssDimension.LAYOUT_VERSION);
        setDirty();
    }

    // ------------------------------------------------------------------------------------------------ NBT
    private static AbyssRecords load(CompoundTag tag, HolderLookup.Provider registries) {
        AbyssRecords d = new AbyssRecords();
        CompoundTag players = tag.getCompound("players");
        for (String k : players.getAllKeys()) {
            try {
                CompoundTag t = players.getCompound(k);
                Row r = new Row();
                r.name = t.getString("name");
                r.best = t.getInt("best");
                r.time = t.getLong("time");
                r.shadowWins = t.getInt("shadow_wins");
                CompoundTag sh = t.getCompound("shadows");
                for (String b : sh.getAllKeys()) r.shadows.put(b, sh.getInt(b));
                d.rows.put(UUID.fromString(k), r);
            } catch (IllegalArgumentException ignored) {
                // UUID inválido (edição manual): ignora a linha
            }
        }
        CompoundTag ret = tag.getCompound("returns");
        for (String k : ret.getAllKeys()) {
            try {
                CompoundTag t = ret.getCompound(k);
                ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
                if (dim == null) continue;
                d.returns.put(UUID.fromString(k), new ReturnPoint(ResourceKey.create(Registries.DIMENSION, dim),
                        new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z")), t.getFloat("yaw")));
            } catch (IllegalArgumentException ignored) {
                // UUID inválido: ignora
            }
        }
        CompoundTag b = tag.getCompound("built");
        for (String k : b.getAllKeys()) {
            try {
                d.built.put(Integer.parseInt(k), b.getInt(k));
            } catch (NumberFormatException ignored) {
                // chave estranha: ignora
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        rows.forEach((id, r) -> {
            CompoundTag t = new CompoundTag();
            t.putString("name", r.name);
            t.putInt("best", r.best);
            t.putLong("time", r.time);
            t.putInt("shadow_wins", r.shadowWins);
            CompoundTag sh = new CompoundTag();
            r.shadows.forEach(sh::putInt);
            t.put("shadows", sh);
            players.put(id.toString(), t);
        });
        tag.put("players", players);
        CompoundTag ret = new CompoundTag();
        returns.forEach((id, rp) -> {
            CompoundTag t = new CompoundTag();
            t.putString("dim", rp.dim().location().toString());
            t.putDouble("x", rp.pos().x);
            t.putDouble("y", rp.pos().y);
            t.putDouble("z", rp.pos().z);
            t.putFloat("yaw", rp.yaw());
            ret.put(id.toString(), t);
        });
        tag.put("returns", ret);
        CompoundTag b = new CompoundTag();
        built.forEach((k, v) -> b.putInt(String.valueOf(k), v));
        tag.put("built", b);
        return tag;
    }
}
