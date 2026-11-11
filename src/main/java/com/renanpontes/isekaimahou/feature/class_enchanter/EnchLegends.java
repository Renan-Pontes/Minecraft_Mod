package com.renanpontes.isekaimahou.feature.class_enchanter;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.feature.class_enchanter.rite.EnchanterRanks.EnchLegendIds;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;

/**
 * Magias Lendárias do Encantador (homenagens com nomes próprios), montadas só com glifos de origem dele + aumentos do
 * núcleo. O Rito de Inscrição V entrega os pergaminhos; a galeria do Grimório mostra as três.
 * <ul>
 *   <li><b>Punho do Imperador: Quebra-Feitiço</b> — o próximo soco sela a magia do alvo (Silêncio e desencanto);</li>
 *   <li><b>Corpo Celestial: Restrição Absoluta</b> — a Restrição Celestial já em nível alto e longa;</li>
 *   <li><b>Coroa do Soberano Encantador</b> — o Santuário com Força, Celeridade, Égide e Vitalidade pulsando no grupo.</li>
 *   <li><b>Portão da Morte: Noite do Dragão</b> — abre 4 portões de uma vez (até o 8º no rank V) com Força;</li>
 *   <li><b>Kaioken ×10: Punho do Rei</b> — sobe o Kaioken três degraus de uma vez, com Sobrecarga;</li>
 *   <li><b>Quarta Marcha: Homem-Mola</b> — o corpo inflado com Égide e mais tempo.</li>
 * </ul>
 */
public final class EnchLegends {
    private static final List<String> IDS = new ArrayList<>();

    static void register() {
        add(EnchLegendIds.EMPEROR, 3, "Punho do Imperador: Quebra-Feitiço", 0xBFE8FF, "Magia? Isso não funciona comigo.",
                "strike", "anti_magic", "invert", "amplify");
        add(EnchLegendIds.CELESTIAL, 5, "Corpo Celestial: Restrição Absoluta", 0x9AA3AE, "Sem mana. Sem limites.",
                "self", "heavenly_restriction", "amplify", "amplify", "extend");
        add(EnchLegendIds.SOVEREIGN, 8, "Coroa do Soberano Encantador", 0xFFD27A, "Que todos sob esta coroa se ergam!",
                "enchant_sovereign", "expand", "enchant_might", "amplify", "enchant_haste", "enchant_aegis", "enchant_vitality");
        // pacote de técnicas de anime
        add(EnchLegendIds.DEATH_GATE, 8, "Portão da Morte: Noite do Dragão", 0xFF2A2A, "Que a minha juventude queime até o último portão!",
                "self", "eight_gates", "amplify", "amplify", "amplify", "enchant_might");
        add(EnchLegendIds.KAIOKEN, 8, "Kaioken ×10: Punho do Rei", 0xFF3B30, "Corpo, aguente só mais um pouco… ×10!",
                "self", "kaioken", "amplify", "amplify", "enchant_overdrive");
        add(EnchLegendIds.BOUNDMAN, 6, "Quarta Marcha: Homem-Mola", 0xC8303C, "Músculos inflados, punhos de canhão — Quarta Marcha!",
                "self", "enchanter_gear_fourth", "enchant_aegis", "extend");
    }

    private static void add(String id, int minCircle, String name, int color, String chant, String... glyphs) {
        List<ResourceLocation> list = new ArrayList<>(glyphs.length);
        for (String g : glyphs) list.add(IsekaiMahou.id(g));
        Legends.register(id, new SpellRecipe(list, name, color, chant), Element.ARCANE, minCircle);
        IDS.add(id);
    }

    /** Depois que todos os glifos foram registrados: cada lenda tem que passar no parser (senão é bug). */
    static void validate() {
        for (String id : IDS) {
            Legends.Legend legend = Legends.get(id);
            if (legend == null) continue;
            SpellParser.Result r = SpellParser.parse(legend.recipe(), null, null);
            if (!r.ok()) IsekaiMahou.LOGGER.error("Magia Lendária '{}' (class_enchanter) inválida: {}", id, r.error().getString());
        }
    }

    private EnchLegends() {}
}
