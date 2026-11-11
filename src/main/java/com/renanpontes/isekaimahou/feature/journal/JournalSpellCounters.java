package com.renanpontes.isekaimahou.feature.journal;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Contadores de "tipo de magia" para os passos do Diário (e para quem mais quiser usá-los, ex.: missões da Guilda).
 * Só conjurações de JOGADORES de verdade ({@code ctx.isPlayerCast()}; pedestais com FakePlayer, chefes e mobs não contam):
 * <ul>
 *   <li>{@value #LINKED}: magia programável — tem um estágio ligado (Ao Acertar / Ao Terminar / Ao Abater) ou Saltar;</li>
 *   <li>{@value #LEAP}: magia com Saltar (corrente que pula de inimigo em inimigo);</li>
 *   <li>{@value #MULTI}: magia que usa 2+ elementos (sem contar o Arcano);</li>
 *   <li>{@value #LEGEND}: Magia Lendária (receita igual a uma lenda registrada, ou com um glifo lendário).</li>
 * </ul>
 */
public final class JournalSpellCounters {
    public static final String LINKED = "spell.linked";
    public static final String LEAP = "spell.leap";
    public static final String MULTI = "spell.multi_element";
    public static final String LEGEND = "spell.legend";

    static void onCast(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        SpellContext ctx = event.getContext();
        // Só conjurações do próprio jogador (teclas/automáticas): castFrom com o jogador como "dono" (portais de lendas,
        // efeitos de outros módulos) não conta como "você montou e lançou esta magia".
        if (ctx == null || !ctx.isPlayerCast()) return;
        // O evento sai só na conjuração raiz: o estágio do contexto já é a magia inteira (sem reanalisar a receita).
        ParsedSpell spell = ctx.spell();
        if (spell == null) return;
        boolean leap = false;
        for (ParsedSpell stage : spell.stages()) {
            if (stage.leap()) leap = true;
        }
        if (spell.hasNext() || leap) MagicApi.addCounter(player, LINKED, 1);
        if (leap) MagicApi.addCounter(player, LEAP, 1);
        int elements = 0;
        for (Element e : spell.elements()) if (e != Element.ARCANE) elements++;
        if (elements >= 2) MagicApi.addCounter(player, MULTI, 1);
        if (isLegend(ctx.recipe())) MagicApi.addCounter(player, LEGEND, 1);
    }

    /** Receita de uma lenda registrada (mesmos glifos, na mesma ordem) ou com algum glifo lendário. */
    static boolean isLegend(SpellRecipe recipe) {
        if (recipe == null || recipe.isEmpty()) return false;
        List<ResourceLocation> glyphs = recipe.glyphs();
        for (Legends.Legend legend : Legends.all()) {
            if (legend.recipe().glyphs().equals(glyphs)) return true;
        }
        for (ResourceLocation id : glyphs) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && g.isLegendary()) return true;
        }
        return false;
    }

    private JournalSpellCounters() {}
}
