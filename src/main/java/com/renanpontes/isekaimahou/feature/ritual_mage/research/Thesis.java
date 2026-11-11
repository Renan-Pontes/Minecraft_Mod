package com.renanpontes.isekaimahou.feature.ritual_mage.research;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * As 10 Teses da Pesquisa Arcana (ranks 1..10). Cada uma pede notas de certos elementos, papel e estantes
 * perto da Mesa, e um circuito rúnico ligando a runa de entrada à de saída.
 *
 * @param requiredTheses quantas teses o jogador precisa ter concluído antes
 * @param blocked        fissuras na grade (células inutilizáveis)
 * @param fixed          runas fixas de fusão (obstáculos ou pontes)
 * @param awakens        elemento despertado ao concluir (só para quem é Mago, se ainda não tiver)
 */
public record Thesis(int index, String id, int requiredTheses, Element entry, Element exit, List<Req> reqs,
                     int xp, int paper, int shelves, int blocked, int fixed, @Nullable Element awakens) {

    /** Requisito: {@code count} notas do elemento (fusões contam para os pais); element null = qualquer nota. */
    public record Req(@Nullable Element element, int count) {
        public boolean accepts(Element note) {
            return element == null || note.matches(element);
        }

        public MutableComponent label() {
            return element == null ? Component.translatable("screen.isekai_mahou.research_table.any") : element.displayName();
        }
    }

    private static final String KEY = "thesis.isekai_mahou.";

    private static Req req(Element e, int n) { return new Req(e, n); }

    public static final List<Thesis> ALL = List.of(
            new Thesis(0, "mana_primordial", 0, Element.ARCANE, Element.ARCANE, List.of(req(null, 3)),
                    100, 1, 0, 2, 0, null),
            new Thesis(1, "ignis", 1, Element.FIRE, Element.ARCANE, List.of(req(Element.FIRE, 4)),
                    180, 2, 2, 3, 1, Element.FIRE),
            new Thesis(2, "aqua", 1, Element.WATER, Element.ARCANE, List.of(req(Element.WATER, 4)),
                    180, 2, 2, 3, 1, Element.WATER),
            new Thesis(3, "terra", 1, Element.EARTH, Element.ARCANE, List.of(req(Element.EARTH, 4)),
                    180, 2, 2, 3, 1, Element.EARTH),
            new Thesis(4, "ventus", 1, Element.WIND, Element.ARCANE, List.of(req(Element.WIND, 4)),
                    180, 2, 2, 3, 1, Element.WIND),
            new Thesis(5, "dualitas", 5, Element.LIGHT, Element.SHADOW, List.of(req(Element.LIGHT, 3), req(Element.SHADOW, 3)),
                    450, 3, 4, 4, 2, null),
            new Thesis(6, "convergentia", 6, Element.MAGMA, Element.ICE,
                    List.of(req(Element.FIRE, 2), req(Element.EARTH, 2), req(Element.WATER, 2), req(Element.WIND, 2)),
                    700, 3, 6, 4, 2, null),
            new Thesis(7, "planorum", 7, Element.HELLFIRE, Element.VOID,
                    List.of(req(Element.FIRE, 3), req(Element.SHADOW, 3), req(Element.WIND, 3)),
                    1000, 4, 8, 4, 3, null),
            new Thesis(8, "astralis", 8, Element.ASTRAL, Element.TWILIGHT,
                    List.of(req(Element.LIGHT, 4), req(Element.WIND, 3), req(Element.SHADOW, 3)),
                    1400, 4, 10, 5, 3, null),
            new Thesis(9, "magnum_opus", 9, Element.ARCANE, Element.ARCANE,
                    List.of(req(Element.FIRE, 2), req(Element.WATER, 2), req(Element.EARTH, 2),
                            req(Element.WIND, 2), req(Element.LIGHT, 2), req(Element.SHADOW, 2)),
                    2200, 5, 12, 5, 3, null));

    public static Thesis byIndex(int i) {
        return i >= 0 && i < ALL.size() ? ALL.get(i) : null;
    }

    /** Nível das Runas ensinadas ao concluir (nº de teses após concluir, até 5). */
    public static int runeTierFor(int thesesDone) {
        return Math.min(5, thesesDone);
    }

    /** Numeral romano do rank (I..X), usado na lista. */
    public String numeral() {
        return switch (index) {
            case 0 -> "I"; case 1 -> "II"; case 2 -> "III"; case 3 -> "IV"; case 4 -> "V";
            case 5 -> "VI"; case 6 -> "VII"; case 7 -> "VIII"; case 8 -> "IX"; default -> "X";
        };
    }

    public MutableComponent displayName() {
        return Component.translatable(KEY + id);
    }

    public MutableComponent description() {
        return Component.translatable(KEY + id + ".desc");
    }

    /** Cor de destaque (elemento de entrada; o Arcano usa o roxo arcano). */
    public int color() {
        return entry.color;
    }
}
