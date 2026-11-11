package com.renanpontes.isekaimahou.client;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Ícones de verdade (§10) para glifos, elementos, tipos de glifo, magias e abas do menu (cliente).
 * Texturas, todas em {@code assets/<ns>/textures/gui/}:
 * <ul>
 *   <li>{@code glyph/<path do id>.png} — 32×32 por glifo</li>
 *   <li>{@code element/<id>.png} — 16×16 por elemento</li>
 *   <li>{@code glyph_kind/<form|effect|augment|trigger|link|law>.png} — 16×16</li>
 *   <li>{@code tab/<id>.png} — 16×16 por aba (ou o ícone de {@link MenuTabs#setIcon})</li>
 * </ul>
 * Qualquer tamanho é desenhado escalado para {@code size}×{@code size}. Se a textura não existir, cai num
 * desenho de reserva (quadrado arredondado na cor do elemento + símbolo do tipo), então dá para usar já.
 * "Textura existe?" fica em cache, limpo a cada recarga de recursos (F3+T).
 */
public final class GlyphArt {
    private static final Map<ResourceLocation, Boolean> EXISTS = new ConcurrentHashMap<>();
    /** Runas dos 22 elementos já existentes (16×16, ordem de Element.ordinal()), usadas como reserva do ícone de elemento. */
    private static final ResourceLocation RUNE_SHEET = IsekaiMahou.id("textures/gui/mage_runes.png");

    // Símbolos 7×7 dos tipos de glifo (reserva quando falta textura).
    private static final String[] SYM_FORM = {"..###..", ".#...#.", "#..#..#", "#.###.#", "#..#..#", ".#...#.", "..###.."};
    private static final String[] SYM_EFFECT = {"...#...", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "...#..."};
    private static final String[] SYM_AUGMENT = {"...#...", "...#...", "...#...", "#######", "...#...", "...#...", "...#..."};
    private static final String[] SYM_TRIGGER = {"....##.", "...##..", "..##...", ".######", "...##..", "..##...", ".##...."};
    private static final String[] SYM_LINK = {".##....", "#..#...", "#..##..", ".##..#.", "..##..#", "...#..#", "....##."};
    private static final String[] SYM_LAW = {"#######", "#.....#", "#.###.#", "#.#.#.#", "#.###.#", "#.....#", "#######"};

    // ---------------------------------------------------------------- Texturas
    public static ResourceLocation glyphTexture(Glyph glyph) {
        return ResourceLocation.fromNamespaceAndPath(glyph.id().getNamespace(), "textures/gui/glyph/" + glyph.id().getPath() + ".png");
    }

    public static ResourceLocation elementTexture(Element element) {
        return IsekaiMahou.id("textures/gui/element/" + element.id + ".png");
    }

    /** Nome do arquivo de tipo: form, effect, augment, trigger, link (ligações) ou law. */
    public static String kindName(GlyphKind kind, boolean link) {
        if (link) return "link";
        return switch (kind) {
            case FORM -> "form";
            case EFFECT -> "effect";
            case AUGMENT -> "augment";
            case TRIGGER -> "trigger";
            case LAW -> "law";
        };
    }

    public static ResourceLocation kindTexture(GlyphKind kind, boolean link) {
        return IsekaiMahou.id("textures/gui/glyph_kind/" + kindName(kind, link) + ".png");
    }

    /** Ícone da aba: o definido por {@link MenuTabs#setIcon} ou {@code textures/gui/tab/<id>.png}. */
    public static ResourceLocation tabTexture(String tabId) {
        return MenuTabs.icon(tabId);
    }

    /** A textura existe nos recursos carregados? (cache; limpo por {@link #clearCache}). */
    public static boolean exists(ResourceLocation texture) {
        return EXISTS.computeIfAbsent(texture, t -> Minecraft.getInstance().getResourceManager().getResource(t).isPresent());
    }

    /** Esquece o cache (chamado na recarga de recursos). */
    public static void clearCache() {
        EXISTS.clear();
    }

    /** Desenha a textura inteira escalada para size×size (com transparência). */
    public static void icon(GuiGraphics g, ResourceLocation texture, int x, int y, int size) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(texture, x, y, size, size, 0F, 0F, 16, 16, 16, 16);
        RenderSystem.disableBlend();
    }

    // ---------------------------------------------------------------- Glifos
    /** Ícone do glifo (32×32 escalado); reserva: quadrado na cor do elemento + símbolo do tipo. */
    public static void glyph(GuiGraphics g, Glyph glyph, int x, int y, int size) {
        if (glyph == null) return;
        ResourceLocation tex = glyphTexture(glyph);
        if (exists(tex)) {
            icon(g, tex, x, y, size);
            return;
        }
        int color = color(glyph);
        MahouUi.roundRect(g, x, y, size, size, MahouUi.lighten(color, 0.15F), MahouUi.darken(color, 0.45F));
        MahouUi.roundOutline(g, x, y, size, size, MahouUi.darken(color, 0.65F));
        if (size >= 12) g.fill(x + 2, y + 1, x + size - 2, y + 2, 0x50FFFFFF);
        symbol(g, symbolOf(glyph.kind(), isLink(glyph)), x, y, size, 0xFFFFFFFF);
        if (size >= 16 && glyph.tier() > 1) {
            for (int i = 0; i < glyph.tier(); i++) g.fill(x + 2 + i * 2, y + size - 3, x + 3 + i * 2, y + size - 2, 0xD0FFFFFF);
        }
    }

    /** Ícone do tipo do glifo (ligações usam "link"). */
    public static void kind(GuiGraphics g, Glyph glyph, int x, int y, int size) {
        if (glyph != null) kind(g, glyph.kind(), isLink(glyph), x, y, size);
    }

    /** Ícone do tipo (reserva: símbolo na cor do tipo sobre um disco escuro). */
    public static void kind(GuiGraphics g, GlyphKind kind, boolean link, int x, int y, int size) {
        ResourceLocation tex = kindTexture(kind, link);
        if (exists(tex)) {
            icon(g, tex, x, y, size);
            return;
        }
        MahouUi.roundRect(g, x, y, size, size, 0xE0201A30, 0xE0100C18);
        symbol(g, symbolOf(kind, link), x, y, size, kindColor(kind, link));
    }

    /** Cor padrão de cada tipo de glifo (ARGB). */
    public static int kindColor(GlyphKind kind, boolean link) {
        if (link) return 0xFF7FE0C8;
        return switch (kind) {
            case FORM -> 0xFF6FB8FF;
            case EFFECT -> 0xFFFF8A6A;
            case AUGMENT -> 0xFFB98CFF;
            case TRIGGER -> 0xFFFFD86B;
            case LAW -> 0xFFFF5A8A;
        };
    }

    public static boolean isLink(Glyph glyph) {
        return glyph instanceof TriggerGlyph t && t.isLink();
    }

    /** Cor do glifo para desenhos: a do elemento (glifos de Origem usam a cor da classe). */
    public static int color(Glyph glyph) {
        return glyph.origin() != null ? glyph.origin().argb() : glyph.argb();
    }

    // ---------------------------------------------------------------- Elementos
    /** Runa do elemento (16×16 escalada); reserva: runa do Grimório do Mago, senão disco na cor + inicial. */
    public static void element(GuiGraphics g, Element element, int x, int y, int size) {
        if (element == null) return;
        ResourceLocation tex = elementTexture(element);
        if (exists(tex)) {
            icon(g, tex, x, y, size);
            return;
        }
        if (exists(RUNE_SHEET)) {
            int i = element.ordinal();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(RUNE_SHEET, x, y, size, size, (i % 16) * 16F, (i / 16) * 16F, 16, 16, 256, 64);
            RenderSystem.disableBlend();
            return;
        }
        int c = element.argb();
        MahouUi.roundRect(g, x, y, size, size, MahouUi.lighten(c, 0.2F), MahouUi.darken(c, 0.5F));
        MahouUi.roundOutline(g, x, y, size, size, MahouUi.darken(c, 0.7F));
        letter(g, element.displayName().getString(), x, y, size);
    }

    // ---------------------------------------------------------------- Magias
    /**
     * Ícone de uma magia: fundo na cor da magia, o ícone do glifo principal (1º efeito, senão a forma) e a runa
     * do elemento primário no canto; pontinhos coloridos para os outros elementos usados (magias multi-elemento).
     * Receita vazia/null = espaço vazio.
     */
    public static void spell(GuiGraphics g, @Nullable SpellRecipe recipe, Element element, int x, int y, int size) {
        if (recipe == null || recipe.isEmpty()) {
            MahouUi.roundRect(g, x, y, size, size, 0x80181424, 0x80100C18);
            MahouUi.roundOutline(g, x, y, size, size, 0x60FFFFFF);
            return;
        }
        int color = recipe.color() >= 0 ? 0xFF000000 | recipe.color() : element.argb();
        MahouUi.roundRect(g, x, y, size, size, MahouUi.darken(color, 0.55F), MahouUi.darken(color, 0.8F));
        Glyph main = mainGlyph(recipe);
        int inset = size >= 16 ? 1 : 0;
        if (main != null && exists(glyphTexture(main))) {
            icon(g, glyphTexture(main), x + inset, y + inset, size - inset * 2);
        } else {
            Font font = Minecraft.getInstance().font;
            String abbr = CoreHud.abbreviation(recipe);
            float s = Math.max(0.5F, Math.min(1.5F, size / 18F));
            int tw = font.width(abbr);
            MahouUi.smallText(g, font, Component.literal(abbr), x + (size - tw * s) / 2F, y + (size - 8 * s) / 2F + 0.5F, s, color, true);
        }
        MahouUi.roundOutline(g, x, y, size, size, color);
        if (size >= 14) {
            int rs = Math.max(7, size * 7 / 16);
            element(g, element, x + size - rs + 1, y + size - rs + 1, rs);
        }
        if (size >= 16) {
            int px = x + size - 3;
            for (Element extra : extraElements(recipe, element)) {
                g.fill(px - 1, y + 1, px + 2, y + 4, 0xFF000000);
                g.fill(px, y + 2, px + 1, y + 3, extra.argb());
                px -= 4;
                if (px < x + 2) break;
            }
        }
    }

    /** Glifo que melhor representa a magia: o primeiro EFEITO, senão a forma (ou o primeiro glifo conhecido). */
    @Nullable
    public static Glyph mainGlyph(SpellRecipe recipe) {
        Glyph form = null, first = null;
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g == null) continue;
            if (first == null) first = g;
            if (g.kind() == GlyphKind.EFFECT) return g;
            if (form == null && g.kind() == GlyphKind.FORM) form = g;
        }
        return form != null ? form : first;
    }

    /** Elementos (não Arcanos) dos glifos da receita, fora o primário. */
    public static Set<Element> extraElements(SpellRecipe recipe, Element primary) {
        Set<Element> out = new LinkedHashSet<>();
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g == null || g.element() == Element.ARCANE || g.origin() != null) continue;
            if (g.element() == primary || primary.canUseGlyphOf(g.element())) continue;
            out.add(g.element());
        }
        return out;
    }

    // ---------------------------------------------------------------- Abas
    /** Ícone da aba do menu B; reserva: quadrado arcano com a inicial do título da aba. */
    public static void tab(GuiGraphics g, String tabId, int x, int y, int size) {
        ResourceLocation tex = tabTexture(tabId);
        if (exists(tex)) {
            icon(g, tex, x, y, size);
            return;
        }
        MahouUi.roundRect(g, x, y, size, size, 0xFF3A2C5A, 0xFF1A1428);
        MahouUi.roundOutline(g, x, y, size, size, MahouUi.GOLD_DIM);
        MenuTabs.Entry e = MenuTabs.get(tabId);
        letter(g, e != null ? e.title().getString() : tabId, x, y, size);
    }

    // ---------------------------------------------------------------- Interno
    private static String[] symbolOf(GlyphKind kind, boolean link) {
        if (link) return SYM_LINK;
        return switch (kind) {
            case FORM -> SYM_FORM;
            case EFFECT -> SYM_EFFECT;
            case AUGMENT -> SYM_AUGMENT;
            case TRIGGER -> SYM_TRIGGER;
            case LAW -> SYM_LAW;
        };
    }

    /** Símbolo 7×7 centralizado, escalado em pixels inteiros (com sombra) para ficar nítido. */
    private static void symbol(GuiGraphics g, String[] rows, int x, int y, int size, int color) {
        int px = Math.max(1, (size - 2) / 9);
        int w = 7 * px;
        int ox = x + (size - w) / 2, oy = y + (size - w) / 2;
        for (int r = 0; r < 7; r++) {
            String row = rows[r];
            for (int c = 0; c < 7; c++) {
                if (row.charAt(c) != '#') continue;
                int sx = ox + c * px, sy = oy + r * px;
                if (size >= 12) g.fill(sx + 1, sy + 1, sx + px + 1, sy + px + 1, 0x80000000);
                g.fill(sx, sy, sx + px, sy + px, color);
            }
        }
    }

    /** Inicial (maiúscula) centralizada, em escala proporcional ao ícone. */
    private static void letter(GuiGraphics g, String text, int x, int y, int size) {
        String l = text.isEmpty() ? "?" : text.substring(0, text.offsetByCodePoints(0, 1)).toUpperCase();
        Font font = Minecraft.getInstance().font;
        float s = Math.max(0.5F, size / 14F);
        MahouUi.smallText(g, font, Component.literal(l), x + (size - font.width(l) * s) / 2F + 0.5F, y + (size - 7 * s) / 2F, s, 0xFFFFFFFF, true);
    }

    private GlyphArt() {}
}
