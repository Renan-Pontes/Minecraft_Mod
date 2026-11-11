package com.renanpontes.isekaimahou.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellPreset;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;

import net.minecraft.resources.ResourceLocation;

/**
 * TODOS os dados mágicos de um jogador (salvos via Data Attachment, sincronizados com o cliente).
 *
 * Mutadores marcam {@code dirty} — o {@code CoreEvents} manda o pacote de sincronização no próximo tick.
 * No cliente existe uma cópia em {@code ClientMagic.data()} (mesma classe).
 *
 * <h3>v1.0</h3>
 * <ul>
 *   <li><b>9 loadouts fixos</b> ({@link #LOADOUTS}), cada um com Z/X/C/G — acabou a Roda Elemental e o loadout por
 *       elemento. O elemento de cada magia vem dos glifos ({@link SpellCost#primaryElement}).</li>
 *   <li><b>Carga de ultimate única</b> ({@link #charge()}).</li>
 *   <li><b>Presets</b>: até {@link #MAX_PRESETS} rascunhos nomeados ({@link #presets()}).</li>
 *   <li><b>Limite de Mana treinável</b> ({@link #manaLimit()}, {@link ResourceTraining}): vale para Mana/Vigor/Foco.</li>
 *   <li>Migração automática do save da v0.8: os slots "elemento:loadout:slot" viram os 9 loadouts em ordem
 *       (Roda × loadout, só os loadouts com alguma magia) e o que sobrar vira preset.</li>
 * </ul>
 * Os métodos antigos (Roda, loadout por elemento, carga por elemento) continuam como adaptadores {@code @Deprecated}.
 */
public class MagicData {
    public static final int MAX_LIBRARY = 60;
    /** v1.0: loadouts fixos (Alt/Mouse 5 + scroll, Alt + 1..9). */
    public static final int LOADOUTS = 9;
    /** v1.0: presets (rascunhos nomeados). */
    public static final int MAX_PRESETS = 30;

    // ---------- Codec (salvar no mundo / sincronizar) ----------
    private record ElementEntry(int xp, List<String> nodes) {
        static final Codec<ElementEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("xp", 0).forGetter(ElementEntry::xp),
                Codec.STRING.listOf().optionalFieldOf("nodes", List.of()).forGetter(ElementEntry::nodes)
        ).apply(i, ElementEntry::new));
    }

    /**
     * Campos extras (agrupados num sub-registro porque o codec principal já está no limite de 16 campos).
     * Novos campos opcionais entram AQUI, sempre com valor padrão (saves antigos continuam abrindo).
     */
    private record Extra(int xpBank, int activeLoadout, float ultCharge, List<SpellPreset> presets, int manaTrained,
                         int trainings, long fatigueUntil) {
        static final Extra DEFAULT = new Extra(0, 0, 0F, List.of(), 0, 0, 0L);
        static final Codec<Extra> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("xp_bank", 0).forGetter(Extra::xpBank),
                Codec.INT.optionalFieldOf("active_loadout", 0).forGetter(Extra::activeLoadout),
                Codec.FLOAT.optionalFieldOf("ult_charge", 0F).forGetter(Extra::ultCharge),
                SpellPreset.CODEC.listOf().optionalFieldOf("presets", List.of()).forGetter(Extra::presets),
                Codec.INT.optionalFieldOf("mana_trained", 0).forGetter(Extra::manaTrained),
                Codec.INT.optionalFieldOf("trainings", 0).forGetter(Extra::trainings),
                Codec.LONG.optionalFieldOf("fatigue_until", 0L).forGetter(Extra::fatigueUntil)
        ).apply(i, Extra::new));
    }

    /*
     * Os campos "wheel", "active_wheel", "active_loadouts" e "charge" (mapa por elemento) são da v0.8: continuam no
     * codec só para LER saves antigos (migração); a v1.0 grava vazio neles.
     */
    public static final Codec<MagicData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("mana", 300F).forGetter(d -> d.mana),
            Codec.INT.optionalFieldOf("level", 1).forGetter(d -> d.level),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(d -> d.xp),
            Codec.INT.optionalFieldOf("circle", 1).forGetter(d -> d.circle),
            Codec.STRING.optionalFieldOf("path", MagicPath.NONE.id).forGetter(d -> d.path.id),
            Codec.unboundedMap(Codec.STRING, ElementEntry.CODEC).optionalFieldOf("elements", Map.of()).forGetter(MagicData::encodeElements),
            ResourceLocation.CODEC.listOf().optionalFieldOf("glyphs", List.of()).forGetter(d -> new ArrayList<>(d.glyphs)),
            Codec.STRING.listOf().optionalFieldOf("wheel", List.of()).forGetter(d -> List.of()),
            Codec.INT.optionalFieldOf("active_wheel", 0).forGetter(d -> 0),
            Codec.unboundedMap(Codec.STRING, SpellSlot.CODEC).optionalFieldOf("slots", Map.of()).forGetter(MagicData::encodeSlots),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("active_loadouts", Map.of()).forGetter(d -> Map.of()),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("charge", Map.of()).forGetter(d -> Map.of()),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of()).forGetter(d -> d.counters),
            Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(d -> new ArrayList<>(d.flags)),
            SpellRecipe.CODEC.listOf().optionalFieldOf("library", List.of()).forGetter(d -> d.library),
            Extra.CODEC.optionalFieldOf("extra", Extra.DEFAULT).forGetter(d -> new Extra(d.xpBank, d.activeLoadout, d.ultCharge,
                    List.copyOf(d.presets), d.manaTrained, d.trainings, d.fatigueUntil))
    ).apply(i, MagicData::new));

    // ---------- Dados salvos ----------
    private float mana;
    private int level;
    private int xp;
    private int circle;
    /** XP guardado no limite do Círculo (aplicado depois da Ascensão). Máximo: {@link Progression#bankCap}. */
    private int xpBank;
    private MagicPath path;
    /** Elementos despertados -> progresso. O ARCANO está sempre presente. */
    private final Map<Element, ElementProgress> elements = new HashMap<>();
    private final Set<ResourceLocation> glyphs = new LinkedHashSet<>();
    /** v1.0: "loadout:slot" (loadout 0..8, slot 0..3) -> slot com as versões da magia. */
    private final Map<String, SpellSlot> slots = new HashMap<>();
    private int activeLoadout;
    /** v1.0: carga de ultimate ÚNICA (0..{@link Progression#MAX_CHARGE}). */
    private float ultCharge;
    /** Contadores genéricos de feitos/estatísticas (ex.: "kill.spell.fire", "kill.minecraft.zombie"). */
    private final Map<String, Integer> counters = new HashMap<>();
    /** Marcadores genéricos (ex.: "kit_given", "shrine.fire"). */
    private final Set<String> flags = new HashSet<>();
    /** Magias salvas (biblioteca do Grimório). */
    private final List<SpellRecipe> library = new ArrayList<>();
    /** v1.0: presets (rascunhos nomeados). */
    private final List<SpellPreset> presets = new ArrayList<>();
    /** v1.0: Limite de Mana conquistado com Treino (somado ao piso do nível; ver {@link ResourceTraining}). */
    private int manaTrained;
    /** v1.0: treinos feitos na vida toda (estatística). */
    private int trainings;
    /** v1.0: até quando (tempo de jogo) a regeneração está reduzida por ter esvaziado o recurso. */
    private long fatigueUntil;

    // ---------- Temporários (não salvos) ----------
    public double lastX, lastY, lastZ;
    public int stillTicks;
    public boolean meditating;
    public boolean dirty = true;
    public boolean manaDirty = true;
    public boolean capWarned;
    /** Já avisou que o banco de XP encheu (zera ao subir de Círculo). */
    public boolean bankFullWarned;
    /** Bônus de mana máximo vindo do atributo isekai_mahou:max_mana (equipamentos). Atualizado todo tick. */
    public int manaBonus;
    public final Map<String, Long> cooldowns = new HashMap<>();
    /** Conjuração em andamento (tecla segurada). channelSlot = -1 quando não está conjurando. */
    public int channelSlot = -1;
    public Element channelElement;
    public SpellRecipe channelRecipe;
    public String channelKey;
    public long channelStart;
    public int channelTicks;
    public boolean channelSustain;
    /** v1.0: último slot conjurado (para {@link #activeElement()}); -1 = nenhum. */
    public int lastCastSlot = -1;
    /** v1.0: o recurso chegou perto de 100% desde o último esvaziamento (o próximo esvaziamento conta como Treino). */
    public boolean trainingArmed;
    /** v1.0: o recurso acabou de ser esvaziado (o {@link ResourceTraining} trata no próximo tick do servidor). */
    public boolean emptiedPending;
    /** v1.0: o save veio da v0.8 e foi convertido: quantas magias viraram presets (aviso no próximo tick); -1 = nada. */
    public int migrationNotice = -1;

    public MagicData() {
        this(ResourceTraining.limit(1, 1, 0), 1, 0, 1, MagicPath.NONE.id, Map.of(), List.of(), List.of(), 0, Map.of(), Map.of(),
                Map.of(), Map.of(), List.of(), List.of(), Extra.DEFAULT);
        this.trainingArmed = true;
    }

    private MagicData(float mana, int level, int xp, int circle, String path, Map<String, ElementEntry> elements,
                      List<ResourceLocation> glyphs, List<String> wheel, int activeWheel, Map<String, SpellSlot> slots,
                      Map<String, Integer> activeLoadouts, Map<String, Float> charge, Map<String, Integer> counters,
                      List<String> flags, List<SpellRecipe> library, Extra extra) {
        this.circle = clamp(circle, 1, Progression.MAX_CIRCLE);
        // v1.0: o teto absoluto (Círculo 10 = 200) — o limite real (150/200) depende dos contadores, lidos depois
        this.level = clamp(level, 1, Progression.absoluteLevelCap(this.circle));
        this.xp = Math.max(0, xp);
        this.xpBank = clamp(extra.xpBank(), 0, Progression.bankCap(this.level));
        this.mana = Math.max(0F, mana);
        this.path = MagicPath.byId(path);
        elements.forEach((id, entry) -> {
            Element e = Element.byId(id);
            if (e != null) this.elements.put(e, new ElementProgress(entry.xp(), new HashSet<>(entry.nodes())));
        });
        this.elements.putIfAbsent(Element.ARCANE, new ElementProgress(0, new HashSet<>()));
        this.glyphs.addAll(glyphs);
        this.counters.putAll(counters);
        this.flags.addAll(flags);
        this.library.addAll(library);
        for (SpellPreset p : extra.presets()) if (this.presets.size() < MAX_PRESETS && p != null) this.presets.add(p);
        this.manaTrained = Math.max(0, extra.manaTrained());
        this.trainings = Math.max(0, extra.trainings());
        this.fatigueUntil = Math.max(0L, extra.fatigueUntil());
        this.activeLoadout = Math.floorMod(extra.activeLoadout(), LOADOUTS);
        this.ultCharge = Math.max(0F, Math.min(Progression.MAX_CHARGE, extra.ultCharge()));

        // Slots: formato v1.0 "loadout:slot"; formato v0.8 "elemento:loadout:slot" é migrado
        Map<String, SpellSlot> legacy = new HashMap<>();
        slots.forEach((key, slot) -> {
            if (slot == null || slot.isEmpty()) return;
            String[] parts = key.split(":");
            if (parts.length == 3) {
                legacy.put(key, slot);
            } else if (parts.length == 2) {
                int l = parseInt(parts[0]), s = parseInt(parts[1]);
                if (l >= 0 && l < LOADOUTS && s >= 0 && s < Progression.SLOTS) this.slots.put(slotKey(l, s), slot);
            }
        });
        // Carga antiga por elemento: fica a maior
        for (Float c : charge.values()) {
            if (c != null) this.ultCharge = Math.max(this.ultCharge, Math.min(Progression.MAX_CHARGE, c));
        }
        if (!legacy.isEmpty()) migrateLegacy(legacy, wheel, activeWheel, activeLoadouts);
    }

    /**
     * Migração do save da v0.8: copia os loadouts com alguma magia para os 9 loadouts, na ordem da Roda (Arcano,
     * elementos registrados, os outros elementos) × loadout; o que não couber vira preset (e, com os presets cheios,
     * vai para a biblioteca).
     */
    private void migrateLegacy(Map<String, SpellSlot> legacy, List<String> wheelIds, int activeWheel, Map<String, Integer> activeLoadouts) {
        // elemento -> loadout antigo -> slot -> SpellSlot
        Map<Element, TreeMap<Integer, Map<Integer, SpellSlot>>> old = new EnumMap<>(Element.class);
        legacy.forEach((key, slot) -> {
            String[] parts = key.split(":");
            Element e = Element.byId(parts[0]);
            int l = parseInt(parts[1]), s = parseInt(parts[2]);
            if (e == null || l < 0 || s < 0 || s >= Progression.SLOTS) return;
            old.computeIfAbsent(e, k -> new TreeMap<>()).computeIfAbsent(l, k -> new HashMap<>()).put(s, slot);
        });
        List<Element> order = new ArrayList<>();
        order.add(Element.ARCANE);
        for (String id : wheelIds) {
            Element e = Element.byId(id);
            if (e != null && !order.contains(e)) order.add(e);
        }
        for (Element e : Element.values()) if (!order.contains(e)) order.add(e);

        // Elemento/loadout que estavam ativos na v0.8
        List<Element> oldWheel = new ArrayList<>();
        oldWheel.add(Element.ARCANE);
        for (String id : wheelIds) {
            Element e = Element.byId(id);
            if (e != null && e != Element.ARCANE && !oldWheel.contains(e)) oldWheel.add(e);
        }
        Element activeEl = oldWheel.get(Math.floorMod(activeWheel, oldWheel.size()));
        int activeOld = activeLoadouts.getOrDefault(activeEl.id, 0);

        int next = 0;
        while (next < LOADOUTS && !isLoadoutEmpty(next)) next++;
        int moved = 0;
        for (Element e : order) {
            TreeMap<Integer, Map<Integer, SpellSlot>> loadouts = old.get(e);
            if (loadouts == null) continue;
            for (Map.Entry<Integer, Map<Integer, SpellSlot>> lo : loadouts.entrySet()) {
                if (next < LOADOUTS) {
                    int target = next;
                    lo.getValue().forEach((s, slot) -> this.slots.put(slotKey(target, s), slot));
                    if (e == activeEl && lo.getKey() == activeOld) this.activeLoadout = target;
                    next++;
                    while (next < LOADOUTS && !isLoadoutEmpty(next)) next++;
                    continue;
                }
                // Sem loadout livre: cada versão de cada slot vira um preset
                for (Map.Entry<Integer, SpellSlot> se : new TreeMap<>(lo.getValue()).entrySet()) {
                    for (Map.Entry<MoveTrigger, SpellRecipe> v : se.getValue().all().entrySet()) {
                        SpellRecipe r = v.getValue();
                        if (r == null || r.isEmpty()) continue;
                        String auto = e.id + " L" + (lo.getKey() + 1) + " " + "ZXCG".charAt(Math.min(3, se.getKey()))
                                + (v.getKey() == MoveTrigger.NORMAL ? "" : " (" + v.getKey().id + ")");
                        String name = uniquePresetName(r.name().isBlank() ? auto : r.name());
                        if (presets.size() < MAX_PRESETS) presets.add(new SpellPreset(name, r));
                        else if (library.size() < MAX_LIBRARY) library.add(r.name().isBlank() ? r.withName(name) : r);
                        moved++;
                    }
                }
            }
        }
        migrationNotice = moved;
        dirty = true;
    }

    private boolean isLoadoutEmpty(int loadout) {
        for (int s = 0; s < Progression.SLOTS; s++) {
            SpellSlot sl = slots.get(slotKey(loadout, s));
            if (sl != null && !sl.isEmpty()) return false;
        }
        return true;
    }

    private Map<String, ElementEntry> encodeElements() {
        Map<String, ElementEntry> map = new HashMap<>();
        elements.forEach((e, p) -> map.put(e.id, new ElementEntry(p.xp, new ArrayList<>(p.nodes))));
        return map;
    }

    /** Só os slots com alguma magia (o mapa interno pode ter slots vazios criados por {@link #slot}). */
    private Map<String, SpellSlot> encodeSlots() {
        Map<String, SpellSlot> map = new HashMap<>();
        slots.forEach((k, v) -> { if (v != null && !v.isEmpty()) map.put(k, v); });
        return map;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // =====================================================================================
    //  Mana / nível / Círculo / classe
    // =====================================================================================
    public float mana() { return mana; }
    public int level() { return level; }
    public int xp() { return xp; }
    public int circle() { return circle; }
    public MagicPath path() { return path; }
    /** Máximo do recurso: Limite de Mana ({@link #manaLimit()}) + bônus de equipamento. */
    public int maxMana() { return manaLimit() + manaBonus; }
    /** Limite de nível real (Círculo; no Círculo 10, 150 ou 200 depois do Dragão Primordial). */
    public int levelCap() { return Progression.levelCap(this); }
    public boolean atLevelCap() { return level >= levelCap(); }

    public void setMana(float v) {
        int max = maxMana();
        float n = Math.max(0, Math.min(max, v));
        // v1.0 — Treino: chegou perto do cheio = "armado"; esvaziou (por gasto) = treino/fadiga no próximo tick
        if (n >= max * ResourceTraining.ARM_FRACTION) trainingArmed = true;
        if (n == mana) return;
        if (n < mana && n <= ResourceTraining.emptyThreshold(max)) emptiedPending = true;
        mana = n;
        manaDirty = true;
    }
    public void addMana(float v) { setMana(mana + v); }
    public void fillMana() { setMana(maxMana()); }
    public boolean consumeMana(float amount) {
        if (mana < amount) return false;
        setMana(mana - amount);
        return true;
    }

    // ---------- v1.0: Limite de Mana treinável ----------
    /** Teto do Limite no nível atual (o máximo que o Treino consegue alcançar). */
    public int manaCeiling() { return ResourceTraining.ceiling(level, circle); }
    /** Piso do Limite (garantido pelos marcos de nível). */
    public int manaFloor() { return ResourceTraining.floor(level, circle); }
    /** Limite de Mana atual = piso + treino (até o teto). Sem o bônus de equipamento. */
    public int manaLimit() { return ResourceTraining.limit(level, circle, manaTrained); }
    /** Quanto do Limite veio de Treino (pode passar do que cabe agora; o {@link #manaLimit()} corta no teto). */
    public int manaTrained() { return manaTrained; }
    /** Treinos feitos na vida toda. */
    public int trainings() { return trainings; }
    /** Até quando (tempo de jogo) a regeneração está reduzida. */
    public long fatigueUntil() { return fatigueUntil; }
    public boolean fatigued(long gameTime) { return gameTime < fatigueUntil; }

    /** Soma Limite treinado (sem passar do teto atual). Retorna quanto entrou. Use {@link ResourceTraining}. */
    public int addTrainedRaw(int amount) {
        int room = Math.max(0, manaCeiling() - manaFloor() - manaTrained);
        int add = Math.max(0, Math.min(amount, room));
        if (add > 0) {
            manaTrained += add;
            dirty = true;
            manaDirty = true;
        }
        return add;
    }

    public void countTrainingRaw() { trainings++; dirty = true; }

    public void setFatigueUntil(long t) {
        if (t != fatigueUntil) { fatigueUntil = t; dirty = true; }
    }

    /**
     * Adiciona XP respeitando o limite do Círculo: o que passar do limite vai para o banco de XP (até
     * {@link #bankCap()}; o excesso além disso é perdido). Retorna quantos níveis subiu. Use {@link MagicApi#giveXp}.
     */
    int addXpRaw(int amount) {
        if (amount <= 0) return 0;
        if (atLevelCap()) {
            xp = 0;
            bankXpRaw(amount);
            return 0;
        }
        xp += amount;
        int gained = 0;
        while (!atLevelCap() && xp >= Progression.xpToNext(level)) {
            xp -= Progression.xpToNext(level);
            level++;
            gained++;
        }
        if (atLevelCap()) {
            int overflow = xp;
            xp = 0;
            bankXpRaw(overflow);
        }
        dirty = true;
        return gained;
    }

    /** XP guardado no limite do Círculo (vira níveis depois da Ascensão). */
    public int xpBank() { return xpBank; }
    /** Quanto XP cabe no banco no nível atual (o equivalente aos próximos 5 níveis). */
    public int bankCap() { return Progression.bankCap(level); }
    public boolean bankFull() { return xpBank >= bankCap(); }

    /** Guarda XP no banco (até o limite). Retorna quanto realmente entrou. */
    int bankXpRaw(int amount) {
        int added = Math.max(0, Math.min(amount, bankCap() - xpBank));
        if (added > 0) {
            xpBank += added;
            dirty = true;
        }
        return added;
    }

    /** Esvazia o banco e devolve o que havia nele (usado pela Ascensão). */
    int takeBankRaw() {
        int bank = xpBank;
        if (bank != 0) {
            xpBank = 0;
            dirty = true;
        }
        return bank;
    }

    public void setLevel(int l) {
        level = clamp(l, 1, levelCap());
        xp = 0;
        xpBank = Math.min(xpBank, bankCap()); // o banco nunca passa do tamanho do nível atual
        dirty = true;
        manaDirty = true;
    }
    public void setCircle(int c) { circle = clamp(c, 1, Progression.MAX_CIRCLE); level = Math.min(level, levelCap()); dirty = true; manaDirty = true; }
    public void setPath(MagicPath p) { path = p; dirty = true; }

    // =====================================================================================
    //  Elementos e glifos
    // =====================================================================================
    public boolean isAwakened(Element e) { return elements.containsKey(e); }
    public ElementProgress element(Element e) { return elements.get(e); }
    public Map<Element, ElementProgress> elements() { return elements; }
    public int elementLevel(Element e) {
        ElementProgress p = elements.get(e);
        return p == null ? -1 : Progression.elementLevel(p.xp);
    }

    /** v1.0: elementos despertados em ordem fixa (Arcano primeiro, depois a ordem de {@link Element}). */
    public List<Element> awakenedElements() {
        List<Element> out = new ArrayList<>();
        for (Element e : Element.values()) if (elements.containsKey(e)) out.add(e);
        return out;
    }

    /** Desperta (sem eventos). Use {@link MagicApi#awakenElement}. Retorna true se era novo. */
    boolean awakenRaw(Element e) {
        if (elements.containsKey(e)) return false;
        elements.put(e, new ElementProgress(0, new HashSet<>()));
        dirty = true;
        return true;
    }

    void addElementXpRaw(Element e, int amount) {
        ElementProgress p = elements.get(e);
        if (p == null) return;
        p.xp += amount;
        dirty = true;
    }

    public boolean hasNode(Element e, String node) {
        ElementProgress p = elements.get(e);
        return p != null && p.nodes.contains(node);
    }

    public boolean addNode(Element e, String node) {
        ElementProgress p = elements.get(e);
        if (p == null || !p.nodes.add(node)) return false;
        dirty = true;
        return true;
    }

    public boolean knowsGlyph(ResourceLocation id) { return glyphs.contains(id); }
    public Set<ResourceLocation> glyphs() { return glyphs; }
    boolean learnGlyphRaw(ResourceLocation id) {
        boolean added = glyphs.add(id);
        if (added) dirty = true;
        return added;
    }

    // =====================================================================================
    //  v1.0 — 9 loadouts × Z/X/C/G
    // =====================================================================================
    /** Loadout ativo (0..8). */
    public int activeLoadout() { return activeLoadout; }

    public void setActiveLoadout(int i) {
        int n = Math.floorMod(i, LOADOUTS);
        if (n != activeLoadout) { activeLoadout = n; dirty = true; }
    }

    /** Quantos loadouts existem (sempre {@link #LOADOUTS}). */
    public int loadoutCount() { return LOADOUTS; }

    /** Chave de recarga/slot: "loadout:slot". */
    public static String slotKey(int loadout, int slot) {
        return loadout + ":" + slot;
    }

    /** Slot de um loadout para ESCREVER (nunca null; cria vazio). */
    public SpellSlot slot(int loadout, int slot) {
        return slots.computeIfAbsent(slotKey(Math.floorMod(loadout, LOADOUTS), slot), k -> new SpellSlot());
    }

    /** Slot de um loadout só para LER (nunca null; pode estar vazio; não cria nada). */
    public SpellSlot peekSlot(int loadout, int slot) {
        SpellSlot s = slots.get(slotKey(Math.floorMod(loadout, LOADOUTS), slot));
        return s == null ? new SpellSlot() : s;
    }

    /** Slot do loadout ativo (leitura). */
    public SpellSlot activeSlot(int slot) {
        return peekSlot(activeLoadout, slot);
    }

    /** O loadout não tem magia nenhuma. */
    public boolean loadoutEmpty(int loadout) {
        return isLoadoutEmpty(Math.floorMod(loadout, LOADOUTS));
    }

    /** Elemento da magia principal de um slot do loadout ativo (vem dos glifos; vazio = Arcano). */
    public Element slotElement(int slot) {
        return slotElement(activeLoadout, slot);
    }

    /** Elemento da magia principal de um slot (vem dos glifos; vazio = Arcano). */
    public Element slotElement(int loadout, int slot) {
        SpellRecipe r = peekSlot(loadout, slot).main();
        return r == null ? Element.ARCANE : SpellCost.primaryElement(r);
    }

    /** Grava (ou apaga, com receita vazia/null) uma versão de um slot. */
    public void setSlotRecipe(int loadout, int slot, MoveTrigger trigger, @Nullable SpellRecipe recipe) {
        if (slot < 0 || slot >= Progression.SLOTS) return;
        String key = slotKey(Math.floorMod(loadout, LOADOUTS), slot);
        SpellSlot s = slots.computeIfAbsent(key, k -> new SpellSlot());
        s.set(trigger == null ? MoveTrigger.NORMAL : trigger, recipe);
        if (s.isEmpty()) slots.remove(key);
        dirty = true;
    }

    /** Apaga os 4 slots de um loadout. */
    public void clearLoadout(int loadout) {
        int l = Math.floorMod(loadout, LOADOUTS);
        for (int s = 0; s < Progression.SLOTS; s++) slots.remove(slotKey(l, s));
        dirty = true;
    }

    public void markDirty() { dirty = true; }

    // =====================================================================================
    //  Adaptadores v0.8 (Roda Elemental e loadout por elemento) — @Deprecated
    // =====================================================================================

    /**
     * @deprecated v1.0 não tem Roda: devolve o elemento da magia do slot em uso — conjurando: o da conjuração; senão o
     * do último slot conjurado; senão o da primeira magia do loadout ativo (Z, X, C, G); loadout vazio = Arcano.
     */
    @Deprecated
    public Element activeElement() {
        if (channelSlot >= 0 && channelElement != null) return channelElement;
        if (lastCastSlot >= 0 && lastCastSlot < Progression.SLOTS && activeSlot(lastCastSlot).main() != null) {
            return slotElement(lastCastSlot);
        }
        for (int s = 0; s < Progression.SLOTS; s++) {
            if (activeSlot(s).main() != null) return slotElement(s);
        }
        return Element.ARCANE;
    }

    /** @deprecated v1.0 não tem Roda: devolve os elementos DESPERTADOS (Arcano primeiro). */
    @Deprecated
    public List<Element> wheel() { return awakenedElements(); }

    /** @deprecated v1.0 não tem Roda: devolve os elementos despertados sem o Arcano. */
    @Deprecated
    public List<Element> registeredElements() {
        List<Element> out = awakenedElements();
        out.remove(Element.ARCANE);
        return Collections.unmodifiableList(out);
    }

    /** @deprecated v1.0 não tem Roda: "capacidade" = todos os elementos (nunca enche). */
    @Deprecated
    public int wheelCapacity() { return Element.values().length - 1; }

    /** @deprecated v1.0 não tem Roda: não guarda nada; true se o elemento está despertado (e não é o Arcano). */
    @Deprecated
    public boolean registerInWheel(Element e) { return e != Element.ARCANE && isAwakened(e); }

    /** @deprecated v1.0 não tem Roda: não faz nada. */
    @Deprecated
    public boolean unregisterFromWheel(Element e) { return false; }

    /** @deprecated v1.0 não tem Roda: índice de {@link #activeElement()} em {@link #wheel()}. */
    @Deprecated
    public int activeWheelIndex() { return Math.max(0, wheel().indexOf(activeElement())); }

    /** @deprecated v1.0 não tem Roda: não faz nada (o elemento vem dos glifos). */
    @Deprecated
    public void setActiveWheel(int i) {}

    /** @deprecated v1.0: loadouts não são por elemento — devolve {@link #activeLoadout()}. */
    @Deprecated
    public int activeLoadout(Element e) { return activeLoadout; }

    /** @deprecated v1.0: loadouts não são por elemento — usa {@link #setActiveLoadout(int)}. */
    @Deprecated
    public void setActiveLoadout(Element e, int i) { setActiveLoadout(i); }

    /** @deprecated v1.0: o elemento não faz parte da chave — devolve {@link #slotKey(int, int)}. */
    @Deprecated
    public static String slotKey(Element e, int loadout, int slot) { return slotKey(loadout, slot); }

    /** @deprecated v1.0: o elemento é ignorado — usa {@link #slot(int, int)}. */
    @Deprecated
    public SpellSlot slot(Element e, int loadout, int slot) { return slot(loadout, slot); }

    /** @deprecated v1.0: o elemento é ignorado — usa {@link #peekSlot(int, int)}. */
    @Deprecated
    public SpellSlot peekSlot(Element e, int loadout, int slot) { return peekSlot(loadout, slot); }

    // =====================================================================================
    //  Carga de ultimate (única na v1.0), contadores, marcadores, biblioteca
    // =====================================================================================
    public float charge() { return ultCharge; }
    public void setCharge(float v) {
        float n = Math.max(0, Math.min(Progression.MAX_CHARGE, v));
        if (n != ultCharge) { ultCharge = n; dirty = true; }
    }
    public void addCharge(float v) { setCharge(ultCharge + v); }
    public boolean chargeFull() { return ultCharge >= Progression.MAX_CHARGE; }

    /** @deprecated v1.0: carga única — devolve {@link #charge()}. */
    @Deprecated
    public float charge(Element e) { return charge(); }
    /** @deprecated v1.0: carga única — usa {@link #setCharge(float)}. */
    @Deprecated
    public void setCharge(Element e, float v) { setCharge(v); }
    /** @deprecated v1.0: carga única — usa {@link #addCharge(float)}. */
    @Deprecated
    public void addCharge(Element e, float v) { addCharge(v); }

    public int counter(String key) { return counters.getOrDefault(key, 0); }
    public Map<String, Integer> counters() { return counters; }
    int addCounterRaw(String key, int amount) {
        int v = counters.getOrDefault(key, 0) + amount;
        counters.put(key, v);
        dirty = true;
        return v;
    }

    public boolean hasFlag(String f) { return flags.contains(f); }
    public void setFlag(String f, boolean on) {
        if (on ? flags.add(f) : flags.remove(f)) dirty = true;
    }
    public Set<String> flags() { return flags; }

    public List<SpellRecipe> library() { return library; }
    public boolean addToLibrary(SpellRecipe r) {
        if (library.size() >= MAX_LIBRARY) return false;
        library.add(r);
        dirty = true;
        return true;
    }
    public void removeFromLibrary(int index) {
        if (index >= 0 && index < library.size()) { library.remove(index); dirty = true; }
    }

    // =====================================================================================
    //  v1.0 — Presets (rascunhos nomeados). Use MagicApi.savePreset/... no servidor (mensagens e validação).
    // =====================================================================================
    /** Presets em ordem (somente leitura). */
    public List<SpellPreset> presets() { return Collections.unmodifiableList(presets); }

    @Nullable
    public SpellPreset preset(int index) {
        return index >= 0 && index < presets.size() ? presets.get(index) : null;
    }

    /** Índice do preset com este nome (sem diferenciar maiúsculas), ou -1. */
    public int presetIndex(String name) {
        String n = SpellPreset.cleanName(name).toLowerCase(Locale.ROOT);
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).name().toLowerCase(Locale.ROOT).equals(n)) return i;
        }
        return -1;
    }

    /**
     * Salva um preset: se já existe um com o mesmo nome, SUBSTITUI; senão adiciona no fim (se couber).
     * @return índice do preset salvo, ou -1 (lista cheia ou receita vazia)
     */
    public int savePreset(String name, SpellRecipe recipe) {
        if (recipe == null || recipe.isEmpty()) return -1;
        SpellPreset p = new SpellPreset(name, recipe);
        if (p.name().isEmpty()) p = p.withName(uniquePresetName("#" + (presets.size() + 1)));
        int at = presetIndex(p.name());
        if (at >= 0) {
            presets.set(at, p);
        } else {
            if (presets.size() >= MAX_PRESETS) return -1;
            presets.add(p);
            at = presets.size() - 1;
        }
        dirty = true;
        return at;
    }

    public boolean deletePreset(int index) {
        if (index < 0 || index >= presets.size()) return false;
        presets.remove(index);
        dirty = true;
        return true;
    }

    /** Renomeia (o nome não pode repetir outro preset). */
    public boolean renamePreset(int index, String name) {
        String n = SpellPreset.cleanName(name);
        if (index < 0 || index >= presets.size() || n.isEmpty()) return false;
        int other = presetIndex(n);
        if (other >= 0 && other != index) return false;
        presets.set(index, presets.get(index).withName(n));
        dirty = true;
        return true;
    }

    /** Nome livre: "nome", "nome 2", "nome 3"... */
    public String uniquePresetName(String base) {
        String b = SpellPreset.cleanName(base);
        if (b.isEmpty()) b = "#" + (presets.size() + 1);
        if (presetIndex(b) < 0) return b;
        for (int i = 2; i < 1000; i++) {
            String suffix = " " + i;
            String c = SpellPreset.cleanName(b.substring(0, Math.min(b.length(), SpellPreset.MAX_NAME - suffix.length())) + suffix);
            if (presetIndex(c) < 0) return c;
        }
        return b;
    }

    /** Progresso de um elemento despertado. */
    public static final class ElementProgress {
        public int xp;
        public final Set<String> nodes;

        public ElementProgress(int xp, Set<String> nodes) {
            this.xp = xp;
            this.nodes = nodes;
        }

        public int level() { return Progression.elementLevel(xp); }
    }
}
