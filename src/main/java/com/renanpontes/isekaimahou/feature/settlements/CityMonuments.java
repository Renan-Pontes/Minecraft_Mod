package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * MONUMENTOS E TEMPLOS COM BÔNUS (onda 6 — "começar a construir monumentos com buffs, templos e etc...").
 * <p>
 * Estátua, arco, Santuário da Luz, os 7 templos elementais, Torre de Magia, Torre de Mana, as maravilhas do Deserto, o
 * Castelo e os monumentos novos ({@code build/MonumentBuildings}) são MONUMENTOS: cada um tem um bônus com nome
 * ({@link #DEFS}), 3 níveis (cada nível é outra obra de vários dias e fortalece o bônus) e só vale enquanto está ATIVO.
 * <ul>
 *   <li><b>Limite de monumentos ativos pelo degrau</b> ({@link #LIMIT}: Vila 1, Burgo 2, Cidade 3, Capital 5). O que
 *       passar do limite fica "adormecido" (o prédio continua de pé e com os efeitos comuns dele); o Senhor troca no
 *       painel (Geral → Bônus). Um monumento consagrado fica ativo pelo menos até o próximo amanhecer.</li>
 *   <li><b>Quem recebe</b>: quem a cidade protege (Senhor, conselho, cidadãos, grupo do Senhor), DENTRO da cidade.
 *       Bônus iguais somam até um teto ({@link Stat#cap}) — números modestos, nada que mude luta de chefe.</li>
 *   <li><b>Bênção</b>: agachar e tocar um monumento ativo com a mão vazia, uma vez por dia, dá a versão pela METADE
 *       do bônus dele por um dia, valendo fora da cidade. Dentro da cidade vale o maior dos dois (não soma).</li>
 * </ul>
 * Tudo passa pelos ganchos que o mod já tem: modificadores de atributo temporários (mana, armadura, vida, velocidade,
 * sorte), o evento de dano de magia, o evento de XP de elemento, os números do dia da cidade (impostos, segurança,
 * comida, felicidade, caravanas) e cura direta. Nenhum ícone novo no HUD.
 */
public final class CityMonuments {
    /** Nível máximo de um monumento (e do bônus). */
    public static final int MAX_LEVEL = 3;
    /** Monumentos ativos por degrau: Acampamento 0, Vila 1, Burgo 2, Cidade 3, Capital 5. */
    private static final int[] LIMIT = {0, 1, 2, 3, 5};
    /** A bênção levada vale esta fração do bônus e dura um dia do Minecraft. */
    public static final float BLESSING_SHARE = 0.5F;
    public static final int BLESSING_TICKS = 24000;
    private static final String HOME_REALM = "home_realm";

    // =====================================================================================
    //  O que um bônus pode dar
    // =====================================================================================
    /** Tipos de bônus. {@code cap} = teto da soma na cidade; {@code player} = vale para a pessoa (os outros são da cidade). */
    public enum Stat {
        XP("xp", 15, true),
        ELEMENT_XP("element_xp", 30, true),
        SPELL("spell", 12, true),
        MAX_MANA("max_mana", 15, true),
        MANA_REGEN("mana_regen", 25, true),
        HEAL("heal", 4, true),
        ARMOR("armor", 6, true),
        HEALTH("health", 10, true),
        SPEED("speed", 12, true),
        LUCK("luck", 3, true),
        SATIETY("satiety", 4, true),
        TAX("tax", 20, false),
        SECURITY("security", 20, false),
        FOOD("food", 40, false),
        APPROVAL("approval", 4, false),
        CARAVAN("caravan", 25, false);

        public final String id;
        public final int cap;
        public final boolean player;

        Stat(String id, int cap, boolean player) {
            this.id = id;
            this.cap = cap;
            this.player = player;
        }
    }

    /** Uma linha do bônus: tipo, elemento ("" = todos / não se aplica) e o valor nos níveis 1, 2 e 3. */
    public record Line(Stat stat, String element, int[] byLevel) {
        public int at(int level) {
            return byLevel[Math.max(1, Math.min(MAX_LEVEL, level)) - 1];
        }
    }

    /**
     * Um monumento.
     *
     * @param capped as melhorias param no nível 3 (torres de magia/mana têm outras funções que usam 5 níveis: nelas só o
     *               bônus para no 3)
     */
    public record Def(String key, boolean capped, List<Line> lines) {}

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    static {
        // ---- o que já existia
        def("statue", true, line(Stat.XP, 3, 5, 7));
        def("arch", true, line(Stat.SPEED, 4, 7, 10), line(Stat.CARAVAN, 3, 5, 8));
        def("temple", true, line(Stat.HEAL, 1, 2, 3));
        for (String el : new String[] {"fire", "water", "earth", "wind", "light", "shadow"}) {
            // o dano do elemento dentro da cidade continua no CityPerks (+3% por nível); aqui entra a maestria
            def("temple_" + el, true, line(Stat.SPELL, el, 3, 6, 9), line(Stat.ELEMENT_XP, el, 8, 14, 20));
        }
        def("temple_arcane", true, line(Stat.SPELL, 2, 4, 6), line(Stat.MAX_MANA, 2, 4, 6));
        def("magic_tower", false, line(Stat.MAX_MANA, 4, 7, 10));
        def("mana_spire", false, line(Stat.MANA_REGEN, 5, 10, 15));
        def("obelisk", true, line(Stat.HEAL, 1, 2, 2));
        def("pyramid", true, line(Stat.LUCK, 1, 1, 2), line(Stat.XP, 2, 3, 4));
        def("sphinx", true, line(Stat.ARMOR, 2, 3, 4));
        def("castle", true, line(Stat.HEALTH, 3, 5, 8));
        // ---- monumentos novos (todos os estilos)
        def("eras_obelisk", true, line(Stat.XP, 4, 6, 8), line(Stat.ELEMENT_XP, 4, 7, 10));
        def("guardian_colossus", true, line(Stat.ARMOR, 2, 3, 4), line(Stat.SECURITY, 6, 9, 12));
        def("great_lighthouse", true, line(Stat.CARAVAN, 8, 14, 20), line(Stat.TAX, 4, 7, 10), line(Stat.SPEED, 3, 5, 7));
        def("hanging_garden", true, line(Stat.FOOD, 10, 20, 30), line(Stat.APPROVAL, 1, 1, 2), line(Stat.SATIETY, 1, 2, 3));
        // ---- maravilhas dos estilos que não tinham (Nórdico, Oriental, Élfico, Sombrio, Anão)
        def("mead_hall", true, line(Stat.HEALTH, 3, 5, 8), line(Stat.APPROVAL, 1, 2, 2));
        def("grand_pagoda", true, line(Stat.MANA_REGEN, 6, 10, 15), line(Stat.ELEMENT_XP, 3, 5, 8));
        def("world_tree", true, line(Stat.HEAL, 1, 2, 3), line(Stat.FOOD, 10, 15, 20));
        def("grand_cathedral", true, line(Stat.MAX_MANA, 4, 7, 10), line(Stat.SECURITY, 4, 6, 8));
        def("great_forge", true, line(Stat.ARMOR, 2, 3, 4), line(Stat.TAX, 4, 7, 10));
    }

    private static void def(String key, boolean capped, Line... lines) {
        DEFS.put(key, new Def(key, capped, List.of(lines)));
    }

    private static Line line(Stat stat, int l1, int l2, int l3) {
        return new Line(stat, "", new int[] {l1, l2, l3});
    }

    private static Line line(Stat stat, String element, int l1, int l2, int l3) {
        return new Line(stat, element, new int[] {l1, l2, l3});
    }

    public static boolean isMonument(@Nullable String key) {
        return key != null && DEFS.containsKey(key);
    }

    /** Até que nível o prédio desta chave pode ser melhorado (monumentos: 3; o resto: o de sempre). */
    public static int maxLevel(@Nullable String key) {
        Def d = key == null ? null : DEFS.get(key);
        return d != null && d.capped() ? MAX_LEVEL : Settlement.MAX_BUILDING_LEVEL;
    }

    /** Quantos monumentos ativos o degrau deixa ter. */
    public static int limit(int cityLevel) {
        return LIMIT[Math.max(1, Math.min(LIMIT.length, cityLevel)) - 1];
    }

    public static int limit(Settlement s) {
        return s.own() ? limit(s.level) : 0;
    }

    // =====================================================================================
    //  Textos (servidor e tela usam os mesmos)
    // =====================================================================================
    /** Nome do bônus de um monumento ("Memória dos Heróis"). */
    public static MutableComponent bonusName(String key) {
        return SettlementsLang.tr("mon.name." + key);
    }

    private static MutableComponent lineText(Line l, int value) {
        if (l.stat() == Stat.SPELL || l.stat() == Stat.ELEMENT_XP) {
            Element el = l.element().isEmpty() ? null : Element.byId(l.element());
            return el == null ? SettlementsLang.tr("mon.stat." + l.stat().id + "_all", value)
                    : SettlementsLang.tr("mon.stat." + l.stat().id, value, el.displayName());
        }
        return SettlementsLang.tr("mon.stat." + l.stat().id, value);
    }

    /** As linhas do bônus num nível, juntas ("+3% de XP · +4% de velocidade"). {@code share} &lt; 1 = versão da bênção. */
    public static MutableComponent effects(String key, int level, float share, boolean playerOnly) {
        MutableComponent out = Component.empty();
        Def d = DEFS.get(key);
        if (d == null) return out;
        boolean any = false;
        for (Line l : d.lines()) {
            if (playerOnly && !l.stat().player) continue;
            if (any) out.append(Component.literal(" · "));
            out.append(lineText(l, scaled(l.at(level), share)));
            any = true;
        }
        return out;
    }

    /**
     * Trecho do bônus para o resumo de efeitos de um prédio ({@code CityViewExtra.fxLine}): "Memória dos Heróis: +3%
     * de XP". Roda também no cliente (só lê a tabela). null = a chave não é monumento.
     */
    @Nullable
    public static Component fx(String key, int level) {
        Def d = DEFS.get(key);
        if (d == null) return null;
        MutableComponent body = Component.empty();
        boolean any = false;
        for (Line l : d.lines()) {
            // o dano dos templos elementais já aparece no texto antigo do templo
            if (l.stat() == Stat.SPELL && key.startsWith("temple_")) continue;
            if (any) body.append(Component.literal(" · "));
            body.append(lineText(l, l.at(level)));
            any = true;
        }
        return any ? SettlementsLang.tr("mon.fx", bonusName(key), body) : null;
    }

    private static int scaled(int value, float share) {
        return share >= 1F ? value : Math.max(1, Math.round(value * share));
    }

    // =====================================================================================
    //  Soma dos bônus
    // =====================================================================================
    /** Bônus somados (da cidade ou da bênção levada). */
    public static final class Totals {
        final int[] v = new int[Stat.values().length];
        /** Maestria e dano por elemento base ("" = todos). */
        final Map<String, Integer> elementXp = new HashMap<>(), spell = new HashMap<>();

        void add(Def d, int level, float share) {
            for (Line l : d.lines()) {
                int val = scaled(l.at(level), share);
                if (l.stat() == Stat.ELEMENT_XP) elementXp.merge(l.element(), val, Integer::sum);
                else if (l.stat() == Stat.SPELL) spell.merge(l.element(), val, Integer::sum);
                else v[l.stat().ordinal()] += val;
            }
        }

        void cap() {
            for (Stat st : Stat.values()) v[st.ordinal()] = Math.min(st.cap, v[st.ordinal()]);
            elementXp.replaceAll((k, val) -> Math.min(Stat.ELEMENT_XP.cap, val));
            spell.replaceAll((k, val) -> Math.min(Stat.SPELL.cap, val));
        }

        /** O maior de cada bônus (cidade × bênção levada: não somam). */
        void max(Totals o) {
            for (int i = 0; i < v.length; i++) v[i] = Math.max(v[i], o.v[i]);
            o.elementXp.forEach((k, val) -> elementXp.merge(k, val, Math::max));
        }

        public int get(Stat st) {
            return v[st.ordinal()];
        }

        boolean any() {
            for (int x : v) if (x > 0) return true;
            return !elementXp.isEmpty() || !spell.isEmpty();
        }

        /** % somado para um elemento (fusões contam pelos dois pais), com o teto. */
        int forElement(Map<String, Integer> map, @Nullable Element el, int cap) {
            int t = map.getOrDefault("", 0);
            if (el != null) {
                for (Map.Entry<String, Integer> e : map.entrySet()) {
                    Element base = e.getKey().isEmpty() ? null : Element.byId(e.getKey());
                    if (base != null && el.matches(base)) t += e.getValue();
                }
            }
            return Math.min(cap, t);
        }
    }

    private static final Totals NONE = new Totals();

    /** Nível do BÔNUS de um monumento pronto (o do prédio, até 3). */
    public static int bonusLevel(Settlement s, int cell) {
        return Math.min(MAX_LEVEL, s.levelOf(cell));
    }

    /** Bônus somados dos monumentos ativos (e inteiros) da cidade, já com os tetos. */
    public static Totals totals(Settlement s) {
        if (!s.own() || s.buildings.isEmpty()) return NONE;
        ensure(s);
        CompoundTag act = s.monuments.getCompound("Active");
        if (act.isEmpty()) return NONE;
        Totals t = new Totals();
        for (String k : act.getAllKeys()) {
            int cell = parse(k);
            Def d = DEFS.get(s.buildings.get(cell));
            if (d == null || s.damaged.contains(cell)) continue;
            t.add(d, bonusLevel(s, cell), 1F);
        }
        t.cap();
        return t;
    }

    /** Um bônus DA CIDADE (impostos, segurança, comida, felicidade, caravanas) — para as contas do dia. */
    public static int cityStat(Settlement s, Stat st) {
        return totals(s).get(st);
    }

    // =====================================================================================
    //  Ativos / adormecidos
    // =====================================================================================
    private static int parse(String cell) {
        try {
            return Integer.parseInt(cell);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    /** Lotes com monumento pronto, na ordem da cidade. */
    public static List<Integer> cells(Settlement s) {
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<Integer, String> e : s.buildings.entrySet()) if (DEFS.containsKey(e.getValue())) out.add(e.getKey());
        return out;
    }

    public static boolean isActive(Settlement s, int cell) {
        return DEFS.containsKey(s.buildings.get(cell)) && s.monuments.getCompound("Active").contains(Integer.toString(cell));
    }

    /** Algum monumento desta chave está ativo e inteiro? (aura do Obelisco do Sol, templos...) */
    public static boolean active(Settlement s, String key) {
        if (!s.own()) return false;
        ensure(s);
        for (Map.Entry<Integer, String> e : s.buildings.entrySet()) {
            if (e.getValue().equals(key) && isActive(s, e.getKey()) && !s.damaged.contains(e.getKey())) return true;
        }
        return false;
    }

    public static int activeCount(Settlement s) {
        int n = 0;
        for (String k : s.monuments.getCompound("Active").getAllKeys()) if (DEFS.containsKey(s.buildings.get(parse(k)))) n++;
        return n;
    }

    /**
     * Arruma a lista de ativos: tira lotes que não têm mais monumento e, na primeira vez (mundo antigo), consagra
     * sozinha os monumentos que já existem, até o limite do degrau.
     */
    public static void ensure(Settlement s) {
        if (!s.own()) return;
        CompoundTag act = s.monuments.getCompound("Active");
        for (String k : new ArrayList<>(act.getAllKeys())) {
            if (!DEFS.containsKey(s.buildings.get(parse(k)))) act.remove(k);
        }
        if (!act.isEmpty() || s.monuments.contains("Active")) s.monuments.put("Active", act);
        if (s.monuments.getBoolean("Init")) return;
        if (cells(s).isEmpty()) return;   // nada a consagrar ainda: a primeira vez fica para quando houver
        s.monuments.putBoolean("Init", true);
        fill(s, 0L);
    }

    /** Consagra monumentos adormecidos enquanto houver vaga (os de nível mais alto primeiro). Devolve quantos. */
    private static int fill(Settlement s, long since) {
        CompoundTag act = s.monuments.getCompound("Active");
        List<Integer> free = new ArrayList<>();
        for (int c : cells(s)) if (!act.contains(Integer.toString(c)) && !s.damaged.contains(c)) free.add(c);
        free.sort((a, b) -> Integer.compare(s.levelOf(b), s.levelOf(a)));
        int n = 0;
        for (int c : free) {
            if (activeCount(s) >= limit(s)) break;
            act.putLong(Integer.toString(c), since);
            s.monuments.put("Active", act);
            n++;
        }
        return n;
    }

    /** Botão do painel: consagra ou adormece o monumento do lote. */
    public static void toggle(ServerPlayer sp, Settlement s, int cell) {
        if (!s.own() || !DEFS.containsKey(s.buildings.get(cell))) return;
        ensure(s);
        Blueprint bp = s.blueprintAt(cell);
        Component name = bp == null ? Component.literal(s.buildings.get(cell)) : bp.displayName();
        CompoundTag act = s.monuments.getCompound("Active");
        String k = Integer.toString(cell);
        long today = CityPace.today(sp.server);
        // depois da primeira troca à mão a cidade não preenche mais as vagas sozinha
        s.monuments.putBoolean("Manual", true);
        if (act.contains(k)) {
            if (act.getLong(k) >= today) {
                warn(sp, "mon.toggle.wait");
                return;
            }
            act.remove(k);
            s.monuments.put("Active", act);
            sp.displayClientMessage(SettlementsLang.tr("mon.toggle.off", name).withColor(s.style.color), true);
            sp.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.2F);
            return;
        }
        if (s.damaged.contains(cell)) {
            warn(sp, "mon.toggle.damaged");
            return;
        }
        if (activeCount(s) >= limit(s)) {
            warn(sp, "mon.toggle.full", limit(s), CityTier.name(s.level));
            return;
        }
        act.putLong(k, today);
        s.monuments.put("Active", act);
        sp.displayClientMessage(SettlementsLang.tr("mon.toggle.on", name, bonusName(s.buildings.get(cell))).withColor(s.style.color), true);
        sp.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    private static void warn(ServerPlayer sp, String key, Object... args) {
        sp.displayClientMessage(SettlementsLang.tr(key, args).withColor(0xE8735E), true);
        sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    // =====================================================================================
    //  Ganchos do ritmo da cidade
    // =====================================================================================
    /** Monumento novo ou melhorado: consagra se houver vaga; se não houver, avisa que ficou adormecido. */
    private static void onWorkDone(ServerLevel sl, Settlement s, Project p, boolean grand) {
        if (!s.own() || p.cell < 0 || !DEFS.containsKey(p.key) || !p.key.equals(s.buildings.get(p.cell))) return;
        if (!Project.KIND_BUILD.equals(p.kind) && !Project.KIND_UPGRADE.equals(p.kind)) return;
        MinecraftServer server = sl.getServer();
        ensure(s);
        s.monuments.putBoolean("Init", true);
        Blueprint bp = s.blueprintAt(p.cell);
        Component name = bp == null ? Component.literal(p.key) : bp.displayName();
        boolean was = isActive(s, p.cell);
        // monumento NOVO ocupa uma vaga livre; numa melhoria só se o Senhor nunca escolheu à mão (senão a escolha dele vale)
        boolean auto = Project.KIND_BUILD.equals(p.kind) || !s.monuments.getBoolean("Manual");
        if (!was && auto && activeCount(s) < limit(s)) {
            CompoundTag act = s.monuments.getCompound("Active");
            act.putLong(Integer.toString(p.cell), CityPace.today(server));
            s.monuments.put("Active", act);
        }
        boolean now = isActive(s, p.cell);
        if (now) {
            CityPace.diary(server, s, SettlementsLang.tr("mon.diary.active", name, bonusName(p.key),
                    effects(p.key, bonusLevel(s, p.cell), 1F, false)));
            glow(sl, s, p.cell, 30);
        }
        for (ServerPlayer g : CityEconomy.governors(server, s)) {
            if (!now && activeCount(s) >= limit(s)) {
                g.displayClientMessage(SettlementsLang.tr("mon.done.dormant", name, activeCount(s), CityTier.name(s.level))
                        .withColor(0xE8A440), false);
            }
            Hints.show(g, "city_monument");
        }
    }

    /** Amanhecer: cidade que nunca trocou monumentos à mão preenche sozinha as vagas livres (degrau novo, conserto). */
    private static void onNewDay(ServerLevel sl, Settlement s, long day, int elapsed) {
        if (!s.own() || cells(s).isEmpty()) return;
        ensure(s);
        if (!s.monuments.getBoolean("Manual")) fill(s, day);
        for (String k : s.monuments.getCompound("Active").getAllKeys()) glow(sl, s, parse(k), 12);
    }

    /** Poucas partículas no alto de cada monumento ativo a até 32 blocos do jogador, mandadas só para ele. */
    private static void sparkle(ServerPlayer sp, Settlement s) {
        ServerLevel sl = sp.serverLevel();
        for (String k : s.monuments.getCompound("Active").getAllKeys()) {
            int cell = parse(k);
            if (!DEFS.containsKey(s.buildings.get(cell)) || s.damaged.contains(cell)) continue;
            BlockPos c = CityLayout.cellCenter(s.heart, cell);
            if (sp.distanceToSqr(c.getX() + 0.5, sp.getY(), c.getZ() + 0.5) > 32 * 32) continue;
            Integer base = s.plotBase.get(cell);
            double y = (base != null ? base : s.heart.getY() - 1) + 6.0;
            sl.sendParticles(sp, ParticleTypes.END_ROD, false, c.getX() + 0.5, y, c.getZ() + 0.5, 3, 2.0, 2.5, 2.0, 0.005);
        }
    }

    /** Brilho discreto num monumento (inauguração, amanhecer) — só se o lote estiver carregado. */
    private static void glow(ServerLevel sl, Settlement s, int cell, int count) {
        if (cell == Integer.MIN_VALUE) return;
        BlockPos c = CityLayout.cellCenter(s.heart, cell);
        if (!sl.hasChunk(c.getX() >> 4, c.getZ() >> 4)) return;
        Integer base = s.plotBase.get(cell);
        double y = (base != null ? base : s.heart.getY() - 1) + 5.0;
        sl.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, y, c.getZ() + 0.5, count, 2.5, 3.0, 2.5, 0.01);
    }

    // =====================================================================================
    //  Jogadores: o bônus de verdade
    // =====================================================================================
    /** O que está valendo para um jogador agora. */
    private static final class Applied {
        /** O que vale agora (o maior entre cidade e bênção levada) e as duas partes (o dano de magia precisa delas). */
        Totals t = NONE, city = NONE, carried = NONE;
        String cityId = "";
        long noticeAt = -100000;
        int cycle;
        long xpSnap = -1;
        float xpFrac, elementFrac;
    }

    private static final Map<UUID, Applied> PLAYERS = new HashMap<>();
    /** Evita que o XP de maestria extra dispare o próprio bônus de novo. */
    private static boolean granting;

    private static final String[] MODS = {"max_mana", "mana_regen", "armor", "health", "speed", "luck"};

    /** A cada 2 s: confere onde cada jogador está e põe/tira os bônus. */
    private static void tickServer(MinecraftServer server) {
        if (server.getTickCount() % 40 != 13) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            try {
                update(sp);
            } catch (RuntimeException e) {
                // um jogador com dado estranho não pode parar os bônus dos outros
                IsekaiMahou.LOGGER.error("[settlements] bônus de monumento falhou para {}", sp.getGameProfile().getName(), e);
            }
        }
    }

    /** A cidade própria (de jogador) que protege este jogador e onde ele está agora; null = nenhuma. */
    @Nullable
    private static Settlement cityOf(ServerPlayer sp) {
        ServerLevel sl = sp.serverLevel();
        if (HOME_REALM.equals(sl.dimension().location().getPath())) return null;
        Places.Place place = SettlementService.cityAt(sl, sp.blockPosition());
        Settlement s = place == null ? null : SettlementService.city(sp.server, place.id());
        if (s == null || !s.own() || !SettlementService.isProtected(sp.server, s, sp)) return null;
        return s;
    }

    private static void update(ServerPlayer sp) {
        Applied a = PLAYERS.computeIfAbsent(sp.getUUID(), u -> new Applied());
        a.cycle++;
        Settlement s = sp.isSpectator() ? null : cityOf(sp);
        Totals city = s == null ? NONE : totals(s);
        Totals carried = carried(sp);
        boolean in = city.any();
        Totals t;
        if (!in) {
            t = carried;
        } else if (!carried.any()) {
            t = city;
        } else {
            // dentro da cidade com uma bênção levada: vale o maior de cada bônus (não somam)
            t = new Totals();
            t.max(city);
            t.max(carried);
        }
        if (in && !s.id.equals(a.cityId) && sp.serverLevel().getGameTime() - a.noticeAt > 6000) {
            // ao entrar: uma linha na barra de ação com os bônus que a cidade está dando (sem ícone, sem chat)
            a.noticeAt = sp.serverLevel().getGameTime();
            sp.displayClientMessage(SettlementsLang.tr("mon.enter", SettlementService.cityName(sp.server, s), activeNames(s))
                    .withColor(s.style.color), true);
        }
        a.cityId = s == null ? "" : s.id;
        // brilho discreto nos monumentos ATIVOS por perto (só para este jogador): dá para ver quais estão valendo
        if (in && a.cycle % 3 == 0) sparkle(sp, s);
        a.city = city;
        a.carried = carried;
        a.t = t;
        attributes(sp, t);
        if (!sp.isAlive()) return;
        // cura: % da vida máxima a cada 10 s
        int heal = t.get(Stat.HEAL);
        if (heal > 0 && a.cycle % 5 == 0 && sp.getHealth() < sp.getMaxHealth()) sp.heal(sp.getMaxHealth() * heal / 100F);
        // fartura: pontos de fome por minuto
        int food = t.get(Stat.SATIETY);
        if (food > 0 && a.cycle % 30 == 0 && sp.getFoodData().needsFood()) sp.getFoodData().eat(food, 0.3F);
        xp(sp, a, t.get(Stat.XP));
    }

    private static void attributes(ServerPlayer sp, Totals t) {
        MagicData d = MagicApi.get(sp);
        // o atributo de mana máxima é SOMA: o bônus em % vira pontos sobre a mana que o jogador tem sem ele
        AttributeInstance mana = sp.getAttribute(CoreRegistries.MAX_MANA);
        double mine = 0;
        if (mana != null) {
            AttributeModifier old = mana.getModifier(modId("max_mana"));
            if (old != null) mine = old.amount();
        }
        double base = Math.max(0, d.maxMana() - mine);
        mod(sp, CoreRegistries.MAX_MANA, "max_mana", Math.round(base * t.get(Stat.MAX_MANA) / 100.0), AttributeModifier.Operation.ADD_VALUE);
        mod(sp, CoreRegistries.MANA_REGEN, "mana_regen", t.get(Stat.MANA_REGEN) / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(sp, Attributes.ARMOR, "armor", t.get(Stat.ARMOR), AttributeModifier.Operation.ADD_VALUE);
        mod(sp, Attributes.MAX_HEALTH, "health", t.get(Stat.HEALTH) / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(sp, Attributes.MOVEMENT_SPEED, "speed", t.get(Stat.SPEED) / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(sp, Attributes.LUCK, "luck", t.get(Stat.LUCK), AttributeModifier.Operation.ADD_VALUE);
    }

    private static net.minecraft.resources.ResourceLocation modId(String name) {
        return IsekaiMahou.id("city_monument_" + name);
    }

    /** Põe, troca ou tira um modificador TEMPORÁRIO (não vai para o save: some sozinho se o servidor cair). */
    private static void mod(Player p, Holder<Attribute> attr, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        var id = modId(name);
        AttributeModifier old = inst.getModifier(id);
        if (amount <= 0) {
            if (old != null) inst.removeModifier(id);
            return;
        }
        if (old != null && Math.abs(old.amount() - amount) < 1.0E-6) return;
        inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
    }

    private static void clearAttributes(Player p) {
        Holder<Attribute>[] attrs = attrs();
        for (int i = 0; i < MODS.length; i++) {
            AttributeInstance inst = p.getAttribute(attrs[i]);
            if (inst != null) inst.removeModifier(modId(MODS[i]));
        }
    }

    @SuppressWarnings("unchecked")
    private static Holder<Attribute>[] attrs() {
        return new Holder[] {CoreRegistries.MAX_MANA, CoreRegistries.MANA_REGEN, Attributes.ARMOR, Attributes.MAX_HEALTH,
                Attributes.MOVEMENT_SPEED, Attributes.LUCK};
    }

    /**
     * Bônus de XP: o núcleo não avisa quando dá XP, então a conta é pelo que o total do jogador cresceu desde a
     * última olhada (2 s). O extra entra pelo {@code MagicApi.giveXp} normal; saltos de nível por comando não rendem.
     */
    private static void xp(ServerPlayer sp, Applied a, int pct) {
        if (pct <= 0) {
            a.xpSnap = -1;
            return;
        }
        MagicData d = MagicApi.get(sp);
        long total = xpTotal(d);
        if (a.xpSnap < 0) {
            // o bônus começou agora: conta só o que vier daqui para a frente
            a.xpSnap = total;
            a.xpFrac = 0F;
            return;
        }
        long gained = total - a.xpSnap;
        if (gained > 0 && gained <= 2L * Progression.xpToNext(d.level())) {
            a.xpFrac += gained * pct / 100F;
            int give = (int) a.xpFrac;
            if (give > 0) {
                a.xpFrac -= give;
                MagicApi.giveXp(sp, give, "city_monument");
            }
        }
        a.xpSnap = xpTotal(d);
    }

    private static long xpTotal(MagicData d) {
        long t = (long) d.xp() + d.xpBank();
        for (int l = 1; l < d.level(); l++) t += Progression.xpToNext(l);
        return t;
    }

    /** XP de maestria de elemento: o extra dos templos e monumentos. */
    private static void onElementXp(MagicProgressEvent.ElementXp e) {
        if (granting || e.amount <= 0) return;
        ServerPlayer sp = e.getPlayer();
        Applied a = PLAYERS.get(sp.getUUID());
        if (a == null) return;
        int pct = a.t.forElement(a.t.elementXp, e.element, Stat.ELEMENT_XP.cap);
        if (pct <= 0) return;
        a.elementFrac += e.amount * pct / 100F;
        int give = (int) a.elementFrac;
        if (give <= 0) return;
        a.elementFrac -= give;
        granting = true;
        try {
            MagicApi.giveElementXp(sp, e.element, give);
        } finally {
            granting = false;
        }
    }

    /**
     * Dano de magia da bênção LEVADA de um templo. Dentro da cidade quem dá o bônus dos templos é o CityPerks: aqui só
     * entra o que a bênção levada tiver A MAIS que a cidade (vale o maior, não soma).
     */
    private static void onSpellDamage(SpellDamageEvent.Pre e) {
        if (!(e.ctx().caster() instanceof ServerPlayer sp)) return;
        Applied a = PLAYERS.get(sp.getUUID());
        if (a == null || a.carried.spell.isEmpty()) return;
        int pct = a.carried.forElement(a.carried.spell, e.element(), Stat.SPELL.cap)
                - a.city.forElement(a.city.spell, e.element(), Stat.SPELL.cap);
        if (pct > 0) e.setAmount(e.getAmount() * (1F + pct / 100F));
    }

    private static MutableComponent activeNames(Settlement s) {
        MutableComponent out = Component.empty();
        int n = 0;
        for (int c : cells(s)) {
            if (!isActive(s, c) || s.damaged.contains(c)) continue;
            if (n++ > 0) out.append(Component.literal(" · "));
            out.append(bonusName(s.buildings.get(c)));
        }
        return out;
    }

    // =====================================================================================
    //  Bênção levada (uma por dia)
    // =====================================================================================
    private static CompoundTag blessTag(Player p, boolean create) {
        CompoundTag root = p.getPersistentData();
        CompoundTag keep = root.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag b = keep.getCompound("isekai_mahou_monument");
        if (create) {
            keep.put("isekai_mahou_monument", b);
            root.put(Player.PERSISTED_NBT_TAG, keep);
        }
        return b;
    }

    /** Bônus da bênção que o jogador carrega (vazio se não tem ou se acabou). */
    private static Totals carried(ServerPlayer sp) {
        CompoundTag b = blessTag(sp, false);
        if (b.isEmpty() || b.getLong("Until") <= sp.server.overworld().getGameTime()) return NONE;
        Def d = DEFS.get(b.getString("Key"));
        if (d == null) return NONE;
        Totals t = new Totals();
        int level = Math.max(1, b.getInt("Level"));
        for (Line l : d.lines()) {
            if (!l.stat().player) continue;
            int val = scaled(l.at(level), BLESSING_SHARE);
            if (l.stat() == Stat.ELEMENT_XP) t.elementXp.merge(l.element(), val, Integer::sum);
            else if (l.stat() == Stat.SPELL) t.spell.merge(l.element(), val, Integer::sum);
            else t.v[l.stat().ordinal()] += val;
        }
        return t;
    }

    /** O monumento ativo cujo lote contém a posição (-1 = nenhum; -2 = há um, mas adormecido ou danificado). */
    private static int monumentAt(Settlement s, BlockPos pos) {
        int found = -1;
        for (int c : cells(s)) {
            BlockPos center = CityLayout.cellCenter(s.heart, c);
            if (Math.abs(pos.getX() - center.getX()) > CityPlanning.PLOT_HALF || Math.abs(pos.getZ() - center.getZ()) > CityPlanning.PLOT_HALF) continue;
            Integer base = s.plotBase.get(c);
            int y0 = base != null ? base : s.heart.getY() - 1;
            if (pos.getY() < y0 - 3 || pos.getY() > y0 + 40) continue;
            if (isActive(s, c) && !s.damaged.contains(c)) return c;
            found = -2;
        }
        return found;
    }

    /** Agachar + tocar um monumento ativo com a mão vazia: recebe a bênção dele (uma por dia). */
    private static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty()) return;
        ServerLevel sl = sp.serverLevel();
        BlockPos pos = e.getPos();
        if (sl.getBlockState(pos).is(SettlementsContent.HEART.get())) return;
        Places.Place place = SettlementService.cityAt(sl, pos);
        Settlement s = place == null ? null : SettlementService.city(sp.server, place.id());
        if (s == null || !s.own() || cells(s).isEmpty()) return;
        // lote em obra (melhoria do próprio monumento): o clique é do canteiro (martelar)
        ensure(s);
        int cell = monumentAt(s, pos);
        if (cell == -1 || cell >= 0 && s.projectAt(cell) != null) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (cell == -2) {
            warn(sp, "mon.bless.dormant");
            return;
        }
        bless(sp, s, cell, false);
    }

    /** Dá a bênção do monumento do lote. {@code force} (comando de teste) ignora "uma por dia" e a proteção. */
    public static boolean bless(ServerPlayer sp, Settlement s, int cell, boolean force) {
        String key = s.buildings.get(cell);
        if (!DEFS.containsKey(key)) return false;
        if (!force && !SettlementService.isProtected(sp.server, s, sp)) {
            warn(sp, "mon.bless.member");
            return false;
        }
        long today = CityPace.today(sp.server);
        CompoundTag b = blessTag(sp, true);
        if (!force && b.contains("Day") && b.getLong("Day") == today) {
            warn(sp, "mon.bless.done");
            return false;
        }
        int level = bonusLevel(s, cell);
        b.putString("Key", key);
        b.putInt("Level", level);
        b.putLong("Day", today);
        b.putLong("Until", sp.server.overworld().getGameTime() + BLESSING_TICKS);
        ServerLevel sl = sp.serverLevel();
        sl.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 0.8F);
        sl.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4F, 1.6F);
        sl.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.0, sp.getZ(), 24, 0.5, 0.8, 0.5, 0.03);
        sp.displayClientMessage(SettlementsLang.tr("mon.bless.got", bonusName(key), effects(key, level, BLESSING_SHARE, true))
                .withColor(0xFFD86B), true);
        Hints.show(sp, "city_monument_bless");
        return true;
    }

    // =====================================================================================
    //  Tela (Geral → Bônus)
    // =====================================================================================
    static void view(CompoundTag t, ServerPlayer sp, Settlement s, HolderLookup.Provider reg) {
        if (!s.own()) return;
        ensure(s);
        CompoundTag m = new CompoundTag();
        m.putInt("Limit", limit(s));
        m.putInt("Active", activeCount(s));
        long today = CityPace.today(sp.server);
        CompoundTag act = s.monuments.getCompound("Active");
        ListTag list = new ListTag();
        for (int pass = 0; pass < 2; pass++) {
            for (int c : cells(s)) {
                boolean on = isActive(s, c);
                if (on != (pass == 0)) continue;   // ativos primeiro
                String key = s.buildings.get(c);
                Blueprint bp = s.blueprintAt(c);
                CompoundTag e = new CompoundTag();
                e.putInt("Cell", c);
                e.putString("Key", key);
                e.putString("Lang", bp == null ? "" : bp.langKey());
                e.putInt("Level", bonusLevel(s, c));
                e.putBoolean("On", on);
                e.putBoolean("Damaged", s.damaged.contains(c));
                // consagrado hoje: só adormece amanhã
                e.putBoolean("Locked", on && act.getLong(Integer.toString(c)) >= today);
                list.add(e);
            }
        }
        m.put("List", list);
        // soma do que a cidade está dando agora, uma linha por tipo de bônus
        Totals tot = totals(s);
        ListTag sum = new ListTag();
        for (Stat st : Stat.values()) {
            if (st == Stat.ELEMENT_XP || st == Stat.SPELL || tot.get(st) <= 0) continue;
            sum.add(StringTag.valueOf(SettlementView.json(SettlementsLang.tr("mon.stat." + st.id, tot.get(st)), reg)));
        }
        for (Stat st : new Stat[] {Stat.SPELL, Stat.ELEMENT_XP}) {
            Map<String, Integer> map = st == Stat.SPELL ? tot.spell : tot.elementXp;
            for (Map.Entry<String, Integer> e : map.entrySet()) {
                Element el = e.getKey().isEmpty() ? null : Element.byId(e.getKey());
                sum.add(StringTag.valueOf(SettlementView.json(el == null ? SettlementsLang.tr("mon.stat." + st.id + "_all", e.getValue())
                        : SettlementsLang.tr("mon.stat." + st.id, e.getValue(), el.displayName()), reg)));
            }
        }
        m.put("Sum", sum);
        // a bênção de quem está olhando
        CompoundTag b = blessTag(sp, false);
        long left = b.getLong("Until") - sp.server.overworld().getGameTime();
        if (left > 0 && DEFS.containsKey(b.getString("Key"))) {
            m.putString("Bless", SettlementView.json(SettlementsLang.tr("ui.mon.bless", bonusName(b.getString("Key")),
                    effects(b.getString("Key"), Math.max(1, b.getInt("Level")), BLESSING_SHARE, true), Math.max(1, left / 1200)), reg));
        }
        m.putBoolean("BlessReady", !(b.contains("Day") && b.getLong("Day") == today));
        t.put("Mon", m);
    }

    // =====================================================================================
    //  Registro
    // =====================================================================================
    public static void register() {
        // dias de obra: monumentos são obras de vários dias (as melhorias saem da fórmula do CityPace)
        CityPace.register("statue", 2F);
        CityPace.register("arch", 2F);
        CityPace.register("eras_obelisk", 5F);
        CityPace.register("hanging_garden", 5F);
        CityPace.register("guardian_colossus", 6F);
        CityPace.register("great_lighthouse", 6F);
        for (String w : new String[] {"mead_hall", "grand_pagoda", "world_tree", "grand_cathedral", "great_forge"}) CityPace.register(w, 7F);
        CityPace.onWorkDone(CityMonuments::onWorkDone);
        CityPace.onNewDay(CityMonuments::onNewDay);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tickServer(e.getServer()));
        NeoForge.EVENT_BUS.addListener(CityMonuments::onRightClick);
        NeoForge.EVENT_BUS.addListener(CityMonuments::onElementXp);
        NeoForge.EVENT_BUS.addListener(CityMonuments::onSpellDamage);
        commands();
    }

    /** Jogador saiu: tira os modificadores (são temporários, mas o jogador pode voltar na mesma sessão do servidor). */
    public static void forget(Player p) {
        PLAYERS.remove(p.getUUID());
        clearAttributes(p);
    }

    public static void clear() {
        PLAYERS.clear();
    }

    // =====================================================================================
    //  Comandos de teste
    // =====================================================================================
    private static void commands() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("city")
                .then(Commands.literal("monuments").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    Settlement s = near(sp);
                    if (s == null) return 0;
                    for (Component line : debug(sp, s)) c.getSource().sendSuccess(() -> line, false);
                    return 1;
                }).then(Commands.literal("toggle").then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 99))
                        .executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            Settlement s = near(sp);
                            List<Integer> cells = s == null ? List.of() : cells(s);
                            int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n");
                            if (n > cells.size()) return 0;
                            // comando de teste: sem a espera de um dia
                            CompoundTag act = s.monuments.getCompound("Active");
                            if (act.contains(Integer.toString(cells.get(n - 1)))) act.putLong(Integer.toString(cells.get(n - 1)), -1L);
                            toggle(sp, s, cells.get(n - 1));
                            SettlementService.refreshViewers(sp.server, s);
                            return 1;
                        }))))
                .then(Commands.literal("bless").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    Settlement s = near(sp);
                    if (s == null) return 0;
                    // o monumento do lote onde o jogador está; senão, o primeiro ativo
                    int cell = monumentAt(s, sp.blockPosition());
                    if (cell < 0) for (int k : cells(s)) if (cell < 0 && isActive(s, k)) cell = k;
                    return cell >= 0 && bless(sp, s, cell, true) ? 1 : 0;
                }).then(Commands.literal("clear").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    CompoundTag keep = sp.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
                    keep.remove("isekai_mahou_monument");
                    c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.mon.cleared"), false);
                    return 1;
                })))));
    }

    @Nullable
    private static Settlement near(ServerPlayer sp) {
        Places.Place at = SettlementService.cityAt(sp.serverLevel(), sp.blockPosition());
        Settlement s = at == null ? null : SettlementService.city(sp.server, at.id());
        if (s != null && s.own()) return s;
        Settlement best = null;
        double bd = Double.MAX_VALUE;
        for (Settlement o : SettlementData.get(sp.server).all()) {
            if (!o.own() || o.dim != sp.level().dimension()) continue;
            double d = o.heart.distSqr(sp.blockPosition());
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    private static List<Component> debug(ServerPlayer sp, Settlement s) {
        ensure(s);
        List<Component> out = new ArrayList<>();
        out.add(SettlementsLang.tr("cmd.mon", SettlementService.cityName(sp.server, s), activeCount(s), limit(s), CityTier.name(s.level)));
        int n = 0;
        for (int c : cells(s)) {
            String key = s.buildings.get(c);
            Blueprint bp = s.blueprintAt(c);
            out.add(Component.literal("  " + (++n) + ". " + (isActive(s, c) ? "● " : "○ "))
                    .append(bp == null ? Component.literal(key) : bp.displayName())
                    .append(Component.literal(" " + SettlementService.roman(bonusLevel(s, c)) + " — "))
                    .append(bonusName(key)).append(Component.literal(": ")).append(effects(key, bonusLevel(s, c), 1F, false)));
        }
        Applied a = PLAYERS.get(sp.getUUID());
        if (a != null) {
            StringBuilder b = new StringBuilder();
            for (Stat st : Stat.values()) if (a.t.get(st) > 0) b.append(' ').append(st.id).append('=').append(a.t.get(st));
            if (!a.t.elementXp.isEmpty()) b.append(" element_xp=").append(a.t.elementXp);
            if (!a.city.spell.isEmpty()) b.append(" spell(city)=").append(a.city.spell);
            if (!a.carried.spell.isEmpty()) b.append(" spell(blessing)=").append(a.carried.spell);
            String now = b.length() == 0 ? " -" : b.toString();
            out.add(SettlementsLang.tr("cmd.mon.you", now));
        }
        return out;
    }

    private CityMonuments() {}
}
