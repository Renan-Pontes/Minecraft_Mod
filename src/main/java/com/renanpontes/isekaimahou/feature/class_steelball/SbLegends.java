package com.renanpontes.isekaimahou.feature.class_steelball;

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
 * As 5 Magias Lendárias do Steel Ball (homenagens com nomes próprios). Nome mostrado: {@code legend.isekai_mahou.<id>};
 * a receita guarda nome/cântico em português para o Grimório. Validadas pelo parser no carregamento comum.
 */
public final class SbLegends {
    /** Esfera do Cavaleiro Dourado: a bola dourada lançada a galope que perfura e faz girar (Círculo 5). */
    public static final String GOLDEN_RIDER = "steelball_golden_rider";
    /** Rotação Infinita — Ato 4: a unha dourada que prende o alvo num giro sem fim e trava os músculos (Círculo 8). */
    public static final String INFINITE_ACT = "steelball_infinite_act";
    /** Ato 2 — O Buraco que Persegue: a unha dourada que deixa no alvo uma espiral que o caça (Círculo 5). */
    public static final String GOLDEN_CHASE = "steelball_golden_chase";
    /** Ato 3 — Travessia do Buraco de Minhoca: atira em si mesmo, some no buraco e sai girando no meio dos inimigos (Círculo 6). */
    public static final String WORMHOLE = "steelball_wormhole";
    /** Esfera Quebrantadora (homenagem ao Ball Breaker): a bola dourada que desgasta o alvo até o fim (Círculo 8). */
    public static final String BALL_BREAKER = "steelball_ball_breaker";

    /** Love Train — A Luz do Coração: a cortina de luz do Santo inteiro; a desgraça gira e trava quem a recebe (Círculo 8). */
    public static final String LOVE_TRAIN = "steelball_love_train_heart";

    private static final List<String> IDS = new ArrayList<>();

    public static void register() {
        add(GOLDEN_RIDER, Element.EARTH, 5, "Esfera do Cavaleiro Dourado", 0xFFD24A,
                "A proporção perfeita... o cavalo, a bola e eu numa só rotação!",
                "spin_ball", "golden_rectangle", "gallop_spin", "rotation_shock", "amplify", "spin_drill");
        add(INFINITE_ACT, Element.EARTH, 8, "Rotação Infinita — Ato 4", 0xF2C14A,
                "Ato 4! Você nunca mais vai chegar ao fim desta rotação.",
                "infinite_rotation", "rotation_shock", "amplify", "muscle_lock");
        add(GOLDEN_CHASE, Element.EARTH, 5, "Ato 2 — O Buraco que Persegue", 0xFFC23A,
                "Tusk, Ato 2! Pode correr: o buraco já está em você.",
                "tusk_act2", "golden_rectangle", "rotation_shock", "muscle_lock");
        add(WORMHOLE, Element.EARTH, 6, "Ato 3 — Travessia do Buraco de Minhoca", 0xB45CFF,
                "Ato 3... eu atiro em mim mesmo e atravesso o espaço!",
                "tusk_act3", "rotation_shock", "spin_drill", "amplify");
        add(BALL_BREAKER, Element.EARTH, 8, "Esfera Quebrantadora", 0xFFB43A,
                "A rotação dourada perfeita... ela desgasta até o tempo do seu corpo.",
                "spin_ball", "golden_rectangle", "ball_breaker", "amplify");
        add(LOVE_TRAIN, Element.EARTH, 8, "Love Train — A Luz do Coração", 0xFFF0B8,
                "O Santo está inteiro em mim... toda a desgraça vai para outro lugar. LOVE TRAIN!",
                "steelball_love_train", "rotation_shock", "amplify", "muscle_lock");
    }

    private static void add(String id, Element element, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        Legends.register(id, new SpellRecipe(list, name, color, chant), element, minCircle);
        IDS.add(id);
    }

    /** Depois que todos os módulos registraram seus glifos: cada lenda tem que passar no parser. */
    static void validate(FMLCommonSetupEvent event) {
        for (String id : IDS) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) continue;
            SpellParser.Result r = SpellParser.parse(legend.recipe(), legend.element(), null);
            if (!r.ok()) IsekaiMahou.LOGGER.warn("Magia Lendária '{}' (class_steelball) inválida: {}", id, r.error().getString());
        }
    }

    private SbLegends() {}
}
