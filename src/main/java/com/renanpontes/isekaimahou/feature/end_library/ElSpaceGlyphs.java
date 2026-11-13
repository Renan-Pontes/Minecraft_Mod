package com.renanpontes.isekaimahou.feature.end_library;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Os glifos espaciais (Inventário Dimensional, Abrir Portal, Teleporte, Marca de Retorno, Gravidade...) são do módulo
 * space_magic, escrito em paralelo: aqui eles são achados SÓ em tempo de execução — todo glifo registrado por uma
 * classe do pacote {@code feature.space_magic}, mais uma lista de ids prováveis. Se o módulo faltar, cai para glifos
 * arcanos altos (o Tomo nunca sai vazio).
 */
public final class ElSpaceGlyphs {
    /** Ids prováveis (o contrato não fixou os ids; a busca pelo pacote cobre os outros). */
    private static final Set<String> CANDIDATES = Set.of("dimensional_inventory", "item_box", "dimensional_storage",
            "open_portal", "portal", "dimension_portal", "teleport", "short_teleport", "blink", "return_mark",
            "mark_of_return", "recall", "gravity", "gravity_well", "space_gravity");

    public static List<Glyph> spaceGlyphs() {
        List<Glyph> out = new ArrayList<>();
        for (Glyph g : GlyphRegistry.all()) {
            if (g.isHidden() || GlyphRegistry.isHidden(g.id())) continue;
            boolean fromModule = g.getClass().getName().contains(".feature.space_magic.");
            boolean named = g.id().getNamespace().equals(IsekaiMahou.MODID) && CANDIDATES.contains(g.id().getPath());
            if (fromModule || named) out.add(g);
        }
        return out;
    }

    /** Reserva: arcanos de Círculo alto (tier ≥ 3), sem lendários nem Magia de Origem. */
    private static List<Glyph> fallback() {
        List<Glyph> out = new ArrayList<>();
        for (Glyph g : GlyphRegistry.all()) {
            if (g.isHidden() || g.element() != Element.ARCANE || g.origin() != null || g.isLegendary() || g.tier() < 3) continue;
            out.add(g);
        }
        return out;
    }

    /** Pergaminho de um glifo espacial, preferindo um que o jogador ainda NÃO conhece. */
    public static ItemStack randomScroll(RandomSource random, @Nullable Player player) {
        List<Glyph> pool = spaceGlyphs();
        if (pool.isEmpty()) pool = fallback();
        if (pool.isEmpty()) return ItemStack.EMPTY;
        if (player != null) {
            MagicData data = MagicApi.get(player);
            List<Glyph> unknown = pool.stream().filter(g -> !data.knowsGlyph(g.id())).toList();
            if (!unknown.isEmpty()) pool = unknown;
        }
        return GlyphScrollItem.of(pool.get(random.nextInt(pool.size())).id());
    }

    private ElSpaceGlyphs() {}
}
