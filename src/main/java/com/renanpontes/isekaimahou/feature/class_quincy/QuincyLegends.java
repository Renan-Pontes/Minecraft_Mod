package com.renanpontes.isekaimahou.feature.class_quincy;

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
 * As Magias Lendárias da classe Quincy (homenagens com nomes próprios). Os pergaminhos das 2 primeiras saem nos ranks
 * IV e V do Auswählen; as 3 das técnicas avançadas (Heilig Bogen, Sankt Zwinger, Letzt Stil) saem quando a técnica é
 * aprendida ({@link QuincyTechniques}); o Grimório também as mostra na galeria. Validadas no parser depois que todos os glifos existem.
 */
public final class QuincyLegends {
    /** "Tempestade Sagrada": o leque teleguiado de Heilig Pfeil que drena quem atinge. */
    public static final String STORM = "quincy_heilig_storm";
    /** "Julgamento da Luz": Vollständig com a Letra e a Devolução em cada flecha da auréola. */
    public static final String JUDGEMENT = "quincy_licht_judgement";
    /** "Heilig Bogen" (técnica avançada): o Arco Sagrado com mais flechas-lança, ampliado. */
    public static final String BOGEN = "quincy_heilig_bogen_legend";
    /** "Sankt Zwinger": a Gaiola Sagrada maior, que drena o Reishi de quem está preso. */
    public static final String ZWINGER = "quincy_sankt_zwinger_legend";
    /** "Quincy: Letzt Stil": a forma final com a Letra em cada flecha. */
    public static final String LETZT = "quincy_letzt_stil_legend";

    private static final List<String> IDS = new ArrayList<>();

    static void register() {
        add(STORM, Element.LIGHT, 4, "Tempestade Sagrada", 0x8FD0FF, "Reishi do mundo, curve-se ao meu arco... Heilig Pfeil!",
                Quincy.BOW, "split", "split", "homing", "accelerate", Quincy.DRAIN, "amplify");
        add(JUDGEMENT, Element.LIGHT, 6, "Julgamento da Luz", 0xE8F8FF, "Abram-se, asas sagradas. Que a luz julgue tudo... Vollständig!",
                Quincy.VOLLSTANDIG, Quincy.SCHRIFT, "amplify", Quincy.RELEASE);
        add(BOGEN, Element.LIGHT, 5, "Heilig Bogen", 0xBFE8FF, "Arco sagrado, abra-se do tamanho do céu... Heilig Bogen!",
                Quincy.HEILIG_BOGEN, "split", "arcane_damage", "amplify", "amplify");
        add(ZWINGER, Element.LIGHT, 6, "Sankt Zwinger", 0xE8F8FF, "Cinco pilares, uma estrela, uma gaiola. Ninguém sai... Sankt Zwinger!",
                Quincy.SANKT_ZWINGER, "expand", "arcane_damage", "amplify", Quincy.DRAIN);
        add(LETZT, Element.LIGHT, 8, "Quincy: Letzt Stil", 0xFFF2C0, "Tudo o que resta do meu Reishi, numa última forma... Letzt Stil!",
                Quincy.LETZT_STIL, "arcane_damage", "amplify", Quincy.SCHRIFT);
    }

    private static void add(String id, Element element, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        Legends.register(id, new SpellRecipe(list, name, color, chant), element, minCircle);
        IDS.add(id);
    }

    static void validate(FMLCommonSetupEvent event) {
        for (String id : IDS) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) continue;
            SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
            if (!r.ok()) IsekaiMahou.LOGGER.error("Magia Lendária '{}' (class_quincy) inválida: {}", id, r.error().getString());
        }
    }

    private QuincyLegends() {}
}
