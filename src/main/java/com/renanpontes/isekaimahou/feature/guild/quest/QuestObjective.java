package com.renanpontes.isekaimahou.feature.guild.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.TrialsApi;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Um objetivo de missão (data-driven). Tipos:
 * <ul>
 *   <li>{@code kill} (ou {@code boss}): abates desde que a missão foi aceita (delta dos contadores {@code kill.<ns>.<mob>} e
 *       {@code boss.<mob>});</li>
 *   <li>{@code gather}: itens no inventário (entregues — consumidos — ao concluir);</li>
 *   <li>{@code explore}: estar numa dimensão, bioma ou estrutura enquanto a missão está ativa;</li>
 *   <li>{@code counter}: um contador qualquer do MagicData (delta, ou absoluto com {@code "absolute": true});</li>
 *   <li>{@code flag}: um marcador do MagicData;</li>
 *   <li>{@code trial_clear}: Câmaras de Provação concluídas (módulo trials), com {@code "rank"} mínimo (1–7),
 *       {@code "element"} opcional e {@code "distinct": true} para contar ELEMENTOS diferentes. Delta desde o aceite, ou
 *       absoluto com {@code "absolute": true} (total de conclusões / elementos com a Provação daquele rank).</li>
 * </ul>
 * Qualquer tipo aceita {@code "icon": "<id de item>"} para trocar o ícone mostrado.
 */
public record QuestObjective(Kind kind, List<ResourceLocation> entities, Optional<String> item, Optional<ResourceLocation> dimension,
                             Optional<String> biome, Optional<String> structure, Optional<String> key, boolean absolute, int count,
                             Optional<String> label, int rank, Optional<String> element, boolean distinct,
                             Optional<ResourceLocation> iconItem) {

    public enum Kind {
        KILL, GATHER, EXPLORE, COUNTER, FLAG, TRIAL_CLEAR;

        public static final Codec<Kind> CODEC = Codec.STRING.comapFlatMap(s -> {
            // "boss" é sinônimo de "kill": os abates de chefe também contam pelo contador boss.<mob>.
            if (s.equalsIgnoreCase("boss")) return DataResult.success(KILL);
            for (Kind k : values()) if (k.name().equalsIgnoreCase(s)) return DataResult.success(k);
            return DataResult.error(() -> "Tipo de objetivo invalido: " + s);
        }, k -> k.name().toLowerCase(Locale.ROOT));
    }

    /** Aceita "entity": "ns:id" ou "entity": ["ns:a", "ns:b"]. */
    private static final Codec<List<ResourceLocation>> ENTITY_LIST =
            Codec.withAlternative(ResourceLocation.CODEC.listOf(), ResourceLocation.CODEC, List::of);

    public static final Codec<QuestObjective> CODEC = RecordCodecBuilder.<QuestObjective>create(i -> i.group(
            Kind.CODEC.fieldOf("type").forGetter(QuestObjective::kind),
            ENTITY_LIST.optionalFieldOf("entity", List.of()).forGetter(QuestObjective::entities),
            Codec.STRING.optionalFieldOf("item").forGetter(QuestObjective::item),
            ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(QuestObjective::dimension),
            Codec.STRING.optionalFieldOf("biome").forGetter(QuestObjective::biome),
            Codec.STRING.optionalFieldOf("structure").forGetter(QuestObjective::structure),
            Codec.STRING.optionalFieldOf("key").forGetter(QuestObjective::key),
            Codec.BOOL.optionalFieldOf("absolute", false).forGetter(QuestObjective::absolute),
            Codec.INT.optionalFieldOf("count", 1).forGetter(QuestObjective::count),
            Codec.STRING.optionalFieldOf("label").forGetter(QuestObjective::label),
            Codec.INT.optionalFieldOf("rank", 1).forGetter(QuestObjective::rank),
            Codec.STRING.optionalFieldOf("element").forGetter(QuestObjective::element),
            Codec.BOOL.optionalFieldOf("distinct", false).forGetter(QuestObjective::distinct),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(QuestObjective::iconItem)
    ).apply(i, QuestObjective::new)).validate(QuestObjective::validate);

    private static DataResult<QuestObjective> validate(QuestObjective o) {
        if (o.count < 1) return DataResult.error(() -> "count precisa ser >= 1");
        return switch (o.kind) {
            case KILL -> o.entities.isEmpty() ? DataResult.error(() -> "kill precisa de \"entity\"") : DataResult.success(o);
            case GATHER -> o.item.isEmpty() ? DataResult.error(() -> "gather precisa de \"item\"") : DataResult.success(o);
            case EXPLORE -> {
                int n = (o.dimension.isPresent() ? 1 : 0) + (o.biome.isPresent() ? 1 : 0) + (o.structure.isPresent() ? 1 : 0);
                yield n != 1 ? DataResult.error(() -> "explore precisa de exatamente um: dimension, biome ou structure")
                        : DataResult.success(o);
            }
            case COUNTER, FLAG -> o.key.isEmpty() ? DataResult.error(() -> o.kind + " precisa de \"key\"") : DataResult.success(o);
            case TRIAL_CLEAR -> {
                if (o.rank < 1 || o.rank > Progression.MAX_TRIAL_RANK) {
                    yield DataResult.error(() -> "trial_clear: rank precisa ser de 1 a " + Progression.MAX_TRIAL_RANK);
                }
                if (o.element.isPresent()) {
                    Element e = Element.byId(o.element.get());
                    if (e == null || e == Element.ARCANE) yield DataResult.error(() -> "trial_clear: elemento invalido " + o.element.get());
                    if (o.distinct) yield DataResult.error(() -> "trial_clear: \"distinct\" nao combina com \"element\"");
                }
                if (o.distinct && o.count > trialElements(null).size()) {
                    yield DataResult.error(() -> "trial_clear: \"count\" maior que o numero de elementos");
                }
                yield DataResult.success(o);
            }
        };
    }

    private static final String OBJECTIVE_KEY = "guild.isekai_mahou.objective.";
    private static final String TRIAL_KEY = "guild.isekai_mahou.trial_subject.";

    /** Explorar e marcadores só "valem 1". */
    public int target() {
        return kind == Kind.EXPLORE || kind == Kind.FLAG ? 1 : count;
    }

    // =====================================================================================
    //  Disponibilidade (ids de outros módulos podem não existir)
    // =====================================================================================
    public boolean isAvailable(MinecraftServer server) {
        return switch (kind) {
            case KILL -> entities.stream().anyMatch(BuiltInRegistries.ENTITY_TYPE::containsKey);
            case GATHER -> isTag(item.get()) || BuiltInRegistries.ITEM.containsKey(parse(item.get()));
            case EXPLORE -> {
                if (dimension.isPresent()) yield server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension.get())) != null;
                if (biome.isPresent()) yield isTag(biome.get())
                        || server.registryAccess().registryOrThrow(Registries.BIOME).containsKey(parse(biome.get()));
                yield isTag(structure.get())
                        || server.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(parse(structure.get()));
            }
            case COUNTER, FLAG -> true;
            // Sem o módulo trials ninguém conclui Provações: a missão fica fora do quadro.
            case TRIAL_CLEAR -> TrialsApi.handler() != null;
        };
    }

    /** Entidades deste objetivo que existem no jogo. */
    public List<EntityType<?>> existingEntities() {
        List<EntityType<?>> out = new ArrayList<>();
        for (ResourceLocation id : entities) BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(out::add);
        return out;
    }

    // =====================================================================================
    //  Linha de base (valores no momento do aceite)
    // =====================================================================================
    /**
     * Valores ATUAIS dos contadores que este objetivo acompanha (vira a linha de base no aceite).
     * Chaves que começam com "@" são contadores virtuais (somas calculadas, ver {@link #trialValue}).
     */
    public Map<String, Integer> snapshot(MagicData md) {
        Map<String, Integer> out = new LinkedHashMap<>();
        switch (kind) {
            case KILL -> {
                for (ResourceLocation id : com.renanpontes.isekaimahou.core.api.KillFamilies.expand(entities)) {
                    for (String k : List.of(killKey(id), "boss." + id.getPath(), "boss." + id.getNamespace() + "." + id.getPath())) {
                        out.put(k, md.counter(k));
                    }
                }
            }
            case COUNTER -> { if (!absolute) out.put(key.get(), md.counter(key.get())); }
            case TRIAL_CLEAR -> {
                if (absolute) break;
                if (distinct) {
                    for (Element e : trialElements(null)) out.put(trialKey(rank, e), trialValue(md, rank, e));
                } else {
                    Element e = trialElement();
                    out.put(trialKey(rank, e), trialValue(md, rank, e));
                }
            }
            default -> { }
        }
        return out;
    }

    public static String killKey(ResourceLocation id) {
        return "kill." + id.getNamespace() + "." + id.getPath();
    }

    // =====================================================================================
    //  Provações (trial_clear)
    // =====================================================================================
    /** Elemento do objetivo (null = qualquer). */
    @Nullable
    public Element trialElement() {
        return element.map(Element::byId).orElse(null);
    }

    /** Elementos que contam: o informado, ou todos os que têm Provação (todos menos o Arcano). */
    public static List<Element> trialElements(@Nullable Element only) {
        if (only != null) return List.of(only);
        List<Element> out = new ArrayList<>();
        for (Element e : Element.values()) if (e != Element.ARCANE) out.add(e);
        return out;
    }

    /**
     * Chave da linha de base: os contadores reais do núcleo quando dá ({@code trial.clear}, {@code trial.clear.<el>}),
     * senão uma chave virtual "@trial.<rank>[.<el>]".
     */
    public static String trialKey(int minRank, @Nullable Element e) {
        if (minRank <= 1) return e == null ? "trial.clear" : "trial.clear." + e.id;
        return "@trial." + minRank + (e == null ? "" : "." + e.id);
    }

    /** Quantas Provações de rank &gt;= {@code minRank} foram concluídas (no elemento {@code e}, ou em todos). */
    public static int trialValue(MagicData md, int minRank, @Nullable Element e) {
        if (minRank <= 1) return md.counter(trialKey(1, e));
        int sum = 0;
        for (Element el : trialElements(e)) {
            for (int r = minRank; r <= Progression.MAX_TRIAL_RANK; r++) sum += md.counter("trial." + el.id + "." + r);
        }
        return sum;
    }

    /** Elementos com alguma Provação de rank &gt;= {@code minRank} concluída (flags do núcleo). */
    public static int trialElementsCleared(MagicData md, int minRank) {
        int n = 0;
        for (Element e : trialElements(null)) {
            if (TrialsApi.clearedRank(md, e) >= minRank) n++;
        }
        return n;
    }

    // =====================================================================================
    //  Itens (gather)
    // =====================================================================================
    public boolean matches(ItemStack stack) {
        if (kind != Kind.GATHER || stack.isEmpty()) return false;
        String s = item.get();
        if (isTag(s)) return stack.is(TagKey.create(Registries.ITEM, parse(s)));
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(parse(s));
    }

    // =====================================================================================
    //  Exploração
    // =====================================================================================
    public boolean isAt(ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (kind != Kind.EXPLORE) return false;
        if (dimension.isPresent()) return level.dimension().location().equals(dimension.get());
        if (biome.isPresent()) {
            Holder<Biome> b = level.getBiome(pos);
            String s = biome.get();
            return isTag(s) ? b.is(TagKey.create(Registries.BIOME, parse(s))) : b.is(parse(s));
        }
        String s = structure.get();
        if (!level.isLoaded(pos)) return false;
        if (isTag(s)) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, parse(s));
            return level.structureManager().getStructureWithPieceAt(pos, tag).isValid();
        }
        ResourceLocation id = parse(s);
        return level.structureManager().getStructureWithPieceAt(pos, (Holder<Structure> h) -> h.is(id)).isValid();
    }

    // =====================================================================================
    //  Texto e ícone (montados no servidor e enviados prontos ao cliente)
    // =====================================================================================
    /** "Derrotar: Goblin" / "Entregar: Trigo" / "Explorar: O Nether"... (o progresso é desenhado à parte). */
    public Component describe() {
        return Component.translatable(OBJECTIVE_KEY + kind.name().toLowerCase(Locale.ROOT), subject());
    }

    public Component subject() {
        if (label.isPresent()) return Component.translatable(label.get());
        return switch (kind) {
            case KILL -> {
                List<EntityType<?>> types = existingEntities();
                if (types.isEmpty()) yield Component.literal(entities.get(0).toString());
                Component first = types.get(0).getDescription();
                yield types.size() == 1 ? first : Component.translatable("guild.isekai_mahou.objective.and_more", first, types.size() - 1);
            }
            case GATHER -> {
                String s = item.get();
                if (isTag(s)) {
                    ResourceLocation t = parse(s);
                    yield Component.translatableWithFallback("tag.item." + t.getNamespace() + "." + t.getPath().replace('/', '.'), "#" + t);
                }
                Item it = BuiltInRegistries.ITEM.get(parse(s));
                yield it.getDescription();
            }
            case EXPLORE -> {
                if (dimension.isPresent()) {
                    ResourceLocation d = dimension.get();
                    yield Component.translatableWithFallback("guild.isekai_mahou.place." + d.getNamespace() + "." + d.getPath(), d.toString());
                }
                if (biome.isPresent() && !isTag(biome.get())) {
                    ResourceLocation b = parse(biome.get());
                    yield Component.translatableWithFallback("biome." + b.getNamespace() + "." + b.getPath(), b.toString());
                }
                String s = biome.orElseGet(structure::get);
                ResourceLocation p = parse(s);
                yield Component.translatableWithFallback("guild.isekai_mahou.place." + p.getNamespace() + "." + p.getPath().replace('/', '.'), s);
            }
            case COUNTER, FLAG -> Component.literal(key.get());
            case TRIAL_CLEAR -> trialSubject();
        };
    }

    /** "Provação Elemental" / "Provação de rank III+" / "Provação de Fogo (rank II+)" / "Provações em elementos diferentes". */
    private Component trialSubject() {
        // "III+" — no rank máximo não há "ou mais".
        String ranks = TrialsApi.roman(rank) + (rank < Progression.MAX_TRIAL_RANK ? "+" : "");
        if (distinct) {
            return rank <= 1 ? Component.translatable(TRIAL_KEY + "distinct")
                    : Component.translatable(TRIAL_KEY + "distinct_rank", ranks);
        }
        Element e = trialElement();
        if (e != null) {
            Component name = e.displayName().copy().withColor(e.color);
            return rank <= 1 ? Component.translatable(TRIAL_KEY + "element", name)
                    : Component.translatable(TRIAL_KEY + "element_rank", name, ranks);
        }
        return rank <= 1 ? Component.translatable(TRIAL_KEY + "any") : Component.translatable(TRIAL_KEY + "rank", ranks);
    }

    public ItemStack icon() {
        if (iconItem.isPresent()) {
            Item custom = BuiltInRegistries.ITEM.getOptional(iconItem.get()).orElse(Items.AIR);
            if (custom != Items.AIR) return new ItemStack(custom);
        }
        return switch (kind) {
            case KILL -> {
                for (EntityType<?> t : existingEntities()) {
                    SpawnEggItem egg = SpawnEggItem.byId(t);
                    if (egg != null) yield new ItemStack(egg);
                }
                yield new ItemStack(Items.IRON_SWORD);
            }
            case GATHER -> {
                String s = item.get();
                if (isTag(s)) {
                    TagKey<Item> tag = TagKey.create(Registries.ITEM, parse(s));
                    yield BuiltInRegistries.ITEM.getTag(tag).flatMap(set -> set.stream().findFirst())
                            .map(h -> new ItemStack(h.value())).orElse(new ItemStack(Items.CHEST));
                }
                yield new ItemStack(BuiltInRegistries.ITEM.get(parse(s)));
            }
            case EXPLORE -> {
                if (dimension.isPresent()) {
                    ResourceLocation d = dimension.get();
                    if (d.equals(Level.NETHER.location())) yield new ItemStack(Items.NETHERRACK);
                    if (d.equals(Level.END.location())) yield new ItemStack(Items.END_STONE);
                    yield new ItemStack(Items.ENDER_EYE);
                }
                yield new ItemStack(biome.isPresent() ? Items.COMPASS : Items.FILLED_MAP);
            }
            case COUNTER -> new ItemStack(Items.WRITABLE_BOOK);
            case FLAG -> new ItemStack(Items.BOOK);
            case TRIAL_CLEAR -> trialIcon();
        };
    }

    /** Essência do elemento (quando há uma), senão o Altar da Provação (módulo trials), senão um Cristal de Mana. */
    private ItemStack trialIcon() {
        Element e = trialElement();
        if (e != null) {
            Element base = e.isFusion() ? e.parentA : e;
            var essence = CoreRegistries.ESSENCES.get(base);
            if (essence != null) return new ItemStack(essence.get());
        }
        Item altar = BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id("trial_altar")).orElse(Items.AIR);
        if (altar != Items.AIR) return new ItemStack(altar);
        return new ItemStack(CoreRegistries.MANA_CRYSTAL.get());
    }

    // =====================================================================================
    static boolean isTag(String s) {
        return s.startsWith("#");
    }

    static ResourceLocation parse(String s) {
        String raw = isTag(s) ? s.substring(1) : s;
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id != null ? id : ResourceLocation.withDefaultNamespace("air");
    }
}
