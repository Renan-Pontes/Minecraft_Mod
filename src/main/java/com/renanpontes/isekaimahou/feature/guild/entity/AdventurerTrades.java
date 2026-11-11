package com.renanpontes.isekaimahou.feature.guild.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.feature.guild.GuildContent;
import com.renanpontes.isekaimahou.feature.guild.GuildService;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Trocas dos aventureiros (2 a 4 por aventureiro, sorteadas quando ele nasce e renovadas a cada dia):
 * compram restos de monstros por Moedas da Guilda e vendem suprimentos do ofício de cada um.
 * O preço final de cada troca segue a reputação do jogador com a Guilda (ver AdventurerEntity#startTrading).
 */
final class AdventurerTrades {
    private record Entry(int weight, Supplier<MerchantOffer> offer) {}

    static MerchantOffers generate(AdventurerEntity.Variant variant, RandomSource random) {
        List<Entry> buys = new ArrayList<>();
        List<Entry> sells = new ArrayList<>();

        // ---- O aventureiro COMPRA (o jogador entrega materiais e recebe moedas) ----
        buys.add(buy(4, Items.BONE, 6, 1, 12));
        buys.add(buy(3, Items.EMERALD, 1, 2, 8));
        buys.add(buy(variant == AdventurerEntity.Variant.WARRIOR ? 6 : 2, Items.ROTTEN_FLESH, 8, 1, 12));
        buys.add(buy(variant == AdventurerEntity.Variant.ARCHER ? 6 : 1, Items.STRING, 6, 1, 12));
        buys.add(buy(variant == AdventurerEntity.Variant.ARCHER ? 5 : 1, Items.FEATHER, 10, 1, 12));
        buys.add(buy(variant == AdventurerEntity.Variant.ROGUE ? 6 : 1, Items.GUNPOWDER, 4, 1, 12));
        buys.add(buy(variant == AdventurerEntity.Variant.ROGUE ? 5 : 1, Items.SPIDER_EYE, 3, 1, 12));
        buys.add(buy(variant == AdventurerEntity.Variant.MAGE ? 6 : 1, Items.AMETHYST_SHARD, 3, 1, 12));
        buys.add(buy(variant == AdventurerEntity.Variant.MAGE ? 4 : 1, Items.LAPIS_LAZULI, 6, 1, 12));
        buys.add(buy(variant == AdventurerEntity.Variant.CLERIC ? 6 : 1, Items.ROTTEN_FLESH, 6, 1, 12));
        buys.add(buy(variant == AdventurerEntity.Variant.CLERIC ? 4 : 1, Items.PHANTOM_MEMBRANE, 2, 2, 6));
        modBuy(buys, "slime_gel", 4, 4);
        modBuy(buys, "goblin_ear", 3, 4);

        // ---- O aventureiro VENDE (o jogador paga moedas) ----
        sells.add(sell(4, 2, () -> new ItemStack(Items.BREAD, 4), 8));
        sells.add(sell(3, 2, () -> new ItemStack(Items.TORCH, 8), 8));
        sells.add(sell(3, 4, () -> PotionContents.createItemStack(Items.POTION, Potions.HEALING), 4));
        sells.add(sell(variant == AdventurerEntity.Variant.WARRIOR ? 6 : 1, 3, () -> new ItemStack(Items.COOKED_BEEF, 4), 8));
        sells.add(sell(variant == AdventurerEntity.Variant.WARRIOR ? 4 : 1, 7, () -> new ItemStack(Items.IRON_SWORD), 2));
        sells.add(sell(variant == AdventurerEntity.Variant.WARRIOR ? 3 : 1, 6, () -> new ItemStack(Items.SHIELD), 2));
        sells.add(sell(variant == AdventurerEntity.Variant.ARCHER ? 7 : 1, 2, () -> new ItemStack(Items.ARROW, 16), 8));
        sells.add(sell(variant == AdventurerEntity.Variant.ARCHER ? 3 : 1, 5, () -> new ItemStack(Items.BOW), 2));
        sells.add(sell(variant == AdventurerEntity.Variant.ROGUE ? 6 : 1, 4, () -> new ItemStack(Items.ENDER_PEARL), 4));
        sells.add(sell(variant == AdventurerEntity.Variant.ROGUE ? 4 : 1, 4,
                () -> PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS), 4));
        sells.add(sell(variant == AdventurerEntity.Variant.ROGUE ? 3 : 1, 5,
                () -> PotionContents.createItemStack(Items.POTION, Potions.NIGHT_VISION), 3));
        sells.add(sell(variant == AdventurerEntity.Variant.MAGE ? 7 : 1, 6, () -> new ItemStack(CoreRegistries.MANA_CRYSTAL.get()), 4));
        sells.add(sell(variant == AdventurerEntity.Variant.MAGE ? 6 : 1, 5, () -> {
            Element e = Element.BASE.get(random.nextInt(Element.BASE.size()));
            return new ItemStack(CoreRegistries.ESSENCES.get(e).get());
        }, 4));
        // Clérigo: poções de cura, regeneração, maçã dourada e água benta (poção de força fraca).
        sells.add(sell(variant == AdventurerEntity.Variant.CLERIC ? 7 : 1, 3,
                () -> PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING), 4));
        sells.add(sell(variant == AdventurerEntity.Variant.CLERIC ? 5 : 1, 6,
                () -> PotionContents.createItemStack(Items.POTION, Potions.REGENERATION), 3));
        sells.add(sell(variant == AdventurerEntity.Variant.CLERIC ? 3 : 0, 12, () -> new ItemStack(Items.GOLDEN_APPLE), 2));
        sells.add(sell(variant == AdventurerEntity.Variant.CLERIC ? 3 : 0, 9, () -> {
            var light = CoreRegistries.ESSENCES.get(Element.LIGHT);
            return light != null ? new ItemStack(light.get()) : new ItemStack(Items.GLOWSTONE_DUST, 4);
        }, 3));
        // Raro: um pergaminho de glifo de tier 1 (mais comum com magos).
        ResourceLocation glyph = GuildService.randomGlyph(1, null, random);
        if (glyph != null) {
            sells.add(sell(variant == AdventurerEntity.Variant.MAGE ? 4 : 1, 14, () -> GlyphScrollItem.of(glyph), 1));
        }

        MerchantOffers offers = new MerchantOffers();
        int nBuy = 1 + random.nextInt(2);
        int nSell = 1 + random.nextInt(2);
        pick(buys, nBuy, random, offers);
        pick(sells, nSell, random, offers);
        return offers;
    }

    private static void pick(List<Entry> pool, int n, RandomSource random, MerchantOffers out) {
        List<Entry> left = new ArrayList<>(pool);
        left.removeIf(e -> e.weight <= 0);
        for (int k = 0; k < n && !left.isEmpty(); k++) {
            int total = 0;
            for (Entry e : left) total += e.weight;
            int r = random.nextInt(total);
            for (int i = 0; i < left.size(); i++) {
                r -= left.get(i).weight;
                if (r < 0) {
                    out.add(left.remove(i).offer.get());
                    break;
                }
            }
        }
    }

    private static Entry buy(int weight, Item item, int amount, int coins, int maxUses) {
        return new Entry(weight, () -> new MerchantOffer(new ItemCost(item, amount),
                new ItemStack(GuildContent.GUILD_COIN.get(), coins), maxUses, 0, 0.05F));
    }

    private static void modBuy(List<Entry> pool, String path, int amount, int weight) {
        BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(path)).ifPresent(item -> pool.add(buy(weight, item, amount, 1, 12)));
    }

    private static Entry sell(int weight, int coins, Supplier<ItemStack> result, int maxUses) {
        return new Entry(weight, () -> new MerchantOffer(new ItemCost(GuildContent.GUILD_COIN.get(), coins), result.get(), maxUses, 0, 0.05F));
    }

    private AdventurerTrades() {}
}
