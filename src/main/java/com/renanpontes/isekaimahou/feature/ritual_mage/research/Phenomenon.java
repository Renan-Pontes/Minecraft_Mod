package com.renanpontes.isekaimahou.feature.ritual_mage.research;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Um fenômeno observável com a Lente Arcana (data/<ns>/mage_phenomena/<id>.json).
 *
 * Campos do JSON:
 * <pre>
 *   category   block | creature | biome | sky | dimension | structure | boss
 *   elements   ["fire", "earth"]   (as notas alternam entre eles)
 *   rarity     common | uncommon | rare | epic | legendary   (notas e XP da 1ª observação)
 *   icon       "ns:item" ou lista (o primeiro que existir)
 *   order      ordem no Atlas
 *   -- alvo (o que a lente está mirando; qualquer um da lista) --
 *   blocks     ["minecraft:lava", "#minecraft:ice"]
 *   entities   ["minecraft:blaze", "#minecraft:raiders"]
 *   -- contexto (todos os informados precisam valer) --
 *   biomes, dimensions, structures (ids ou #tags), weather (rain|thunder|snow),
 *   moon [fases 0..7, só à noite], time [min, max] (hora do dia 0..23999), sky (olhando o céu aberto), clear (sem chuva)
 *   -- alternativa --
 *   memory     ["boss.minecraft.wither", ...]  qualquer contador ≥ 1 permite registrar a lembrança em qualquer lugar
 *   repeatable false = não dá notas extras depois da 1ª observação
 * </pre>
 */
public final class Phenomenon {
    public static final List<String> CATEGORIES = List.of("block", "creature", "biome", "sky", "dimension", "structure", "boss");

    private static final String RARITY_KEY = "mage_rarity.isekai_mahou.";

    public enum Rarity {
        COMMON(1, 6, 0xFF9A9A9A),
        UNCOMMON(2, 12, 0xFF3FAF4A),
        RARE(2, 22, 0xFF3B7BE0),
        EPIC(3, 40, 0xFFA040E0),
        LEGENDARY(4, 70, 0xFFE09A1A);

        public final int notes;
        public final int xp;
        public final int argb;

        Rarity(int notes, int xp, int argb) {
            this.notes = notes;
            this.xp = xp;
            this.argb = argb;
        }

        public MutableComponent displayName() {
            return Component.translatable(RARITY_KEY + name().toLowerCase(Locale.ROOT)).withColor(argb & 0xFFFFFF);
        }

        public static Rarity byName(String s) {
            for (Rarity r : values()) if (r.name().equalsIgnoreCase(s)) return r;
            return COMMON;
        }
    }

    public final ResourceLocation id;
    public final String category;
    public final List<Element> elements;
    public final Rarity rarity;
    public final List<ResourceLocation> icons;
    public final int order;
    public final boolean repeatable;

    private final Targets<Block> blocks;
    private final Targets<EntityType<?>> entities;
    private final Targets<Biome> biomes;
    private final Targets<Structure> structures;
    private final Set<ResourceLocation> dimensions;
    @Nullable private final String weather;
    private final int[] moon;
    private final int timeMin, timeMax;
    private final boolean sky;
    private final boolean clear;
    private final List<String> memory;

    private Phenomenon(ResourceLocation id, JsonObject json) {
        this.id = id;
        String cat = GsonHelper.getAsString(json, "category", "block");
        this.category = CATEGORIES.contains(cat) ? cat : "block";
        List<Element> els = new ArrayList<>();
        for (String s : strings(json, "elements")) {
            Element e = Element.byId(s);
            if (e != null) els.add(e);
        }
        if (els.isEmpty()) els.add(Element.ARCANE);
        this.elements = List.copyOf(els);
        this.rarity = Rarity.byName(GsonHelper.getAsString(json, "rarity", "common"));
        List<ResourceLocation> ic = new ArrayList<>();
        for (String s : strings(json, "icon")) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) ic.add(rl);
        }
        this.icons = List.copyOf(ic);
        this.order = GsonHelper.getAsInt(json, "order", 1000);
        this.blocks = new Targets<>(Registries.BLOCK, strings(json, "blocks"));
        this.entities = new Targets<>(Registries.ENTITY_TYPE, strings(json, "entities"));
        this.biomes = new Targets<>(Registries.BIOME, strings(json, "biomes"));
        this.structures = new Targets<>(Registries.STRUCTURE, strings(json, "structures"));
        Set<ResourceLocation> dims = new HashSet<>();
        for (String s : strings(json, "dimensions")) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) dims.add(rl);
        }
        this.dimensions = dims;
        this.weather = json.has("weather") ? GsonHelper.getAsString(json, "weather") : null;
        JsonArray m = GsonHelper.getAsJsonArray(json, "moon", new JsonArray());
        this.moon = new int[m.size()];
        for (int i = 0; i < m.size(); i++) moon[i] = m.get(i).getAsInt();
        JsonArray t = GsonHelper.getAsJsonArray(json, "time", new JsonArray());
        this.timeMin = t.size() == 2 ? t.get(0).getAsInt() : -1;
        this.timeMax = t.size() == 2 ? t.get(1).getAsInt() : -1;
        this.sky = GsonHelper.getAsBoolean(json, "sky", false);
        this.clear = GsonHelper.getAsBoolean(json, "clear", false);
        this.memory = strings(json, "memory");
        this.repeatable = GsonHelper.getAsBoolean(json, "repeatable", true);
    }

    public static Phenomenon parse(ResourceLocation id, JsonObject json) {
        return new Phenomenon(id, json);
    }

    private static List<String> strings(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        JsonElement el = json.get(key);
        List<String> out = new ArrayList<>();
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) out.add(e.getAsString());
        } else {
            out.add(el.getAsString());
        }
        return out;
    }

    // ---------------------------------------------------------------- Nomes
    public static String translationKey(ResourceLocation id) {
        return "phenomenon." + id.getNamespace() + "." + id.getPath();
    }

    public static MutableComponent displayName(ResourceLocation id) {
        return Component.translatableWithFallback(translationKey(id), id.getPath());
    }

    public MutableComponent displayName() {
        return displayName(id);
    }

    // ---------------------------------------------------------------- Regras de observação
    /** Tem alvo (bloco/entidade) — só vale mirando nele. */
    public boolean hasTarget() {
        return !blocks.isEmpty() || !entities.isEmpty();
    }

    /** Tem alguma condição de ambiente (fenômeno "do lugar", observável sem mirar em nada específico). */
    public boolean hasContext() {
        return !biomes.isEmpty() || !structures.isEmpty() || !dimensions.isEmpty() || weather != null
                || moon.length > 0 || timeMin >= 0 || sky;
    }

    public boolean matchesEntity(ObservationContext ctx) {
        return ctx.entity() != null && !entities.isEmpty() && entities.matches(ctx.entity().getType().builtInRegistryHolder());
    }

    public boolean matchesBlock(ObservationContext ctx) {
        if (blocks.isEmpty()) return false;
        if (ctx.block() != null && blocks.matches(ctx.block().getBlockHolder())) return true;
        return ctx.fluid() != null && blocks.matches(ctx.fluid().getBlockHolder());
    }

    /** Condições de ambiente (todas as informadas precisam valer). */
    public boolean contextOk(ObservationContext ctx) {
        ServerLevel level = ctx.level();
        if (!dimensions.isEmpty() && !dimensions.contains(level.dimension().location())) return false;
        if (!biomes.isEmpty() && !biomes.matches(level.getBiome(ctx.pos()))) return false;
        if (sky && !ctx.openSky()) return false;
        if (clear && level.isRaining()) return false;
        if (weather != null && !weatherOk(ctx)) return false;
        long time = level.getDayTime() % 24000L;
        if (moon.length > 0) {
            if (level.dimensionType().hasFixedTime() || time < 13000 || time > 23000) return false;
            int phase = level.getMoonPhase();
            boolean any = false;
            for (int p : moon) any |= p == phase;
            if (!any) return false;
        }
        if (timeMin >= 0 && (level.dimensionType().hasFixedTime() || time < timeMin || time > timeMax)) return false;
        return structures.isEmpty() || inStructure(ctx);
    }

    private boolean weatherOk(ObservationContext ctx) {
        ServerLevel level = ctx.level();
        if (!level.canSeeSky(ctx.pos())) return false;
        Biome biome = level.getBiome(ctx.pos()).value();
        return switch (weather) {
            case "rain" -> level.isRaining() && biome.getPrecipitationAt(ctx.pos()) == Biome.Precipitation.RAIN;
            case "snow" -> level.isRaining() && biome.getPrecipitationAt(ctx.pos()) == Biome.Precipitation.SNOW;
            case "thunder" -> level.isThundering();
            case "clear" -> !level.isRaining();
            default -> false;
        };
    }

    private boolean inStructure(ObservationContext ctx) {
        ServerLevel level = ctx.level();
        var manager = level.structureManager();
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (ResourceLocation id : structures.ids) {
            Structure s = registry.get(id);
            if (s != null && manager.getStructureWithPieceAt(ctx.pos(), s).isValid()) return true;
        }
        for (TagKey<Structure> tag : structures.tags) {
            if (manager.getStructureWithPieceAt(ctx.pos(), tag).isValid()) return true;
        }
        return false;
    }

    /** Lembrança (chefes): algum contador ≥ 1. */
    public boolean memoryOk(MagicData data) {
        for (String key : memory) if (data.counter(key) > 0) return true;
        return false;
    }

    /** Existe neste mundo? (alvos de módulos/dimensões ausentes escondem o fenômeno do Atlas.) */
    public boolean available(MinecraftServer server) {
        if (!blocks.anyExists(BuiltInRegistries.BLOCK)) return false;
        if (!entities.anyExists(BuiltInRegistries.ENTITY_TYPE)) return false;
        if (!biomes.anyExists(server.registryAccess().registryOrThrow(Registries.BIOME))) return false;
        if (!structures.anyExists(server.registryAccess().registryOrThrow(Registries.STRUCTURE))) return false;
        if (!dimensions.isEmpty()) {
            boolean any = false;
            for (ResourceLocation d : dimensions) any |= server.getLevel(ResourceKey.create(Registries.DIMENSION, d)) != null;
            if (!any) return false;
        }
        return true;
    }

    /** Lista de alvos: ids diretos + tags ("#ns:tag"). */
    private static final class Targets<T> {
        final Set<ResourceLocation> ids = new HashSet<>();
        final List<TagKey<T>> tags = new ArrayList<>();

        Targets(ResourceKey<? extends Registry<T>> registry, List<String> raw) {
            for (String s : raw) {
                boolean tag = s.startsWith("#");
                ResourceLocation rl = ResourceLocation.tryParse(tag ? s.substring(1) : s);
                if (rl == null) continue;
                if (tag) tags.add(TagKey.create(registry, rl));
                else ids.add(rl);
            }
        }

        boolean isEmpty() {
            return ids.isEmpty() && tags.isEmpty();
        }

        boolean matches(Holder<T> holder) {
            if (holder.unwrapKey().map(k -> ids.contains(k.location())).orElse(false)) return true;
            for (TagKey<T> t : tags) if (holder.is(t)) return true;
            return false;
        }

        boolean anyExists(Registry<?> registry) {
            if (isEmpty() || !tags.isEmpty()) return true;
            for (ResourceLocation id : ids) if (registry.containsKey(id)) return true;
            return false;
        }
    }
}
