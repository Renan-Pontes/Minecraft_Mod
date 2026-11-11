package com.renanpontes.isekaimahou.feature.journal.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Condição de um passo do Diário. Avaliada com o {@link MagicData} (que também existe no cliente) e, para as
 * condições de inventário, com o jogador — então a mesma regra serve para o servidor marcar o passo e para a aba e o
 * rastreador mostrarem o progresso.
 *
 * Tipos aceitos no JSON ({@code "type"}):
 * <ul>
 *   <li>{@code path} [path]: classe escolhida (qualquer, ou a informada)</li>
 *   <li>{@code flag} flag: marcador do MagicData</li>
 *   <li>{@code flag_prefix} prefix, [exclude], [min]: nº de marcadores que começam com o prefixo (sem os que começam com
 *       algum dos {@code exclude}) — ex.: destinos de viagem descobertos {@code travel.*}</li>
 *   <li>{@code counter} key, min: contador ≥ min</li>
 *   <li>{@code counter_prefix} prefix, min, [distinct]: soma (ou nº de chaves distintas) dos contadores com o prefixo</li>
 *   <li>{@code circle} / {@code level} / {@code glyphs} min</li>
 *   <li>{@code element} element: elemento despertado · {@code elements} set (base|fusion|any), min</li>
 *   <li>{@code element_level} min, [element]: nível do elemento (ou o maior entre os despertados)</li>
 *   <li>{@code node} node, [element], [min]: nó de caminho (num elemento, ou em ≥ min elementos)</li>
 *   <li>{@code visit} dimension: já esteve na dimensão</li>
 *   <li>{@code bosses} ids, [min]: chefes derrotados (contador boss.&lt;id&gt; ou abate)</li>
 *   <li>{@code kills} ids, min: soma dos abates (kill.&lt;ns&gt;.&lt;id&gt;) das criaturas listadas</li>
 *   <li>{@code trial} rank, [element], [min]: Provação concluída (flag trial.&lt;el&gt;.&lt;rank&gt;), num elemento ou em ≥ min</li>
 *   <li>{@code glyph} id | ids, [min]: glifos específicos aprendidos</li>
 *   <li>{@code item} item | tag, [count]: itens no inventário agora</li>
 *   <li>{@code placed} block, [min]: blocos colocados (o Diário conta os blocos citados: placed.&lt;ns&gt;.&lt;id&gt;)</li>
 *   <li>{@code all} / {@code any} conditions: combinações</li>
 * </ul>
 */
public sealed interface JournalCondition {
    /** Valor atual (pode passar do alvo; a tela limita). {@code player} pode ser null (só condições de inventário o usam). */
    int current(MagicData d, @Nullable Player player);

    /** Alvo (o passo está pronto quando {@code current >= target}). */
    int target();

    default boolean done(MagicData d, @Nullable Player player) {
        return current(d, player) >= target();
    }

    /**
     * A condição pode ser cumprida neste jogo? (item/glifo de outro módulo que não existe → false: o passo some).
     * Só depende dos registros, que são iguais no cliente e no servidor.
     */
    default boolean available() {
        return true;
    }

    /** Dimensões citadas (o servidor marca a visita quando o jogador está nelas). */
    default void collectDimensions(Set<ResourceLocation> out) {}

    /** Blocos citados em "placed" (o servidor conta só a colocação destes). */
    default void collectPlaced(Set<ResourceLocation> out) {}

    /** Marcador gravado pelo próprio Diário quando o jogador é visto numa dimensão. */
    static String visitFlag(ResourceLocation dim) {
        return "journal.visit." + dim.getNamespace() + "." + dim.getPath();
    }

    /** Contador de blocos colocados mantido pelo Diário. */
    static String placedKey(ResourceLocation block) {
        return "placed." + block.getNamespace() + "." + block.getPath();
    }

    // =====================================================================================
    //  Tipos
    // =====================================================================================
    record PathChosen(String pathId) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            MagicPath path = d.path();
            return path != MagicPath.NONE && (pathId == null || path.id.equals(pathId)) ? 1 : 0;
        }
        @Override public int target() { return 1; }
    }

    record Flag(String flag) implements JournalCondition {
        @Override public int current(MagicData d, Player p) { return d.hasFlag(flag) ? 1 : 0; }
        @Override public int target() { return 1; }
    }

    /** Quantos marcadores começam com {@code prefix} (menos os que começam com algum de {@code exclude}). */
    record FlagPrefix(String prefix, List<String> exclude, int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            int n = 0;
            for (String f : d.flags()) {
                if (!f.startsWith(prefix)) continue;
                boolean skip = false;
                for (String ex : exclude) {
                    if (f.startsWith(ex)) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) n++;
            }
            return n;
        }
        @Override public int target() { return min; }
    }

    record Counter(String key, int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) { return d.counter(key); }
        @Override public int target() { return min; }
    }

    record CounterPrefix(String prefix, int min, boolean distinct) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            int total = 0;
            for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
                if (!e.getKey().startsWith(prefix) || e.getValue() <= 0) continue;
                total += distinct ? 1 : e.getValue();
            }
            return total;
        }
        @Override public int target() { return min; }
    }

    record Circle(int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) { return d.circle(); }
        @Override public int target() { return min; }
    }

    record Level(int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) { return d.level(); }
        @Override public int target() { return min; }
    }

    record Glyphs(int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) { return d.glyphs().size(); }
        @Override public int target() { return min; }
    }

    record ElementAwake(Element element) implements JournalCondition {
        @Override public int current(MagicData d, Player p) { return d.isAwakened(element) ? 1 : 0; }
        @Override public int target() { return 1; }
    }

    /** set: "base" (6 base), "fusion" (15 fusões) ou "any" (todos menos o Arcano). */
    record Elements(String set, int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            int n = 0;
            for (Element e : Element.values()) {
                if (e == Element.ARCANE || !d.isAwakened(e)) continue;
                if (set.equals("base") && !e.isBase()) continue;
                if (set.equals("fusion") && !e.isFusion()) continue;
                n++;
            }
            return n;
        }
        @Override public int target() { return min; }
    }

    /** Nível de um elemento; sem elemento, o MAIOR nível entre os despertados (o Arcano não conta: não tem caminho). */
    record ElementLevel(@Nullable Element element, int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            if (element != null) return d.isAwakened(element) ? Math.max(0, d.elementLevel(element)) : 0;
            int best = 0;
            for (Element e : Element.values()) {
                if (e != Element.ARCANE && d.isAwakened(e)) best = Math.max(best, d.elementLevel(e));
            }
            return best;
        }
        @Override public int target() { return min; }
    }

    record Node(String node, Element element, int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            if (element != null) return d.hasNode(element, node) ? 1 : 0;
            int n = 0;
            for (Element e : Element.values()) if (d.hasNode(e, node)) n++;
            return n;
        }
        @Override public int target() { return element != null ? 1 : min; }
    }

    record Visit(ResourceLocation dimension) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            String key = "visit." + dimension.getNamespace() + "." + dimension.getPath();
            return d.counter(key) > 0 || d.hasFlag(visitFlag(dimension)) ? 1 : 0;
        }
        @Override public int target() { return 1; }
        @Override public void collectDimensions(Set<ResourceLocation> out) { out.add(dimension); }
    }

    record Bosses(List<ResourceLocation> ids, int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            int n = 0;
            for (ResourceLocation id : ids) {
                if (d.counter("boss." + id.getPath()) > 0 || d.counter("kill." + id.getNamespace() + "." + id.getPath()) > 0) n++;
            }
            return n;
        }
        @Override public int target() { return min; }
    }

    /** Soma dos abates das criaturas listadas (ids ausentes do jogo só não somam nada). */
    record Kills(List<ResourceLocation> ids, int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            int n = 0;
            for (ResourceLocation id : com.renanpontes.isekaimahou.core.api.KillFamilies.expand(ids)) {
                n += Math.max(0, d.counter("kill." + id.getNamespace() + "." + id.getPath()));
            }
            return n;
        }
        @Override public int target() { return min; }
        @Override public boolean available() {
            for (ResourceLocation id : ids) if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return true;
            return false;
        }
    }

    /**
     * Provação de rank {@code rank} concluída (flags {@code trial.<el>.<rank>} do núcleo). Num elemento, ou em pelo menos
     * {@code min} elementos diferentes. Um rank maior concluído também vale (cada rank exige o anterior).
     */
    record Trial(@Nullable Element element, int rank, int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            if (element != null) return TrialsApi.clearedRank(d, element) >= rank ? 1 : 0;
            int n = 0;
            for (Element e : Element.values()) {
                if (e != Element.ARCANE && TrialsApi.clearedRank(d, e) >= rank) n++;
            }
            return n;
        }
        @Override public int target() { return element != null ? 1 : min; }
    }

    /** Glifos específicos aprendidos (conta só os que existem no jogo). */
    record GlyphKnown(List<ResourceLocation> ids, int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            int n = 0;
            for (ResourceLocation id : ids) if (d.knowsGlyph(id)) n++;
            return n;
        }
        @Override public int target() { return min; }
        @Override public boolean available() {
            int n = 0;
            for (ResourceLocation id : ids) if (GlyphRegistry.get(id) != null) n++;
            return n >= min;
        }
    }

    /** Itens no inventário (por id ou por tag). Sem jogador (contexto sem inventário), vale 0. */
    record HasItem(@Nullable ResourceLocation item, @Nullable TagKey<Item> tag, int count) implements JournalCondition {
        @Override public int current(MagicData d, @Nullable Player p) {
            if (p == null) return 0;
            Item exact = item == null ? null : BuiltInRegistries.ITEM.getOptional(item).orElse(null);
            if (item != null && exact == null) return 0;
            Inventory inv = p.getInventory();
            int n = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty()) continue;
                if (exact != null ? s.is(exact) : s.is(tag)) n += s.getCount();
            }
            return n;
        }
        @Override public int target() { return count; }
        @Override public boolean available() { return item == null || BuiltInRegistries.ITEM.containsKey(item); }
    }

    /** Blocos colocados pelo jogador (contados pelo Diário a partir do momento em que o passo existe). */
    record Placed(ResourceLocation block, int min) implements JournalCondition {
        @Override public int current(MagicData d, Player p) { return d.counter(placedKey(block)); }
        @Override public int target() { return min; }
        @Override public boolean available() { return BuiltInRegistries.BLOCK.containsKey(block); }
        @Override public void collectPlaced(Set<ResourceLocation> out) { out.add(block); }
    }

    record All(List<JournalCondition> conditions) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            int n = 0;
            for (JournalCondition c : conditions) if (c.done(d, p)) n++;
            return n;
        }
        @Override public int target() { return conditions.size(); }
        @Override public boolean available() { return conditions.stream().allMatch(JournalCondition::available); }
        @Override public void collectDimensions(Set<ResourceLocation> out) { conditions.forEach(c -> c.collectDimensions(out)); }
        @Override public void collectPlaced(Set<ResourceLocation> out) { conditions.forEach(c -> c.collectPlaced(out)); }
    }

    /** Qualquer uma (das que existem neste jogo). */
    record Any(List<JournalCondition> conditions) implements JournalCondition {
        @Override public int current(MagicData d, Player p) {
            for (JournalCondition c : conditions) if (c.available() && c.done(d, p)) return 1;
            return 0;
        }
        @Override public int target() { return 1; }
        @Override public boolean available() { return conditions.stream().anyMatch(JournalCondition::available); }
        @Override public void collectDimensions(Set<ResourceLocation> out) { conditions.forEach(c -> c.collectDimensions(out)); }
        @Override public void collectPlaced(Set<ResourceLocation> out) { conditions.forEach(c -> c.collectPlaced(out)); }
    }

    // =====================================================================================
    //  Leitura do JSON
    // =====================================================================================
    static JournalCondition parse(JsonObject o) {
        String type = GsonHelper.getAsString(o, "type");
        return switch (type) {
            case "path" -> new PathChosen(o.has("path") ? GsonHelper.getAsString(o, "path") : null);
            case "flag" -> new Flag(GsonHelper.getAsString(o, "flag"));
            case "flag_prefix" -> {
                List<String> exclude = new ArrayList<>();
                if (o.has("exclude")) {
                    for (JsonElement e : GsonHelper.getAsJsonArray(o, "exclude")) exclude.add(e.getAsString());
                }
                String prefix = GsonHelper.getAsString(o, "prefix");
                if (prefix.isEmpty()) throw new JsonParseException("flag_prefix sem prefixo");
                yield new FlagPrefix(prefix, List.copyOf(exclude), min(o, 1));
            }
            case "counter" -> new Counter(GsonHelper.getAsString(o, "key"), min(o, 1));
            case "counter_prefix" -> new CounterPrefix(GsonHelper.getAsString(o, "prefix"), min(o, 1),
                    GsonHelper.getAsBoolean(o, "distinct", false));
            case "circle" -> new Circle(min(o, 1));
            case "level" -> new Level(min(o, 1));
            case "glyphs" -> new Glyphs(min(o, 1));
            case "element" -> new ElementAwake(element(GsonHelper.getAsString(o, "element")));
            case "elements" -> {
                String set = GsonHelper.getAsString(o, "set", "any");
                if (!set.equals("base") && !set.equals("fusion") && !set.equals("any")) {
                    throw new JsonParseException("conjunto de elementos invalido: " + set);
                }
                yield new Elements(set, min(o, 1));
            }
            case "element_level" -> new ElementLevel(o.has("element") ? element(GsonHelper.getAsString(o, "element")) : null, min(o, 1));
            case "node" -> new Node(GsonHelper.getAsString(o, "node"),
                    o.has("element") ? element(GsonHelper.getAsString(o, "element")) : null, min(o, 1));
            case "visit" -> new Visit(ResourceLocation.parse(GsonHelper.getAsString(o, "dimension")));
            case "bosses" -> {
                List<ResourceLocation> ids = ids(o, "ids");
                yield new Bosses(ids, Math.min(ids.size(), min(o, ids.size())));
            }
            case "kills" -> new Kills(ids(o, "ids"), min(o, 1));
            case "trial" -> {
                int rank = GsonHelper.getAsInt(o, "rank", 1);
                if (rank < 1 || rank > Progression.MAX_TRIAL_RANK) throw new JsonParseException("rank de provacao invalido: " + rank);
                yield new Trial(o.has("element") ? element(GsonHelper.getAsString(o, "element")) : null, rank, min(o, 1));
            }
            case "glyph" -> {
                List<ResourceLocation> ids = o.has("ids") ? ids(o, "ids") : List.of(modId(GsonHelper.getAsString(o, "id")));
                yield new GlyphKnown(ids, Math.min(ids.size(), min(o, 1)));
            }
            case "item" -> {
                if (o.has("tag")) {
                    String t = GsonHelper.getAsString(o, "tag");
                    ResourceLocation tagId = ResourceLocation.parse(t.startsWith("#") ? t.substring(1) : t);
                    yield new HasItem(null, TagKey.create(Registries.ITEM, tagId), Math.max(1, GsonHelper.getAsInt(o, "count", 1)));
                }
                yield new HasItem(ResourceLocation.parse(GsonHelper.getAsString(o, "item")), null,
                        Math.max(1, GsonHelper.getAsInt(o, "count", 1)));
            }
            case "placed" -> new Placed(modId(GsonHelper.getAsString(o, "block")), min(o, 1));
            case "all" -> new All(list(GsonHelper.getAsJsonArray(o, "conditions")));
            case "any" -> new Any(list(GsonHelper.getAsJsonArray(o, "conditions")));
            default -> throw new JsonParseException("tipo de condicao desconhecido: " + type);
        };
    }

    private static int min(JsonObject o, int def) {
        return Math.max(1, GsonHelper.getAsInt(o, "min", def));
    }

    private static Element element(String id) {
        Element e = Element.byId(id);
        if (e == null) throw new JsonParseException("elemento desconhecido: " + id);
        return e;
    }

    private static List<ResourceLocation> ids(JsonObject o, String field) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, field)) ids.add(modId(e.getAsString()));
        if (ids.isEmpty()) throw new JsonParseException("lista vazia: " + field);
        return List.copyOf(ids);
    }

    /** "ignis" → isekai_mahou:ignis; "minecraft:wither" fica como está. */
    static ResourceLocation modId(String s) {
        return s.contains(":") ? ResourceLocation.parse(s) : IsekaiMahou.id(s);
    }

    private static List<JournalCondition> list(JsonArray arr) {
        List<JournalCondition> out = new ArrayList<>();
        for (JsonElement e : arr) out.add(parse(e.getAsJsonObject()));
        if (out.isEmpty()) throw new JsonParseException("lista de condicoes vazia");
        return List.copyOf(out);
    }
}
