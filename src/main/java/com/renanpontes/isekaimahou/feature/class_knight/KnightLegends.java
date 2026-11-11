package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.feature.class_knight.glyph.KnightGlyphs;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;

/**
 * As 2 Magias Lendárias do Cavaleiro Rúnico (validadas no SpellParser antes de registrar; a que falhar só avisa no log):
 * <ul>
 *   <li><b>Excalibur — Luz Prometida</b>: a forma G com a Sentença do Juramento amplificada.</li>
 *   <li><b>Mil Lâminas do Juramento</b>: Chuva de Espadas dividida que grava Marcas Rúnicas (as marcas se rompem em
 *   cadeia) e encerra com um Raio de Espada a partir do último impacto.</li>
 *   <li>Pacote de técnicas de anime (Juramento V): <b>Avalon — A Utopia Distante</b> (Avalon estendido e amplificado em
 *   você e no grupo), <b>Balmung — Crepúsculo do Dragão</b> (leque gigante matador de dragões) e <b>Corte Negro Anti-Magia</b>
 *   (túnel de vento que anula a magia de tudo que atravessa).</li>
 * </ul>
 */
public final class KnightLegends {
    public static final String EXCALIBUR = "knight_excalibur";
    public static final String THOUSAND = "knight_thousand_blades";
    public static final String AVALON_UTOPIA = "knight_avalon_utopia";
    public static final String BALMUNG_TWILIGHT = "knight_balmung_twilight";
    public static final String BLACK_DIVIDER = "knight_black_divider";
    /** As lendas do pacote de técnicas (Juramento V; login de quem já tinha o V). */
    public static final List<String> TECH_LEGENDS = List.of(AVALON_UTOPIA, BALMUNG_TWILIGHT, BLACK_DIVIDER);

    static void register() {
        add(EXCALIBUR, Element.LIGHT, 7, "Excalibur", 0xFFF4C8,
                "Luz que o lago guardou, corte o destino de ponta a ponta... EXCALIBUR!",
                KnightGlyphs.EXCALIBUR, "expand", KnightGlyphs.JUDGMENT, "amplify", "amplify");
        add(THOUSAND, Element.LIGHT, 5, "Mil Lâminas do Juramento", 0x8FD8FF,
                "Por cada juramento cumprido, uma lâmina cai do céu!",
                KnightGlyphs.SWORD_RAIN, "split", "split", "expand", KnightGlyphs.RUNE_MARK, "amplify",
                "link_end", KnightGlyphs.SWORD_BEAM, "split", "arcane_damage");
        add(AVALON_UTOPIA, Element.LIGHT, 8, "Avalon — A Utopia Distante", 0xFFE9A8,
                "Que a bainha perdida volte ao seu rei: longe de toda ferida, além de todo mal... AVALON!",
                "self", "expand", KnightGlyphs.AVALON, "extend", "amplify");
        add(BALMUNG_TWILIGHT, Element.LIGHT, 7, "Balmung — Crepúsculo do Dragão", 0xFFB15A,
                "Banhado no sangue do dragão, o crepúsculo cai sobre o céu... BALMUNG!",
                KnightGlyphs.BALMUNG, "expand", "arcane_damage", "amplify", "amplify");
        add(BLACK_DIVIDER, Element.ARCANE, 6, "Corte Negro Anti-Magia", 0x3E7A4A,
                "Sem mana, sem magia, sem desistir: esta lâmina corta qualquer feitiço!",
                KnightGlyphs.STRIKE_AIR, "expand", KnightGlyphs.ANTI_MAGIC, "amplify", "amplify");
    }

    private static void add(String id, Element el, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>();
        for (String g : glyphs) {
            ResourceLocation rl = IsekaiMahou.id(g);
            if (GlyphRegistry.get(rl) == null) {
                IsekaiMahou.LOGGER.warn("Lenda '{}' (class_knight): glifo {} não existe; lenda não registrada.", id, rl);
                return;
            }
            list.add(rl);
        }
        SpellRecipe recipe = new SpellRecipe(list, name, color, chant);
        SpellParser.Result r = SpellParser.parse(recipe, null, null);
        if (!r.ok()) {
            IsekaiMahou.LOGGER.warn("Lenda '{}' (class_knight) não validou: {}", id, r);
            return;
        }
        Legends.register(id, recipe, el, minCircle);
    }

    private KnightLegends() {}
}
