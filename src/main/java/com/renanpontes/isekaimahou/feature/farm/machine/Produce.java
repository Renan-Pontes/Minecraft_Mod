package com.renanpontes.isekaimahou.feature.farm.machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Tabelas de produção das fazendas mágicas (sem estado; só o servidor usa). */
public final class Produce {

    /** Uma linha de sorteio: item, peso e quantidade (mín.–máx.). */
    record Roll(Item item, int weight, int min, int max) {}

    /** Sorteia uma linha pelo peso e devolve a pilha. */
    static ItemStack pick(List<Roll> table, RandomSource rnd) {
        int total = 0;
        for (Roll r : table) total += r.weight;
        int v = rnd.nextInt(Math.max(1, total));
        for (Roll r : table) {
            v -= r.weight;
            if (v < 0) return new ItemStack(r.item, r.min + (r.max > r.min ? rnd.nextInt(r.max - r.min + 1) : 0));
        }
        return ItemStack.EMPTY;
    }

    // =====================================================================================
    //  Gerador de Pedra/Minério
    // =====================================================================================
    static final List<Roll> STONE = List.of(
            new Roll(Items.COBBLESTONE, 50, 1, 2), new Roll(Items.STONE, 10, 1, 1), new Roll(Items.ANDESITE, 8, 1, 1),
            new Roll(Items.DIORITE, 8, 1, 1), new Roll(Items.GRANITE, 8, 1, 1), new Roll(Items.TUFF, 6, 1, 1),
            new Roll(Items.COBBLED_DEEPSLATE, 6, 1, 1), new Roll(Items.GRAVEL, 4, 1, 1));
    static final List<Roll> ORES_EARTH = List.of(
            new Roll(Items.COAL, 26, 1, 2), new Roll(Items.RAW_COPPER, 18, 1, 3), new Roll(Items.RAW_IRON, 18, 1, 1),
            new Roll(Items.REDSTONE, 10, 2, 4), new Roll(Items.LAPIS_LAZULI, 8, 2, 4), new Roll(Items.RAW_GOLD, 8, 1, 1),
            new Roll(Items.AMETHYST_SHARD, 7, 1, 2), new Roll(Items.DIAMOND, 3, 1, 1), new Roll(Items.EMERALD, 2, 1, 1));
    static final List<Roll> ORES_FIRE = List.of(
            new Roll(Items.NETHERRACK, 30, 2, 4), new Roll(Items.QUARTZ, 22, 1, 2), new Roll(Items.GOLD_NUGGET, 18, 2, 5),
            new Roll(Items.GLOWSTONE_DUST, 12, 1, 3), new Roll(Items.MAGMA_BLOCK, 8, 1, 1), new Roll(Items.BLACKSTONE, 8, 1, 2),
            new Roll(Items.ANCIENT_DEBRIS, 1, 1, 1));
    static final List<Roll> ORES_WATER = List.of(
            new Roll(Items.SAND, 25, 1, 3), new Roll(Items.CLAY_BALL, 20, 2, 4), new Roll(Items.PRISMARINE_SHARD, 18, 1, 2),
            new Roll(Items.PRISMARINE_CRYSTALS, 12, 1, 2), new Roll(Items.GRAVEL, 12, 1, 2), new Roll(Items.SEA_PICKLE, 6, 1, 1),
            new Roll(Items.NAUTILUS_SHELL, 1, 1, 1));
    static final List<Roll> ORES_SHADOW = List.of(
            new Roll(Items.OBSIDIAN, 30, 1, 1), new Roll(Items.END_STONE, 20, 1, 2), new Roll(Items.COAL, 20, 1, 3),
            new Roll(Items.CRYING_OBSIDIAN, 10, 1, 1), new Roll(Items.ENDER_PEARL, 4, 1, 1), new Roll(Items.ECHO_SHARD, 1, 1, 1));

    /** Catalisadores aceitos (a essência dura {@link #CATALYST_CHARGES} ciclos). */
    public static final int CATALYST_CHARGES = 6;

    @Nullable
    public static Element catalyst(ItemStack s) {
        for (Element e : new Element[]{Element.EARTH, Element.FIRE, Element.WATER, Element.SHADOW}) {
            var essence = CoreRegistries.ESSENCES.get(e);
            if (essence != null && s.is(essence.get())) return e;
        }
        return null;
    }

    static List<Roll> oreTable(Element e) {
        return switch (e) {
            case FIRE -> ORES_FIRE;
            case WATER -> ORES_WATER;
            case SHADOW -> ORES_SHADOW;
            default -> ORES_EARTH;
        };
    }

    /** Tempo (ticks) e mana do ciclo do gerador. */
    static int stoneTicks(@Nullable Element cat) {
        if (cat == null) return 50;
        return switch (cat) {
            case WATER -> 400;
            case SHADOW -> 600;
            default -> 500;
        };
    }

    static int stoneMana(@Nullable Element cat) {
        if (cat == null) return 3;
        return switch (cat) {
            case WATER -> 60;
            case SHADOW -> 120;
            default -> 90;
        };
    }

    // =====================================================================================
    //  Árvore Acelerada
    // =====================================================================================
    /** Colheita por semente/planta: [item colhido, mín, máx] + extra opcional. */
    record Crop(Item main, int min, int max, @Nullable Item extra, int extraMax) {}

    private static final Map<Item, Crop> CROPS = new HashMap<>();

    static {
        CROPS.put(Items.WHEAT_SEEDS, new Crop(Items.WHEAT, 2, 3, Items.WHEAT_SEEDS, 2));
        CROPS.put(Items.CARROT, new Crop(Items.CARROT, 2, 4, null, 0));
        CROPS.put(Items.POTATO, new Crop(Items.POTATO, 2, 4, Items.POISONOUS_POTATO, 1));
        CROPS.put(Items.BEETROOT_SEEDS, new Crop(Items.BEETROOT, 1, 2, Items.BEETROOT_SEEDS, 2));
        CROPS.put(Items.MELON_SEEDS, new Crop(Items.MELON_SLICE, 3, 7, null, 0));
        CROPS.put(Items.PUMPKIN_SEEDS, new Crop(Items.PUMPKIN, 1, 1, null, 0));
        CROPS.put(Items.SUGAR_CANE, new Crop(Items.SUGAR_CANE, 2, 3, null, 0));
        CROPS.put(Items.CACTUS, new Crop(Items.CACTUS, 1, 2, null, 0));
        CROPS.put(Items.BAMBOO, new Crop(Items.BAMBOO, 3, 5, null, 0));
        CROPS.put(Items.NETHER_WART, new Crop(Items.NETHER_WART, 2, 4, null, 0));
        CROPS.put(Items.SWEET_BERRIES, new Crop(Items.SWEET_BERRIES, 2, 3, null, 0));
        CROPS.put(Items.GLOW_BERRIES, new Crop(Items.GLOW_BERRIES, 1, 2, null, 0));
        CROPS.put(Items.COCOA_BEANS, new Crop(Items.COCOA_BEANS, 2, 3, null, 0));
        CROPS.put(Items.KELP, new Crop(Items.KELP, 2, 4, null, 0));
        CROPS.put(Items.BROWN_MUSHROOM, new Crop(Items.BROWN_MUSHROOM, 1, 2, null, 0));
        CROPS.put(Items.RED_MUSHROOM, new Crop(Items.RED_MUSHROOM, 1, 2, null, 0));
        CROPS.put(Items.CHORUS_FLOWER, new Crop(Items.CHORUS_FRUIT, 2, 4, Items.CHORUS_FLOWER, 1));
        CROPS.put(Items.TORCHFLOWER_SEEDS, new Crop(Items.TORCHFLOWER, 1, 1, null, 0));
        CROPS.put(Items.PITCHER_POD, new Crop(Items.PITCHER_PLANT, 1, 1, null, 0));
    }

    public static boolean isTree(ItemStack s) {
        return s.is(ItemTags.SAPLINGS) || s.is(Items.CRIMSON_FUNGUS) || s.is(Items.WARPED_FUNGUS) || s.is(Items.MANGROVE_PROPAGULE);
    }

    public static boolean isCrop(ItemStack s) {
        return CROPS.containsKey(s.getItem());
    }

    public static boolean isPlantable(ItemStack s) {
        return isTree(s) || isCrop(s);
    }

    /** Tora que a muda dá (mesmo nome trocando "_sapling" por "_log"; reserva: carvalho). */
    static Item logFor(Item sapling) {
        if (sapling == Items.CRIMSON_FUNGUS) return Items.CRIMSON_STEM;
        if (sapling == Items.WARPED_FUNGUS) return Items.WARPED_STEM;
        if (sapling == Items.MANGROVE_PROPAGULE) return Items.MANGROVE_LOG;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(sapling);
        String path = id.getPath();
        if (path.endsWith("_sapling")) {
            String base = path.substring(0, path.length() - "_sapling".length());
            for (String suffix : new String[]{"_log", "_stem"}) {
                Item log = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), base + suffix));
                if (log != Items.AIR) return log;
            }
        }
        return Items.OAK_LOG;
    }

    static List<ItemStack> harvest(ItemStack plant, RandomSource rnd) {
        List<ItemStack> out = new ArrayList<>();
        Item it = plant.getItem();
        if (isTree(plant)) {
            out.add(new ItemStack(logFor(it), 4 + rnd.nextInt(4)));
            if (it == Items.CRIMSON_FUNGUS || it == Items.WARPED_FUNGUS) {
                out.add(new ItemStack(it == Items.CRIMSON_FUNGUS ? Items.NETHER_WART_BLOCK : Items.WARPED_WART_BLOCK, 1 + rnd.nextInt(3)));
                if (rnd.nextFloat() < 0.2F) out.add(new ItemStack(Items.SHROOMLIGHT));
                if (rnd.nextFloat() < 0.4F) out.add(new ItemStack(it));
                return out;
            }
            if (rnd.nextFloat() < 0.4F) out.add(new ItemStack(it));
            int sticks = rnd.nextInt(3);
            if (sticks > 0) out.add(new ItemStack(Items.STICK, sticks));
            if ((it == Items.OAK_SAPLING || it == Items.DARK_OAK_SAPLING) && rnd.nextFloat() < 0.15F) out.add(new ItemStack(Items.APPLE));
            if (it == Items.CHERRY_SAPLING && rnd.nextFloat() < 0.3F) out.add(new ItemStack(Items.PINK_PETALS, 1 + rnd.nextInt(2)));
            return out;
        }
        Crop c = CROPS.get(it);
        if (c == null) return out;
        out.add(new ItemStack(c.main, c.min + (c.max > c.min ? rnd.nextInt(c.max - c.min + 1) : 0)));
        if (c.extra != null) {
            int n = c.extra == Items.POISONOUS_POTATO ? (rnd.nextFloat() < 0.02F ? 1 : 0) : rnd.nextInt(c.extraMax + 1);
            if (n > 0) out.add(new ItemStack(c.extra, n));
        }
        return out;
    }

    // =====================================================================================
    //  Combustível (vira mana na reserva)
    // =====================================================================================
    private static final ResourceLocation CRYSTAL_BLOCK = IsekaiMahou.id("mana_crystal_block");

    public static int fuelValue(ItemStack s) {
        if (s.isEmpty()) return 0;
        if (s.is(CoreRegistries.MANA_CRYSTAL.get())) return 600;
        if (CRYSTAL_BLOCK.equals(BuiltInRegistries.ITEM.getKey(s.getItem()))) return 5400;
        if (s.is(Items.AMETHYST_SHARD)) return 80;
        if (s.is(Items.LAPIS_LAZULI)) return 50;
        if (s.is(Items.GLOWSTONE_DUST)) return 40;
        if (s.is(Items.REDSTONE)) return 25;
        return 0;
    }

    /** Extras do Criadouro além da loot table do animal (ovo da galinha, favo da abelha...). */
    static void hatcheryExtras(ResourceLocation animal, RandomSource rnd, List<ItemStack> out) {
        switch (animal.toString()) {
            case "minecraft:chicken" -> out.add(new ItemStack(Items.EGG, 1 + rnd.nextInt(2)));
            case "minecraft:bee" -> { if (rnd.nextFloat() < 0.5F) out.add(new ItemStack(Items.HONEYCOMB)); }
            case "minecraft:turtle" -> { if (rnd.nextFloat() < 0.2F) out.add(new ItemStack(Items.TURTLE_SCUTE)); }
            case "minecraft:armadillo" -> { if (rnd.nextFloat() < 0.3F) out.add(new ItemStack(Items.ARMADILLO_SCUTE)); }
            case "minecraft:cow", "minecraft:goat" -> { if (rnd.nextFloat() < 0.25F) out.add(new ItemStack(Items.LEATHER)); }
            default -> { }
        }
    }

    private Produce() {}
}
