package com.renanpontes.isekaimahou.feature.ascension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.common.Tags;

/**
 * Tributos da Ascensão (data-driven): {@code data/<ns>/ascension/circle_<n>.json}.
 * <pre>
 * { "circle": 3, "duration_seconds": 70,            (duração é opcional: padrão 40s + 10s por Círculo)
 *   "tribute": [
 *     {"type": "item", "item": "isekai_mahou:essence_arcane", "count": 4},
 *     {"type": "tag", "tag": "c:gems/diamond", "count": 2},
 *     {"type": "elemental_essences", "count": 2, "min_types": 2, "max_types": 3, "arcane_substitute": true},
 *     {"type": "any_guardian_core", "count": 1},
 *     {"type": "boss_trophy", "count": 1, "items": ["isekai_mahou:king_slime_crown"],
 *      "bosses": ["swamp_hydra", "wyvern_king"],
 *      "fallback": [{"type": "any_guardian_core", "count": 1}]} ] }
 * </pre>
 * "boss_trophy" (v0.9): N itens de chefe, quaisquer dos candidatos — os {@code items} listados (ids desconhecidos são
 * ignorados) + todo item {@code isekai_mahou:<chefe>_*} de cada id em {@code bosses} (menos ovos e armas/ferramentas).
 * Assim um módulo de chefe que ainda não existe (ou mudou o nome do drop) não quebra o tributo: sem nenhum candidato
 * registrado, vale a lista {@code fallback} no lugar.
 * <p>O item "Tributo do Círculo N" ({@code ascension_tribute_<n>}) dispensa todo o resto: com 1 deles, o tributo
 * daquele Círculo está completo.
 * "elemental_essences": N essências de cada elemento base DESPERTADO (entre min e max tipos; se o jogador
 * tiver menos elementos que o mínimo, completa com essências de outros elementos base — ou, com
 * {@code arcane_substitute}, com N Essências Arcanas por elemento que falta).
 *
 * O servidor guarda o texto de cada arquivo e o envia ao cliente ({@link AscensionNetwork}), que monta a mesma
 * tabela para o rastreador de objetivos mostrar o que falta.
 */
public final class AscensionTributes extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<Integer, Tribute> BY_CIRCLE = Map.of();
    /** Texto JSON de cada tributo (para sincronizar com os clientes). */
    private static volatile Map<Integer, String> RAW = Map.of();
    /** Tabela recebida do servidor (só no cliente). */
    private static volatile Map<Integer, Tribute> SYNCED = Map.of();

    public AscensionTributes() {
        super(GSON, "ascension");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Integer, Tribute> map = new HashMap<>();
        Map<Integer, String> raw = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                Tribute t = parse(GsonHelper.convertToJsonObject(json, "tribute"));
                map.put(t.circle(), t);
                raw.put(t.circle(), json.toString());
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.error("Tributo de Ascensão inválido em {}: {}", id, e.getMessage());
            }
        });
        BY_CIRCLE = Map.copyOf(map);
        RAW = Map.copyOf(raw);
        IsekaiMahou.LOGGER.debug("Tributos de Ascensão carregados: {}", map.keySet());
    }

    /** Tributo para chegar ao Círculo informado (2..10), lado servidor. Sem arquivo: 2 Essências Arcanas por Círculo. */
    public static Tribute get(int targetCircle) {
        Tribute t = BY_CIRCLE.get(targetCircle);
        return t != null ? t : fallback(targetCircle);
    }

    /** Tributo visto pelo cliente (o que o servidor enviou); null se ainda não chegou. */
    public static Tribute synced(int targetCircle) {
        return SYNCED.get(targetCircle);
    }

    private static Tribute fallback(int targetCircle) {
        return new Tribute(targetCircle, -1, List.of(new ItemReq(CoreRegistries.ESSENCES.get(Element.ARCANE).get(), 2 * (targetCircle - 1))));
    }

    /** Textos JSON atuais (servidor), para o pacote de sincronização. */
    static Map<Integer, String> rawForSync() {
        return RAW;
    }

    /** Cliente: recebe os textos do servidor e monta a tabela (arquivos inválidos são ignorados). */
    static void acceptSync(Map<Integer, String> raw) {
        Map<Integer, Tribute> map = new HashMap<>();
        raw.forEach((circle, text) -> {
            try {
                Tribute t = parse(JsonParser.parseString(text).getAsJsonObject());
                map.put(t.circle(), t);
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.warn("Tributo de Ascensão do servidor ignorado (Círculo {}): {}", circle, e.getMessage());
            }
        });
        SYNCED = Map.copyOf(map);
    }

    // =====================================================================================
    //  Modelo
    // =====================================================================================
    public record Tribute(int circle, int durationSeconds, List<Req> requirements) {
        /** Duração da Tribulação em ticks. */
        public int durationTicks() {
            int s = durationSeconds > 0 ? durationSeconds : 40 + 10 * circle;
            return s * 20;
        }
    }

    public sealed interface Req permits ItemReq, TagReq, ElementalReq, AnyCoreReq, TrophyReq {
        /** Ordem de resolução: requisitos específicos consomem antes dos genéricos. */
        int order();
    }

    public record ItemReq(Item item, int count) implements Req {
        public int order() { return 0; }
    }

    public record TagReq(TagKey<Item> tag, int count) implements Req {
        public int order() { return 1; }
    }

    public record ElementalReq(int countEach, int minTypes, int maxTypes, boolean arcaneSubstitute) implements Req {
        public int order() { return 2; }
    }

    public record AnyCoreReq(int count) implements Req {
        public int order() { return 3; }
    }

    /** N itens quaisquer da lista de troféus de chefe (já resolvida contra o registro de itens). */
    public record TrophyReq(List<Item> items, int count) implements Req {
        public int order() { return 1; }
    }

    private static Tribute parse(JsonObject o) {
        int circle = GsonHelper.getAsInt(o, "circle");
        if (circle < 2 || circle > Progression.MAX_CIRCLE) throw new IllegalArgumentException("circle fora de 2..10: " + circle);
        int duration = GsonHelper.getAsInt(o, "duration_seconds", -1);
        List<Req> reqs = new ArrayList<>();
        parseEntries(GsonHelper.getAsJsonArray(o, "tribute"), reqs, 0);
        reqs.sort((a, b) -> Integer.compare(a.order(), b.order()));
        return new Tribute(circle, duration, List.copyOf(reqs));
    }

    private static void parseEntries(JsonArray arr, List<Req> reqs, int depth) {
        if (depth > 3) throw new IllegalArgumentException("fallback aninhado demais");
        for (JsonElement el : arr) {
            JsonObject r = GsonHelper.convertToJsonObject(el, "tribute entry");
            String type = GsonHelper.getAsString(r, "type", "item");
            int count = Math.max(1, GsonHelper.getAsInt(r, "count", 1));
            switch (type) {
                case "item" -> {
                    ResourceLocation id = ResourceLocation.parse(GsonHelper.getAsString(r, "item"));
                    Item item = BuiltInRegistries.ITEM.getOptional(id)
                            .orElseThrow(() -> new IllegalArgumentException("item desconhecido: " + id));
                    if (item == Items.AIR) throw new IllegalArgumentException("item vazio");
                    reqs.add(new ItemReq(item, count));
                }
                case "tag" -> reqs.add(new TagReq(TagKey.create(Registries.ITEM,
                        ResourceLocation.parse(GsonHelper.getAsString(r, "tag"))), count));
                case "elemental_essences" -> {
                    int min = Mth.clamp(GsonHelper.getAsInt(r, "min_types", 1), 1, 6);
                    int max = Mth.clamp(GsonHelper.getAsInt(r, "max_types", 6), min, 6);
                    reqs.add(new ElementalReq(count, min, max, GsonHelper.getAsBoolean(r, "arcane_substitute", false)));
                }
                case "any_guardian_core" -> reqs.add(new AnyCoreReq(count));
                case "boss_trophy" -> {
                    List<Item> items = trophyCandidates(r);
                    if (!items.isEmpty()) {
                        reqs.add(new TrophyReq(List.copyOf(items), count));
                    } else if (r.has("fallback")) {
                        parseEntries(GsonHelper.getAsJsonArray(r, "fallback"), reqs, depth + 1);
                    }
                    // Sem candidatos e sem reserva: o requisito some (melhor que travar a Ascensão).
                }
                default -> throw new IllegalArgumentException("tipo de tributo desconhecido: " + type);
            }
        }
    }

    /** Itens de chefe aceitos: os listados + {@code isekai_mahou:<chefe>_*} (sem ovos, armas e ferramentas). */
    private static List<Item> trophyCandidates(JsonObject r) {
        List<Item> out = new ArrayList<>();
        if (r.has("items")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(r, "items")) {
                ResourceLocation id = ResourceLocation.tryParse(GsonHelper.convertToString(e, "item"));
                if (id == null) continue;
                BuiltInRegistries.ITEM.getOptional(id).filter(i -> i != Items.AIR && !out.contains(i)).ifPresent(out::add);
            }
        }
        if (r.has("bosses")) {
            List<String> prefixes = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(r, "bosses")) prefixes.add(GsonHelper.convertToString(e, "boss") + "_");
            List<ResourceLocation> ids = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                if (!id.getNamespace().equals(IsekaiMahou.MODID)) continue;
                String path = id.getPath();
                if (path.endsWith("_spawn_egg")) continue;
                for (String pre : prefixes) {
                    if (path.startsWith(pre)) {
                        ids.add(id);
                        break;
                    }
                }
            }
            ids.sort((a, b) -> a.getPath().compareTo(b.getPath()));
            for (ResourceLocation id : ids) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == Items.AIR || out.contains(item) || item instanceof SpawnEggItem) continue;
                if (new ItemStack(item).isDamageableItem()) continue;
                out.add(item);
            }
        }
        return out;
    }

    // =====================================================================================
    //  Conferência do tributo
    // =====================================================================================
    /**
     * Uma linha do relatório: texto completo para o chat ({@code text}: ✔/✘ + item + "(n/m)"), o item sozinho
     * ({@code label}, para o rastreador), se está cumprida e o progresso.
     */
    public record Line(Component text, Component label, boolean ok, int have, int need) {}

    /** Resultado: linhas para o chat e quanto de cada item será consumido. */
    public record Check(List<Line> lines, Map<Item, Integer> consume, boolean complete) {
        /** Quantas linhas já estão cumpridas. */
        public int satisfied() {
            int n = 0;
            for (Line l : lines) if (l.ok()) n++;
            return n;
        }
    }

    /** Confere o tributo contra os itens dos pedestais (ou, no rastreador, do inventário + pedestais). */
    public static Check check(Tribute tribute, List<ItemStack> pedestalItems, MagicData data) {
        Map<Item, Integer> avail = new LinkedHashMap<>();
        for (ItemStack s : pedestalItems) if (!s.isEmpty()) avail.merge(s.getItem(), s.getCount(), Integer::sum);
        Map<Item, Integer> take = new HashMap<>();
        List<Line> lines = new ArrayList<>();
        boolean complete = true;

        // "Tributo do Círculo N" pronto: vale pelo tributo inteiro (só o do Círculo certo).
        Item seal = AscensionRegistry.tributeItem(tribute.circle());
        if (seal != null && avail.getOrDefault(seal, 0) >= 1) {
            take.put(seal, 1);
            lines.add(line(Component.translatable("message.isekai_mahou.ascension.req.seal", seal.getDescription()), 1, 1, true));
            return new Check(lines, take, true);
        }

        for (Req req : tribute.requirements()) {
            Line line = switch (req) {
                case ItemReq r -> checkItem(r, avail, take);
                case TagReq r -> checkTag(r, avail, take);
                case ElementalReq r -> checkElemental(r, avail, take, data);
                case AnyCoreReq r -> checkAnyCore(r, avail, take);
                case TrophyReq r -> checkTrophy(r, avail, take);
            };
            lines.add(line);
            complete &= line.ok();
        }
        return new Check(lines, take, complete);
    }

    private static int takeFrom(Map<Item, Integer> avail, Map<Item, Integer> take, Item item, int wanted) {
        int have = avail.getOrDefault(item, 0);
        int got = Math.min(have, wanted);
        if (got > 0) {
            avail.put(item, have - got);
            take.merge(item, got, Integer::sum);
        }
        return got;
    }

    private static Line checkItem(ItemReq r, Map<Item, Integer> avail, Map<Item, Integer> take) {
        int have = avail.getOrDefault(r.item(), 0);
        boolean ok = have >= r.count();
        if (ok) takeFrom(avail, take, r.item(), r.count());
        return line(Component.translatable("message.isekai_mahou.ascension.count", r.count(), r.item().getDescription()),
                Math.min(have, r.count()), r.count(), ok);
    }

    private static Line checkTag(TagReq r, Map<Item, Integer> avail, Map<Item, Integer> take) {
        int have = 0;
        for (Map.Entry<Item, Integer> e : avail.entrySet()) {
            if (e.getValue() > 0 && BuiltInRegistries.ITEM.wrapAsHolder(e.getKey()).is(r.tag())) have += e.getValue();
        }
        boolean ok = have >= r.count();
        if (ok) {
            int left = r.count();
            for (Item item : new ArrayList<>(avail.keySet())) {
                if (left <= 0) break;
                if (BuiltInRegistries.ITEM.wrapAsHolder(item).is(r.tag())) left -= takeFrom(avail, take, item, left);
            }
        }
        Component name = Component.translatableWithFallback(Tags.getTagTranslationKey(r.tag()), "#" + r.tag().location());
        return line(Component.translatable("message.isekai_mahou.ascension.count", r.count(), name), Math.min(have, r.count()), r.count(), ok);
    }

    /**
     * Essências elementais: primeiro as dos elementos despertados (obrigatórias), depois as de outros elementos
     * base que estejam nos pedestais e, se o tributo permitir, Essências Arcanas no lugar de cada elemento que falta.
     */
    private static Line checkElemental(ElementalReq r, Map<Item, Integer> avail, Map<Item, Integer> take, MagicData data) {
        List<Element> awakened = new ArrayList<>();
        for (Element e : Element.BASE) if (data.isAwakened(e)) awakened.add(e);
        int required = Mth.clamp(awakened.size(), r.minTypes(), r.maxTypes());
        int awakenedNeeded = Math.min(awakened.size(), required);

        List<Element> chosen = new ArrayList<>();
        List<Element> missingAwakened = new ArrayList<>();
        for (Element e : awakened) {
            if (chosen.size() >= required) break;
            if (avail.getOrDefault(essence(e), 0) >= r.countEach()) chosen.add(e);
            else missingAwakened.add(e);
        }
        boolean awakenedOk = chosen.size() >= awakenedNeeded;
        int arcaneSlots = 0;
        if (awakenedOk) {
            for (Element e : Element.BASE) {
                if (chosen.size() >= required) break;
                if (!chosen.contains(e) && avail.getOrDefault(essence(e), 0) >= r.countEach()) chosen.add(e);
            }
            if (r.arcaneSubstitute() && chosen.size() < required) {
                int arcaneHave = avail.getOrDefault(essence(Element.ARCANE), 0);
                arcaneSlots = Math.min(required - chosen.size(), arcaneHave / r.countEach());
            }
        }
        int filled = chosen.size() + arcaneSlots;
        boolean ok = awakenedOk && filled >= required;
        if (ok) {
            for (Element e : chosen) takeFrom(avail, take, essence(e), r.countEach());
            if (arcaneSlots > 0) takeFrom(avail, take, essence(Element.ARCANE), arcaneSlots * r.countEach());
        }

        MutableComponent body = Component.translatable(r.arcaneSubstitute() ? "message.isekai_mahou.ascension.req.elemental_sub"
                : "message.isekai_mahou.ascension.req.elemental", r.countEach(), required);
        MutableComponent text = body.copy();
        if (!ok && !missingAwakened.isEmpty() && !awakenedOk) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < missingAwakened.size(); i++) {
                if (i > 0) names.append(Component.literal(", "));
                names.append(missingAwakened.get(i).displayName());
            }
            text.append(Component.translatable("message.isekai_mahou.ascension.req.elemental_missing", names).withStyle(ChatFormatting.GRAY));
        }
        return line(text, body, Math.min(filled, required), required, ok);
    }

    private static Line checkAnyCore(AnyCoreReq r, Map<Item, Integer> avail, Map<Item, Integer> take) {
        int have = 0;
        for (Element e : Element.BASE) have += avail.getOrDefault(core(e), 0);
        boolean ok = have >= r.count();
        if (ok) {
            int left = r.count();
            for (Element e : Element.BASE) {
                if (left <= 0) break;
                left -= takeFrom(avail, take, core(e), left);
            }
        }
        return line(Component.translatable("message.isekai_mahou.ascension.req.any_core", r.count()), Math.min(have, r.count()), r.count(), ok);
    }

    private static Line checkTrophy(TrophyReq r, Map<Item, Integer> avail, Map<Item, Integer> take) {
        int have = 0;
        for (Item i : r.items()) have += avail.getOrDefault(i, 0);
        boolean ok = have >= r.count();
        if (ok) {
            int left = r.count();
            for (Item i : r.items()) {
                if (left <= 0) break;
                left -= takeFrom(avail, take, i, left);
            }
        }
        // "1× troféu de chefe: Coroa do Rei Slime / Chifre do Minotauro / ..."
        MutableComponent names = Component.empty();
        int shown = Math.min(3, r.items().size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) names.append(Component.literal(" / "));
            names.append(r.items().get(i).getDescription());
        }
        if (r.items().size() > shown) names.append(Component.literal(" / …"));
        return line(Component.translatable("message.isekai_mahou.ascension.req.trophy", r.count(), names),
                Math.min(have, r.count()), r.count(), ok);
    }

    private static Line line(Component body, int have, int need, boolean ok) {
        return line(body, body, have, need, ok);
    }

    private static Line line(Component body, Component label, int have, int need, boolean ok) {
        MutableComponent text = Component.literal(ok ? "✔ " : "✘ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
        text.append(body.copy().withStyle(ok ? ChatFormatting.GRAY : ChatFormatting.WHITE));
        text.append(Component.literal(" (" + have + "/" + need + ")").withStyle(ChatFormatting.DARK_GRAY));
        return new Line(text, label, ok, have, need);
    }

    public static Item essence(Element e) {
        return CoreRegistries.ESSENCES.get(e).get();
    }

    public static Item core(Element e) {
        return CoreRegistries.GUARDIAN_CORES.get(e).get();
    }
}
