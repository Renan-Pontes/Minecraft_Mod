package com.renanpontes.isekaimahou.feature.legends_a;

import java.util.Arrays;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;

/**
 * As Magias Lendárias A, prontas (homenagens, não cópias). Nomes traduzidos em {@code legend.isekai_mahou.<id>};
 * o nome e o cântico da receita ficam em português (são o que o jogador vê no Grimório e ouve ao conjurar).
 */
public final class LegendRecipes {

    public static void register() {
        // Megumin: Queda + Explosão Colossal, gastando TODO o mana, colossal. Fogo, Círculo 4.
        Legends.register("explosion", recipe("Explosão!", LegendFx.CRIMSON,
                "Ó trevas mais escuras que a noite, carmesim mais rubro que o sangue: despertai! Que tudo vire cinzas... EXPLOSÃO!",
                "fall", "crater", "all_in", "colossal"), Element.FIRE, 4);

        // Gilgamesh: 8 portais dourados que disparam armas conjuradas. Arcano, Círculo 4.
        Legends.register("treasure_gate", recipe("Portão do Tesouro", LegendFx.GOLD,
                "Abri-vos, cofres do rei! Que mil tesouros julguem os indignos.",
                "arsenal_gate", "split", "split", "conjured_weapon", "amplify"), Element.ARCANE, 4);

        // Emiya: Domínio de espadas cravadas que se lançam, com armas caindo do céu. Terra, Círculo 6.
        // v1.0: lenda do FERREIRO (os glifos de lâmina são de origem dele; o rank IV das Provas da Forja entrega o
        // pergaminho) — cântico de forja e cor de aço em brasa.
        Legends.register("infinite_blades", recipe("Campo de Lâminas Infinitas", 0xE8A04A,
                "O aço é o meu corpo e o fogo, o meu sangue. Forjei mil lâminas e nenhuma conheceu a derrota. Desperta, forja sem fim — campo de lâminas infinitas!",
                "domain_expansion", "blade_field", "conjured_weapon", "law_sure_hit", "law_landscape"), Element.EARTH, 6);

        // Natsu: cone gigante de fogo que incendeia e explode. Fogo, Círculo 3.
        Legends.register("dragon_roar", recipe("Rugido do Dragão", 0xFF7A1A,
                "Fogo no estômago, fúria nos pulmões... RUGE, DRAGÃO!",
                "cone", "colossal", "ignite", "amplify", "explosion"), Element.FIRE, 3);
    }

    /**
     * Confere as receitas com o parser depois que TODOS os módulos registraram seus glifos (no início do servidor):
     * se algum glifo de outro módulo mudar de id/regra, o log diz exatamente qual lenda quebrou.
     */
    public static void validate() {
        for (String id : List.of("explosion", "treasure_gate", "infinite_blades", "dragon_roar")) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) continue;
            SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
            if (!r.ok()) {
                IsekaiMahou.LOGGER.error("Magia Lendária '{}' inválida: {}", id, r.error().getString());
            }
        }
    }

    private static SpellRecipe recipe(String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> ids = Arrays.stream(glyphs).map(IsekaiMahou::id).toList();
        return new SpellRecipe(ids, name, color, chant);
    }

    private LegendRecipes() {}
}
