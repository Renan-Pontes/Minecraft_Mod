package com.renanpontes.isekaimahou.feature.legends_c;

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
 * Magias Lendárias das Técnicas Malditas (homenagens). O nome mostrado vem de {@code legend.isekai_mahou.<id>}; o
 * nome/cântico da receita são os que vão para o Grimório. O "Santuário Maligno" fica no legends_b (mesmo id), com a
 * receita nova que usa {@code law_shrine}, {@code dismantle} e {@code cleave} — ele também é conferido aqui.
 */
public final class LcRecipes {
    private static final List<String> IDS = new ArrayList<>();

    public static void register() {
        // Vazio Infinito: domínio fechado onde a informação infinita paralisa quem está dentro
        add("unlimited_void", Element.ARCANE, 6, "Vazio Infinito", 0x8A6BFF,
                "Expansão de Domínio... Vazio Infinito.",
                "domain_expansion", "arcane_damage", "amplify", "law_infinite_void", "law_sure_hit", "law_no_escape");
        // Azul: um projétil rápido que abre um ponto de atração no impacto
        add("blue", Element.ARCANE, 4, "Azul", 0x2F6BFF,
                "Técnica Amaldiçoada, Lapso... Azul.",
                "projectile", "accelerate", "lapse_blue", "expand", "amplify");
        // Vermelho: a repulsão disparada da ponta dos dedos
        add("red", Element.ARCANE, 4, "Vermelho", 0xE0182A,
                "Técnica Reversa, Inversão... Vermelho.",
                "self", "reversal_red", "amplify", "amplify");
        // Infinito: o espaço infinito entre você e o mundo
        add("infinity", Element.ARCANE, 5, "Infinito", 0x9FD8FF,
                "Mugen.",
                "self", "infinity", "extend", "extend");
        // Fuga: o arco de chamas do Rei das Maldições
        add("fuga", Element.FIRE, 6, "Fuga", 0xFF7A1A,
                "Abra-se... Fuga.",
                "self", "flame_arrow", "amplify", "expand");
        // Corte que Divide o Mundo: um Desmantelar colossal que fatia o próprio terreno
        add("world_cutting_slash", Element.WIND, 7, "Corte que Divide o Mundo", 0xF4E8E8,
                "Escama de Dragão. Recuo. Estrelas Gêmeas... Desmantelar.",
                "self", "dismantle", "colossal", "amplify", "amplify");
    }

    private static void add(String id, Element element, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        Legends.register(id, new SpellRecipe(list, name, color, chant), element, minCircle);
        IDS.add(id);
    }

    /** Depois que todos os módulos registraram os glifos: confere as receitas no parser (inválida = aviso no log). */
    static void validate(FMLCommonSetupEvent event) {
        List<String> ids = new ArrayList<>(IDS);
        ids.add("malevolent_shrine");
        for (String id : ids) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) {
                IsekaiMahou.LOGGER.warn("Magia Lendária '{}' não registrada", id);
                continue;
            }
            SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
            if (!r.ok()) IsekaiMahou.LOGGER.warn("Magia Lendária '{}' inválida: {}", id, r.error().getString());
        }
    }

    private LcRecipes() {}
}
