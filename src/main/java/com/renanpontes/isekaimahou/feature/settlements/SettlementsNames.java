package com.renanpontes.isekaimahou.feature.settlements;

import net.minecraft.util.RandomSource;

/**
 * Nomes próprios dos NPCs por estilo (nomes não se traduzem; o papel vem traduzido junto: "Bjorn, Construtor").
 */
public final class SettlementsNames {
    private static final String[][] MALE = {
            {"Aldo", "Bertram", "Caspian", "Dorian", "Edwin", "Florian", "Godric", "Hugo", "Ivor", "Julian", "Lorenz", "Matthias",
                    "Osric", "Percival", "Roland", "Tristan"},
            {"Adil", "Bilal", "Faris", "Ghazi", "Harun", "Imran", "Jabir", "Kamal", "Latif", "Munir", "Nabil", "Qasim", "Rafiq",
                    "Salim", "Tariq", "Zayd"},
            {"Arne", "Bjorn", "Dag", "Einar", "Frode", "Geir", "Hakon", "Ingvar", "Knut", "Olav", "Rolf", "Sten", "Tore", "Ulf",
                    "Vidar", "Yngve"}};
    private static final String[][] FEMALE = {
            {"Adelaide", "Beatrice", "Clarissa", "Edith", "Genevieve", "Hilda", "Isabeau", "Juliana", "Lenora", "Matilda",
                    "Odette", "Rosalind", "Sabine", "Theodora", "Vivienne", "Yvette"},
            {"Aisha", "Basma", "Dunya", "Farida", "Hana", "Inaya", "Jamila", "Leila", "Malak", "Nour", "Rasha", "Salma", "Suha",
                    "Wafa", "Yara", "Zainab"},
            {"Alva", "Bodil", "Eira", "Frida", "Gerd", "Hild", "Ingrid", "Jorunn", "Kari", "Linnea", "Ragna", "Sif", "Solveig",
                    "Thyra", "Unn", "Vigdis"}};

    public static String pick(SettlementStyle style, boolean female, RandomSource random) {
        String[] list = (female ? FEMALE : MALE)[style.ordinal()];
        return list[random.nextInt(list.length)];
    }

    private SettlementsNames() {}
}
