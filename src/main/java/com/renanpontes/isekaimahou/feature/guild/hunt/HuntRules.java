package com.renanpontes.isekaimahou.feature.guild.hunt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.guild.quest.GuildQuest;
import com.renanpontes.isekaimahou.feature.guild.quest.QuestObjective;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.Tags;

/**
 * Regras das Áreas de Caça: quais criaturas podem ser "caçadas" (nascem em ondas numa área), em que biomas, e o elemento
 * de cada bioma (essências). Tudo calculado no aceite (barato, com cache por tipo/dimensão, limpo ao parar o servidor).
 * <ul>
 *   <li>Fora: chefes (tag {@code c:bosses}, vida máx. ≥ 150), criaturas aquáticas/ambientes, mobs de estrutura do Nether/End
 *       e dos módulos de provação ({@link #DENY});</li>
 *   <li>Bioma: onde a criatura nasce naturalmente NESTA dimensão (biomas + modificadores de bioma dos módulos), ou o mapa
 *       {@link #OVERRIDE} (bruxas no pântano, saqueadores nas planícies/taigas...). Sem nenhum dos dois, não é caçável;</li>
 *   <li>Criatura que nasce em quase todo bioma de terra é "de qualquer lugar" (a área vai para um ponto qualquer em terra).</li>
 * </ul>
 */
public final class HuntRules {
    /** Vida máxima a partir da qual a criatura é tratada como chefe (mesma regra de BossKit/Statuses). */
    private static final float BOSS_HEALTH = 150F;
    /** Fração dos biomas de terra a partir da qual a criatura é "de qualquer lugar". */
    private static final float UNIVERSAL_FRACTION = 0.55F;

    /** Nunca nascem numa Área de Caça (estrutura do Nether/End, aquáticas, voadoras que queimam, provações). */
    private static final Set<ResourceLocation> DENY = Set.of(
            id("minecraft:guardian"), id("minecraft:elder_guardian"), id("minecraft:phantom"), id("minecraft:ghast"),
            id("minecraft:blaze"), id("minecraft:wither_skeleton"), id("minecraft:piglin_brute"), id("minecraft:piglin"),
            id("minecraft:zombified_piglin"), id("minecraft:hoglin"), id("minecraft:zoglin"), id("minecraft:magma_cube"),
            id("minecraft:breeze"), id("minecraft:shulker"), id("minecraft:endermite"), id("minecraft:silverfish"), id("minecraft:vex"),
            id("minecraft:warden"), id("minecraft:ender_dragon"), id("minecraft:wither"), id("minecraft:illusioner"),
            id("minecraft:bogged"), id("minecraft:strider"),
            id("isekai_mahou:trial_avatar"), id("isekai_mahou:trial_sentinel"));

    /** Biomas preferidos (ids ou #tags) que valem MAIS que o nascimento natural (ou suprem a falta dele). */
    private static final Map<ResourceLocation, List<String>> OVERRIDE = Map.ofEntries(
            Map.entry(id("minecraft:witch"), List.of("#c:is_swamp", "minecraft:dark_forest")),
            Map.entry(id("minecraft:pillager"), List.of("#c:is_plains", "#minecraft:is_taiga", "#minecraft:is_savanna")),
            Map.entry(id("minecraft:vindicator"), List.of("minecraft:dark_forest", "#minecraft:is_taiga")),
            Map.entry(id("minecraft:evoker"), List.of("minecraft:dark_forest")),
            Map.entry(id("minecraft:drowned"), List.of("#minecraft:is_beach", "#c:is_swamp")),
            Map.entry(id("minecraft:cave_spider"), List.of("minecraft:dark_forest", "#minecraft:is_forest")),
            Map.entry(id("minecraft:husk"), List.of("#c:is_desert")),
            Map.entry(id("minecraft:stray"), List.of("#c:is_snowy")),
            Map.entry(id("minecraft:zombie_villager"), List.of("#c:is_plains", "#minecraft:is_savanna", "#minecraft:is_taiga")));

    /** Cache: o tipo se comporta como chefe (vida alta). */
    private static final Map<EntityType<?>, Boolean> BOSS_LIKE = new ConcurrentHashMap<>();
    /** Cache: biomas onde o tipo nasce naturalmente, por dimensão. */
    private static final Map<ResourceKey<Level>, Map<EntityType<?>, Set<Holder<Biome>>>> NATURAL = new ConcurrentHashMap<>();
    /** Cache: biomas de terra possíveis por dimensão. */
    private static final Map<ResourceKey<Level>, Set<Holder<Biome>>> LAND = new ConcurrentHashMap<>();

    private static ResourceLocation id(String s) {
        return ResourceLocation.parse(s);
    }

    /** Servidor parou: esquece os caches (registros podem mudar no próximo mundo). */
    public static void clear() {
        BOSS_LIKE.clear();
        NATURAL.clear();
        LAND.clear();
    }

    // =====================================================================================
    //  Criaturas
    // =====================================================================================
    public static boolean huntable(ServerLevel level, EntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (DENY.contains(key)) return false;
        MobCategory cat = type.getCategory();
        if (cat == MobCategory.MISC || cat == MobCategory.AMBIENT || cat == MobCategory.WATER_CREATURE || cat == MobCategory.WATER_AMBIENT
                || cat == MobCategory.UNDERGROUND_WATER_CREATURE || cat == MobCategory.AXOLOTLS) return false;
        if (type.is(Tags.EntityTypes.BOSSES)) return false;
        if (bossLike(level, type)) return false;
        return OVERRIDE.containsKey(key) || !natural(level, type).isEmpty();
    }

    /** Vida máxima padrão (atributos registrados — sem criar a entidade) a partir de 150, ou sem atributos de criatura. */
    @SuppressWarnings("unchecked")
    private static boolean bossLike(ServerLevel level, EntityType<?> type) {
        return BOSS_LIKE.computeIfAbsent(type, t -> {
            if (!DefaultAttributes.hasSupplier(t)) return true;
            try {
                AttributeSupplier sup = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) t);
                return sup == null || !sup.hasAttribute(Attributes.MAX_HEALTH) || sup.getValue(Attributes.MAX_HEALTH) >= BOSS_HEALTH;
            } catch (RuntimeException ex) {
                return true;
            }
        });
    }

    /** Criaturas caçáveis de uma missão (de todos os objetivos de abate), sem repetir, na ordem dos objetivos. */
    public static List<EntityType<?>> huntableMobs(ServerLevel level, GuildQuest q) {
        Set<EntityType<?>> out = new LinkedHashSet<>();
        for (QuestObjective o : q.objectives()) {
            if (o.kind() != QuestObjective.Kind.KILL) continue;
            for (EntityType<?> t : o.existingEntities()) if (huntable(level, t)) out.add(t);
        }
        return new ArrayList<>(out);
    }

    /**
     * A missão ganha Área de Caça? Só missões de abate de criaturas comuns, sem objetivo de explorar (essas já mandam o
     * jogador a uma estrutura), numa dimensão com céu (Overworld e afins).
     */
    public static boolean eligible(ServerLevel level, GuildQuest q) {
        if (q.hasExplore()) return false;
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) return false;
        return !huntableMobs(level, q).isEmpty();
    }

    // =====================================================================================
    //  Biomas
    // =====================================================================================
    /** Biomas (desta dimensão) onde o tipo nasce sozinho. */
    public static Set<Holder<Biome>> natural(ServerLevel level, EntityType<?> type) {
        return NATURAL.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).computeIfAbsent(type, t -> {
            Set<Holder<Biome>> out = new HashSet<>();
            for (Holder<Biome> b : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
                MobSpawnSettings s = b.value().getMobSettings();
                for (MobCategory c : MobCategory.values()) {
                    for (MobSpawnSettings.SpawnerData sd : s.getMobs(c).unwrap()) {
                        if (sd.type == t) {
                            out.add(b);
                            break;
                        }
                    }
                }
            }
            return out;
        });
    }

    /** Biomas de terra (sem oceano, rio e cavernas) que existem nesta dimensão. */
    public static Set<Holder<Biome>> land(ServerLevel level) {
        return LAND.computeIfAbsent(level.dimension(), k -> {
            Set<Holder<Biome>> out = new HashSet<>();
            for (Holder<Biome> b : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
                if (isLand(b)) out.add(b);
            }
            return out;
        });
    }

    public static boolean isLand(Holder<Biome> b) {
        return !b.is(BiomeTags.IS_OCEAN) && !b.is(BiomeTags.IS_DEEP_OCEAN) && !b.is(BiomeTags.IS_RIVER)
                && !b.is(Tags.Biomes.IS_UNDERGROUND) && !b.is(Tags.Biomes.IS_CAVE);
    }

    /** A criatura combina com este bioma (mapa de preferências, senão nascimento natural). */
    public static boolean fits(ServerLevel level, EntityType<?> type, Holder<Biome> biome) {
        List<String> pref = OVERRIDE.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        if (pref != null) {
            for (String s : pref) if (matches(biome, s)) return true;
            return false;
        }
        return natural(level, type).contains(biome);
    }

    /** Nasce em quase toda parte (zumbi, aranha, creeper...): a área pode ir para qualquer terra firme. */
    public static boolean universal(ServerLevel level, EntityType<?> type) {
        if (OVERRIDE.containsKey(BuiltInRegistries.ENTITY_TYPE.getKey(type))) return false;
        Set<Holder<Biome>> land = land(level);
        if (land.isEmpty()) return true;
        int n = 0;
        for (Holder<Biome> b : natural(level, type)) if (land.contains(b)) n++;
        return n >= land.size() * UNIVERSAL_FRACTION;
    }

    private static boolean matches(Holder<Biome> biome, String s) {
        if (s.startsWith("#")) {
            ResourceLocation t = ResourceLocation.tryParse(s.substring(1));
            return t != null && biome.is(TagKey.create(Registries.BIOME, t));
        }
        ResourceLocation r = ResourceLocation.tryParse(s);
        return r != null && biome.is(r);
    }

    // =====================================================================================
    //  Elemento do bioma (essências e cor da área)
    // =====================================================================================
    public static Element elementOf(Holder<Biome> b) {
        if (b.is(Tags.Biomes.IS_SPOOKY) || b.is(Tags.Biomes.IS_DEAD)) return Element.SHADOW;
        if (b.is(Tags.Biomes.IS_DESERT) || b.is(BiomeTags.IS_BADLANDS) || b.is(BiomeTags.IS_SAVANNA) || (b.is(Tags.Biomes.IS_HOT)
                && b.is(Tags.Biomes.IS_DRY))) return Element.FIRE;
        if (b.is(Tags.Biomes.IS_SWAMP) || b.is(BiomeTags.IS_BEACH) || b.is(Tags.Biomes.IS_SNOWY) || b.is(Tags.Biomes.IS_ICY)
                || b.is(Tags.Biomes.IS_AQUATIC)) return Element.WATER;
        if (b.is(BiomeTags.IS_MOUNTAIN) || b.is(Tags.Biomes.IS_WINDSWEPT) || b.is(BiomeTags.IS_HILL) || b.is(Tags.Biomes.IS_MOUNTAIN)) {
            return Element.WIND;
        }
        if (b.is(Tags.Biomes.IS_FLORAL)) return Element.LIGHT;
        return Element.EARTH;
    }

    /** Agrupa tipos por id (para salvar). */
    public static List<ResourceLocation> ids(List<EntityType<?>> types) {
        List<ResourceLocation> out = new ArrayList<>();
        for (EntityType<?> t : types) out.add(BuiltInRegistries.ENTITY_TYPE.getKey(t));
        return out;
    }

    /** Tipos (que ainda existem) a partir dos ids salvos. */
    public static List<EntityType<?>> types(List<ResourceLocation> ids) {
        List<EntityType<?>> out = new ArrayList<>();
        for (ResourceLocation r : ids) BuiltInRegistries.ENTITY_TYPE.getOptional(r).ifPresent(out::add);
        return out;
    }

    private HuntRules() {}
}
