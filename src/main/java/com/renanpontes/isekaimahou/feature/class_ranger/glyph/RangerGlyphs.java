package com.renanpontes.isekaimahou.feature.class_ranger.glyph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerGlyphIds;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Glifos de ORIGEM do Ranger (10 + a forma G), as duas Lendas da classe e as magias prontas.
 * <ul>
 *   <li>Efeitos de flecha: Explosiva, em Corrente, Rede, Perfurante, Chuva de Flechas.</li>
 *   <li>Efeitos de caçada: Marca do Caçador, Chamado da Matilha, Chamado do Corcel.</li>
 *   <li>Aumentos: Flecha Guiada (teleguiada + rápida + crítica) e Saque Rápido (+2 flechas em leque, mais fracas).</li>
 *   <li>Forma G: Tempestade das Mil Penas.</li>
 * </ul>
 * Todos combinam com qualquer forma/elemento do Grimório (são de origem) e pagam o custo por cópia do núcleo.
 */
public final class RangerGlyphs {
    public static void register() {
        GlyphRegistry.register(new ArrowEffects.Explosive());
        GlyphRegistry.register(new ArrowEffects.Chain());
        GlyphRegistry.register(new ArrowEffects.Net());
        GlyphRegistry.register(new ArrowEffects.Piercing());
        GlyphRegistry.register(new ArrowEffects.Rain());
        GlyphRegistry.register(new HuntEffects.HuntersMark());
        GlyphRegistry.register(new HuntEffects.PackCall());
        GlyphRegistry.register(new HuntEffects.SteedCall());
        // Flecha Guiada: teleguiada, mais rápida e com chance de crítico (o tiro "que não erra")
        GlyphRegistry.register(new AugmentGlyph(RangerGlyphIds.SEEKING, 10, s -> {
            s.homing = true;
            s.speed += 1;
            s.crit = true;
        }).origin(MagicPath.RANGER).tier(2));
        // Saque Rápido: +2 flechas em leque e mais velocidade, cada uma com menos poder (custo por cópia do núcleo)
        GlyphRegistry.register(new AugmentGlyph(RangerGlyphIds.QUICKDRAW, 9, s -> {
            s.split += 2;
            s.speed += 1;
            s.amplify -= 1;
        }).origin(MagicPath.RANGER).tier(4));
        GlyphRegistry.register(new ThousandFeathersForm());
        // Pacote de técnicas de anime: Flecha Sagrada, Disparo Triplo Élfico, Correntes Conjuradas, Linha de Bala, Deusa Loba
        GlyphRegistry.register(new AnimeArrowEffects.SacredArrow());
        GlyphRegistry.register(new AnimeArcheryForms.TripleShot());
        GlyphRegistry.register(new AnimeArrowEffects.ConjuredChains());
        GlyphRegistry.register(new AnimeArcheryForms.BulletLine());
        GlyphRegistry.register(new AnimeArrowEffects.WolfGod());

        // Lendas da classe
        Legends.register(RangerGlyphIds.LEGEND_LAMENT, recipe("Lamento da Caçadora", 0x9AF07A,
                "Deusa da caça, aceite meu lamento: que mil penas caiam como estrelas!",
                RangerGlyphIds.THOUSAND_FEATHERS, RangerGlyphIds.MARK, RangerGlyphIds.EXPLOSIVE, "amplify"), Element.WIND, 8);
        Legends.register(RangerGlyphIds.LEGEND_HECATE, recipe("Disparo Hécate", 0x7FE8FF,
                "Respire. Mire. Nada escapa ao olho do caçador.",
                "arrow", RangerGlyphIds.SEEKING, RangerGlyphIds.PIERCING, "amplify", "amplify", RangerGlyphIds.EXPLOSIVE), Element.WIND, 6);
        // Lendas do pacote de técnicas de anime
        Legends.register(RangerGlyphIds.LEGEND_BULLET_LINE, recipe("Hécate II: Linha de Bala", 0xFF3B3B,
                "A linha vermelha já tocou você. Agora é só puxar o gatilho.",
                RangerGlyphIds.BULLET_LINE, "pierce", RangerGlyphIds.PIERCING, "amplify", "amplify", "critical"), Element.WIND, 6);
        Legends.register(RangerGlyphIds.LEGEND_SACRED, recipe("Flecha Sagrada: Purificação", 0xFF9AD8,
                "Que esta flecha atravesse a escuridão e purifique tudo o que tocar!",
                "arrow", RangerGlyphIds.SEEKING, RangerGlyphIds.SACRED, "expand", "amplify"), Element.LIGHT, 4);
        Legends.register(RangerGlyphIds.LEGEND_WOLF_GOD, recipe("Fúria da Deusa Loba", 0xF4F4FF,
                "Mãe loba da floresta antiga, desperte — e devore quem ousou entrar!",
                "self", RangerGlyphIds.WOLF_GOD, RangerGlyphIds.PACK, "amplify"), Element.EARTH, 8);
    }

    // =====================================================================================
    //  Magias prontas (loadout 1: Z, X, C) — nomes são texto da receita (o jogador pode renomear)
    // =====================================================================================

    public static final List<SpellRecipe> STARTERS = List.of(
            recipe("Flecha Explosiva", 0xFF8A3B, "", "arrow", RangerGlyphIds.EXPLOSIVE),
            recipe("Marca do Caçador", 0xE04A3B, "", "arrow", RangerGlyphIds.MARK),
            recipe("Chamado da Matilha", 0x9FE07A, "", "self", RangerGlyphIds.PACK));

    private static final Map<String, String> NAMES_EN = Map.of(
            "Flecha Explosiva", "Explosive Arrow",
            "Marca do Caçador", "Hunter's Mark",
            "Chamado da Matilha", "Call of the Pack");

    /** Magias prontas no idioma do jogador ("pt_br", "en_us"...). */
    public static List<SpellRecipe> starters(String language) {
        if (language == null || language.startsWith("pt")) return STARTERS;
        List<SpellRecipe> out = new ArrayList<>();
        for (SpellRecipe r : STARTERS) out.add(r.withName(NAMES_EN.getOrDefault(r.name(), r.name())));
        return out;
    }

    private static SpellRecipe recipe(String name, int rgb, String chant, String... glyphs) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (String g : glyphs) ids.add(IsekaiMahou.id(g));
        return new SpellRecipe(ids, name, rgb, chant);
    }

    /** Depois que todos os módulos registraram: magias prontas e lendas têm que passar no parser. */
    public static void validate(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (SpellRecipe r : STARTERS) check(r.name(), r);
            for (String id : List.of(RangerGlyphIds.LEGEND_LAMENT, RangerGlyphIds.LEGEND_HECATE, RangerGlyphIds.LEGEND_BULLET_LINE,
                    RangerGlyphIds.LEGEND_SACRED, RangerGlyphIds.LEGEND_WOLF_GOD)) {
                Legends.Legend legend = Legends.get(id);
                if (legend != null) check(id, legend.recipe());
            }
        });
    }

    private static void check(String name, SpellRecipe r) {
        SpellParser.Result res = SpellParser.parse(r, Element.ARCANE, null);
        if (!res.ok()) IsekaiMahou.LOGGER.warn("Magia '{}' (class_ranger) inválida: {}", name, res.error().getString());
    }

    private RangerGlyphs() {}
}
