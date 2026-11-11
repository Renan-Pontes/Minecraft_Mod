package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.LoadoutInput;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Informações das magias do loadout ativo (custo, validade, versões, elemento) e um resumo dos 9 loadouts (seletor do
 * HUD), recalculadas só quando os dados mudam. Evita rodar o {@link SpellParser} a cada quadro do HUD.
 * <p>
 * v1.0: 9 loadouts fixos (sem Roda Elemental): o elemento de cada magia vem dos glifos
 * ({@link SpellCost#primaryElement}) e a carga de ultimate é única.
 */
final class LoadoutCache {
    /**
     * Uma versão (normal/agachado/...) de um slot, já validada.
     * @param cost       custo em mana real (custo por cópia e escala ×3 do núcleo já aplicados)
     * @param healthCost vida paga quando a magia tem Sobrecarga ({@link SpellCaster#overloadHealthCost})
     */
    record Variant(MoveTrigger trigger, SpellRecipe recipe, Element element, int cost, float healthCost, boolean valid,
                   boolean overload, boolean ultimate, boolean channel, int color, Component name) {}

    /** Resumo de um loadout para o seletor: a magia principal de cada slot (null = vazio). */
    record Summary(SpellRecipe[] spells, Element[] elements, int[] colors, int count, Set<Element> distinct) {
        static final Summary EMPTY = new Summary(new SpellRecipe[Progression.SLOTS], new Element[Progression.SLOTS],
                new int[Progression.SLOTS], 0, Set.of());

        /** Cor que representa o loadout (a da primeira magia; vazio = cinza). */
        int accent() {
            for (int i = 0; i < spells.length; i++) if (spells[i] != null) return colors[i];
            return 0xFF5A6478;
        }
    }

    @SuppressWarnings("unchecked")
    private static final Map<MoveTrigger, Variant>[] SLOTS = new Map[Progression.SLOTS];
    private static final Summary[] SUMMARY = new Summary[MagicData.LOADOUTS];
    private static int version = Integer.MIN_VALUE;
    private static int summaryVersion = Integer.MIN_VALUE;
    private static int loadout = -1;
    private static boolean creative;
    private static MagicData dataRef;
    private static MagicData summaryRef;

    static {
        for (int i = 0; i < SLOTS.length; i++) SLOTS[i] = new EnumMap<>(MoveTrigger.class);
    }

    private LoadoutCache() {}

    /** Garante que o cache corresponde ao loadout ativo. */
    static void refresh(MagicData d, boolean isCreative) {
        int lo = d.activeLoadout();
        if (version == ClientMagic.syncVersion && lo == loadout && isCreative == creative && d == dataRef) return;
        version = ClientMagic.syncVersion;
        loadout = lo;
        creative = isCreative;
        dataRef = d;
        for (int s = 0; s < Progression.SLOTS; s++) {
            SLOTS[s].clear();
            SpellSlot slot = d.peekSlot(lo, s);
            for (Map.Entry<MoveTrigger, SpellRecipe> e : slot.all().entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) continue;
                SLOTS[s].put(e.getKey(), build(s, e.getKey(), e.getValue(), d, isCreative));
            }
        }
    }

    /** Força recálculo no próximo acesso (ex.: ao sair do mundo). */
    static void invalidate() {
        version = Integer.MIN_VALUE;
        summaryVersion = Integer.MIN_VALUE;
        dataRef = null;
        summaryRef = null;
    }

    private static Variant build(int slot, MoveTrigger t, SpellRecipe r, MagicData d, boolean isCreative) {
        // Modo jogador: o primário vem dos glifos; criativo não checa conhecimento/Círculo (mesma regra do servidor)
        SpellParser.Result res = SpellParser.parse(r, isCreative ? null : d);
        Element el = SpellCost.primaryElement(r);
        int color = LoadoutInput.color(r);
        Component name = displayName(r);
        if (!res.ok()) return new Variant(t, r, el, 0, 0F, false, false, false, false, color, name);
        ParsedSpell p = res.spell();
        if (p.primary() != null) el = p.primary();
        // Sobrecarga em QUALQUER estágio faz a magia inteira ser paga com vida (mesma regra do SpellCaster)
        boolean overload = p.anyStats(s -> s.overload);
        // Ultimate só no G e o G só aceita ultimate (mesma regra do SpellCaster): fora disso a magia não sai
        boolean placed = p.isUltimate() == (slot == Progression.ULTIMATE_SLOT);
        return new Variant(t, r, el, p.manaCost(), SpellCaster.overloadHealthCost(p), placed, overload, p.isUltimate(),
                p.formStats().channel, color, name);
    }

    /** Nome da magia, ou o nome da forma quando o jogador não deu nome. */
    static Component displayName(SpellRecipe r) {
        if (!r.name().isBlank()) return Component.literal(r.name());
        for (var id : r.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && g.kind() == GlyphKind.FORM) return g.displayName();
        }
        return LoadoutInput.spellName(r);
    }

    static Map<MoveTrigger, Variant> variants(int slot) {
        return SLOTS[slot];
    }

    /**
     * Versão que sai AGORA ao apertar a tecla — mesma regra do servidor (SpellCaster.onKey):
     * a versão do movimento atual se existir e o gatilho for conhecido; senão a normal.
     */
    @Nullable
    static Variant active(int slot, Player player, MagicData d) {
        return resolve(slot, MoveTrigger.fromPlayer(player), d);
    }

    /** Versão que sai para o movimento {@code t}: a dele se existir e o gatilho for conhecido; senão a normal. */
    @Nullable
    static Variant resolve(int slot, MoveTrigger t, MagicData d) {
        Map<MoveTrigger, Variant> m = SLOTS[slot];
        Variant v = m.get(t);
        if (v != null && (t.glyphId() == null || d.knowsGlyph(t.glyphId()))) return v;
        return m.get(MoveTrigger.NORMAL);
    }

    /** Há uma ultimate válida no G do loadout ativo? (som de "ultimate pronta" só faz sentido com uma equipada) */
    static boolean hasUltimate() {
        for (Variant v : SLOTS[Progression.ULTIMATE_SLOT].values()) if (v.valid() && v.ultimate()) return true;
        return false;
    }

    /**
     * Elemento "em uso" para o Olho Analítico (fraquezas/resistências do alvo): o da magia sendo conjurada; senão o da
     * primeira magia do loadout ativo (Z, X, C, G); loadout vazio = Arcano.
     */
    static Element focusElement() {
        if (HudState.casting && HudState.castElement != null) return HudState.castElement;
        for (Map<MoveTrigger, Variant> m : SLOTS) {
            Variant v = m.get(MoveTrigger.NORMAL);
            if (v != null) return v.element();
        }
        return Element.ARCANE;
    }

    /** Cor da ultimate do G (anel de carga); sem ultimate, dourado. */
    static int ultimateColor() {
        Variant v = SLOTS[Progression.ULTIMATE_SLOT].get(MoveTrigger.NORMAL);
        if (v == null) for (Variant o : SLOTS[Progression.ULTIMATE_SLOT].values()) { v = o; break; }
        return v != null ? v.color() : HudDraw.GOLD;
    }

    /** O jogador consegue pagar esta versão agora? (mana, ou vida com Sobrecarga) */
    static boolean canPay(Variant v, Player player, float mana) {
        if (v == null || !v.valid()) return false;
        if (player.getAbilities().instabuild) return true;
        if (v.overload()) return player.getHealth() > v.healthCost() + 1.0F;
        return mana >= v.cost();
    }

    /**
     * true quando existe pelo menos uma magia válida no loadout e NENHUMA pode ser paga
     * (ultimates sem carga não contam: não sairiam de qualquer jeito).
     */
    static boolean nothingAffordable(Player player, MagicData d, float mana) {
        boolean any = false;
        boolean charged = d.charge() >= Progression.MAX_CHARGE;
        for (int s = 0; s < Progression.SLOTS; s++) {
            for (Variant v : SLOTS[s].values()) {
                if (!v.valid()) continue;
                if (v.ultimate() && !charged) continue;
                if (v.trigger().glyphId() != null && !d.knowsGlyph(v.trigger().glyphId())) continue;
                any = true;
                if (canPay(v, player, mana)) return false;
            }
        }
        return any;
    }

    // =====================================================================================
    //  Resumo dos 9 loadouts (seletor do HUD)
    // =====================================================================================
    /** Resumo do loadout {@code i} (0..8); refeito só quando chega uma sincronização nova. */
    static Summary summary(MagicData d, int i) {
        if (summaryVersion != ClientMagic.syncVersion || summaryRef != d) {
            summaryVersion = ClientMagic.syncVersion;
            summaryRef = d;
            for (int l = 0; l < SUMMARY.length; l++) SUMMARY[l] = null;
        }
        int l = Math.floorMod(i, MagicData.LOADOUTS);
        Summary s = SUMMARY[l];
        if (s == null) {
            s = buildSummary(d, l);
            SUMMARY[l] = s;
        }
        return s;
    }

    private static Summary buildSummary(MagicData d, int l) {
        if (d.loadoutEmpty(l)) return Summary.EMPTY;
        SpellRecipe[] spells = new SpellRecipe[Progression.SLOTS];
        Element[] els = new Element[Progression.SLOTS];
        int[] colors = new int[Progression.SLOTS];
        Set<Element> distinct = new LinkedHashSet<>();
        int count = 0;
        for (int s = 0; s < Progression.SLOTS; s++) {
            SpellRecipe r = d.peekSlot(l, s).main();
            if (r == null || r.isEmpty()) continue;
            spells[s] = r;
            els[s] = SpellCost.primaryElement(r);
            colors[s] = LoadoutInput.color(r);
            distinct.add(els[s]);
            count++;
        }
        return new Summary(spells, els, colors, count, distinct);
    }
}
