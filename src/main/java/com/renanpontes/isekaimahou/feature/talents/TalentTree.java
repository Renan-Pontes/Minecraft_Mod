package com.renanpontes.isekaimahou.feature.talents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Árvore de talentos de UMA classe, lida de {@code data/isekai_mahou/talents/<classe>.json} (o mesmo JSON vai para o
 * cliente, que monta a tela com este mesmo leitor). Formato (release 1.0, "layout 2"):
 * <pre>
 * { "layout": 2,
 *   "branches":   [ {"id": "study", "color": "5555FF"}, ... ],
 *   "nodes":      [ {"id": "sharp_mind", "branch": 0, "tier": 1, "max": 5, "level": 6, "icon": "minecraft:book",
 *                    "kind": "small|notable|choice|capstone|mastery", "branch_points": 3,
 *                    "exclusive": ["outro_no"], "subclass": "mage_archmage", "curve": 0.95,
 *                    "requires": {"tome_reading": 3},
 *                    "effects": [ {"attribute": "isekai_mahou:mana_regen", "amount": 0.02, "op": "add_value"},
 *                                 {"stat": "crit_chance", "amount": 0.01} ]}, ... ],
 *   "subclasses": [ {"id": "mage_battlemage", "color": "FF5555", "icon": "minecraft:iron_sword", "effects": [ ... ]}, ... ] }
 * </pre>
 * Regras de um nó: nível mínimo ({@code level}); {@code branch_points} = pontos gastos nas fileiras ACIMA dele no mesmo
 * ramo (ou, num nó de especialização, na mesma fileira exclusiva); {@code exclusive} = nós que não podem ser comprados
 * junto (escolha "um ou outro"); {@code subclass} = só quem tem essa especialização compra; {@code requires} = nós
 * específicos (formato antigo, ainda aceito). {@code amount} é POR NÍVEL do nó; com {@code curve} (0 &lt; q &lt; 1) cada
 * nível rende q vezes o anterior (o nó de Maestria: ganhos pequenos que diminuem). Efeitos são de dois tipos:
 * {@code attribute} (vira modificador de atributo) e {@code stat} (passiva lida pelo {@link TalentCombat} — ver as chaves
 * em {@link TalentStats}). Campos ausentes têm padrão; nós/efeitos inválidos são ignorados (um datapack quebrado não
 * derruba o servidor).
 */
public final class TalentTree {
    /** Tipo do nó: muda a moldura na tela e nada mais (as regras vêm dos outros campos). */
    public enum Kind {
        SMALL, NOTABLE, CHOICE, CAPSTONE, MASTERY;

        static Kind byName(String s) {
            for (Kind k : values()) if (k.name().equalsIgnoreCase(s)) return k;
            return SMALL;
        }
    }

    /** Um efeito de nó/especialização. {@code attribute} null = passiva ({@code stat}). */
    public record Effect(@Nullable ResourceLocation attribute, String stat, double amount, AttributeModifier.Operation op) {
        public boolean isAttribute() { return attribute != null; }
    }

    public record Branch(String id, int color) {}

    public record Node(String id, int branch, int tier, int max, int level, Map<String, Integer> requires, String icon,
                       List<Effect> effects, Kind kind, int branchPoints, List<String> exclusive, String subclass,
                       double curve) {
        /** Nó da fileira exclusiva de uma especialização. */
        public boolean isSpec() { return !subclass.isEmpty(); }

        /** Quantos "níveis cheios" valem {@code rank} níveis deste nó (linear, ou a soma que diminui da Maestria). */
        public double scale(int rank) {
            int r = Math.max(0, Math.min(rank, max));
            if (curve <= 0.0 || curve >= 1.0) return r;
            return (1.0 - Math.pow(curve, r)) / (1.0 - curve);
        }

        /** O mesmo grupo de contagem de pontos (ramo, ou fileira da especialização)? */
        public boolean sameGroup(Node other) {
            return isSpec() ? subclass.equals(other.subclass) : !other.isSpec() && branch == other.branch;
        }
    }

    public record Subclass(String id, int color, String icon, List<Effect> effects) {}

    private final String classId;
    private final List<Branch> branches;
    private final Map<String, Node> nodes;
    private final List<Subclass> subclasses;
    private final int tiers;
    private final int layout;

    private TalentTree(String classId, List<Branch> branches, Map<String, Node> nodes, List<Subclass> subclasses, int layout) {
        this.classId = classId;
        this.branches = List.copyOf(branches);
        this.nodes = nodes;
        this.subclasses = List.copyOf(subclasses);
        this.layout = layout;
        int t = 0;
        for (Node n : nodes.values()) if (!n.isSpec()) t = Math.max(t, n.tier() + 1);
        this.tiers = t;
    }

    public String classId() { return classId; }
    public List<Branch> branches() { return branches; }
    public Collection<Node> nodes() { return nodes.values(); }
    public List<Subclass> subclasses() { return subclasses; }
    /** Quantas fileiras (tiers) os ramos da classe têm (sem contar a fileira das especializações). */
    public int tiers() { return tiers; }
    /** Versão do desenho da árvore (campo {@code layout} do JSON; 1 = a árvore antiga de 5 fileiras). */
    public int layout() { return layout; }

    @Nullable
    public Node node(String id) { return nodes.get(id); }

    @Nullable
    public Subclass subclass(String id) {
        for (Subclass s : subclasses) if (s.id().equals(id)) return s;
        return null;
    }

    /** Os nós exclusivos de uma especialização, na ordem da fileira. */
    public List<Node> specNodes(String subclassId) {
        List<Node> out = new ArrayList<>();
        for (Node n : nodes.values()) if (n.subclass().equals(subclassId)) out.add(n);
        out.sort((a, b) -> Integer.compare(a.tier(), b.tier()));
        return out;
    }

    /**
     * Soma dos níveis que UM jogador consegue comprar sem contar a Maestria: os ramos da classe (de cada escolha
     * "um ou outro" só conta um lado) mais a fileira de UMA especialização.
     */
    public int totalRanks() {
        int n = 0, spec = 0;
        Map<String, Integer> perSub = new LinkedHashMap<>();
        java.util.Set<String> counted = new java.util.HashSet<>();
        for (Node node : nodes.values()) {
            if (node.kind() == Kind.MASTERY) continue;
            if (node.isSpec()) {
                perSub.merge(node.subclass(), node.max(), Integer::sum);
                continue;
            }
            boolean rival = false;
            for (String x : node.exclusive()) if (counted.contains(x)) rival = true;
            if (rival) continue;
            counted.add(node.id());
            n += node.max();
        }
        for (int v : perSub.values()) spec = Math.max(spec, v);
        return n + spec;
    }

    /** Níveis da Maestria (o destino dos pontos que sobram). */
    public int masteryRanks() {
        int n = 0;
        for (Node node : nodes.values()) if (node.kind() == Kind.MASTERY) n += node.max();
        return n;
    }

    // =====================================================================================
    //  Leitura do JSON
    // =====================================================================================

    public static TalentTree parse(String classId, JsonObject json) {
        List<Branch> branches = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "branches", new JsonArray())) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            branches.add(new Branch(GsonHelper.getAsString(o, "id", "branch" + branches.size()), color(o, 0xB98CFF)));
        }
        if (branches.isEmpty()) branches.add(new Branch("main", 0xB98CFF));

        Map<String, Node> nodes = new LinkedHashMap<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "nodes", new JsonArray())) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String id = GsonHelper.getAsString(o, "id", "");
            if (id.isEmpty() || nodes.containsKey(id)) continue;
            Map<String, Integer> req = new LinkedHashMap<>();
            if (o.has("requires") && o.get("requires").isJsonObject()) {
                for (Map.Entry<String, JsonElement> r : o.getAsJsonObject("requires").entrySet()) {
                    if (r.getValue().isJsonPrimitive()) req.put(r.getKey(), Math.max(1, r.getValue().getAsInt()));
                }
            }
            List<String> exclusive = new ArrayList<>();
            for (JsonElement x : GsonHelper.getAsJsonArray(o, "exclusive", new JsonArray())) {
                if (x.isJsonPrimitive() && !x.getAsString().equals(id)) exclusive.add(x.getAsString());
            }
            nodes.put(id, new Node(id,
                    Math.max(0, Math.min(branches.size() - 1, GsonHelper.getAsInt(o, "branch", 0))),
                    Math.max(0, Math.min(15, GsonHelper.getAsInt(o, "tier", 0))),
                    Math.max(1, Math.min(100, GsonHelper.getAsInt(o, "max", 1))),
                    Math.max(1, GsonHelper.getAsInt(o, "level", 1)),
                    req, GsonHelper.getAsString(o, "icon", ""), effects(o),
                    Kind.byName(GsonHelper.getAsString(o, "kind", "small")),
                    Math.max(0, GsonHelper.getAsInt(o, "branch_points", 0)),
                    List.copyOf(exclusive), GsonHelper.getAsString(o, "subclass", ""),
                    GsonHelper.getAsDouble(o, "curve", 0.0)));
        }

        List<Subclass> subs = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "subclasses", new JsonArray())) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String id = GsonHelper.getAsString(o, "id", "");
            if (id.isEmpty()) continue;
            subs.add(new Subclass(id, color(o, 0xFFD86B), GsonHelper.getAsString(o, "icon", ""), effects(o)));
        }
        return new TalentTree(classId, branches, nodes, subs, GsonHelper.getAsInt(json, "layout", 1));
    }

    private static int color(JsonObject o, int def) {
        try {
            return o.has("color") ? Integer.parseInt(o.get("color").getAsString().replace("#", ""), 16) & 0xFFFFFF : def;
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private static List<Effect> effects(JsonObject owner) {
        List<Effect> out = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(owner, "effects", new JsonArray())) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            double amount = GsonHelper.getAsDouble(o, "amount", 0.0);
            if (amount == 0.0 || Double.isNaN(amount)) continue;
            if (o.has("attribute")) {
                ResourceLocation attr = ResourceLocation.tryParse(GsonHelper.getAsString(o, "attribute", ""));
                if (attr == null) continue;
                out.add(new Effect(attr, "", amount, op(GsonHelper.getAsString(o, "op", "add_value"))));
            } else {
                String stat = GsonHelper.getAsString(o, "stat", "").toLowerCase(Locale.ROOT);
                if (!stat.isEmpty()) out.add(new Effect(null, stat, amount, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        return List.copyOf(out);
    }

    private static AttributeModifier.Operation op(String s) {
        return switch (s) {
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }
}
