package com.renanpontes.isekaimahou.feature.class_shinigami;

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
 * As Magias Lendárias da classe Shinigami (homenagens com nomes próprios). Os pergaminhos das 2 primeiras saem nos ranks
 * IV e V do Caminho da Zanpakutō; as 3 das técnicas avançadas (Getsuga Tenshō, Kurohitsugi, Mugetsu) saem quando a
 * técnica é aprendida ({@link ShinigamiTechniques}); o Grimório também as mostra na galeria. Validadas no parser depois que todos os glifos existem.
 */
public final class ShinigamiLegends {
    /** "Presa da Lua Negra" (homenagem Getsuga Tenshō): três meias-luas rápidas e brutais. */
    public static final String BLACK_MOON = "shinigami_black_moon";
    /** "Kidō Duplo: Grade e Canhão" (homenagem ao encantamento duplo): o Raio Branco prende e o Canhão Rubro explode. */
    public static final String DOUBLE_KIDO = "shinigami_double_kido";
    /** "Getsuga Tenshō" (técnica avançada): o crescente carregado e ampliado que corta tudo no caminho. */
    public static final String GETSUGA_TENSHO = "shinigami_getsuga_tensho";
    /** "Hadō nº 90: Kurohitsugi": o caixão negro com lanças reforçadas e mais tempo de prisão. */
    public static final String KUROHITSUGI = "shinigami_kurohitsugi";
    /** "Mugetsu": a escuridão final com o corte reforçado (forma G de último recurso). */
    public static final String MUGETSU = "shinigami_mugetsu";

    private static final List<String> IDS = new ArrayList<>();

    static void register() {
        add(BLACK_MOON, Element.SHADOW, 5, "Presa da Lua Negra", 0x2A0A10, "Devore o céu, lâmina sem lua... Presa da Lua Negra!",
                Shinigami.MOON_FANG, "split", "split", "accelerate", "arcane_damage", "amplify", "amplify");
        add(DOUBLE_KIDO, Element.FIRE, 6, "Kidō Duplo: Grade e Canhão", 0xFF3A22,
                "Seis grades de luz, prendam. Muralha de fogo, rubra e ardente... Canhão Rubro!",
                Shinigami.HADO_4, Shinigami.BAKUDO_61, "extend", "link_hit", Shinigami.HADO_31, "expand", "arcane_damage", "amplify", "amplify");
        add(GETSUGA_TENSHO, Element.SHADOW, 5, "Getsuga Tenshō", 0xDDF4FF, "Lâmina, beba o meu Reiatsu e rasgue a lua... Getsuga Tenshō!",
                Shinigami.GETSUGA, "charge", "expand", "arcane_damage", "amplify", "amplify");
        add(KUROHITSUGI, Element.SHADOW, 7, "Hadō nº 90: Kurohitsugi", 0x7A2AC8,
                "Treva que transborda do brasão, feche a tampa do mundo e esmague o que estiver dentro... Hadō nº 90: Kurohitsugi!",
                Shinigami.HADO_90, "extend", "expand", "arcane_damage", "amplify", "amplify");
        add(MUGETSU, Element.SHADOW, 9, "Mugetsu", 0x3A1450, "Eu me torno o próprio Getsuga. Sem lua, sem céu... Mugetsu.",
                Shinigami.MUGETSU, "arcane_damage", "amplify", "amplify");
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
            if (!r.ok()) IsekaiMahou.LOGGER.error("Magia Lendária '{}' (class_shinigami) inválida: {}", id, r.error().getString());
        }
    }

    private ShinigamiLegends() {}
}
