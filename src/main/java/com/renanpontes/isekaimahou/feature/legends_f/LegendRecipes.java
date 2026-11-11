package com.renanpontes.isekaimahou.feature.legends_f;

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
 * Magias Lendárias F — as técnicas de anime das 5 classes mágicas, prontas para o Grimório (homenagens: Frieren,
 * A Jornada da Bruxa, A Bela Adormecida, Re:Zero, Chainsaw Man, Fausto, Princesa Mononoke, Naruto, Solo Leveling).
 * Nome mostrado: {@code legend.isekai_mahou.<id>}; nome/cântico da receita vão para o Grimório.
 */
public final class LegendRecipes {
    private static final List<String> IDS = new ArrayList<>();

    public static void register() {
        // ---------------- Mago ----------------
        add("frieren_suppression", Element.ARCANE, 3, "Supressão de Mana", 0x8FE6FF,
                "Esconda a chama. Deixe que subestimem você... e então veja tudo.",
                "self", "mage_conceal_mana", "mage_mana_sense", "expand");
        add("general_defensive_magic", Element.ARCANE, 4, "Magia Defensiva Geral", 0x8FE6FF, "",
                "self", "mage_general_barrier", "expand", "extend");
        // ---------------- Bruxa ----------------
        add("wandering_witch", Element.WIND, 2, "A Bruxa Viajante", 0xF0C8E8,
                "Uma bruxa de cabelos cinzentos, viajando ao sabor do vento...",
                "self", "witch_broom", "extend", "accelerate");
        add("sleeping_beauty", Element.SHADOW, 4, "Sono da Bela Adormecida", 0xF0A0D0,
                "Que a roca fure o dedo, e o sono dure cem anos.",
                "projectile", "split", "witch_sleep_curse", "extend");
        add("unseen_envy", Element.SHADOW, 5, "Mãos da Inveja", 0x8A4FC8, "Eu te amo... eu te amo... eu te amo...",
                "projectile", "witch_envy_hands", "amplify");
        // ---------------- Bruxo ----------------
        add("chainsaw_devil", Element.FIRE, 5, "Demônio da Motosserra", 0xFF7A1A, "Puxa a cordinha. VRUUUM!",
                "self", "warlock_chainsaw", "extend");
        add("bang", Element.SHADOW, 6, "Bang.", 0xD01818, "", "beam", "warlock_bang", "amplify");
        add("faustian_bargain", Element.FIRE, 4, "Pacto de Fausto", 0xC0182E,
                "Eu dou minha alma, e você me dá o mundo por um instante.",
                "self", "warlock_faust", "amplify");
        // ---------------- Druida ----------------
        add("forest_spirit", Element.EARTH, 7, "O Deus da Floresta", 0xBFF0C8,
                "Onde ele pisa, a vida brota... e onde ele quer, a vida acaba.",
                "projectile", "druid_forest_god", "expand");
        add("sage_art", Element.EARTH, 5, "Arte Sábia: Modo Sábio", 0xF08A24,
                "Fique quieto. Sinta a natureza. Torne-se parte dela.",
                "self", "druid_sage_mode", "extend");
        add("kodama_grove", Element.EARTH, 2, "Bosque dos Kodama", 0xE8F0E0, "",
                "self", "druid_kodama", "split", "split");
        // ---------------- Necromante ----------------
        add("arise", Element.SHADOW, 4, "Levante-se!", 0x6A3DD8, "", "self", "necro_arise", "expand");
        add("shadow_monarch", Element.SHADOW, 8, "Monarca das Sombras", 0x6A3DD8,
                "Eu sou o rei. Ergam-se, meus soldados, e marchem.",
                "necro_monarch", "extend", "necro_arise", "expand");
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
            if (!r.ok()) IsekaiMahou.LOGGER.warn("Magia Lendária '{}' (legends_f) inválida: {}", id, r.error().getString());
        }
    }

    private LegendRecipes() {}
}
