package com.renanpontes.isekaimahou.feature.class_rogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.AssassinClawEffect;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.BackstabEffect;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.BuffTheftEffect;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.DrainTouchEffect;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.GodspeedEffect;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.KnifeFanForm;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.LurkEffect;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.ManaSiphonEffect;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.OneCutCurseEffect;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.PhantomHeistForm;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.PickpocketEffect;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.RhythmEchoForm;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.ShadowStepForm;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.SmokeVeilEffect;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.SpellTheftEffect;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.StolenPageForm;
import com.renanpontes.isekaimahou.feature.class_rogue.glyph.VitalMarkEffect;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Glifos de origem do Ladino (todos {@code origin(MagicPath.ROGUE)}: exclusivos, ensináveis a outras classes só por
 * Lição). 10 + a forma G:
 * <ul>
 *   <li>Formas: {@code rogue_shadow_step} Passo Sombrio, {@code rogue_knife_fan} Leque de Facas, {@code rogue_stolen_page}
 *   Página Roubada; G: {@code rogue_phantom_heist} Golpe do Ladrão Fantasma;</li>
 *   <li>Efeitos: {@code rogue_backstab} Apunhalar, {@code rogue_pickpocket} Mão Leve, {@code rogue_mana_siphon} Sifão,
 *   {@code rogue_buff_theft} Roubar Bênção, {@code rogue_spell_theft} Roubar Magia, {@code rogue_vital_mark} Ponto
 *   Vital, {@code rogue_smoke_veil} Véu de Fumaça.</li>
 * </ul>
 * Pacote de técnicas de anime (homenagens): Espreitar e Toque Dreno (rank I), Garra Assassina (II), Ritmo de Eco
 * (III), Velocidade Divina (IV) e Lâmina Maldita: Um Corte, Uma Morte (V), com 3 lendas novas.
 * Magias iniciais (loadout 1) e as Lendas da classe ficam aqui também; tudo é validado no parser no setup.
 */
public final class RogueGlyphs {
    public static final ResourceLocation SHADOW_STEP = IsekaiMahou.id("rogue_shadow_step");
    public static final ResourceLocation KNIFE_FAN = IsekaiMahou.id("rogue_knife_fan");
    public static final ResourceLocation STOLEN_PAGE = IsekaiMahou.id("rogue_stolen_page");
    public static final ResourceLocation PHANTOM_HEIST = IsekaiMahou.id("rogue_phantom_heist");
    public static final ResourceLocation BACKSTAB = IsekaiMahou.id("rogue_backstab");
    public static final ResourceLocation PICKPOCKET = IsekaiMahou.id("rogue_pickpocket");
    public static final ResourceLocation MANA_SIPHON = IsekaiMahou.id("rogue_mana_siphon");
    public static final ResourceLocation BUFF_THEFT = IsekaiMahou.id("rogue_buff_theft");
    public static final ResourceLocation SPELL_THEFT = IsekaiMahou.id("rogue_spell_theft");
    public static final ResourceLocation VITAL_MARK = IsekaiMahou.id("rogue_vital_mark");
    public static final ResourceLocation SMOKE_VEIL = IsekaiMahou.id("rogue_smoke_veil");
    // pacote de técnicas de anime
    public static final ResourceLocation LURK = IsekaiMahou.id("rogue_lurk");
    public static final ResourceLocation DRAIN_TOUCH = IsekaiMahou.id("rogue_drain_touch");
    public static final ResourceLocation ASSASSIN_CLAW = IsekaiMahou.id("rogue_assassin_claw");
    public static final ResourceLocation RHYTHM_ECHO = IsekaiMahou.id("rogue_rhythm_echo");
    public static final ResourceLocation GODSPEED = IsekaiMahou.id("rogue_godspeed");
    public static final ResourceLocation ONE_CUT = IsekaiMahou.id("rogue_one_cut");

    /** Ensinados ao escolher a classe (os outros vêm dos ranks do Livro-Razão). */
    public static final List<ResourceLocation> STARTER = List.of(BACKSTAB, SHADOW_STEP, PICKPOCKET, SMOKE_VEIL);

    public static final String LEGEND_HEIST = "rogue_heist_of_the_century";
    public static final String LEGEND_BANDIT = "rogue_bandits_secret";
    public static final String LEGEND_GODSPEED = "rogue_godspeed_thunder";
    public static final String LEGEND_ONE_CUT = "rogue_one_cut_one_kill";
    public static final String LEGEND_RHYTHM = "rogue_thousand_shadows";
    /** As lendas do pacote de técnicas (rank V; login de quem já era rank V). */
    public static final List<String> TECH_LEGENDS = List.of(LEGEND_GODSPEED, LEGEND_ONE_CUT, LEGEND_RHYTHM);

    public static void register() {
        GlyphRegistry.register(new ShadowStepForm());
        GlyphRegistry.register(new KnifeFanForm());
        GlyphRegistry.register(new StolenPageForm());
        GlyphRegistry.register(new PhantomHeistForm());
        GlyphRegistry.register(new BackstabEffect());
        GlyphRegistry.register(new PickpocketEffect());
        GlyphRegistry.register(new ManaSiphonEffect());
        GlyphRegistry.register(new BuffTheftEffect());
        GlyphRegistry.register(new SpellTheftEffect());
        GlyphRegistry.register(new VitalMarkEffect());
        GlyphRegistry.register(new SmokeVeilEffect());
        GlyphRegistry.register(new LurkEffect());
        GlyphRegistry.register(new DrainTouchEffect());
        GlyphRegistry.register(new AssassinClawEffect());
        GlyphRegistry.register(new RhythmEchoForm());
        GlyphRegistry.register(new GodspeedEffect());
        GlyphRegistry.register(new OneCutCurseEffect());

        // Lenda 1 — Roubo do Século (homenagem aos ladrões fantasmas do cartão de visita): o Golpe do Ladrão Fantasma
        // amplificado, apunhalando e esvaziando os bolsos de todos.
        Legends.register(LEGEND_HEIST, recipe("Roubo do Século", 0xF2ECFF,
                "Esta noite, à meia-noite, levarei o que lhe é mais precioso. Assinado: o Ladrão Fantasma.",
                "rogue_phantom_heist", "rogue_backstab", "amplify", "rogue_pickpocket"), Element.SHADOW, 7);
        // Lenda 2 — Segredo do Saqueador (homenagem ao livro que rouba habilidades): um leque de facas que copia a
        // magia e drena o mana de cada inimigo atingido.
        Legends.register(LEGEND_BANDIT, recipe("Segredo do Saqueador", 0x8E5BD6,
                "Tudo o que é seu já está escrito nas minhas páginas.",
                "rogue_knife_fan", "split", "rogue_spell_theft", "rogue_mana_siphon", "amplify"), Element.WIND, 5);
        // Lenda 3 — Velocidade Divina: Trovão Ligeiro (homenagem ao corpo elétrico do jovem assassino): some nas costas
        // do inimigo, desce o raio e arranca o coração com a garra.
        Legends.register(LEGEND_GODSPEED, recipe("Velocidade Divina: Trovão Ligeiro", 0x9FE8FF,
                "Minha aura vira raio, meu corpo vira trovão. Você nem vai ver o meu passo.",
                "rogue_shadow_step", "rogue_godspeed", "amplify", "rogue_assassin_claw"), Element.WIND, 6);
        // Lenda 4 — Um Corte, Uma Morte (homenagem à espada imperial amaldiçoada): o passo de sombra leva a lâmina maldita.
        Legends.register(LEGEND_ONE_CUT, recipe("Um Corte, Uma Morte", 0xC0182E,
                "Eliminar.",
                "rogue_shadow_step", "rogue_one_cut", "amplify", "rogue_backstab"), Element.SHADOW, 8);
        // Lenda 5 — Ritmo de Eco: Dança das Mil Sombras (homenagem aos passos da família de assassinos): imagens que
        // rasgam com garras e drenam a vida.
        Legends.register(LEGEND_RHYTHM, recipe("Ritmo de Eco: Dança das Mil Sombras", 0xB06BFF,
                "Um passo lento, um passo rápido... qual de nós é o verdadeiro?",
                "rogue_rhythm_echo", "split", "split", "rogue_assassin_claw", "rogue_drain_touch"), Element.SHADOW, 6);
    }

    // =====================================================================================
    //  Magias iniciais (nomes em PT; inglês fora do português)
    // =====================================================================================
    public static final SpellRecipe START_STAB = recipe("Apunhalar", 0xB06BFF, "", "strike", "rogue_backstab");
    public static final SpellRecipe START_STEP = recipe("Passo Sombrio", 0x5A3A8A, "", "rogue_shadow_step", "rogue_backstab");
    public static final SpellRecipe START_HAND = recipe("Mão Leve", 0xFFD86B, "", "touch", "rogue_pickpocket");
    public static final SpellRecipe START_VEIL = recipe("Véu de Fumaça", 0x6A6A80, "", "self", "rogue_smoke_veil");

    private static final Map<String, String> NAMES_EN = Map.of(
            "Apunhalar", "Backstab",
            "Passo Sombrio", "Shadow Step",
            "Mão Leve", "Light Fingers",
            "Véu de Fumaça", "Smoke Veil");

    /** Magias do loadout 1 (Z, X, C) no idioma do jogador. */
    public static List<SpellRecipe> starters(String language) {
        List<SpellRecipe> base = List.of(START_STAB, START_STEP, START_HAND);
        if (language == null || language.startsWith("pt")) return base;
        return base.stream().map(RogueGlyphs::english).toList();
    }

    public static SpellRecipe english(SpellRecipe r) {
        String en = NAMES_EN.get(r.name());
        return en == null ? r : r.withName(en);
    }

    private static SpellRecipe recipe(String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        return new SpellRecipe(list, name, color, chant);
    }

    /** Depois que todos os módulos registraram: magias iniciais e lendas passam no parser. */
    static void validate(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (SpellRecipe r : List.of(START_STAB, START_STEP, START_HAND, START_VEIL)) check(r.name(), r);
            for (String id : List.of(LEGEND_HEIST, LEGEND_BANDIT, LEGEND_GODSPEED, LEGEND_ONE_CUT, LEGEND_RHYTHM)) {
                Legends.Legend l = Legends.get(id);
                if (l != null) check(id, l.recipe());
            }
        });
    }

    private static void check(String name, SpellRecipe r) {
        SpellParser.Result res = SpellParser.parse(r, null, null);
        if (!res.ok()) IsekaiMahou.LOGGER.warn("Magia '{}' (class_rogue) inválida: {}", name, res.error().getString());
    }

    private RogueGlyphs() {}
}
