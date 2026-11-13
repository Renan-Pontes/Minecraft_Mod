package com.renanpontes.isekaimahou.feature.cities.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Reputation.Standing;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.feature.cities.entity.CityMerchant;
import com.renanpontes.isekaimahou.feature.cities.entity.Profession;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Trocas dos mercadores da cidade, montadas para UM jogador quando ele abre a loja:
 * preço em Moedas da Guilda (ou esmeraldas) × {@link Reputation#priceMultiplier} da facção da profissão; o que o
 * mercador compra paga mais com reputação alta; ofertas extras liberadas por patamar (Amigável, Honrado, Reverenciado,
 * Exaltado). Itens de outros módulos são buscados pelo id (se não existirem, a oferta some).
 */
public final class CityTrades {
    /** Uma oferta com a chave de estoque (o estoque do dia é do mercador, não do jogador). */
    public record Trade(String key, MerchantOffer offer) {}

    /** Arsenal: as 5 armas de classe (ids "&lt;arma&gt;_t&lt;tier&gt;" do módulo armory). */
    private static final String[] ARMORY = {"mage_staff", "witch_wand", "pact_blade", "druid_staff", "reaper_scythe"};
    /** Gemas do módulo gear_plus (ids "gem_&lt;tipo&gt;_chipped" = lascadas). */
    private static final String[] GEMS = {"ruby", "sapphire", "emerald", "topaz", "amethyst", "onyx"};

    public static List<Trade> build(CityMerchant merchant, ServerPlayer player) {
        Profession prof = merchant.profession();
        String faction = merchant.faction();
        Standing st = Reputation.standing(player, faction);
        float mult = Reputation.priceMultiplier(player, faction);
        long day = player.level().getDayTime() / 24000L;
        // A semente do dia muda as ofertas "sorteadas" (pergaminhos, livros) uma vez por dia, por mercador e jogador.
        RandomSource r = RandomSource.create(day * 31L + merchant.getUUID().getLeastSignificantBits() * 17L
                + player.getUUID().getMostSignificantBits());
        B b = new B(mult, st, r);
        switch (prof) {
            case BLACKSMITH -> blacksmith(b, player);
            case ALCHEMIST -> alchemist(b);
            case SCRIBE -> scribe(b, player);
            case JEWELER -> jeweler(b);
            case INNKEEPER -> innkeeper(b);
            case PRIEST -> priest(b, player);
            case FENCE -> fence(b, player);
            case GROCER -> grocer(b);
            case TAILOR -> tailor(b, player);
            case MECHANIC -> mechanic(b);
        }
        return b.out;
    }

    // =====================================================================================
    //  Profissões
    // =====================================================================================
    private static void blacksmith(B b, ServerPlayer p) {
        b.buy("iron", Items.IRON_INGOT, 4, 2, 16);
        b.buy("coal", Items.COAL, 10, 1, 16);
        // Metal Estelar (meteoritos do módulo world_events): o ferreiro paga bem — mais um uso para o minério raro.
        b.modBuy("star_metal", "world_events_star_metal", 1, 5, 8);
        b.sell("iron_sword", 6, new ItemStack(Items.IRON_SWORD), 3);
        b.sell("iron_axe", 6, new ItemStack(Items.IRON_AXE), 3);
        b.sell("shield", 5, new ItemStack(Items.SHIELD), 3);
        b.sell("crossbow", 6, new ItemStack(Items.CROSSBOW), 2);
        b.sell("arrows", 2, new ItemStack(Items.ARROW, 16), 8);
        b.sell("iron_helmet", 7, new ItemStack(Items.IRON_HELMET), 2);
        b.sell("iron_chest", 12, new ItemStack(Items.IRON_CHESTPLATE), 2);
        b.sell("iron_legs", 10, new ItemStack(Items.IRON_LEGGINGS), 2);
        b.sell("iron_boots", 6, new ItemStack(Items.IRON_BOOTS), 2);
        // Arsenal da classe: o tier liberado pelo patamar (todas as 5 armas do tier).
        int tier = b.st.atLeast(Standing.EXALTED) ? 4 : b.st.atLeast(Standing.REVERED) ? 3 : b.st.atLeast(Standing.HONORED) ? 2
                : b.st.atLeast(Standing.FRIENDLY) ? 1 : 0;
        int[] price = {0, 18, 40, 80, 120};
        for (String w : ARMORY) {
            if (tier > 0) b.modSell("armory_" + w + "_t" + tier, w + "_t" + tier, price[tier], 1, 1);
        }
        b.sellIf(Standing.FRIENDLY, "diamond_sword", 20, new ItemStack(Items.DIAMOND_SWORD), 1);
        b.sellIf(Standing.HONORED, "diamond_chest", 32, new ItemStack(Items.DIAMOND_CHESTPLATE), 1);
        if (b.st.atLeast(Standing.REVERED)) b.modSell("arcane_template", "arcane_upgrade_template", 50, 1, 1);
        b.sellIf(Standing.EXALTED, "netherite_template", 64, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), 1);
        if (b.st.atLeast(Standing.EXALTED)) b.modSell("arcane_diamond", "arcane_diamond", 60, 1, 1);
    }

    private static void alchemist(B b) {
        b.buy("wart", Items.NETHER_WART, 6, 1, 16);
        b.buy("spider_eye", Items.SPIDER_EYE, 3, 1, 16);
        b.buy("glowstone", Items.GLOWSTONE_DUST, 4, 1, 16);
        b.sell("healing", 3, potion(Potions.HEALING), 6);
        b.sell("regen", 4, potion(Potions.REGENERATION), 4);
        b.sell("fire_res", 4, potion(Potions.FIRE_RESISTANCE), 4);
        b.sell("swift", 3, potion(Potions.SWIFTNESS), 4);
        b.sell("night", 3, potion(Potions.NIGHT_VISION), 4);
        b.sell("water", 3, potion(Potions.WATER_BREATHING), 4);
        b.sell("crystal", 5, new ItemStack(CoreRegistries.MANA_CRYSTAL.get()), 6);
        // Bolsa do Alquimista (módulo arcana_items): um pouco mais barata que na loja da Guilda, uma por dia.
        b.modSell("alchemist_bag", "alchemist_bag", 26, 1, 1);
        // onda 9 (fundar mais fácil): o Núcleo Celeste da cidade no céu, desde o primeiro dia (um por dia)
        b.modSell("sky_core", "sky_core", 16, 1, 1);
        b.sellIf(Standing.FRIENDLY, "strength", 5, potion(Potions.STRENGTH), 4);
        b.sellIf(Standing.FRIENDLY, "splash_heal", 4, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING), 6);
        if (b.st.atLeast(Standing.FRIENDLY)) b.modSell("elixir_mana", "witch_elixir_mana", 6, 1, 4);
        b.sellIf(Standing.HONORED, "strong_heal", 6, potion(Potions.STRONG_HEALING), 4);
        b.sellIf(Standing.HONORED, "long_regen", 7, potion(Potions.LONG_REGENERATION), 3);
        if (b.st.atLeast(Standing.HONORED)) {
            b.modSell("elixir_vitality", "witch_elixir_vitality", 10, 1, 3);
            b.modSell("elixir_stone", "witch_elixir_stone_skin", 10, 1, 3);
        }
        b.sellIf(Standing.REVERED, "turtle", 10, potion(Potions.TURTLE_MASTER), 2);
        b.sellIf(Standing.REVERED, "golden_apple", 8, new ItemStack(Items.GOLDEN_APPLE), 3);
        b.sellIf(Standing.EXALTED, "gapple", 90, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 1);
    }

    /**
     * Escriba (release 1.0): NÃO vende mais Pergaminhos de Glifo — glifos vêm de exploração, chefes e templos. Ficou o
     * material de escrita: papel (o Pergaminho de Magia do Grimório gasta 1 papel), tinta, penas, livros, estantes.
     */
    private static void scribe(B b, ServerPlayer p) {
        b.buy("paper", Items.PAPER, 12, 1, 16);
        b.buy("book", Items.BOOK, 2, 1, 12);
        b.buy("lapis", Items.LAPIS_LAZULI, 6, 1, 12);
        b.buy("feather", Items.FEATHER, 8, 1, 12);
        b.sell("blank_scrolls", 1, new ItemStack(Items.PAPER, 6), 12);
        b.sell("ink", 1, new ItemStack(Items.INK_SAC, 3), 8);
        b.sell("quill", 1, new ItemStack(Items.FEATHER, 3), 8);
        b.sell("book_sell", 1, new ItemStack(Items.BOOK), 12);
        b.sell("writable", 2, new ItemStack(Items.WRITABLE_BOOK), 6);
        b.sell("map", 2, new ItemStack(Items.MAP), 4);
        b.sell("crystal", 4, new ItemStack(CoreRegistries.MANA_CRYSTAL.get()), 6);
        // onda 9 (fundar mais fácil): a Carta de Fundação do jogador, desde o primeiro dia (uma por dia)
        b.modSell("founding_charter", "founding_charter", 12, 1, 1);
        b.sellIf(Standing.FRIENDLY, "bookshelf", 3, new ItemStack(Items.BOOKSHELF), 6);
        b.sellIf(Standing.FRIENDLY, "glow_ink", 2, new ItemStack(Items.GLOW_INK_SAC, 2), 6);
        b.sellIf(Standing.FRIENDLY, "lectern", 4, new ItemStack(Items.LECTERN), 2);
        b.sellIf(Standing.HONORED, "ench_book", 14, enchantedBook(p, b.r), 1);
        b.sellIf(Standing.REVERED, "xp_bottles", 5, new ItemStack(Items.EXPERIENCE_BOTTLE, 4), 4);
        // Exaltado: Pergaminhos Lendários (Legends) — um por dia, sorteado entre as lendas do Círculo do jogador.
        if (b.st.atLeast(Standing.EXALTED)) {
            ItemStack legend = Legends.randomScroll(b.r, Math.max(1, MagicApi.get(p).circle()));
            if (!legend.isEmpty()) b.sell("legend", 120, legend, 1);
        }
    }

    private static void jeweler(B b) {
        b.buy("gold", Items.GOLD_INGOT, 3, 2, 16);
        b.buy("diamond_buy", Items.DIAMOND, 1, 4, 8);
        b.buy("emerald_buy", Items.EMERALD, 2, 1, 16);
        b.buy("amethyst", Items.AMETHYST_SHARD, 6, 1, 16);
        b.sell("diamond", 8, new ItemStack(Items.DIAMOND), 4);
        b.sell("emerald", 3, new ItemStack(Items.EMERALD), 16);
        b.sell("gold_nuggets", 1, new ItemStack(Items.GOLD_NUGGET, 4), 8);
        b.sellIf(Standing.FRIENDLY, "lapis_block", 3, new ItemStack(Items.LAPIS_BLOCK), 4);
        if (b.st.atLeast(Standing.FRIENDLY)) {
            b.modSell("vitality_charm", "vitality_charm", 30, 1, 1);
            b.modSell("mana_ring", "mana_ring", 25, 1, 1);
            b.modSell("regen_ring", "regen_ring", 30, 1, 1);
        }
        // Gemas (módulo gear_plus): o Cinzel de Lapidário é ferramenta, vende sempre. Gemas LASCADAS só a partir de
        // Amigável, duas sorteadas por dia e uma de cada — juntar 9 para uma perfeita leva dias ("nada de poder fácil").
        b.modSell("gem_chisel", "gem_chisel", 16, 1, 1);
        if (b.st.atLeast(Standing.FRIENDLY)) {
            int first = b.r.nextInt(GEMS.length);
            int second = (first + 1 + b.r.nextInt(GEMS.length - 1)) % GEMS.length;
            for (int g : new int[] {first, second}) {
                String id = "gem_" + GEMS[g] + "_chipped";
                b.modSell(id, id, 22, 1, 1);
            }
        }
        if (b.st.atLeast(Standing.HONORED)) {
            b.modSell("ward_ring", "ward_ring", 45, 1, 1);
            b.modSell("meditation_charm", "meditation_charm", 35, 1, 1);
            b.modSell("arcane_amulet", "arcane_amulet", 40, 1, 1);
        }
        if (b.st.atLeast(Standing.REVERED)) b.modSell("archmage_sigil", "archmage_sigil", 90, 1, 1);
        b.sellIf(Standing.EXALTED, "heart_sea", 40, new ItemStack(Items.HEART_OF_THE_SEA), 1);
        b.sellIf(Standing.EXALTED, "totem", 50, new ItemStack(Items.TOTEM_OF_UNDYING), 1);
    }

    private static void innkeeper(B b) {
        b.buy("wheat", Items.WHEAT, 16, 1, 16);
        b.buy("beef", Items.BEEF, 6, 1, 12);
        b.buy("porkchop", Items.PORKCHOP, 6, 1, 12);
        b.sell("bread", 1, new ItemStack(Items.BREAD, 3), 16);
        b.sell("steak", 2, new ItemStack(Items.COOKED_BEEF, 4), 12);
        b.sell("stew", 1, new ItemStack(Items.MUSHROOM_STEW), 8);
        b.sell("rabbit_stew", 2, new ItemStack(Items.RABBIT_STEW), 6);
        b.sell("pie", 1, new ItemStack(Items.PUMPKIN_PIE, 2), 8);
        b.sell("honey", 1, new ItemStack(Items.HONEY_BOTTLE), 8);
        b.sell("milk", 1, new ItemStack(Items.MILK_BUCKET), 4);
        b.sellIf(Standing.FRIENDLY, "golden_carrot", 3, new ItemStack(Items.GOLDEN_CARROT, 4), 6);
        b.sellIf(Standing.FRIENDLY, "cake", 3, new ItemStack(Items.CAKE), 3);
        b.sellIf(Standing.HONORED, "feast", 4, new ItemStack(Items.COOKED_MUTTON, 8), 6);
        b.sellIf(Standing.REVERED, "golden_apple", 7, new ItemStack(Items.GOLDEN_APPLE), 2);
        // Chave do Lar (módulo home_realm, onda 2): bem cara; a oferta some se o item não existir.
        b.modSell("home_key", "home_key", 128, 1, 1);
        // Costura da release 1.0 — itens das ondas 1–2 pelo id (somem se o módulo não existir):
        // Pergaminho de Retorno baratinho (todo viajante passa pela estalagem), Incenso do Vínculo (ritual do familiar)
        // e, para quem já é Amigável, as Rédeas do Drake Terrestre.
        b.modSell("return_scroll", "return_scroll", 3, 2, 8);
        b.modSell("bond_incense", "bond_incense", 14, 1, 1);
        if (b.st.atLeast(Standing.FRIENDLY)) b.modSell("land_drake_reins", "land_drake_reins", 45, 1, 1);
    }

    private static void priest(B b, ServerPlayer p) {
        b.buy("flesh", Items.ROTTEN_FLESH, 16, 1, 16);
        b.buy("bones", Items.BONE, 12, 1, 16);
        b.sell("candle", 1, new ItemStack(Items.CANDLE, 2), 8);
        b.sell("splash_heal", 4, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING), 6);
        b.sell("regen", 4, potion(Potions.REGENERATION), 4);
        b.sellIf(Standing.FRIENDLY, "golden_apple", 8, new ItemStack(Items.GOLDEN_APPLE), 3);
        b.sellIf(Standing.FRIENDLY, "glow_berries", 2, new ItemStack(Items.GLOW_BERRIES, 8), 6);
        b.sellIf(Standing.HONORED, "xp_bottles", 4, new ItemStack(Items.EXPERIENCE_BOTTLE, 4), 6);
        b.sellIf(Standing.REVERED, "mending", 40, book(p, Enchantments.MENDING, 1), 1);
        b.sellIf(Standing.EXALTED, "totem", 64, new ItemStack(Items.TOTEM_OF_UNDYING), 1);
    }

    private static void fence(B b, ServerPlayer p) {
        b.buy("gold", Items.GOLD_INGOT, 3, 2, 16);
        b.buy("diamond_buy", Items.DIAMOND, 1, 4, 8);
        b.buy("emerald_buy", Items.EMERALD, 2, 2, 16);
        b.sell("pearl", 3, new ItemStack(Items.ENDER_PEARL), 6);
        b.sell("name_tag", 6, new ItemStack(Items.NAME_TAG), 3);
        b.sell("saddle", 5, new ItemStack(Items.SADDLE), 2);
        b.sell("lead", 2, new ItemStack(Items.LEAD), 4);
        b.sell("tnt", 3, new ItemStack(Items.TNT, 2), 4);
        b.sell("spyglass", 4, new ItemStack(Items.SPYGLASS), 2);
        Item[] discs = {Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS, Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_MALL,
                Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_WAIT, Items.MUSIC_DISC_PIGSTEP, Items.MUSIC_DISC_OTHERSIDE};
        b.sell("disc", 8, new ItemStack(discs[b.r.nextInt(discs.length)]), 1);
        b.sellIf(Standing.FRIENDLY, "diamond", 8, new ItemStack(Items.DIAMOND), 4);
        b.sellIf(Standing.FRIENDLY, "ench_book", 10, enchantedBook(p, b.r), 1);
        b.sellIf(Standing.FRIENDLY, "shulker", 10, new ItemStack(Items.SHULKER_SHELL), 2);
        b.sellIf(Standing.HONORED, "echo", 20, new ItemStack(Items.ECHO_SHARD), 2);
        b.sellIf(Standing.HONORED, "trident", 30, new ItemStack(Items.TRIDENT), 1);
        b.sellIf(Standing.REVERED, "scrap", 30, new ItemStack(Items.NETHERITE_SCRAP), 2);
        b.sellIf(Standing.REVERED, "totem", 45, new ItemStack(Items.TOTEM_OF_UNDYING), 1);
        b.sellIf(Standing.EXALTED, "netherite", 100, new ItemStack(Items.NETHERITE_INGOT), 1);
        if (b.st.atLeast(Standing.EXALTED)) {
            for (ResourceLocation id : glyphsFor(p, 4, 1, b.r)) b.sell("contraband_" + id.getPath(), 70, GlyphScrollItem.of(id), 1);
        }
    }

    private static void grocer(B b) {
        b.buy("wheat", Items.WHEAT, 18, 1, 16);
        b.buy("carrot", Items.CARROT, 16, 1, 16);
        b.buy("potato", Items.POTATO, 16, 1, 16);
        b.buy("pumpkin", Items.PUMPKIN, 6, 1, 12);
        b.buy("melon", Items.MELON, 4, 1, 12);
        b.sell("bread", 1, new ItemStack(Items.BREAD, 4), 16);
        b.sell("apples", 1, new ItemStack(Items.APPLE, 4), 12);
        b.sell("baked_potato", 1, new ItemStack(Items.BAKED_POTATO, 5), 12);
        b.sell("carrots", 1, new ItemStack(Items.CARROT, 6), 12);
        b.sell("berries", 1, new ItemStack(Items.SWEET_BERRIES, 8), 12);
        b.sell("seeds", 1, new ItemStack(Items.WHEAT_SEEDS, 12), 8);
        b.sell("cane", 1, new ItemStack(Items.SUGAR_CANE, 6), 8);
        b.sell("cookies", 1, new ItemStack(Items.COOKIE, 8), 8);
        // Armazém: o Pergaminho de Retorno também sai daqui, pelo mesmo preço da estalagem.
        b.modSell("return_scroll", "return_scroll", 3, 2, 8);
        b.sellIf(Standing.FRIENDLY, "golden_carrot", 2, new ItemStack(Items.GOLDEN_CARROT, 3), 6);
        b.sellIf(Standing.FRIENDLY, "glistering", 2, new ItemStack(Items.GLISTERING_MELON_SLICE, 2), 6);
        b.sellIf(Standing.HONORED, "cake", 2, new ItemStack(Items.CAKE), 4);
        b.sellIf(Standing.EXALTED, "golden_apple", 6, new ItemStack(Items.GOLDEN_APPLE), 2);
    }

    private static void tailor(B b, ServerPlayer p) {
        b.buy("leather", Items.LEATHER, 6, 1, 16);
        b.buy("string", Items.STRING, 12, 1, 16);
        b.buy("wool", Items.WHITE_WOOL, 16, 1, 16);
        b.sell("wool_sell", 1, new ItemStack(Items.WHITE_WOOL, 4), 12);
        DyeColor dye = DyeColor.values()[b.r.nextInt(DyeColor.values().length)];
        b.sell("dye", 1, new ItemStack(net.minecraft.world.item.DyeItem.byColor(dye), 6), 8);
        b.sell("banner", 2, new ItemStack(Items.BLUE_BANNER), 4);
        b.sell("painting", 2, new ItemStack(Items.PAINTING, 2), 4);
        b.sell("item_frame", 1, new ItemStack(Items.ITEM_FRAME, 2), 6);
        b.sell("leather_cap", 2, dyed(Items.LEATHER_HELMET, 0x3F6FD8), 2);
        b.sell("leather_tunic", 4, dyed(Items.LEATHER_CHESTPLATE, 0x3F6FD8), 2);
        b.sell("leather_pants", 3, dyed(Items.LEATHER_LEGGINGS, 0x2B3A67), 2);
        b.sell("leather_boots", 2, dyed(Items.LEATHER_BOOTS, 0x4A3220), 2);
        b.sellIf(Standing.FRIENDLY, "horse_armor", 4, new ItemStack(Items.LEATHER_HORSE_ARMOR), 2);
        b.sellIf(Standing.FRIENDLY, "saddle", 5, new ItemStack(Items.SADDLE), 1);
        b.sellIf(Standing.HONORED, "name_tag", 5, new ItemStack(Items.NAME_TAG), 2);
        if (b.st.atLeast(Standing.REVERED)) {
            ItemStack cloak = dyed(Items.LEATHER_CHESTPLATE, 0xC9A23A);
            enchant(p, cloak, Enchantments.PROTECTION, 3);
            enchant(p, cloak, Enchantments.UNBREAKING, 3);
            b.sell("royal_cloak", 24, cloak, 1);
        }
        if (b.st.atLeast(Standing.EXALTED)) {
            ItemStack boots = dyed(Items.LEATHER_BOOTS, 0xC9A23A);
            enchant(p, boots, Enchantments.FEATHER_FALLING, 4);
            enchant(p, boots, Enchantments.SOUL_SPEED, 2);
            b.sell("royal_boots", 30, boots, 1);
        }
    }

    // =====================================================================================
    //  Auxiliares
    // =====================================================================================
    private static ItemStack potion(Holder<Potion> p) {
        return PotionContents.createItemStack(Items.POTION, p);
    }

    private static ItemStack dyed(Item item, int rgb) {
        ItemStack s = new ItemStack(item);
        s.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new DyedItemColor(rgb, true));
        return s;
    }

    private static Optional<Holder.Reference<Enchantment>> enchantment(ServerPlayer p, ResourceKey<Enchantment> key) {
        return p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
    }

    private static void enchant(ServerPlayer p, ItemStack stack, ResourceKey<Enchantment> key, int level) {
        enchantment(p, key).ifPresent(h -> stack.enchant(h, level));
    }

    private static ItemStack book(ServerPlayer p, ResourceKey<Enchantment> key, int level) {
        return enchantment(p, key).map(h -> EnchantedBookItem.createForEnchantment(new EnchantmentInstance(h, level)))
                .orElse(ItemStack.EMPTY);
    }

    /** Livro encantado útil sorteado pela semente do dia. */
    private static ItemStack enchantedBook(ServerPlayer p, RandomSource r) {
        List<ResourceKey<Enchantment>> keys = List.of(Enchantments.UNBREAKING, Enchantments.SHARPNESS, Enchantments.PROTECTION,
                Enchantments.EFFICIENCY, Enchantments.FORTUNE, Enchantments.LOOTING, Enchantments.FEATHER_FALLING,
                Enchantments.POWER, Enchantments.RESPIRATION, Enchantments.SILK_TOUCH);
        int[] levels = {3, 4, 4, 4, 2, 2, 4, 4, 3, 1};
        int i = r.nextInt(keys.size());
        return book(p, keys.get(i), levels[i]);
    }

    /**
     * Glifos de um tier que o jogador ainda NÃO conhece (não lendários), preferindo os que ele consegue usar agora
     * (elemento despertado, Arcano ou Magia de Origem da classe dele).
     */
    public static List<ResourceLocation> glyphsFor(ServerPlayer p, int tier, int count, RandomSource r) {
        MagicData d = MagicApi.get(p);
        List<ResourceLocation> usable = new ArrayList<>();
        List<ResourceLocation> others = new ArrayList<>();
        for (Glyph g : GlyphRegistry.all()) {
            if (g.tier() != tier || g.isLegendary() || d.knowsGlyph(g.id())) continue;
            if (g.origin() != null && g.origin() != d.path()) continue;
            boolean canUse = g.origin() != null || g.element() == com.renanpontes.isekaimahou.core.Element.ARCANE
                    || d.isAwakened(g.element());
            (canUse ? usable : others).add(g.id());
        }
        // Ordem estável antes de embaralhar (o registro é um mapa): a semente do dia decide.
        usable.sort(null);
        others.sort(null);
        Collections.shuffle(usable, new java.util.Random(r.nextLong()));
        Collections.shuffle(others, new java.util.Random(r.nextLong()));
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : usable) if (out.size() < count) out.add(id);
        for (ResourceLocation id : others) if (out.size() < count) out.add(id);
        return out;
    }

    /**
     * Onda 10 — Mecânico das cidades do mundo: o básico para todos (barco, carroça, kit, cristal); o resto pela
     * reputação no reino, como o Ferreiro. Os veículos saem com meio tanque.
     */
    private static void mechanic(B b) {
        b.buy("iron", Items.IRON_INGOT, 4, 2, 12);
        b.buy("copper", Items.COPPER_INGOT, 8, 1, 12);
        b.sell("crystal", 5, new ItemStack(CoreRegistries.MANA_CRYSTAL.get()), 6);
        vehicle(b, "arcane_boat", 22, null);
        vehicle(b, "golem_cart", 28, null);
        b.modSell("repair_kit", "vehicle_repair_kit", 10, 2, 3);
        vehicle(b, "flying_carpet", 45, Standing.FRIENDLY);
        if (b.st.atLeast(Standing.FRIENDLY)) {
            b.modSell("part_engine", "vehicle_part_engine", 18, 1, 2);
            b.modSell("part_armor", "vehicle_part_armor", 16, 1, 2);
        }
        vehicle(b, "arcane_broom", 72, Standing.HONORED);
        vehicle(b, "mana_rover", 64, Standing.HONORED);
        if (b.st.atLeast(Standing.HONORED)) b.modSell("part_storage", "vehicle_part_storage", 14, 1, 2);
        vehicle(b, "mini_airship", 110, Standing.REVERED);
        vehicle(b, "tide_skiff", 90, Standing.REVERED);
        vehicle(b, "sky_galleon", 128, Standing.EXALTED);
    }

    /** Um veículo do módulo vehicles (pelo id; some se o módulo faltar), um por dia, com meio tanque. */
    private static void vehicle(B b, String id, int coins, @javax.annotation.Nullable Standing min) {
        if (min != null && !b.st.atLeast(min)) return;
        BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(id)).ifPresent(i -> b.sell("vehicle_" + id, coins,
                com.renanpontes.isekaimahou.feature.vehicles.VehicleStacks.withFuel(new ItemStack(i), 600), 1));
    }

    /** Moeda: Moeda da Guilda (módulo guild) ou, se faltar, esmeralda. */
    public static Item coin() {
        return BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id("guild_coin")).orElse(Items.EMERALD);
    }

    /** Monta as ofertas com preço ajustado. */
    private static final class B {
        final List<Trade> out = new ArrayList<>();
        final float mult;
        final Standing st;
        final RandomSource r;
        final Item coin = coin();

        B(float mult, Standing st, RandomSource r) {
            this.mult = mult;
            this.st = st;
            this.r = r;
        }

        /** O jogador paga moedas. Acima de 64 a segunda pilha de moedas entra no 2º espaço. */
        void sell(String key, int coins, ItemStack result, int maxUses) {
            if (result.isEmpty()) return;
            int price = Math.max(1, Math.min(128, Math.round(coins * mult)));
            ItemCost a = new ItemCost(coin, Math.min(64, price));
            Optional<ItemCost> extra = price > 64 ? Optional.of(new ItemCost(coin, price - 64)) : Optional.empty();
            out.add(new Trade(key, new MerchantOffer(a, extra, result, maxUses, 0, 0.0F)));
        }

        void sellIf(Standing min, String key, int coins, ItemStack result, int maxUses) {
            if (st.atLeast(min)) sell(key, coins, result, maxUses);
        }

        /** Item de outro módulo pelo id (some se o módulo não existir). */
        void modSell(String key, String path, int coins, int count, int maxUses) {
            BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(path)).ifPresent(i -> sell(key, coins, new ItemStack(i, count), maxUses));
        }

        /** O mercador compra um item de outro módulo pelo id (a oferta some se o módulo não existir). */
        void modBuy(String key, String path, int count, int coins, int maxUses) {
            BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(path)).ifPresent(i -> buy(key, i, count, coins, maxUses));
        }

        /** O mercador compra: com reputação alta ele paga mais. */
        void buy(String key, Item item, int count, int coins, int maxUses) {
            int pay = Math.max(1, Math.round(coins * (2.0F - Math.min(mult, 1.5F))));
            out.add(new Trade("buy_" + key, new MerchantOffer(new ItemCost(item, count), Optional.empty(),
                    new ItemStack(coin, Math.min(64, pay)), maxUses, 0, 0.0F)));
        }
    }

    private CityTrades() {}
}
