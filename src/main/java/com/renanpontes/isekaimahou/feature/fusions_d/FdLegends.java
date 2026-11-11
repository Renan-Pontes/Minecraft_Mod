package com.renanpontes.isekaimahou.feature.fusions_d;

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
 * Magias Lendárias das Misturas D (homenagens, nomes e visuais próprios). Validadas no parser no setup comum:
 * receita inválida aparece como ERRO no log (é bug).
 */
public final class FdLegends {
    private static final List<String> IDS = new ArrayList<>();

    public static void register() {
        // Esmagamento Planetário (homenagem ao Chibaku Tensei): o núcleo voa longe e ergue uma lua de rocha
        add("planetary_devastation", Element.GRAVITY, 6, "Esmagamento Planetário", 0x6B5BA8,
                "Que a terra se erga e devore o céu... Esmagamento Planetário!",
                "projectile", "accelerate", "planet_crush", "amplify", "amplify", "expand", "extend");
        // Horizonte de Eventos: um orbe lento que se torna um buraco negro colossal
        add("event_horizon", Element.ASTRAL, 6, "Horizonte de Eventos", 0x9DB4FF,
                "Estrelas, colapsem. Que nem a luz escape... Horizonte de Eventos.",
                "projectile", "decelerate", "black_hole", "amplify", "amplify", "expand", "extend");
        // Crepúsculo dos Deuses: o Ragnarök em escala total
        add("twilight_of_the_gods", Element.TWILIGHT, 6, "Crepúsculo dos Deuses", 0xC06AA0,
                "Luz e trevas, caiam juntas sobre o fim do mundo: Ragnarök!",
                "projectile", "ragnarok", "amplify", "amplify", "expand", "expand");
    }

    private static void add(String id, Element element, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        Legends.register(id, new SpellRecipe(list, name, color, chant), element, minCircle);
        IDS.add(id);
    }

    /** Depois que todos os módulos registraram seus glifos: toda lenda tem que passar no parser. */
    static void validate(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (String id : IDS) {
                Legends.Legend legend = Legends.get(id);
                if (legend == null) continue;
                SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
                if (!r.ok()) IsekaiMahou.LOGGER.error("Magia Lendária '{}' (fusions_d) inválida: {}", id, r.error().getString());
            }
        });
    }

    private FdLegends() {}
}
