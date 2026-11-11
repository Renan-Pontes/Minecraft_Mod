package com.renanpontes.isekaimahou.feature.class_rogue;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * Dados do Ladino guardados no jogador (anexo {@link RogueContent#DATA}, copiado na morte): as páginas do Grimório
 * Roubado (magias copiadas de quem conjurou, com cargas) e a página escolhida. Só o servidor escreve; o cliente recebe
 * uma cópia pelo {@link RogueNet.Sync}.
 */
public final class RogueData {
    /** Páginas do Grimório Roubado (4 no rank V do Livro-Razão). */
    public static final int MAX_PAGES = 3;
    public static final int MAX_PAGES_MASTER = 4;
    public static final int MAX_CHARGES = 3;

    /** Uma magia roubada: receita, elemento, cargas e de quem foi roubada. */
    public record Page(SpellRecipe recipe, Element element, int charges, String from, boolean ultimate) {
        public Page withCharges(int c) {
            return new Page(recipe, element, c, from, ultimate);
        }

        public String displayName() {
            return recipe.name().isEmpty() ? "?" : recipe.name();
        }
    }

    private static CompoundTag tag(Player p) {
        return p.getData(RogueContent.DATA);
    }

    public static List<Page> pages(Player p) {
        List<Page> out = new ArrayList<>();
        ListTag list = tag(p).getList("Pages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            SpellRecipe r = SpellRecipe.CODEC.parse(NbtOps.INSTANCE, t.get("Recipe")).result().orElse(null);
            if (r == null || r.isEmpty()) continue;
            Element e = Element.byId(t.getString("Element"));
            out.add(new Page(r, e == null ? Element.ARCANE : e, Math.max(0, t.getInt("Charges")), t.getString("From"), t.getBoolean("Ult")));
        }
        return out;
    }

    public static void setPages(Player p, List<Page> pages) {
        ListTag list = new ListTag();
        for (Page pg : pages) {
            if (pg.charges() <= 0) continue;
            CompoundTag t = new CompoundTag();
            Tag rec = SpellRecipe.CODEC.encodeStart(NbtOps.INSTANCE, pg.recipe()).result().orElse(null);
            if (rec == null) continue;
            t.put("Recipe", rec);
            t.putString("Element", pg.element().id);
            t.putInt("Charges", Math.min(MAX_CHARGES, pg.charges()));
            t.putString("From", pg.from().length() > 48 ? pg.from().substring(0, 48) : pg.from());
            t.putBoolean("Ult", pg.ultimate());
            list.add(t);
        }
        CompoundTag root = tag(p).copy();
        root.put("Pages", list);
        int sel = root.getInt("Sel");
        if (sel >= list.size()) root.putInt("Sel", Math.max(0, list.size() - 1));
        p.setData(RogueContent.DATA, root);
    }

    public static int selected(Player p) {
        int n = pages(p).size();
        int s = tag(p).getInt("Sel");
        return n == 0 ? 0 : Math.floorMod(s, n);
    }

    public static void select(Player p, int index) {
        CompoundTag root = tag(p).copy();
        root.putInt("Sel", Math.max(0, index));
        p.setData(RogueContent.DATA, root);
    }

    /**
     * Guarda uma magia roubada. A mesma receita roubada de novo só recarrega (até {@link #MAX_CHARGES}); cheio de
     * páginas, a mais antiga sai. A nova vira a página escolhida.
     */
    public static Page store(Player p, Page page, int maxPages) {
        List<Page> list = pages(p);
        for (int i = 0; i < list.size(); i++) {
            Page old = list.get(i);
            if (old.recipe().glyphs().equals(page.recipe().glyphs())) {
                Page merged = old.withCharges(Math.min(MAX_CHARGES, Math.max(old.charges(), page.charges()) + 1));
                list.set(i, merged);
                setPages(p, list);
                select(p, i);
                return merged;
            }
        }
        while (list.size() >= Math.max(1, maxPages)) list.remove(0);
        list.add(page);
        setPages(p, list);
        select(p, list.size() - 1);
        return page;
    }

    /** Gasta uma carga da página {@code index} (a página some ao zerar). */
    public static void spend(Player p, int index) {
        List<Page> list = pages(p);
        if (index < 0 || index >= list.size()) return;
        Page pg = list.get(index);
        if (pg.charges() <= 1) list.remove(index);
        else list.set(index, pg.withCharges(pg.charges() - 1));
        setPages(p, list);
    }

    private RogueData() {}
}
