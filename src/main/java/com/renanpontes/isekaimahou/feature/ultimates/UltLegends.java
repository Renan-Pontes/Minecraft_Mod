package com.renanpontes.isekaimahou.feature.ultimates;

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
 * Magias Lendárias do módulo: receitas prontas com as formas novas (Meteoro, Chuva de Meteoros, Transcendência,
 * Colosso). Nome mostrado = {@code legend.isekai_mahou.<id>}; nome/cântico da receita = os do Grimório.
 */
public final class UltLegends {
    private static final List<String> IDS = new ArrayList<>();

    static void register() {
        // Meteoro: uma rocha enorme em brasa que explode e incendeia (rocha maior com Amplificar)
        add("meteor", Element.FIRE, 4, "Meteoro", 0xFF6A2B,
                "Rocha errante das estrelas, rasgue o céu e caia em chamas!",
                "meteor", "amplify", "expand", "explosion", "amplify", "ignite");
        // Chuva de Meteoros: o céu sangra e dezenas de rochas caem por 15 s
        add("meteor_swarm", Element.FIRE, 6, "Chuva de Meteoros", 0xFF4A1A,
                "Céu escarlate, abra-se! Que as estrelas caiam sobre meus inimigos!",
                "meteor_shower", "expand", "explosion", "amplify", "ignite", "amplify");
        // Despertar: transcendência arcana — voa e pulsa dano e empurrão em volta
        add("awakening", Element.ARCANE, 5, "Despertar", 0xFFE9A8,
                "Além dos limites da carne... eu desperto!",
                "transcend", "arcane_damage", "amplify", "push");
        // Colosso Espectral: o gigante corta com dano arcano reforçado e atordoa
        add("spectral_colossus", Element.ARCANE, 6, "Colosso Espectral", 0xB98CFF,
                "Guardião sem nome, empunhe a lâmina por mim!",
                "colossus", "arcane_damage", "amplify", "amplify", "stun");
    }

    private static void add(String id, Element element, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        Legends.register(id, new SpellRecipe(list, name, color, chant), element, minCircle);
        IDS.add(id);
    }

    /** Depois que todos os módulos registraram os glifos: toda receita tem que passar no parser. */
    static void validate(FMLCommonSetupEvent event) {
        for (String id : IDS) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) continue;
            SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
            if (!r.ok()) IsekaiMahou.LOGGER.warn("Magia Lendária '{}' inválida: {}", id, r.error().getString());
        }
    }

    private UltLegends() {}
}
