package com.renanpontes.isekaimahou.glyph;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.resources.ResourceLocation;

/**
 * Registro de todos os glifos (lado comum: existe igual no cliente e no servidor).
 * Módulos registram no seu {@code init}, ex.: {@code GlyphRegistry.register(new MeuEfeito())}.
 */
public final class GlyphRegistry {
    private static final Map<ResourceLocation, Glyph> GLYPHS = new LinkedHashMap<>();
    /** v1.4: ids aposentados pedidos antes de o glifo ser registrado (a ordem dos módulos não importa). */
    private static final Set<ResourceLocation> PENDING_HIDE = new HashSet<>();

    public static synchronized <T extends Glyph> T register(T glyph) {
        Glyph old = GLYPHS.putIfAbsent(glyph.id(), glyph);
        if (old != null) {
            throw new IllegalStateException("Glifo duplicado: " + glyph.id() + " (" + old.getClass().getName()
                    + " vs " + glyph.getClass().getName() + ")");
        }
        if (PENDING_HIDE.remove(glyph.id())) glyph.hidden();
        return glyph;
    }

    /**
     * v1.4: aposenta um glifo — ele continua registrado (magias e mundos antigos seguem funcionando), mas some das
     * listas do Grimório ({@link Glyph#isHidden()}). Pode ser chamado antes ou depois do registro.
     */
    public static synchronized void hide(ResourceLocation id) {
        Glyph g = GLYPHS.get(id);
        if (g != null) g.hidden();
        else PENDING_HIDE.add(id);
    }

    public static void hide(String path) {
        hide(IsekaiMahou.id(path));
    }

    /** O glifo com este id está aposentado? */
    public static boolean isHidden(ResourceLocation id) {
        Glyph g = GLYPHS.get(id);
        return g != null && g.isHidden();
    }

    public static Glyph get(ResourceLocation id) {
        return GLYPHS.get(id);
    }

    public static Glyph get(String path) {
        return GLYPHS.get(IsekaiMahou.id(path));
    }

    public static Collection<Glyph> all() {
        return Collections.unmodifiableCollection(GLYPHS.values());
    }

    public static List<Glyph> byKind(GlyphKind kind) {
        return GLYPHS.values().stream().filter(g -> g.kind() == kind).toList();
    }

    public static List<Glyph> byElement(Element element) {
        return GLYPHS.values().stream().filter(g -> g.element() == element && g.origin() == null).toList();
    }

    private GlyphRegistry() {}
}
