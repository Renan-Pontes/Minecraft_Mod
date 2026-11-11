package com.renanpontes.isekaimahou.feature.ritual_druid;

import java.util.List;

import com.renanpontes.isekaimahou.feature.ritual_druid.entity.AnimalSpiritEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tabela dos 10 estágios da Árvore Coração. Para chegar ao estágio N o druida precisa de:
 * Harmonia mínima no bosque + a oferenda do estágio + a tarefa de estação (contadores harvest/breed/feat.trees_planted).
 */
public final class DruidStages {
    public static final int MAX_STAGE = 10;
    /** Contador próprio: mudas plantadas pelo jogador que viraram árvore. */
    public static final String TREES_COUNTER = "feat.trees_planted";
    /** Rank do Druida (= estágio da árvore que ele já conquistou). */
    public static final String RANK_COUNTER = "ritual_rank.druid";
    /** Convenção entre módulos: +1 a cada subida de ritual de classe. */
    public static final String GLOBAL_RANK_COUNTER = "ritual_rank";

    public enum Task {
        TREES(TREES_COUNTER, Items.OAK_SAPLING),
        HARVEST("harvest", Items.WHEAT),
        BREED("breed", Items.EGG);

        public final String counter;
        public final Item icon;

        Task(String counter, Item icon) {
            this.counter = counter;
            this.icon = icon;
        }

        public MutableComponent describe(int amount) {
            String key = "gui.isekai_mahou.ritual_druid.task." + name().toLowerCase();
            return Component.translatable(key, amount);
        }
    }

    public enum Season {
        SPRING(0x8BE07A), SUMMER(0xFFC94A), AUTUMN(0xE07B39), WINTER(0x9FD8FF);

        public final int rgb;

        Season(int rgb) {
            this.rgb = rgb;
        }

        public MutableComponent displayName() {
            String key = "gui.isekai_mahou.ritual_druid.season." + name().toLowerCase();
            return Component.translatable(key)
                    .withStyle(s -> s.withColor(rgb));
        }
    }

    public record Offering(Item item, int count) {
        public ItemStack stack() { return new ItemStack(item, count); }
    }

    /** Um estágio (número 1..10): o que pede e o que dá. */
    public record Stage(int number, int harmony, List<Offering> offerings, Season season, Task task, int amount, int xp,
                        AnimalSpiritEntity.Kind spirit) {
        /** Tier dos glifos de Origem (Metamorfose) liberados neste estágio. */
        public int glyphTier() {
            return switch (number) {
                case 1, 2 -> 1;
                case 3, 4 -> 2;
                case 5, 6 -> 3;
                case 7, 8, 9 -> 4;
                default -> 5;
            };
        }
    }

    private static final Stage[] STAGES = {
            new Stage(1, 12, List.of(new Offering(Items.BONE_MEAL, 8)),
                    Season.SPRING, Task.TREES, 2, 120, AnimalSpiritEntity.Kind.DEER),
            new Stage(2, 25, List.of(new Offering(Items.APPLE, 3), new Offering(Items.WHEAT, 8)),
                    Season.SUMMER, Task.BREED, 3, 250, AnimalSpiritEntity.Kind.WOLF),
            new Stage(3, 40, List.of(new Offering(Items.HONEY_BOTTLE, 2), new Offering(Items.SWEET_BERRIES, 8)),
                    Season.AUTUMN, Task.HARVEST, 24, 400, AnimalSpiritEntity.Kind.HAWK),
            new Stage(4, 55, List.of(new Offering(Items.GOLDEN_CARROT, 4), new Offering(Items.COCOA_BEANS, 8)),
                    Season.SPRING, Task.TREES, 4, 600, AnimalSpiritEntity.Kind.DEER),
            new Stage(5, 72, List.of(new Offering(Items.GOLDEN_APPLE, 1), new Offering(Items.GLOW_BERRIES, 8)),
                    Season.SUMMER, Task.BREED, 6, 850, AnimalSpiritEntity.Kind.WOLF),
            new Stage(6, 90, List.of(new Offering(Items.HONEYCOMB, 6), new Offering(Items.MOSS_BLOCK, 8)),
                    Season.AUTUMN, Task.HARVEST, 64, 1150, AnimalSpiritEntity.Kind.HAWK),
            new Stage(7, 108, List.of(new Offering(Items.TURTLE_EGG, 1), new Offering(Items.LILY_PAD, 4)),
                    Season.SPRING, Task.TREES, 7, 1500, AnimalSpiritEntity.Kind.DEER),
            new Stage(8, 126, List.of(new Offering(Items.GLISTERING_MELON_SLICE, 4), new Offering(Items.RABBIT_FOOT, 1)),
                    Season.SUMMER, Task.BREED, 10, 1900, AnimalSpiritEntity.Kind.WOLF),
            new Stage(9, 148, List.of(new Offering(Items.GOLDEN_APPLE, 2), new Offering(Items.SPORE_BLOSSOM, 1)),
                    Season.AUTUMN, Task.HARVEST, 128, 2400, AnimalSpiritEntity.Kind.HAWK),
            new Stage(10, 170, List.of(new Offering(Items.TORCHFLOWER, 1), new Offering(Items.GOLDEN_APPLE, 3)),
                    Season.WINTER, Task.TREES, 12, 3000, AnimalSpiritEntity.Kind.DEER),
    };

    /** Estágio de número {@code n} (1..10) ou null. */
    public static Stage get(int n) {
        return n >= 1 && n <= MAX_STAGE ? STAGES[n - 1] : null;
    }

    /** Algarismos romanos para os estágios (0..10). */
    public static String roman(int n) {
        return switch (n) {
            case 0 -> "0";
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    public static MutableComponent stageName(int n) {
        return Component.translatable("gui.isekai_mahou.ritual_druid.stage", roman(n)).withStyle(ChatFormatting.GOLD);
    }

    private DruidStages() {}
}
