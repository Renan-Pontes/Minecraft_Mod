package com.renanpontes.isekaimahou.feature.arcane_duel;

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
 * Magias Lendárias do duelo arcano (homenagens a Magic: The Gathering). Nome mostrado: {@code legend.isekai_mahou.<id>}.
 */
public final class DuelLegends {
    private static final List<String> IDS = new ArrayList<>();

    public static void register() {
        // Contramágica Perfeita: fica PREPARADA e, quando algo hostil entra na Pilha, sai na hora (respostas não têm tempo
        // de conjuração) um pulso de poder 2,1 — contraria até magias Supremas — que varre 16 blocos em volta
        add("perfect_counter", Element.ARCANE, 5, "Contramágica Perfeita", 0xD8C8FF,
                "Não. Em resposta, eu nego.",
                "in_response", "self", "counterspell", "amplify", "amplify", "charge", "expand", "expand");
        // Ira de Deus: o céu julga toda criatura em volta de quem conjura
        add("wrath_of_god", Element.LIGHT, 8, "Ira de Deus", 0xFFF1B8,
                "Que o céu julgue todas as criaturas. Nenhuma se regenera.",
                "self", "wrath", "expand", "amplify");
        // Passo no Tempo: um "turno extra" — recargas zeradas e 30% do mana de volta, na hora
        add("time_walk", Element.ARCANE, 7, "Passo no Tempo", 0x9FD8FF,
                "Volte, ponteiro. O turno ainda é meu.",
                "self", "time_walk");
    }

    private static void add(String id, Element element, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        Legends.register(id, new SpellRecipe(list, name, color, chant), element, minCircle);
        IDS.add(id);
    }

    /** Confere cada lenda no parser depois que todos os módulos registraram os glifos. Receita inválida = bug (log). */
    static void validate(FMLCommonSetupEvent event) {
        for (String id : IDS) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) continue;
            SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
            if (!r.ok()) IsekaiMahou.LOGGER.error("Magia Lendária '{}' (arcane_duel) inválida: {}", id, r.error().getString());
        }
    }

    private DuelLegends() {}
}
