package com.renanpontes.isekaimahou.feature.class_enchanter.rite;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.feature.class_enchanter.Enchants;
import com.renanpontes.isekaimahou.feature.class_enchanter.glyph.AnimeGlyphs;
import com.renanpontes.isekaimahou.feature.class_enchanter.glyph.EnchanterGlyphs;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Os 5 ranks do RITO DE INSCRIÇÃO (progressão própria do Encantador). Lado comum: a aba do cliente lê a mesma tabela.
 * Cada rank pede Círculo e nível, tarefas (contadores acumulados desde a escolha da classe) e uma oferenda queimada
 * numa mesa de encantamento; dá XP, glifos de origem, despertar/XP de elemento e itens. A dificuldade acompanha a dos
 * outras classes (Círculos 1/2/4/6/8, XP 600/1500/3500/7000/14000, chefes III 1 · IV 2 · V 4).
 */
public final class EnchanterRanks {
    /** Prefixos das chaves (rank.0..5 e req.&lt;tarefa&gt; em lang/class_enchanter). */
    static final String RANK_KEY = "screen.isekai_mahou.enchanter.rank.";
    static final String REQ_KEY = "screen.isekai_mahou.enchanter.req.";

    /** Tarefa: contador ≥ quantidade. */
    public record Req(String counter, int amount, String key) {
        public MutableComponent label() {
            return Component.translatable(REQ_KEY + key, amount);
        }

        public int have(MagicData d) {
            return d.counter(counter);
        }
    }

    /** Item da oferenda. */
    public record Offer(Supplier<Item> item, int count) {
        public int have(Player p) {
            Item it = item.get();
            int n = 0;
            for (ItemStack s : p.getInventory().items) if (s.is(it)) n += s.getCount();
            for (ItemStack s : p.getInventory().offhand) if (s.is(it)) n += s.getCount();
            return n;
        }
    }

    public record Rank(int rank, int circle, int level, List<Req> reqs, List<Offer> offering, int xp, List<String> glyphs,
                       @Nullable Element awaken, List<Supplier<ItemStack>> gifts) {
        public MutableComponent title() {
            return Component.translatable(RANK_KEY + rank);
        }
    }

    private static Supplier<Item> essence(Element e) {
        return () -> CoreRegistries.ESSENCES.get(e).get();
    }

    public static final List<Rank> RANKS = List.of(
            new Rank(1, 1, 5,
                    List.of(applied(25), kills(15)),
                    List.of(new Offer(() -> Items.LAPIS_LAZULI, 12), new Offer(() -> Items.AMETHYST_SHARD, 4)),
                    600, List.of(EnchanterGlyphs.AEGIS, EnchanterGlyphs.VITALITY, EnchanterGlyphs.LINK), null,
                    List.of(() -> new ItemStack(CoreRegistries.ESSENCES.get(Element.LIGHT).get(), 2))),
            new Rank(2, 2, 15,
                    List.of(applied(80), ally(8), nullified(60), kills(50)),
                    List.of(new Offer(() -> Items.LAPIS_LAZULI, 24), new Offer(essence(Element.LIGHT), 2), new Offer(essence(Element.WIND), 2)),
                    1500, List.of(EnchanterGlyphs.IMBUE, EnchanterGlyphs.OVERDRIVE, AnimeGlyphs.GEAR_SECOND), Element.WIND,
                    List.of(() -> new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 4))),
            new Rank(3, 4, 25,
                    List.of(applied(200), nullified(300), kills(100), overdrive(5), boss(1)),
                    List.of(new Offer(() -> Items.LAPIS_LAZULI, 32), new Offer(() -> CoreRegistries.MANA_CRYSTAL.get(), 4),
                            new Offer(() -> Items.DIAMOND, 1)),
                    3500, List.of(EnchanterGlyphs.HEAVENLY, AnimeGlyphs.GATES, AnimeGlyphs.KAIOKEN), null,
                    List.of(() -> new ItemStack(CoreRegistries.ESSENCES.get(Element.ARCANE).get(), 4),
                            () -> Legends.scroll(EnchLegendIds.EMPEROR))),
            new Rank(4, 6, 45,
                    List.of(applied(400), nullified(900), restricted(30), ally(25), boss(2)),
                    List.of(new Offer(() -> Items.DIAMOND, 2), new Offer(() -> CoreRegistries.MANA_CRYSTAL.get(), 8),
                            new Offer(essence(Element.LIGHT), 4), new Offer(essence(Element.WIND), 4)),
                    7000, List.of(EnchanterGlyphs.GEAR, AnimeGlyphs.ULTRA, AnimeGlyphs.GEAR_FOURTH), null,
                    List.of(() -> new ItemStack(Items.GOLDEN_APPLE, 2), () -> new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 6),
                            () -> Legends.scroll(EnchLegendIds.CELESTIAL), () -> Legends.scroll(EnchLegendIds.BOUNDMAN))),
            new Rank(5, 8, 75,
                    List.of(applied(800), nullified(2500), restricted(100), boss(4)),
                    List.of(new Offer(() -> Items.DIAMOND, 3), new Offer(() -> CoreRegistries.MANA_CRYSTAL.get(), 16),
                            new Offer(essence(Element.ARCANE), 6), new Offer(essence(Element.LIGHT), 6), new Offer(essence(Element.WIND), 6)),
                    14000, List.of(EnchanterGlyphs.SOVEREIGN), null,
                    List.of(() -> Legends.scroll(EnchLegendIds.SOVEREIGN), () -> Legends.scroll(EnchLegendIds.DEATH_GATE),
                            () -> Legends.scroll(EnchLegendIds.KAIOKEN), () -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1))));

    /** Glifos que o Encantador ganha ao escolher a classe (rank 0). */
    public static final List<String> STARTING_GLYPHS = List.of(EnchanterGlyphs.MIGHT, EnchanterGlyphs.HASTE, EnchanterGlyphs.ANTI_MAGIC);

    /** Ids das lendas da classe (registradas pelo módulo). */
    public static final class EnchLegendIds {
        public static final String EMPEROR = "enchanter_emperor_fist";
        public static final String CELESTIAL = "enchanter_celestial_body";
        public static final String SOVEREIGN = "enchanter_sovereign_crown";
        /** Pacote de técnicas de anime. */
        public static final String DEATH_GATE = "enchanter_death_gate";
        public static final String KAIOKEN = "enchanter_kaioken_x10";
        public static final String BOUNDMAN = "enchanter_boundman";

        private EnchLegendIds() {}
    }

    @Nullable
    public static Rank get(int rank) {
        return rank >= 1 && rank <= RANKS.size() ? RANKS.get(rank - 1) : null;
    }

    public static int current(MagicData d) {
        return Math.max(0, Math.min(Enchants.MAX_RANK, d.counter(Enchants.RANK)));
    }

    /** Nome do rank atual ("Noviço" no 0). */
    public static MutableComponent name(int rank) {
        return Component.translatable(RANK_KEY + Math.max(0, Math.min(5, rank)));
    }

    // ------------------------------------------------------------------ fábricas curtas das tarefas
    private static Req applied(int n) { return new Req(Enchants.C_APPLIED, n, "applied"); }

    private static Req kills(int n) { return new Req(Enchants.C_KILLS, n, "kills"); }

    private static Req ally(int n) { return new Req(Enchants.C_ALLY, n, "ally"); }

    private static Req nullified(int n) { return new Req(Enchants.C_NULLIFIED, n, "nullified"); }

    private static Req overdrive(int n) { return new Req(Enchants.C_OVERDRIVE, n, "overdrive"); }

    private static Req restricted(int n) { return new Req(Enchants.C_RESTRICTED, n, "restricted"); }

    private static Req boss(int n) { return new Req(Enchants.C_BOSS, n, "boss"); }

    private EnchanterRanks() {}
}
