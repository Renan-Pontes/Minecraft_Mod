package com.renanpontes.isekaimahou.feature.legends_e;

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
 * Magias Lendárias E — Magias de Nível Supremo (homenagem a Overlord; Iä Shub-Niggurath é de Lovecraft, domínio
 * público). O nome mostrado vem de {@code legend.isekai_mahou.<id>}; nome/cântico da receita vão para o Grimório.
 */
public final class LegendRecipes {
    private static final List<String> IDS = new ArrayList<>();

    public static void register() {
        // Queda Final: o céu desaba num pilar de luz sobre cada inimigo da área
        add("fallen_down", Element.LIGHT, 7, "Queda Final", 0xBFE6FF,
                "Firmamento, abre teus olhos e derrama a luz que apaga tudo... Queda Final!",
                "super_tier", "fallen_down", "amplify");
        // Iä Shub-Niggurath: o vento negro sacrifica os fracos e a Cabra Negra manda seus Filhotes
        add("ia_shub_niggurath", Element.SHADOW, 8, "Iä Shub-Niggurath", 0x3A1F4A,
                "Iä! Iä! Shub-Niggurath! Cabra Negra dos Bosques, a dos Mil Filhotes!",
                "super_tier", "black_goat", "amplify");
        // Agarrar o Coração: rápido, a mão espectral esmaga o coração de um alvo
        add("grasp_heart", Element.SHADOW, 5, "Agarrar o Coração", 0x8B1A2B, "",
                "beam", "grasp_heart", "amplify");
        // O Fim de Toda Vida é a Morte: relógios da morte sobre todos os inimigos a 48 blocos
        add("goal_of_all_life", Element.SHADOW, 9, "O Fim de Toda Vida é a Morte", 0x5A0F1F,
                "Todo relógio para. Toda vida acaba. O fim de toda vida é a morte.",
                "super_tier", "death_clock", "amplify");
        // Queda de Meteoros Suprema: meteoros caem sobre cada inimigo e abrem crateras
        add("supreme_meteor", Element.FIRE, 6, "Queda de Meteoros Suprema", 0xFF6A2B,
                "Estrelas do firmamento, ouçam meu chamado e caiam sobre meus inimigos!",
                "super_tier", "crater", "explosion", "amplify");
    }

    private static void add(String id, Element element, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        Legends.register(id, new SpellRecipe(list, name, color, chant), element, minCircle);
        IDS.add(id);
    }

    /** Depois que todos os módulos registraram seus glifos: cada lenda tem que passar no parser. */
    static void validate(FMLCommonSetupEvent event) {
        for (String id : IDS) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) continue;
            SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
            if (!r.ok()) IsekaiMahou.LOGGER.warn("Magia Lendária '{}' (legends_e) inválida: {}", id, r.error().getString());
        }
    }

    private LegendRecipes() {}
}
