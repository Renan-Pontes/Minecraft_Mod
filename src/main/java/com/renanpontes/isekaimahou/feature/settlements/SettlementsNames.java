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
                    "Vidar", "Yngve"},
            // onda 5: Oriental, Élfico, Sombrio, Anão (na ordem do enum)
            {"Akira", "Daichi", "Haruto", "Hiro", "Isamu", "Jiro", "Kaito", "Kenji", "Makoto", "Ren", "Ryota", "Satoshi", "Shin",
                    "Takeshi", "Yuto", "Zen"},
            {"Aelar", "Caelum", "Elandor", "Faelar", "Galen", "Ilyan", "Lorien", "Maeron", "Naeris", "Orophin", "Rilien",
                    "Sylvar", "Thalion", "Vaelin", "Yllar", "Zephir"},
            {"Alaric", "Bram", "Corvin", "Damien", "Edgar", "Grimm", "Ignatius", "Lucien", "Mordred", "Nikolai", "Raven",
                    "Severin", "Silas", "Victor", "Vlad", "Wolfram"},
            {"Balin", "Borin", "Dain", "Durgan", "Farin", "Gimrik", "Grom", "Harbek", "Kazrik", "Morgran", "Norin", "Orsik",
                    "Rurik", "Thorin", "Thrain", "Ulfgar"}};
    private static final String[][] FEMALE = {
            {"Adelaide", "Beatrice", "Clarissa", "Edith", "Genevieve", "Hilda", "Isabeau", "Juliana", "Lenora", "Matilda",
                    "Odette", "Rosalind", "Sabine", "Theodora", "Vivienne", "Yvette"},
            {"Aisha", "Basma", "Dunya", "Farida", "Hana", "Inaya", "Jamila", "Leila", "Malak", "Nour", "Rasha", "Salma", "Suha",
                    "Wafa", "Yara", "Zainab"},
            {"Alva", "Bodil", "Eira", "Frida", "Gerd", "Hild", "Ingrid", "Jorunn", "Kari", "Linnea", "Ragna", "Sif", "Solveig",
                    "Thyra", "Unn", "Vigdis"},
            {"Aiko", "Akemi", "Chiyo", "Emi", "Hana", "Haruka", "Kaede", "Keiko", "Mei", "Midori", "Rin", "Sakura", "Sayuri",
                    "Yoko", "Yuki", "Yumi"},
            {"Aerin", "Arwen", "Caelynn", "Elowen", "Faelyn", "Ielenia", "Lirael", "Maelis", "Nimue", "Raelyn", "Sariel",
                    "Sylvia", "Thalia", "Vaela", "Ysolde", "Zinnia"},
            {"Agatha", "Belladonna", "Carmilla", "Drusilla", "Elvira", "Isolde", "Lenore", "Lilith", "Morgana", "Morwenna",
                    "Ophelia", "Raven", "Selene", "Sybil", "Valeria", "Wilhelmina"},
            {"Audhild", "Bardryn", "Dagnal", "Eldeth", "Falkrunn", "Gunnloda", "Helja", "Hlin", "Kathra", "Kristryd", "Mardred",
                    "Riswynn", "Sannl", "Torbera", "Torgga", "Vistra"}};

    public static String pick(SettlementStyle style, boolean female, RandomSource random) {
        String[][] all = female ? FEMALE : MALE;
        // estilo que entrar depois sem lista própria usa a do Reino (nunca estoura o vetor)
        String[] list = all[style.ordinal() < all.length ? style.ordinal() : 0];
        return list[random.nextInt(list.length)];
    }

    private SettlementsNames() {}
}
