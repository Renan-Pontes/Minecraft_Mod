package com.renanpontes.isekaimahou.feature.fusions_c;

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
 * Magias Lendárias das Misturas C (homenagens, nomes em português): receitas prontas com os glifos novos, registradas
 * em {@link Legends}. O nome mostrado vem de {@code legend.isekai_mahou.<id>}.
 */
public final class FcLegends {
    private static final List<String> IDS = new ArrayList<>();

    static void register() {
        // Zero Absoluto: caixões de gelo prendem os inimigos em volta e a onda de frio absoluto congela tudo
        add("absolute_zero", Element.ICE, 5, "Zero Absoluto", 0xA8E8FF,
                "Que o próprio calor esqueça como existir... Zero Absoluto.",
                "self", "ice_coffin", "absolute_zero", "expand", "amplify");
        // Funeral do Deserto: o caixão de areia persegue o alvo, prende, esmaga, e a onda de areia soterra o resto
        add("desert_funeral", Element.SAND, 5, "Caixão de Areia: Funeral do Deserto", 0xE3C77A,
                "Caixão de Areia... Funeral do Deserto!",
                "projectile", "homing", "sand_coffin", "amplify", "extend", "sand_tsunami", "expand");
        // Canhão Elétrico — Nível 5: o disparo mais forte, que atravessa a montanha (homenagem a "Railgun")
        add("level5_railgun", Element.LIGHTNING, 5, "Canhão Elétrico: Nível 5", 0x7FDBFF,
                "Uma moeda, um disparo. Canhão Elétrico!",
                "self", "railgun", "amplify", "amplify", "expand");
    }

    private static void add(String id, Element element, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        Legends.register(id, new SpellRecipe(list, name, color, chant), element, minCircle);
        IDS.add(id);
    }

    /** Depois que todos os módulos registraram os glifos: a receita tem que passar no parser (senão é bug). */
    static void validate(FMLCommonSetupEvent event) {
        for (String id : IDS) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) continue;
            SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
            if (!r.ok()) IsekaiMahou.LOGGER.error("Magia Lendária '{}' (fusions_c) inválida: {}", id, r.error().getString());
        }
    }

    private FcLegends() {}
}
