package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.glyph.CircleStyle;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;

/**
 * Estado do editor do Grimório. Fica estático porque o StatusScreen recria a aba a cada troca/redimensionamento
 * e o rascunho não pode se perder (também sobrevive a fechar e abrir o menu).
 */
public final class GrimoireDraft {
    private static final int MAX_UNDO = 40;
    /** Limite de glifos no editor = limite absoluto do núcleo (o nível/Círculo do jogador é checado pelo parser). */
    public static final int MAX_EDIT = Progression.MAX_GLYPHS_HARD;

    /** O que a coluna da esquerda mostra. */
    enum Mode { GLYPHS, PRESETS, LIBRARY, LEGENDS }

    /**
     * Prefixo da chave do filtro de "elemento" para os glifos de Origem: {@code origin:<categoria>} (Mágicas,
     * Físicas, Destreza — a categoria da classe dona do glifo).
     */
    static final String ORIGIN_KEY = "origin:";

    // ---- rascunho da magia
    static final List<ResourceLocation> glyphs = new ArrayList<>();
    static String name = "";
    static String chant = "";
    static int color = -1;
    /** Círculo mágico desta magia (forma/símbolo/cor do círculo de conjuração). */
    static CircleStyle circle = CircleStyle.DEFAULT;
    /** Incrementa a cada mudança feita fora das caixas de texto (para a aba atualizar os campos). */
    static int revision;
    private static final Deque<Snapshot> UNDO = new ArrayDeque<>();

    /**
     * Ponto de desfazer. {@code full} = carregar/limpar (volta tudo); senão foi só edição da sequência e
     * desfazer mexe só nos glifos — o nome/cântico/cor digitados depois não podem se perder.
     */
    private record Snapshot(SpellRecipe recipe, boolean full) {}

    // ---- destino (slot): v1.0 = 9 loadouts fixos, sem elemento (o elemento vem dos glifos)
    private static int loadout = -1;
    static int slot;
    private static MoveTrigger trigger = MoveTrigger.NORMAL;

    // ---- coluna da esquerda
    static Mode mode = Mode.GLYPHS;
    static GlyphKind kindFilter;
    /** Elementos mostrados na paleta (ids de Element ou {@link #ORIGIN_KEY}); vazio = todos. */
    static final Set<String> elementFilter = new LinkedHashSet<>();
    static String search = "";
    static ResourceLocation selected;
    static double paletteScroll;
    static double libraryScroll;
    static double legendScroll;
    static double presetScroll;
    static double programScroll;
    static double detailScroll;
    /** Caixa do programa mostra o DETALHAMENTO (dano/custo por parte) em vez do programa. */
    static boolean details;

    public static SpellRecipe recipe() {
        return new SpellRecipe(glyphs, name, color, chant, circle);
    }

    public static boolean isEmpty() {
        return glyphs.isEmpty() && name.isEmpty() && chant.isEmpty() && color < 0 && circle.isDefault();
    }

    // ------------------------------------------------------------------ edição
    private static void snapshot(boolean full) {
        UNDO.push(new Snapshot(recipe(), full));
        while (UNDO.size() > MAX_UNDO) UNDO.removeLast();
    }

    static boolean canUndo() {
        return !UNDO.isEmpty();
    }

    static boolean undo() {
        if (UNDO.isEmpty()) return false;
        Snapshot s = UNDO.pop();
        if (s.full()) {
            apply(s.recipe());
        } else {
            glyphs.clear();
            glyphs.addAll(s.recipe().glyphs());
            revision++;
        }
        return true;
    }

    /** Carrega uma receita no editor (o rascunho anterior vai para o desfazer). */
    public static void load(SpellRecipe r) {
        snapshot(true);
        apply(r);
    }

    static void clear() {
        snapshot(true);
        apply(SpellRecipe.EMPTY);
    }

    private static void apply(SpellRecipe r) {
        glyphs.clear();
        // receitas antigas/maiores que o editor: guarda só o que cabe (o resto nunca seria válido)
        glyphs.addAll(r.glyphs().subList(0, Math.min(MAX_EDIT, r.glyphs().size())));
        name = r.name();
        chant = r.chant();
        color = r.color();
        circle = r.circle();
        revision++;
    }

    static boolean add(ResourceLocation id) {
        return insert(glyphs.size(), id);
    }

    /** Insere o glifo na posição {@code index} (0..size). Falso se o editor está cheio. */
    static boolean insert(int index, ResourceLocation id) {
        if (glyphs.size() >= MAX_EDIT) return false;
        snapshot(false);
        glyphs.add(Math.max(0, Math.min(glyphs.size(), index)), id);
        revision++;
        return true;
    }

    static void remove(int index) {
        if (index < 0 || index >= glyphs.size()) return;
        snapshot(false);
        glyphs.remove(index);
        revision++;
    }

    /** Move o glifo de {@code from} para a posição de inserção {@code to} (0..size). */
    static boolean move(int from, int to) {
        if (from < 0 || from >= glyphs.size()) return false;
        int target = to > from ? to - 1 : to;
        target = Math.max(0, Math.min(glyphs.size() - 1, target));
        if (target == from) return false;
        snapshot(false);
        ResourceLocation id = glyphs.remove(from);
        glyphs.add(target, id);
        revision++;
        return true;
    }

    static void setColor(int rgb) {
        if (rgb == color) return;
        color = rgb;
        revision++;
    }

    static void setCircle(CircleStyle c) {
        if (c == null || c.equals(circle)) return;
        circle = c;
        revision++;
    }

    // ------------------------------------------------------------------ elemento (vem dos glifos)
    /** Elemento primário do rascunho (mesma regra do núcleo: {@link SpellCost#primaryElement}). */
    public static Element primary() {
        return primaryOf(glyphs);
    }

    static Element primaryOf(List<ResourceLocation> ids) {
        List<Glyph> resolved = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null) resolved.add(g);
        }
        return SpellCost.primaryElement(resolved);
    }

    // ------------------------------------------------------------------ destino
    /** Loadout de destino 0..8 (começa no loadout ativo). */
    public static int loadout(MagicData d) {
        if (loadout < 0) loadout = d.activeLoadout();
        loadout = Math.max(0, Math.min(d.loadoutCount() - 1, loadout));
        return loadout;
    }

    public static int slot() {
        return Math.max(0, Math.min(Progression.SLOTS - 1, slot));
    }

    /** Versão de movimento (volta para Normal se o glifo de gatilho não for conhecido). */
    public static MoveTrigger trigger(MagicData d) {
        if (!triggerUnlocked(d, trigger)) trigger = MoveTrigger.NORMAL;
        return trigger;
    }

    public static boolean triggerUnlocked(MagicData d, MoveTrigger t) {
        return t.glyphId() == null || d.knowsGlyph(t.glyphId());
    }

    static void setLoadout(int l) {
        loadout = l;
    }

    static void setTrigger(MoveTrigger t) {
        trigger = t;
    }

    /**
     * Aponta o editor para um slot. Se o slot já tem magia nessa versão, carrega para editar;
     * se está vazio, mantém o rascunho atual (para o jogador salvar ali o que estava montando).
     */
    public static void openSlot(MagicData d, int l, int s, MoveTrigger t) {
        loadout = l;
        slot = s;
        trigger = t;
        mode = Mode.GLYPHS;
        details = false;
        SpellRecipe existing = d.peekSlot(l, s).get(t);
        if (existing != null && !existing.equals(recipe())) load(existing);
    }

    private GrimoireDraft() {}
}
