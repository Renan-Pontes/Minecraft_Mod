package com.renanpontes.isekaimahou.feature.class_gunslinger;

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
 * As Magias Lendárias do Pistoleiro (homenagens com nomes próprios), entregues como pergaminho nos ranks III e V
 * do Caminho do Gatilho e listadas na galeria do Grimório:
 * <ul>
 *   <li><b>Cruz do Pregador</b> (homenagem à cruz-metralhadora do pregador errante de um faroeste espacial): rajada
 *       dupla de balas explosivas e incendiárias.</li>
 *   <li><b>Chacal de Prata</b> (homenagem à pistola gigante do vampiro caçador de monstros): uma bala perfurante
 *       amplificada duas vezes que marca e explode tudo em linha.</li>
 * </ul>
 */
public final class GsLegends {
    public static final String PREACHERS_CROSS = "preachers_cross";
    public static final String SILVER_JACKAL = "silver_jackal";
    /** Rajada Estelar (homenagem ao combo de 16 golpes do espadachim negro): a forma G com crítico e dano arcano. */
    public static final String STARBURST = "gunslinger_starburst";
    /** Execução da Estrela Negra (homenagem ao atirador da caveira): a bala negra que marca e executa. */
    public static final String BLACK_STAR = "gunslinger_black_star";
    /** Tiro da Deusa da Lua Gelada (homenagem à atiradora de gelo): fuzil antimaterial que perfura, congela e explode. */
    public static final String MOON_GODDESS = "gunslinger_moon_goddess";
    private static final List<String> IDS = new ArrayList<>();

    public static void register() {
        add(PREACHERS_CROSS, Element.FIRE, 4, "Cruz do Pregador", 0xFF8A3A,
                "Que os pecadores ouçam o sermão... da cruz!",
                "gun_burst", "split", "gun_explosive", "gun_incendiary");
        add(SILVER_JACKAL, Element.ARCANE, 6, "Chacal de Prata", 0xE6EEF8,
                "Prata benta, chumbo de mana: o chacal não erra.",
                "gun_piercing", "amplify", "gun_mark", "gun_explosive", "amplify");
        add(STARBURST, Element.ARCANE, 8, "Rajada Estelar", 0x8FD8FF,
                "Mais rápido... mais rápido! Dezesseis golpes de luz!",
                "stellar_burst", "critical", "arcane_damage", "amplify");
        add(BLACK_STAR, Element.ARCANE, 7, "Execução da Estrela Negra", 0xFF1030,
                "Em nome da estrela negra... eu sou a sua morte.",
                "black_star", "gun_mark", "arcane_damage", "amplify");
        add(MOON_GODDESS, Element.WATER, 6, "Tiro da Deusa da Lua Gelada", 0x9AF0FF,
                "Respira... mira... a deusa da lua não erra.",
                "anti_materiel", "pierce", "freeze", "gun_explosive", "amplify");
    }

    private static void add(String id, Element element, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        Legends.register(id, new SpellRecipe(list, name, color, chant), element, minCircle);
        IDS.add(id);
    }

    /** Depois de todos os glifos registrados: cada lenda tem que passar no parser. */
    static void validate(FMLCommonSetupEvent event) {
        for (String id : IDS) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) continue;
            SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
            if (!r.ok()) IsekaiMahou.LOGGER.warn("Magia Lendária '{}' (class_gunslinger) inválida: {}", id, r.error().getString());
        }
    }

    private GsLegends() {}
}
