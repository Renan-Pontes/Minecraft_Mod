package com.renanpontes.isekaimahou.feature.legends_d;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Magias Lendárias D (homenagens, não cópias). O nome mostrado vem de {@code legend.isekai_mahou.<id>}; o nome e o
 * cântico da receita são os da magia copiada para o Grimório.
 */
public final class LegendRecipes {
    private static final List<String> IDS = new ArrayList<>();

    public static void register() {
        // A Eminência nas Sombras: flutua, "Eu... sou..." e tudo vira luz azul-violeta. Gasta TODO o mana.
        add("i_am_atomic", Element.ARCANE, 7, "Eu Sou Atômico", 0x8A5CFF, "Eu... sou... ATÔMICO.",
                "self", "atomic", "all_in");
        // A versão "alcance total": Colossal + Tudo ou Nada — a cratera máxima.
        add("all_range_atomic", Element.ARCANE, 8, "Eu Sou Atômico — Alcance Total", 0x6E7BFF,
                "Alcance total... Eu... sou... ATÔMICO!",
                "self", "atomic", "colossal", "all_in");
        // O Sábio trava o alvo; a luz sagrada o apaga da existência (e o terreno até o céu).
        add("disintegration", Element.LIGHT, 6, "Desintegração", 0xFFF1B8,
                "Análise concluída. Alvo travado... Desintegração.",
                "projectile", "accelerate", "homing", "disintegration", "amplify");
        // "Tempo, pare!": tudo para; quando volta, as lâminas conjuradas e o dano guardado caem de uma vez.
        add("the_world", Element.ARCANE, 6, "O Mundo", 0xE8C34A, "Tempo, pare!",
                "time_stop", "conjured_weapon", "amplify", "arcane_damage");
        // Uma cabra. Por 30 segundos. Se ela morrer, ele morre.
        add("goat_curse", Element.ARCANE, 3, "Maldição da Cabra", 0xD9CBB0,
                "Mostre sua forma verdadeira... BÉÉÉ!",
                "projectile", "homing", "goatify", "extend");
    }

    private static void add(String id, Element element, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        Legends.register(id, new SpellRecipe(list, name, color, chant), element, minCircle);
        IDS.add(id);
    }

    /** Depois que todos os módulos registraram os glifos: cada lenda tem que passar no parser (senão é bug). */
    static void validate(FMLCommonSetupEvent event) {
        for (String id : IDS) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) continue;
            SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
            if (!r.ok()) IsekaiMahou.LOGGER.error("Magia Lendária '{}' (legends_d) inválida: {}", id, r.error().getString());
        }
    }

    private LegendRecipes() {}
}
