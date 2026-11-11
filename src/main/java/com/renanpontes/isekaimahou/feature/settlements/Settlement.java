package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Estado de uma cidade governada por um jogador: a PRÓPRIA (fundada com a Carta) ou uma CONQUISTADA (Senhor de uma
 * cidade de reino). Guardado em {@link SettlementData}. O id é o id do lugar ({@code Places}) — o mesmo do
 * {@code Allegiance}.
 */
public final class Settlement {
    public enum Kind { OWN, CONQUERED }

    /** Papéis dos NPCs da cidade. */
    public enum Role {
        BUILDER, RESIDENT, GUARD, MERCHANT, MILITIA;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final int TAX_LOW = 0, TAX_NORMAL = 1, TAX_HIGH = 2;
    public static final int GATES_OPEN = 0, GATES_NIGHT = 1, GATES_CLOSED = 2;
    public static final int ORDER_FOLLOW = 0, ORDER_DEFEND = 1;
    public static final int MAX_AFFINITY = 100;
    public static final int MAX_TREASURY = 99999;

    public final String id;
    public final Kind kind;
    public SettlementStyle style;
    public final ResourceKey<Level> dim;
    public final BlockPos heart;
    /** Nome escolhido (cidade própria); conquistada = "" (usa o nome do lugar). */
    public String name;
    @Nullable public UUID owner;
    public String ownerName = "";

    public int level = 1;
    public int points;
    public int affinity;
    public int treasury;
    public int tax = TAX_NORMAL;
    public int gates = GATES_OPEN;
    public int militiaOrder = ORDER_FOLLOW;

    /** Dia do Overworld da última conta diária. */
    public long lastDay = -1;
    public int tradesToday;
    public int killsToday;
    public boolean lordVisited;
    public long festivalDay = -1;
    public int lastIncome;
    public int lastAffinityDelta;

    /** Obras (as primeiras, até o número de construtores, andam ao mesmo tempo). */
    public final List<Project> projects = new ArrayList<>();
    /** Prédios prontos: célula → chave (praça = célula central; muralha = -1). */
    public final Map<Integer, String> buildings = new LinkedHashMap<>();
    public boolean wallDone;
    public final Map<Role, List<UUID>> npcs = new EnumMap<>(Role.class);
    /** Grades dos portões (posições) e se estão fechadas agora. */
    public final List<BlockPos> gateBlocks = new ArrayList<>();
    public boolean gatesShut;
    @Nullable public BlockPos stone;
    /** Cidades que propuseram paz a esta. */
    public final Set<String> peaceOffers = new LinkedHashSet<>();
    /** Última vez (tempo do jogo) que cada NPC deu sinal de vida — só em memória. */
    public final transient Map<UUID, Long> lastSeen = new HashMap<>();

    public Settlement(String id, Kind kind, SettlementStyle style, ResourceKey<Level> dim, BlockPos heart, String name) {
        this.id = id;
        this.kind = kind;
        this.style = style;
        this.dim = dim;
        this.heart = heart.immutable();
        this.name = name;
        for (Role r : Role.values()) npcs.put(r, new ArrayList<>());
    }

    public boolean own() {
        return kind == Kind.OWN;
    }

    // =====================================================================================
    //  Prédios e efeitos
    // =====================================================================================
    public int count(String key) {
        int n = 0;
        for (String k : buildings.values()) if (k.equals(key)) n++;
        if (Blueprint.WALL.equals(key) && wallDone) n++;
        return n;
    }

    public boolean has(String key) {
        return count(key) > 0;
    }

    public int inProgress(String key) {
        int n = 0;
        for (Project p : projects) if (p.key.equals(key)) n++;
        return n;
    }

    private int sum(java.util.function.ToIntFunction<Blueprint.Effects> f) {
        int t = 0;
        for (String key : buildings.values()) {
            Blueprint b = Blueprints.get(style, key);
            if (b != null) t += f.applyAsInt(b.fx());
        }
        if (wallDone) {
            Blueprint w = Blueprints.get(style, Blueprint.WALL);
            if (w != null) t += f.applyAsInt(w.fx());
        }
        return t;
    }

    public boolean wonder(String id) {
        for (String key : buildings.values()) {
            Blueprint b = Blueprints.get(style, key);
            if (b != null && b.fx().wonder().equals(id)) return true;
        }
        return false;
    }

    public boolean guardPower() {
        for (String key : buildings.values()) {
            Blueprint b = Blueprints.get(style, key);
            if (b != null && b.fx().guardPower()) return true;
        }
        return false;
    }

    /** Vagas de cada papel. Cidade conquistada: guarda pessoal do Senhor e milícia fixas. */
    public int cap(Role role) {
        if (!own()) {
            return switch (role) {
                case GUARD -> 4;
                case MILITIA -> 6;
                default -> 0;
            };
        }
        return switch (role) {
            case BUILDER -> 1 + sum(Blueprint.Effects::builders) + (level >= 3 ? 1 : 0);
            case RESIDENT -> sum(Blueprint.Effects::residents);
            case GUARD -> sum(Blueprint.Effects::guards) + (level >= 2 ? 1 : 0);
            case MERCHANT -> sum(Blueprint.Effects::merchants);
            case MILITIA -> has("barracks") ? 2 + 2 * level + sum(Blueprint.Effects::militia) : 0;
        };
    }

    public int speedPct() {
        return sum(Blueprint.Effects::speedPct);
    }

    public int taxPct() {
        return sum(Blueprint.Effects::taxPct);
    }

    public int buildingAffinity() {
        return sum(Blueprint.Effects::affinity);
    }

    public int population(Role role) {
        return npcs.get(role).size();
    }

    public int totalPopulation() {
        int t = 0;
        for (List<UUID> l : npcs.values()) t += l.size();
        return t;
    }

    /** Recalcula o nível pela prosperidade. Devolve true se subiu. */
    public boolean recomputeLevel() {
        int old = level;
        if (own()) level = Math.max(level, Blueprints.levelFor(style, points, has(Blueprint.TOWN_HALL)));
        return level > old;
    }

    // =====================================================================================
    //  NBT
    // =====================================================================================
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Id", id);
        t.putString("Kind", kind.name());
        t.putString("Style", style.id);
        t.putString("Dim", dim.location().toString());
        t.put("Heart", NbtUtils.writeBlockPos(heart));
        t.putString("Name", name);
        if (owner != null) t.putUUID("Owner", owner);
        t.putString("OwnerName", ownerName);
        t.putInt("Level", level);
        t.putInt("Points", points);
        t.putInt("Affinity", affinity);
        t.putInt("Treasury", treasury);
        t.putInt("Tax", tax);
        t.putInt("Gates", gates);
        t.putInt("MilitiaOrder", militiaOrder);
        t.putLong("LastDay", lastDay);
        t.putInt("TradesToday", tradesToday);
        t.putInt("KillsToday", killsToday);
        t.putBoolean("LordVisited", lordVisited);
        t.putLong("FestivalDay", festivalDay);
        t.putInt("LastIncome", lastIncome);
        t.putInt("LastAffinityDelta", lastAffinityDelta);
        ListTag pl = new ListTag();
        for (Project p : projects) pl.add(p.save());
        t.put("Projects", pl);
        CompoundTag b = new CompoundTag();
        buildings.forEach((cell, key) -> b.putString(Integer.toString(cell), key));
        t.put("Buildings", b);
        t.putBoolean("WallDone", wallDone);
        CompoundTag n = new CompoundTag();
        npcs.forEach((role, list) -> {
            ListTag l = new ListTag();
            for (UUID u : list) l.add(NbtUtils.createUUID(u));
            n.put(role.name(), l);
        });
        t.put("Npcs", n);
        ListTag g = new ListTag();
        for (BlockPos p : gateBlocks) g.add(NbtUtils.writeBlockPos(p));
        t.put("GateBlocks", g);
        t.putBoolean("GatesShut", gatesShut);
        if (stone != null) t.put("Stone", NbtUtils.writeBlockPos(stone));
        ListTag po = new ListTag();
        for (String s : peaceOffers) po.add(StringTag.valueOf(s));
        t.put("PeaceOffers", po);
        return t;
    }

    @Nullable
    public static Settlement load(CompoundTag t) {
        ResourceLocation dimId = ResourceLocation.tryParse(t.getString("Dim"));
        BlockPos heart = NbtUtils.readBlockPos(t, "Heart").orElse(null);
        if (dimId == null || heart == null || t.getString("Id").isEmpty()) return null;
        Kind kind = "CONQUERED".equals(t.getString("Kind")) ? Kind.CONQUERED : Kind.OWN;
        SettlementStyle style = SettlementStyle.byId(t.getString("Style"));
        Settlement s = new Settlement(t.getString("Id"), kind, style == null ? SettlementStyle.KINGDOM : style,
                ResourceKey.create(Registries.DIMENSION, dimId), heart, t.getString("Name"));
        if (t.hasUUID("Owner")) s.owner = t.getUUID("Owner");
        s.ownerName = t.getString("OwnerName");
        s.level = Math.max(1, t.getInt("Level"));
        s.points = t.getInt("Points");
        s.affinity = t.getInt("Affinity");
        s.treasury = t.getInt("Treasury");
        s.tax = t.getInt("Tax");
        s.gates = t.getInt("Gates");
        s.militiaOrder = t.getInt("MilitiaOrder");
        s.lastDay = t.getLong("LastDay");
        s.tradesToday = t.getInt("TradesToday");
        s.killsToday = t.getInt("KillsToday");
        s.lordVisited = t.getBoolean("LordVisited");
        s.festivalDay = t.getLong("FestivalDay");
        s.lastIncome = t.getInt("LastIncome");
        s.lastAffinityDelta = t.getInt("LastAffinityDelta");
        ListTag pl = t.getList("Projects", Tag.TAG_COMPOUND);
        for (int i = 0; i < pl.size(); i++) {
            Project p = Project.load(pl.getCompound(i));
            if (p != null) s.projects.add(p);
        }
        CompoundTag b = t.getCompound("Buildings");
        for (String k : b.getAllKeys()) {
            try {
                s.buildings.put(Integer.parseInt(k), b.getString(k));
            } catch (NumberFormatException ignored) {
                // célula inválida (save editado): ignora o prédio
            }
        }
        s.wallDone = t.getBoolean("WallDone");
        CompoundTag n = t.getCompound("Npcs");
        for (Role r : Role.values()) {
            ListTag l = n.getList(r.name(), Tag.TAG_INT_ARRAY);
            for (Tag e : l) s.npcs.get(r).add(NbtUtils.loadUUID(e));
        }
        ListTag g = t.getList("GateBlocks", Tag.TAG_INT_ARRAY);
        for (Tag e : g) {
            if (e instanceof net.minecraft.nbt.IntArrayTag ia && ia.size() == 3) {
                s.gateBlocks.add(new BlockPos(ia.get(0).getAsInt(), ia.get(1).getAsInt(), ia.get(2).getAsInt()));
            }
        }
        s.gatesShut = t.getBoolean("GatesShut");
        if (t.contains("Stone")) s.stone = NbtUtils.readBlockPos(t, "Stone").orElse(null);
        ListTag po = t.getList("PeaceOffers", Tag.TAG_STRING);
        for (int i = 0; i < po.size(); i++) s.peaceOffers.add(po.getString(i));
        return s;
    }
}
