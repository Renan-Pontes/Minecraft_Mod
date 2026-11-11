package com.renanpontes.isekaimahou.feature.legends_b;

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
 * Magias Lendárias B (homenagens, não cópias): receitas prontas registradas em {@link Legends}. O nome mostrado vem
 * da tradução {@code legend.isekai_mahou.<id>}; o nome/cântico da receita são os da magia copiada para o Grimório.
 */
public final class LegendRecipes {
    /** Ids registrados por este módulo (para a checagem no setup). */
    private static final List<String> IDS = new ArrayList<>();

    public static void register() {
        // Vazio Roxo: atração (azul) e repulsão (vermelho) giram, se fundem e apagam tudo numa linha reta
        add("hollow_purple", Element.VOID, 5, "Vazio Roxo", 0x9B30FF,
                "Atração e repulsão, girem e tornem-se um só... Vazio Roxo.",
                "converge", "expand", "pull", "push", "annihilate", "amplify", "amplify");
        // Ruína Carmesim: orbe lento e teleguiado de chama negra que aniquila o que toca
        add("crimson_ruin", Element.HELLFIRE, 4, "Ruína Carmesim", 0xC0182E,
                "Poder da ruína, tinja de carmesim tudo o que existe.",
                "projectile", "decelerate", "homing", "annihilate", "amplify", "black_flame");
        // Santuário Maligno (v0.8, módulo legends_c): o santuário aparece, domínio aberto e maior; Desmantelar retalha
        // o terreno e Clivar corta todo inimigo lá dentro. Leis no fim da receita (regra do parser).
        add("malevolent_shrine", Element.VOID, 6, "Santuário Maligno", 0x8B0A1A,
                "Expansão de Domínio... Santuário Maligno.",
                "domain_expansion", "dismantle", "cleave", "amplify", "law_shrine", "law_sure_hit");
        // Megido Solar: lentes no céu concentram o sol em feixes sobre cada inimigo da área
        add("solar_megiddo", Element.SUN, 4, "Megido Solar", 0xFFC83A,
                "Lentes do céu, reúnam a luz do sol e purifiquem a terra.",
                "sky_lances", "solar_ray", "amplify");
        // Esfera Espiral: esfera de vento na mão que tritura e arremessa
        add("spiral_sphere", Element.WIND, 2, "Esfera Espiral", 0x7FD8FF, "",
                "spiral_sphere", "wind_blade", "amplify", "gust");
        // Raio Assassino: o feixe ofensivo "comum" que atravessa tudo, barato e rápido
        add("killing_ray", Element.ARCANE, 2, "Raio Assassino", 0xF4F4FF,
                "A magia que matava pessoas agora é comum.",
                "beam", "accelerate", "pierce", "arcane_damage", "amplify", "efficient");
        // Lei Sagrada: cataclismo de luz enorme que só julga os inimigos
        add("holy_law", Element.LIGHT, 4, "Lei Sagrada", 0xFFF1B8,
                "Luz sagrada, julgue apenas meus inimigos: Lei Sagrada!",
                "cataclysm", "expand", "expand", "holy_ray", "amplify", "amplify");
    }

    private static void add(String id, Element element, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        Legends.register(id, new SpellRecipe(list, name, color, chant), element, minCircle);
        IDS.add(id);
    }

    /**
     * Depois que todos os módulos registraram seus glifos: confere cada lenda no parser (gramática e glifos
     * existentes). Uma lenda inválida não quebra nada — só fica avisada no log.
     */
    static void validate(FMLCommonSetupEvent event) {
        for (String id : IDS) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) continue;
            SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
            if (!r.ok()) {
                IsekaiMahou.LOGGER.warn("Magia Lendária '{}' inválida: {}", id, r.error().getString());
            }
        }
    }

    private LegendRecipes() {}
}
