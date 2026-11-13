package com.renanpontes.isekaimahou.feature.auto_glyphs;

import com.renanpontes.isekaimahou.feature.automation.Pedestals;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * O "filtro" dos glifos de fazenda: no pedestal, o item do espaço de FILTRO ({@link Pedestals#currentFilter()});
 * na mão (só onde faz sentido, ex.: semente/muda/tocha), o item da MÃO SECUNDÁRIA do jogador.
 */
public final class Filters {
    public static ItemStack of(SpellContext ctx, boolean offhandToo) {
        ItemStack f = Pedestals.currentFilter();
        if (!f.isEmpty()) return f;
        if (offhandToo) {
            ServerPlayer sp = ctx.casterPlayer();
            if (sp != null && !(sp instanceof FakePlayer)) return sp.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    private Filters() {}
}
