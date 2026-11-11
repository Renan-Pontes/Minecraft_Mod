package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.network.chat.Component;

/** Estatísticas da Janela de Status, calculadas a partir dos contadores/marcadores do jogador. */
final class StatusStats {
    /** Uma linha "rótulo: valor". */
    record Stat(Component label, Component value, int color, Component tooltip) {}

    static final String[] NODES = {"awakening", "apprentice", "adept", "fluidity", "master", "cataclysm", "domain", "archmage"};

    private StatusStats() {}

    static int sumPrefix(MagicData d, String prefix) {
        int sum = 0;
        for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
            if (e.getKey().startsWith(prefix)) sum += Math.max(0, e.getValue());
        }
        return sum;
    }

    static int countPrefix(MagicData d, String prefix) {
        int n = 0;
        for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getValue() > 0) n++;
        }
        return n;
    }

    /** Elemento com mais conjurações (null se nenhuma). */
    static Element favoriteElement(MagicData d) {
        Element best = null;
        int bestCount = 0;
        for (Element e : Element.values()) {
            int c = d.counter("cast." + e.id);
            if (c > bestCount) { bestCount = c; best = e; }
        }
        return best;
    }

    static int nodesReached(MagicData d) {
        int n = 0;
        for (MagicData.ElementProgress p : d.elements().values()) {
            for (String node : NODES) if (p.nodes.contains(node)) n++;
        }
        return n;
    }

    private static Component num(int v) {
        return Component.literal(String.valueOf(v));
    }

    private static Stat stat(String key, Component value, int color) {
        return new Stat(HudDraw.tr("screen.isekai_mahou.status_window.stat." + key), value, color,
                HudDraw.tr("screen.isekai_mahou.status_window.stat." + key + ".desc"));
    }

    /** Lista de estatísticas na ordem da janela. */
    static List<Stat> counters(MagicData d) {
        List<Stat> out = new ArrayList<>();
        int white = 0xFFFFFFFF;
        out.add(stat("casts", num(sumPrefix(d, "cast.")), white));
        out.add(stat("spell_kills", num(sumPrefix(d, "kill.spell.")), white));
        out.add(stat("kills", num(d.counter("kill.total")), white));
        out.add(stat("bosses", num(sumPrefix(d, "boss.")), 0xFFFF8A6B));
        out.add(stat("shrines", Component.literal(countPrefix(d, "shrine.") + "/" + Element.BASE.size()), 0xFFFFD86B));
        out.add(stat("quests", num(sumPrefix(d, "quest.")), white));
        out.add(stat("nodes", num(nodesReached(d)), 0xFF8FEFFF));
        out.add(stat("dimensions", num(countPrefix(d, "visit.")), white));
        out.add(stat("meditations", num(d.counter("meditate")), white));
        out.add(stat("studies", num(d.counter("study")), white));
        out.add(stat("brews", num(d.counter("brew")), white));
        out.add(stat("harvests", num(d.counter("harvest")), white));
        out.add(stat("breeds", num(d.counter("breed")), white));
        out.add(stat("library", Component.literal(d.library().size() + "/" + MagicData.MAX_LIBRARY), white));
        return out;
    }

    /** Glifos conhecidos (só os que existem no registro) e total. */
    static int[] glyphTotals(MagicData d) {
        int known = 0, total = 0;
        for (Glyph g : GlyphRegistry.all()) {
            total++;
            if (d.knowsGlyph(g.id())) known++;
        }
        return new int[] {known, total};
    }

    static int[] glyphTotals(MagicData d, GlyphKind kind) {
        int known = 0, total = 0;
        for (Glyph g : GlyphRegistry.byKind(kind)) {
            total++;
            if (d.knowsGlyph(g.id())) known++;
        }
        return new int[] {known, total};
    }

    static int[] legendaryTotals(MagicData d) {
        int known = 0, total = 0;
        for (Glyph g : GlyphRegistry.all()) {
            if (!g.isLegendary()) continue;
            total++;
            if (d.knowsGlyph(g.id())) known++;
        }
        return new int[] {known, total};
    }
}
