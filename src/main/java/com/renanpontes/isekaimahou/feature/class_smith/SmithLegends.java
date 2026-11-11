package com.renanpontes.isekaimahou.feature.class_smith;

import java.util.Arrays;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.feature.class_smith.glyph.SmithGlyphs;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

/**
 * As duas Magias Lendárias do Ferreiro (homenagens com nomes próprios):
 * <ul>
 *   <li><b>Martelo do Deus da Forja</b> (homenagem ao deus ferreiro dos mitos): a Forja Estelar com metal fundido,
 *   amplificada e com runa — três marteladas de aço estelar que derretem e marcam tudo. Fogo, Círculo 5.</li>
 *   <li><b>Fantasmas Partidos</b> (homenagem às armas falsas que explodem do arqueiro de lâminas): projéteis que chamam
 *   armas conjuradas e cada uma deixa uma runa que estoura. Arcano, Círculo 4.</li>
 * </ul>
 * Entregues pelo rank V das Provas da Forja (a "Campo de Lâminas Infinitas" do legends_a vem no rank IV).
 * <p>Pacote de técnicas de anime (também no rank V):
 * <ul>
 *   <li><b>Eu Sou o Osso da Minha Espada</b>: o cântico inteiro e o Mundo das Lâminas com armas conjuradas e metal
 *   fundido. Fogo, Círculo 8.</li>
 *   <li><b>Caladbolg II — Fantasma Partido</b>: a espada-flecha amplificada duas vezes. Arcano, Círculo 6.</li>
 *   <li><b>Asas do Grou: Três Golpes</b>: três pares de Kanshō e Bakuya em Sobrefio, que cortam na ida e na volta.
 *   Arcano, Círculo 5.</li>
 * </ul>
 */
public final class SmithLegends {
    public static final String STARFORGE_GOD = "starforge_god";
    public static final String BROKEN_PHANTASM = "broken_phantasm";
    public static final String BONE_OF_MY_SWORD = "smith_bone_of_my_sword";
    public static final String CALADBOLG = "smith_caladbolg_phantasm";
    public static final String CRANE_WINGS = "smith_crane_wings";
    /** As lendas do pacote de técnicas (entregues no rank V e no login de quem já era rank V). */
    public static final List<String> TECH_LEGENDS = List.of(BONE_OF_MY_SWORD, CALADBOLG, CRANE_WINGS);

    public static void register() {
        Legends.register(STARFORGE_GOD, recipe("Martelo do Deus da Forja", 0x8FA8FF,
                "Pela bigorna das estrelas e pelo fogo que nunca esfria: desce, martelo do deus ferreiro!",
                SmithGlyphs.STARFORGE, SmithGlyphs.MOLTEN, "amplify", SmithGlyphs.RUNE), Element.FIRE, 5);
        Legends.register(BROKEN_PHANTASM, recipe("Fantasmas Partidos", 0xE8C46A,
                "Traço ligado. Que cada lâmina falsa se parta como uma estrela!",
                "projectile", "split", "split", "conjured_weapon", SmithGlyphs.RUNE, "amplify"), Element.ARCANE, 4);
        Legends.register(BONE_OF_MY_SWORD, recipe("Eu Sou o Osso da Minha Espada", 0xE8A04A,
                "Eu sou o osso da minha espada. Brasa corre onde havia sangue, aço sustenta onde havia carne. Mil lâminas "
                        + "passaram pela minha bigorna e nenhuma voltou vencida. Sozinho no alto da colina de espadas, sigo "
                        + "forjando. Então que se abra — Mundo das Lâminas!",
                SmithGlyphs.BLADE_WORLD, "conjured_weapon", "amplify", SmithGlyphs.MOLTEN), Element.FIRE, 8);
        Legends.register(CALADBOLG, recipe("Caladbolg II — Fantasma Partido", 0xC9A8FF,
                "Eu sou o osso da minha espada... Torce, lâmina; voa, flecha; parte-te, fantasma — Caladbolg!",
                SmithGlyphs.CALADBOLG, "arcane_damage", "amplify", "amplify"), Element.ARCANE, 6);
        Legends.register(CRANE_WINGS, recipe("Asas do Grou: Três Golpes", 0xF2F4FF,
                "Força que desmonta montanhas, espada que não para em água... as gêmeas se chamam: asas do grou!",
                SmithGlyphs.TWIN_FALCHIONS, "invert", "split", "split", "arcane_damage", "amplify"), Element.ARCANE, 5);
    }

    /** Confere as lendas com o parser depois que todos os módulos registraram os glifos (o log diz qual quebrou). */
    public static void validate() {
        for (String id : List.of(STARFORGE_GOD, BROKEN_PHANTASM, BONE_OF_MY_SWORD, CALADBOLG, CRANE_WINGS)) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) continue;
            SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
            if (!r.ok()) IsekaiMahou.LOGGER.error("Magia Lendária '{}' (Ferreiro) inválida: {}", id, r.error().getString());
        }
    }

    private static SpellRecipe recipe(String name, int color, String chant, String... glyphs) {
        return new SpellRecipe(Arrays.stream(glyphs).map(IsekaiMahou::id).toList(), name, color, chant);
    }

    private SmithLegends() {}
}
