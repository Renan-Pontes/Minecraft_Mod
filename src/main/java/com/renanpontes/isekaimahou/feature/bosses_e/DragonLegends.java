package com.renanpontes.isekaimahou.feature.bosses_e;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Magia Lendária do Dragão: "Sopro Primordial" — um cone com os QUATRO elementos primordiais de uma vez (fogo, mar,
 * rocha e vento; o Círculo 7+ mistura no máx. 4 elementos). Os glifos são de outros módulos: no setup comum (com
 * todos os glifos registrados) a primeira variante que passa no SpellParser E cabe nos limites de um jogador do
 * Círculo {@link #MIN_CIRCLE} (elementos e glifos) é registrada; se nenhuma passar, o Dragão entrega uma lenda
 * aleatória no lugar (e o log avisa).
 */
public final class DragonLegends {
    public static final String ID = "primordial_breath";
    private static final String NAME = "Sopro Primordial";
    private static final String CHANT = "Fogo, mar, rocha e tempestade... rujam comigo: Sopro Primordial!";
    private static final int COLOR = 0xFFB347;
    private static final int MIN_CIRCLE = 9;

    /** Variantes (elemento do slot + glifos), da mais completa para a mais simples. */
    private static final Object[][] VARIANTS = {
            {Element.FIRE, new String[]{"cone", "ignite", "tide", "rock", "wind_blade", "amplify", "amplify", "expand"}},
            {Element.FIRE, new String[]{"cone", "ignite", "tide", "rock", "gust", "amplify", "expand"}},
            {Element.FIRE, new String[]{"cone", "ignite", "tide", "amplify", "expand"}},
            {Element.FIRE, new String[]{"cone", "ignite", "amplify", "amplify", "expand"}},
    };

    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (Object[] v : VARIANTS) {
                Element el = (Element) v[0];
                String[] ids = (String[]) v[1];
                List<ResourceLocation> list = new ArrayList<>();
                boolean ok = true;
                for (String id : ids) {
                    ResourceLocation rl = IsekaiMahou.id(id);
                    if (GlyphRegistry.get(rl) == null) {
                        ok = false;
                        break;
                    }
                    list.add(rl);
                }
                if (!ok) continue;
                SpellRecipe recipe = new SpellRecipe(list, NAME, COLOR, CHANT);
                SpellParser.Result r = SpellParser.parse(recipe, el, null);
                if (!r.ok() || !fitsCircle(r, ids.length)) continue;
                Legends.register(ID, recipe, el, MIN_CIRCLE);
                return;
            }
            IsekaiMahou.LOGGER.warn("Magia Lendária '{}' (bosses_e): nenhuma variante validou; o Dragão dará uma lenda aleatória.", ID);
        });
    }

    /** Um jogador do Círculo {@link #MIN_CIRCLE} consegue conjurar (nº de elementos e de glifos)? */
    private static boolean fitsCircle(SpellParser.Result r, int glyphs) {
        long distinct = r.spell().elements().stream().filter(e -> e != Element.ARCANE).count();
        return distinct <= Progression.maxSpellElements(MIN_CIRCLE)
                && glyphs <= Progression.maxGlyphs(Progression.levelCap(MIN_CIRCLE), MIN_CIRCLE);
    }

    /** Pergaminho do Sopro Primordial (vazio se a lenda não foi registrada). */
    public static ItemStack scroll() {
        return Legends.get(ID) != null ? Legends.scroll(ID) : ItemStack.EMPTY;
    }

    private DragonLegends() {}
}
