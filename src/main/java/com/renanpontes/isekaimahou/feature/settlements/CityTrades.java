package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Dialogue;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.settlements.entity.TraderNpc;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleKind;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleMechanic;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleStacks;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleUpgrades;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Onda 6 (city_trades): COMERCIANTES DE OFÍCIO da cidade fundada — "talvez ter ferreiros e outros tipos de comerciantes".
 * <ul>
 *   <li>Cada ofício ({@link Job}) mora num prédio: Ferreiro (ferraria), Curandeiro (casa do curandeiro), Escriba
 *       (biblioteca), Taverneiro (taverna), Estribeiro (estábulo), Joalheiro (oficina), Lenhador (serraria), Minerador
 *       (mina) e Mercador Arcano (torre de magia ou pináculo de mana). Um por ofício por cidade; o prédio de maior nível
 *       é a loja dele.</li>
 *   <li>O que vendem está NUM LUGAR SÓ ({@link #catalog}): utilidade, material e conveniência, com itens que já existem
 *       no mod (por id, opcionais). O NÍVEL DO PRÉDIO abre mais ofertas e aumenta o estoque; o DEGRAU da cidade abre os
 *       itens raros e aumenta um pouco o estoque. Nada de equipamento novo de fim de jogo.</li>
 *   <li>O estoque é da cidade (não de cada jogador) e volta ao amanhecer — carimbado com o dia do Overworld, então renova
 *       mesmo com a cidade descarregada; o gancho de "novo dia" do {@link CityPace} também o renova e escreve no Diário.</li>
 *   <li>O Senhor recebe {@link #FEE_PCT}% de cada venda e de cada serviço no cofre.</li>
 *   <li>Serviços (agachar + clicar): o Ferreiro conserta e reforça a peça da mão, o Curandeiro cura, o Taverneiro conta
 *       um boato. Sempre com o preço antes e um segundo clique para confirmar.</li>
 *   <li>Cidade é sagrada: comerciante que morre ou se perde volta em pouco tempo.</li>
 * </ul>
 * Estado salvo em {@link Settlement#trades} (opcional): {@code Npc} (ofício → UUID), {@code Names}, {@code Day},
 * {@code Used} (ofício.oferta → vendidos hoje), {@code FeeCents}, {@code FeeDay}, {@code FeeLast}, {@code FeeTotal},
 * {@code Sales}, {@code SalesLast}, {@code HookDay}.
 */
public final class CityTrades {
    /** Taxa do Senhor sobre vendas e serviços (%). */
    public static final int FEE_PCT = 10;
    /** Sem sinal de vida por este tempo (ticks), o comerciante é dado como perdido e outro chega. */
    private static final long LOST_AFTER = 2400;
    /** Depois de morrer, quanto tempo até outro assumir a loja (ticks). */
    private static final long RESPAWN_DELAY = 1200;
    /** Reforço do Ferreiro (Inquebrável I, II, III): preço de cada passo. */
    private static final int[] REINFORCE_COST = {15, 35, 60};
    /** Quantos boatos o Taverneiro sabe (textos {@code trade.rumor.N}). */
    private static final int RUMORS = 8;

    // =====================================================================================
    //  Ofícios
    // =====================================================================================
    /** Os ofícios. O id é salvo (NBT da cidade e do NPC): só ACRESCENTE valores. */
    public enum Job {
        BLACKSMITH("blacksmith", 0xD9824A, true, Set.of("apron", "hammer", "beard"), "smithy"),
        HEALER("healer", 0x7DDA6B, false, Set.of("hood", "satchel"), "hospital"),
        SCRIBE("scribe", 0x8FC8FF, false, Set.of("cap", "cape"), "library"),
        INNKEEPER("innkeeper", 0xE8B84A, false, Set.of("apron", "beard"), "tavern"),
        STABLEHAND("stablehand", 0xC9A27A, false, Set.of("cap", "satchel"), "stable"),
        JEWELER("jeweler", 0xE07BD0, false, Set.of("apron", "satchel"), "workshop"),
        LUMBERJACK("lumberjack", 0x8FBF5A, true, Set.of("axe", "beard"), "sawmill"),
        MINER("miner", 0xB0BAC6, true, Set.of("helmet", "hammer", "beard"), "mine"),
        ARCANIST("arcanist", 0xB98CFF, false, Set.of("hood", "cape"), "magic_tower", "mana_spire"),
        // onda 10 (vehicles_mechanic): "que sejam vendidos na cidade por um mecânico"
        MECHANIC("mechanic", 0x5FB8D9, true, Set.of("cap", "apron", "hammer"), "garage");

        public final String id;
        public final int color;
        /** Faz a animação de trabalho de vez em quando (martela, racha lenha...). */
        public final boolean manual;
        /** Peças do modelo que este ofício mostra. */
        public final Set<String> bones;
        /** Prédios que trazem o ofício (o primeiro é o que o painel indica para quem ainda não tem). */
        public final String[] buildings;

        Job(String id, int color, boolean manual, Set<String> bones, String... buildings) {
            this.id = id;
            this.color = color;
            this.manual = manual;
            this.bones = bones;
            this.buildings = buildings;
        }

        public MutableComponent displayName() {
            return SettlementsLang.tr("trade.job." + id);
        }

        /** Nome do ofício para o NPC (em português muda com o gênero: Ferreiro / Ferreira). */
        public MutableComponent displayName(boolean female) {
            return SettlementsLang.tr("trade.job." + id + (female ? ".f" : ""));
        }

        public boolean worksAt(String key) {
            for (String b : buildings) if (b.equals(key)) return true;
            return false;
        }

        @Nullable
        public static Job byId(String id) {
            for (Job j : values()) if (j.id.equals(id)) return j;
            return null;
        }

        /** O ofício que um prédio traz (null = nenhum). */
        @Nullable
        public static Job ofBuilding(String key) {
            for (Job j : values()) if (j.worksAt(key)) return j;
            return null;
        }
    }

    /** Fala sorteada de um ofício ({@code trade.line.<ofício>.<grupo>.<n>}). */
    public static MutableComponent line(Job job, String group, int count, RandomSource random) {
        return SettlementsLang.tr("trade.line." + job.id + "." + group + "." + random.nextInt(count));
    }

    /** A loja do ofício: o prédio dele de maior nível (-1 = a cidade não tem). */
    public static int workCell(Settlement s, Job job) {
        int best = -1, bestLevel = 0;
        for (Map.Entry<Integer, String> e : s.buildings.entrySet()) {
            if (!job.worksAt(e.getValue())) continue;
            int lvl = shopLevelAt(s, e.getKey());
            if (lvl > bestLevel) {
                best = e.getKey();
                bestLevel = lvl;
            }
        }
        return best;
    }

    /** Nível da loja num lote: o do prédio; danificado por um ataque, atende como nível 1 até o conserto. */
    private static int shopLevelAt(Settlement s, int cell) {
        return s.damaged.contains(cell) ? 1 : s.levelOf(cell);
    }

    /** Nível da loja do ofício (0 = a cidade não tem o prédio). */
    public static int shopLevel(Settlement s, Job job) {
        int c = workCell(s, job);
        return c < 0 ? 0 : shopLevelAt(s, c);
    }

    /** Onde o comerciante fica: o centro do lote, na altura do piso do prédio. */
    public static BlockPos workPos(Settlement s, int cell) {
        BlockPos c = CityLayout.cellCenter(s.heart, cell);
        Integer base = s.plotBase.get(cell);
        return base == null ? c : new BlockPos(c.getX(), base + 1, c.getZ());
    }

    // =====================================================================================
    //  Catálogo (o que cada ofício compra e vende — números num lugar só)
    // =====================================================================================
    /**
     * Uma oferta.
     *
     * @param key     chave estável da oferta dentro do ofício (o estoque do dia é contado por ela)
     * @param buy     true = o comerciante COMPRA ({@code stack} é o que o jogador entrega; {@code price} = moedas pagas)
     * @param price   moedas (venda: antes do decreto de preços)
     * @param stock   trocas por dia numa loja de nível 1, no degrau 1
     * @param minShop nível do prédio que abre a oferta
     * @param minTier degrau da cidade que abre a oferta (itens raros)
     */
    public record Good(String key, boolean buy, ItemStack stack, int price, int stock, int minShop, int minTier) {
        public boolean open(int shop, int tier) {
            return shop >= minShop && tier >= minTier;
        }

        /**
         * Trocas por dia. Venda: +25% por nível do prédio acima do 1 e +10% por degrau acima do Acampamento (até 2,8×).
         * Compra: só +12,5% por nível do prédio (até 1,5×) — vender matéria-prima não pode virar fonte fácil de moedas.
         */
        public int quantity(int shop, int tier) {
            if (buy) return Math.max(1, Math.round(stock * (1F + 0.125F * (Math.max(1, shop) - 1))));
            return Math.max(1, Math.round(stock * (0.75F + 0.25F * Math.max(1, shop)) * (1F + 0.1F * (Math.max(1, tier) - 1))));
        }
    }

    /** Monta a prateleira; item de outro módulo que não existe simplesmente não entra. */
    private static final class Shelf {
        final List<Good> goods = new ArrayList<>();

        void sell(String key, ItemStack stack, int price, int stock, int minShop, int minTier) {
            if (!stack.isEmpty()) goods.add(new Good(key, false, stack, price, stock, minShop, minTier));
        }

        void sell(String key, ItemLike item, int count, int price, int stock, int minShop) {
            sell(key, new ItemStack(item, count), price, stock, minShop, 1);
        }

        void buy(String key, ItemLike item, int count, int pay, int stock, int minShop) {
            goods.add(new Good(key, true, new ItemStack(item, count), pay, stock, minShop, 1));
        }

        void buy(String key, ItemStack stack, int pay, int stock, int minShop) {
            if (!stack.isEmpty()) goods.add(new Good(key, true, stack, pay, stock, minShop, 1));
        }
    }

    /** Item do mod por id (vazio se o módulo dele não existir). */
    private static ItemStack mod(String path, int count) {
        return BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(path)).map(i -> new ItemStack(i, count)).orElse(ItemStack.EMPTY);
    }

    private static ItemStack potion(Holder<net.minecraft.world.item.alchemy.Potion> p) {
        return PotionContents.createItemStack(Items.POTION, p);
    }

    private static final String[] GEMS = {"ruby", "sapphire", "emerald", "topaz", "amethyst", "onyx"};
    private static final String[] ESSENCES = {"fire", "water", "earth", "wind", "light", "shadow"};
    private static final Item[] LOGS = {Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.DARK_OAK_LOG, Items.ACACIA_LOG,
            Items.JUNGLE_LOG};
    private static final Item[] EXOTIC_LOGS = {Items.CHERRY_LOG, Items.MANGROVE_LOG, Items.BAMBOO_BLOCK, Items.CRIMSON_STEM,
            Items.WARPED_STEM};

    /** Madeira da terra de cada estilo: tora, tábuas e muda. */
    private static Item[] wood(SettlementStyle style) {
        return switch (style) {
            case DESERT -> new Item[] {Items.ACACIA_LOG, Items.ACACIA_PLANKS, Items.ACACIA_SAPLING};
            case NORDIC, DWARVEN -> new Item[] {Items.SPRUCE_LOG, Items.SPRUCE_PLANKS, Items.SPRUCE_SAPLING};
            case ORIENTAL -> new Item[] {Items.CHERRY_LOG, Items.CHERRY_PLANKS, Items.CHERRY_SAPLING};
            case ELVEN -> new Item[] {Items.BIRCH_LOG, Items.BIRCH_PLANKS, Items.BIRCH_SAPLING};
            case GOTHIC -> new Item[] {Items.DARK_OAK_LOG, Items.DARK_OAK_PLANKS, Items.DARK_OAK_SAPLING};
            default -> new Item[] {Items.OAK_LOG, Items.OAK_PLANKS, Items.OAK_SAPLING};
        };
    }

    /**
     * Tudo o que o ofício pode ter na prateleira (inclusive o que ainda está trancado por nível do prédio ou degrau).
     * {@code day} gira as ofertas que mudam todo dia (gemas, essências, madeiras de fora).
     */
    public static List<Good> catalog(Job job, SettlementStyle style, long day) {
        Shelf b = new Shelf();
        int d = (int) Math.floorMod(day, 60L);
        switch (job) {
            case BLACKSMITH -> {
                b.buy("iron", Items.IRON_INGOT, 4, 2, 12, 1);
                b.buy("coal", Items.COAL, 10, 1, 12, 1);
                b.buy("star_metal", mod("world_events_star_metal", 1), 5, 4, 3);
                b.sell("pickaxe", Items.IRON_PICKAXE, 1, 6, 3, 1);
                b.sell("axe", Items.IRON_AXE, 1, 6, 3, 1);
                b.sell("shovel", Items.IRON_SHOVEL, 1, 3, 3, 1);
                b.sell("sword", Items.IRON_SWORD, 1, 6, 3, 1);
                b.sell("shield", Items.SHIELD, 1, 5, 2, 1);
                b.sell("arrows", Items.ARROW, 16, 2, 6, 1);
                b.sell("helmet", Items.IRON_HELMET, 1, 7, 2, 2);
                b.sell("chestplate", Items.IRON_CHESTPLATE, 1, 12, 2, 2);
                b.sell("leggings", Items.IRON_LEGGINGS, 1, 10, 2, 2);
                b.sell("boots", Items.IRON_BOOTS, 1, 6, 2, 2);
                b.sell("bucket", Items.BUCKET, 1, 3, 3, 2);
                b.sell("shears", Items.SHEARS, 1, 2, 3, 2);
                b.sell("crossbow", Items.CROSSBOW, 1, 6, 2, 3);
                b.sell("anvil", Items.ANVIL, 1, 14, 1, 3);
                b.sell("smithing_table", Items.SMITHING_TABLE, 1, 4, 1, 3);
                // raros: travados pelo degrau (Cidade e Capital)
                b.sell("mithril", mod("mithril_ingot", 1), 28, 2, 4, 4);
                b.sell("arcane_template", mod("arcane_upgrade_template", 1), 55, 1, 5, 5);
            }
            case HEALER -> {
                b.buy("wart", Items.NETHER_WART, 6, 1, 12, 1);
                b.buy("spider_eye", Items.SPIDER_EYE, 3, 1, 12, 1);
                b.buy("melon", Items.GLISTERING_MELON_SLICE, 2, 1, 8, 2);
                b.sell("hp_lesser", mod("health_potion_lesser", 1), 2, 6, 1, 1);
                b.sell("healing", potion(Potions.HEALING), 3, 4, 1, 1);
                b.sell("milk", Items.MILK_BUCKET, 1, 1, 3, 1);
                b.sell("hp_common", mod("health_potion_common", 1), 4, 4, 2, 1);
                b.sell("splash_healing", PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING), 4, 4, 2, 1);
                b.sell("regeneration", potion(Potions.REGENERATION), 4, 3, 2, 1);
                b.sell("fire_resistance", potion(Potions.FIRE_RESISTANCE), 4, 3, 3, 1);
                b.sell("hp_greater", mod("health_potion_greater", 1), 8, 3, 3, 3);
                b.sell("golden_apple", new ItemStack(Items.GOLDEN_APPLE), 10, 1, 3, 3);
                b.sell("elixir_vitality", mod("witch_elixir_vitality", 1), 10, 2, 4, 4);
                b.sell("hp_superior", mod("health_potion_superior", 1), 14, 2, 4, 4);
                b.sell("hp_supreme", mod("health_potion_supreme", 1), 24, 1, 5, 5);
            }
            case SCRIBE -> {
                b.buy("paper", Items.PAPER, 12, 1, 12, 1);
                b.buy("book", Items.BOOK, 2, 1, 8, 1);
                b.buy("feather", Items.FEATHER, 8, 1, 8, 1);
                b.buy("ink", Items.INK_SAC, 6, 1, 8, 1);
                b.sell("paper_sell", Items.PAPER, 8, 1, 8, 1);
                b.sell("book_sell", Items.BOOK, 1, 1, 8, 1);
                b.sell("ink_sell", Items.INK_SAC, 3, 1, 6, 1);
                b.sell("quill", Items.FEATHER, 3, 1, 6, 1);
                b.sell("writable", Items.WRITABLE_BOOK, 1, 2, 4, 2);
                b.sell("blank_scroll", mod("blank_recipe_scroll", 1), 4, 4, 2, 1);
                b.sell("map", Items.MAP, 1, 2, 3, 2);
                b.sell("bookshelf", Items.BOOKSHELF, 2, 4, 4, 3);
                b.sell("name_tag", Items.NAME_TAG, 1, 6, 2, 3);
                b.sell("lectern", Items.LECTERN, 1, 5, 2, 4);
                b.sell("xp_bottle", Items.EXPERIENCE_BOTTLE, 4, 6, 2, 4);
                b.sell("lore_page", mod("lore_page", 1), 12, 1, 3, 3);
            }
            case INNKEEPER -> {
                b.buy("wheat", Items.WHEAT, 16, 1, 12, 1);
                b.buy("beef", Items.BEEF, 6, 1, 8, 1);
                b.buy("porkchop", Items.PORKCHOP, 6, 1, 8, 1);
                b.buy("potato", Items.POTATO, 16, 1, 8, 1);
                b.sell("bread", Items.BREAD, 3, 1, 12, 1);
                b.sell("steak", Items.COOKED_BEEF, 4, 2, 8, 1);
                b.sell("baked_potato", Items.BAKED_POTATO, 5, 1, 8, 1);
                b.sell("honey", Items.HONEY_BOTTLE, 1, 1, 6, 1);
                b.sell("stew", Items.RABBIT_STEW, 1, 2, 4, 2);
                b.sell("pie", Items.PUMPKIN_PIE, 2, 1, 6, 2);
                b.sell("return_scroll", mod("return_scroll", 2), 3, 4, 2, 1);
                b.sell("golden_carrot", Items.GOLDEN_CARROT, 4, 3, 4, 3);
                b.sell("cake", Items.CAKE, 1, 3, 2, 3);
                b.sell("bond_incense", mod("bond_incense", 1), 14, 1, 3, 3);
                b.sell("golden_apple", new ItemStack(Items.GOLDEN_APPLE), 10, 1, 4, 4);
            }
            case STABLEHAND -> {
                b.buy("hay", Items.HAY_BLOCK, 4, 1, 8, 1);
                b.buy("leather", Items.LEATHER, 6, 1, 8, 1);
                b.buy("apple", Items.APPLE, 8, 1, 8, 1);
                b.sell("saddle", Items.SADDLE, 1, 5, 2, 1);
                b.sell("lead", Items.LEAD, 2, 2, 4, 1);
                b.sell("hay_sell", Items.HAY_BLOCK, 4, 2, 6, 1);
                b.sell("wheat_sell", Items.WHEAT, 16, 1, 6, 1);
                b.sell("mount_whistle", mod("mount_whistle", 1), 10, 1, 2, 1);
                b.sell("leather_armor", Items.LEATHER_HORSE_ARMOR, 1, 4, 2, 2);
                b.sell("name_tag", Items.NAME_TAG, 1, 6, 2, 2);
                b.sell("iron_armor", Items.IRON_HORSE_ARMOR, 1, 12, 1, 3);
                b.sell("golden_carrot", Items.GOLDEN_CARROT, 6, 4, 4, 3);
                b.sell("land_drake_reins", mod("land_drake_reins", 1), 45, 1, 3, 3);
                b.sell("golden_armor", new ItemStack(Items.GOLDEN_HORSE_ARMOR), 20, 1, 4, 4);
                b.sell("diamond_armor", new ItemStack(Items.DIAMOND_HORSE_ARMOR), 40, 1, 5, 5);
            }
            case JEWELER -> {
                b.buy("gold", Items.GOLD_INGOT, 3, 2, 12, 1);
                b.buy("diamond_buy", Items.DIAMOND, 1, 4, 6, 1);
                b.buy("lapis", Items.LAPIS_LAZULI, 6, 1, 12, 1);
                b.buy("amethyst", Items.AMETHYST_SHARD, 6, 1, 12, 1);
                b.sell("gem_chisel", mod("gem_chisel", 1), 16, 1, 1, 1);
                b.sell("gold_nuggets", Items.GOLD_NUGGET, 9, 2, 4, 1);
                b.sell("amethyst_sell", Items.AMETHYST_SHARD, 4, 2, 4, 1);
                // gemas lascadas: os tipos mudam a cada dia (1 tipo no nível 1, 2 no nível 2–3, 3 no nível 4–5)
                for (int i = 0; i < 3; i++) {
                    String gem = GEMS[(d + i * 2) % GEMS.length];
                    b.sell("chipped_" + gem, mod("gem_" + gem + "_chipped", 1), 22, 1, i == 0 ? 1 : i == 1 ? 2 : 4, 1);
                }
                b.sell("diamond", Items.DIAMOND, 1, 8, 3, 3);
                // raro: uma gema lapidada por dia, só na Cidade
                b.sell("cut_" + GEMS[(d + 3) % GEMS.length], mod("gem_" + GEMS[(d + 3) % GEMS.length], 1), 60, 1, 4, 4);
            }
            case LUMBERJACK -> {
                Item[] w = wood(style);
                b.buy("bone_meal", Items.BONE_MEAL, 8, 1, 8, 1);
                b.buy("sapling", w[2], 8, 1, 8, 1);
                b.sell("logs", w[0], 16, 2, 8, 1);
                b.sell("planks", w[1], 32, 2, 6, 1);
                b.sell("sticks", Items.STICK, 16, 1, 6, 1);
                // toras de outras terras: duas por dia
                int n = 0;
                for (int i = 0; i < LOGS.length && n < 2; i++) {
                    Item log = LOGS[(d + i) % LOGS.length];
                    if (log == w[0]) continue;
                    b.sell("far_" + BuiltInRegistries.ITEM.getKey(log).getPath(), log, 16, 3, 4, 2);
                    n++;
                }
                b.sell("charcoal", Items.CHARCOAL, 8, 2, 6, 2);
                b.sell("ladder", Items.LADDER, 8, 1, 4, 2);
                b.sell("saplings", w[2], 4, 1, 4, 3);
                b.sell("scaffolding", Items.SCAFFOLDING, 16, 3, 4, 3);
                b.sell("chest", Items.CHEST, 4, 2, 4, 3);
                Item exotic = EXOTIC_LOGS[d % EXOTIC_LOGS.length];
                if (exotic != w[0]) b.sell("exotic_" + BuiltInRegistries.ITEM.getKey(exotic).getPath(), exotic, 16, 4, 3, 4);
            }
            case MINER -> {
                b.buy("torch", Items.TORCH, 32, 1, 8, 1);
                b.buy("bread", Items.BREAD, 6, 1, 8, 1);
                b.sell("cobblestone", Items.COBBLESTONE, 32, 1, 8, 1);
                b.sell("stone", Items.STONE, 32, 2, 6, 1);
                b.sell("coal", Items.COAL, 8, 2, 6, 1);
                b.sell("flint", Items.FLINT, 8, 1, 4, 1);
                b.sell("raw_iron", Items.RAW_IRON, 4, 3, 6, 2);
                b.sell("raw_copper", Items.RAW_COPPER, 8, 2, 6, 2);
                b.sell("redstone", Items.REDSTONE, 8, 3, 4, 3);
                b.sell("lapis_sell", Items.LAPIS_LAZULI, 6, 3, 4, 3);
                b.sell("raw_gold", Items.RAW_GOLD, 3, 4, 4, 3);
                b.sell("quartz", Items.QUARTZ, 8, 4, 4, 4);
                b.sell("obsidian", Items.OBSIDIAN, 4, 4, 3, 4);
                b.sell("diamond", new ItemStack(Items.DIAMOND), 9, 2, 4, 4);
            }
            case ARCANIST -> {
                b.buy("amethyst", Items.AMETHYST_SHARD, 6, 1, 12, 1);
                b.buy("glowstone", Items.GLOWSTONE_DUST, 4, 1, 12, 1);
                b.buy("lapis", Items.LAPIS_LAZULI, 6, 1, 12, 1);
                b.sell("mana_crystal", CoreRegistries.MANA_CRYSTAL.get(), 1, 5, 4, 1);
                b.sell("mp_lesser", mod("mana_potion_lesser", 1), 2, 6, 1, 1);
                b.sell("glowstone_sell", Items.GLOWSTONE_DUST, 4, 2, 4, 1);
                b.sell("mp_common", mod("mana_potion_common", 1), 4, 4, 2, 1);
                b.sell("essence_arcane", mod("essence_arcane", 1), 6, 3, 2, 1);
                b.sell("blank_scroll", mod("blank_recipe_scroll", 1), 4, 3, 2, 1);
                b.sell("ender_pearl", Items.ENDER_PEARL, 1, 3, 3, 3);
                // essências dos elementos: duas por dia
                for (int i = 0; i < 2; i++) {
                    String el = ESSENCES[(d + i * 3) % ESSENCES.length];
                    b.sell("essence_" + el, mod("essence_" + el, 1), 8, 2, 3, 1);
                }
                b.sell("mp_greater", mod("mana_potion_greater", 1), 8, 3, 3, 3);
                b.sell("mp_superior", mod("mana_potion_superior", 1), 14, 2, 4, 4);
                b.sell("mana_necklace", mod("mana_necklace", 1), 35, 1, 4, 4);
                b.sell("mp_supreme", mod("mana_potion_supreme", 1), 24, 1, 5, 5);
            }
            case MECHANIC -> {
                // onda 10: veículos pelo nível da Oficina Mecânica (VehicleKind.shopLevel), peças, kits e cristais
                b.buy("iron", Items.IRON_INGOT, 4, 2, 10, 1);
                b.buy("copper", Items.COPPER_INGOT, 8, 1, 10, 1);
                b.buy("redstone", Items.REDSTONE, 8, 1, 8, 2);
                b.sell("mana_crystal", CoreRegistries.MANA_CRYSTAL.get(), 1, 5, 4, 1);
                b.sell("repair_kit", mod("vehicle_repair_kit", 2), 10, 3, 1, 1);
                for (VehicleKind k : VehicleKind.values()) {
                    // sai da loja com meio tanque: dá para andar mesmo sem mana sobrando
                    b.sell("v_" + k.id, VehicleStacks.withFuel(mod(k.id, 1), 600), k.price, 1, k.shopLevel, 1);
                }
                b.sell("part_engine", mod(VehicleUpgrades.Part.ENGINE.itemId(), 1), 18, 2, 2, 1);
                b.sell("part_armor", mod(VehicleUpgrades.Part.ARMOR.itemId(), 1), 16, 2, 2, 1);
                b.sell("part_storage", mod(VehicleUpgrades.Part.STORAGE.itemId(), 1), 14, 2, 3, 1);
            }
        }
        return b.goods;
    }

    /** Quantas ofertas estão abertas e quantas existem: {abertas, total}. */
    public static int[] offerCount(Job job, SettlementStyle style, long day, int shop, int tier) {
        int open = 0, total = 0;
        for (Good g : catalog(job, style, day)) {
            total++;
            if (g.open(shop, tier)) open++;
        }
        return new int[] {open, total};
    }

    /** Preço de venda com o decreto de preços da cidade (o mesmo dos mascates). */
    private static int price(Good g, @Nullable Settlement s) {
        int tax = s == null ? Settlement.TAX_NORMAL : s.tax;
        float mult = tax == Settlement.TAX_LOW ? 0.85F : tax == Settlement.TAX_HIGH ? 1.25F : 1.0F;
        // onda 10: o que custa mais de 64 (veículos) usa as duas pilhas de moedas da tela de troca (até 128)
        return Math.max(1, Math.min(g.price() > 64 ? 128 : 64, Math.round(g.price() * mult)));
    }

    /**
     * As ofertas de agora de um comerciante (com o que sobrou do estoque de hoje). {@code keys} recebe, na mesma ordem,
     * a chave de cada oferta — o NPC devolve a chave em {@link #onTrade}.
     */
    public static MerchantOffers offers(MinecraftServer server, @Nullable Settlement s, TraderNpc npc, List<String> keys) {
        Job job = npc.job();
        int shop = s == null ? 1 : Math.max(1, shopLevel(s, job));
        int tier = s == null ? 1 : s.level;
        long day = CityPace.today(server);
        if (s != null) roll(server, s);
        Item coin = SettlementService.coinItem();
        MerchantOffers out = new MerchantOffers();
        keys.clear();
        for (Good g : catalog(job, s == null ? npc.style() : s.style, day)) {
            if (!g.open(shop, tier)) continue;
            int left = Math.max(0, g.quantity(shop, tier) - (s == null ? 0 : used(s, job, g.key())));
            MerchantOffer o = g.buy()
                    ? new MerchantOffer(new ItemCost(g.stack().getItem(), g.stack().getCount()), Optional.empty(),
                            new ItemStack(coin, g.price()), left, 0, 0.0F)
                    : sellOffer(coin, price(g, s), g.stack().copy(), left);
            out.add(o);
            keys.add(g.key());
        }
        return out;
    }

    /** Venda em moedas; acima de 64 a segunda pilha vai no segundo espaço da tela de troca. */
    private static MerchantOffer sellOffer(Item coin, int price, ItemStack result, int left) {
        Optional<ItemCost> extra = price > 64 ? Optional.of(new ItemCost(coin, price - 64)) : Optional.empty();
        return new MerchantOffer(new ItemCost(coin, Math.min(64, price)), extra, result, left, 0, 0.0F);
    }

    // =====================================================================================
    //  Estoque do dia e a taxa do Senhor
    // =====================================================================================
    /** Vira o dia do estoque se o dia do mundo mudou: prateleiras cheias, conta da taxa de ontem e uma linha no Diário. */
    public static void roll(MinecraftServer server, Settlement s) {
        long today = CityPace.today(server);
        CompoundTag t = s.trades;
        if (t.contains("Day") && t.getLong("Day") == today) return;
        boolean first = !t.contains("Day");
        int fee = t.getInt("FeeDay"), sales = t.getInt("Sales");
        t.putLong("Day", today);
        t.remove("Used");
        t.putInt("FeeLast", fee);
        t.putInt("SalesLast", sales);
        t.putInt("FeeDay", 0);
        t.putInt("Sales", 0);
        if (!first && sales > 0) CityPace.diary(server, s, SettlementsLang.tr("trade.diary.day", sales, fee));
    }

    private static int used(Settlement s, Job job, String key) {
        return s.trades.getCompound("Used").getInt(job.id + "." + key);
    }

    /** Uma troca feita num comerciante de ofício: gasta o estoque do dia e, se foi venda, paga a taxa do Senhor. */
    public static void onTrade(ServerLevel sl, @Nullable Settlement s, TraderNpc npc, String key, MerchantOffer offer) {
        if (s == null) return;
        MinecraftServer server = sl.getServer();
        roll(server, s);
        CompoundTag usedTag = s.trades.getCompound("Used");
        String k = npc.job().id + "." + key;
        usedTag.putInt(k, usedTag.getInt(k) + 1);
        s.trades.put("Used", usedTag);
        s.trades.putInt("Sales", s.trades.getInt("Sales") + 1);
        // venda = o jogador pagou em moedas
        if (offer.getItemCostA().item().value() == SettlementService.coinItem() && !offer.getResult().is(SettlementService.coinItem())) {
            fee(server, s, offer.getItemCostA().count() + offer.getItemCostB().map(ItemCost::count).orElse(0));
        }
        SettlementData.get(server).setDirty();
    }

    /** A parte do Senhor: {@link #FEE_PCT}% das moedas gastas vai para o cofre (os centavos acumulam até virar moeda). */
    public static void fee(MinecraftServer server, Settlement s, int coins) {
        if (coins <= 0) return;
        roll(server, s);
        int cents = s.trades.getInt("FeeCents") + coins * FEE_PCT;
        int whole = cents / 100;
        s.trades.putInt("FeeCents", cents % 100);
        if (whole <= 0) return;
        s.treasury = Math.min(Settlement.MAX_TREASURY, s.treasury + whole);
        s.trades.putInt("FeeDay", s.trades.getInt("FeeDay") + whole);
        s.trades.putInt("FeeTotal", s.trades.getInt("FeeTotal") + whole);
    }

    // =====================================================================================
    //  Os NPCs: quem a cidade tem, quem falta, quem chega
    // =====================================================================================
    @Nullable
    private static UUID npcId(Settlement s, Job job) {
        CompoundTag n = s.trades.getCompound("Npc");
        return n.hasUUID(job.id) ? n.getUUID(job.id) : null;
    }

    private static void setNpc(Settlement s, Job job, @Nullable UUID id) {
        CompoundTag n = s.trades.getCompound("Npc");
        if (id == null) n.remove(job.id);
        else n.putUUID(job.id, id);
        s.trades.put("Npc", n);
    }

    /** Depois de uma morte: cidade/ofício → tempo do jogo em que outro pode chegar (só em memória). */
    private static final Map<String, Long> RESPAWN = new HashMap<>();

    /** Sinal de vida de um comerciante de ofício (lista própria: não disputa vaga com os mascates). Falso = vai embora. */
    public static boolean heartbeat(ServerLevel sl, Settlement s, TraderNpc npc) {
        Job job = npc.job();
        // morrendo (a animação ainda roda uns ticks): não reassume a loja que acabou de ficar vaga
        if (!npc.isAlive()) return true;
        int cell = s.own() ? workCell(s, job) : -1;
        if (cell < 0) return false;   // o prédio saiu (ou a cidade não é fundada): o comerciante vai embora
        UUID cur = npcId(s, job);
        if (cur == null) {
            // loja sem dono (o registro se perdeu): este assume
            setNpc(s, job, npc.getUUID());
            rememberName(sl.getServer(), s, job, npc);
        } else if (!cur.equals(npc.getUUID())) {
            return false;   // outro já ocupa a loja
        }
        s.lastSeen.put(npc.getUUID(), sl.getGameTime());
        // o prédio mudou de lugar ou outro de nível maior ficou pronto: a loja muda junto; estilo novo = roupa da cidade
        BlockPos work = workPos(s, cell);
        if (!work.equals(npc.home())) npc.setHome(work);
        if (npc.style() != s.style) npc.restyle(s.style);
        return true;
    }

    /** Morreu (cidade é sagrada: não é para sempre): a loja fica vaga e outro chega daqui a pouco. */
    public static void onDeath(ServerLevel sl, TraderNpc npc) {
        Settlement s = npc.settlement();
        if (s == null) return;
        Job job = npc.job();
        if (!npc.getUUID().equals(npcId(s, job))) return;
        setNpc(s, job, null);
        s.lastSeen.remove(npc.getUUID());
        RESPAWN.put(s.id + "/" + job.id, sl.getGameTime() + RESPAWN_DELAY);
    }

    /**
     * Cuida dos comerciantes de uma cidade carregada: tira o de um prédio que saiu, dá por perdido quem sumiu e traz
     * UM que falta por vez (chegam aos poucos, pelo portão).
     *
     * @param force comando de teste: ignora a espera depois de uma morte e traz todos de uma vez
     */
    public static void maintain(ServerLevel sl, Settlement s, boolean force) {
        if (!s.own() || !s.has(Blueprint.PLAZA)) return;
        long now = sl.getGameTime();
        for (Job job : Job.values()) {
            int cell = workCell(s, job);
            UUID id = npcId(s, job);
            if (cell < 0) {
                if (id != null) {
                    Entity e = sl.getEntity(id);
                    if (e instanceof TraderNpc t) t.discard();
                    s.lastSeen.remove(id);
                    setNpc(s, job, null);
                }
                continue;
            }
            // loja fora da área que está ticando (cidade grande, jogador longe): o comerciante de lá não dá sinal de vida
            // nem anda — não é para dá-lo por perdido nem mandar outro para se amontoar com ele
            if (!sl.isPositionEntityTicking(workPos(s, cell))) {
                if (id != null) s.lastSeen.put(id, now);
                continue;
            }
            if (id != null) {
                Long seen = s.lastSeen.get(id);
                if (seen == null) {
                    // servidor acabou de subir: dá tempo de o NPC carregar e dar sinal de vida
                    s.lastSeen.put(id, now);
                    continue;
                }
                if (now - seen <= LOST_AFTER) continue;
                s.lastSeen.remove(id);
                setNpc(s, job, null);
            }
            if (!force && RESPAWN.getOrDefault(s.id + "/" + job.id, 0L) > now) continue;
            RESPAWN.remove(s.id + "/" + job.id);
            if (spawn(sl, s, job, cell) && !force) return;
        }
    }

    private static boolean spawn(ServerLevel sl, Settlement s, Job job, int cell) {
        BlockPos work = workPos(s, cell);
        if (!sl.hasChunk(work.getX() >> 4, work.getZ() >> 4)) return false;
        TraderNpc npc = SettlementsContent.TRADER.get().create(sl);
        if (npc == null) return false;
        // chega pelo portão quando ele está carregado; senão já aparece na porta da loja
        BlockPos at = CityLayout.arrivalPoint(s, sl.random.nextInt(4));
        if (!sl.isPositionEntityTicking(at)) at = work;
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        npc.moveTo(at.getX() + 0.5, y, at.getZ() + 0.5, sl.random.nextFloat() * 360F, 0F);
        npc.finalizeSpawn(sl, sl.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
        npc.bind(s, work, "");
        npc.setJob(job);
        sl.addFreshEntity(npc);
        setNpc(s, job, npc.getUUID());
        s.lastSeen.put(npc.getUUID(), sl.getGameTime());
        rememberName(sl.getServer(), s, job, npc);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, npc.getX(), npc.getY() + 1, npc.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
        Blueprint bp = s.blueprintAt(cell);
        Component where = bp == null ? Component.literal(s.buildings.getOrDefault(cell, "")) : bp.displayName();
        MinecraftServer server = sl.getServer();
        CityPace.diary(server, s, SettlementsLang.tr("trade.diary.arrived", npc.getName(), where));
        for (ServerPlayer g : CityEconomy.governors(server, s)) {
            // aviso curto na barra de ação (um por vez) e a dica de primeira vez
            g.displayClientMessage(SettlementsLang.tr("trade.arrived", npc.getName(), where).withColor(job.color), true);
            Hints.show(g, "city_trades_new");
        }
        SettlementData.get(server).setDirty();
        return true;
    }

    private static void rememberName(MinecraftServer server, Settlement s, Job job, TraderNpc npc) {
        CompoundTag names = s.trades.getCompound("Names");
        // getName() = o nome dado ("Bjorn, Ferreiro"), sem o evento de passar o mouse que o getDisplayName() embute
        names.putString(job.id, Component.Serializer.toJson(npc.getName(), server.registryAccess()));
        s.trades.put("Names", names);
    }

    // =====================================================================================
    //  Serviços (agachar + clicar)
    // =====================================================================================
    private record Pending(String what, int cost, long until) {}

    /** Serviço com preço mostrado, esperando o segundo clique (só em memória). */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private static int coins(ServerPlayer sp) {
        return sp.getInventory().countItem(SettlementService.coinItem());
    }

    /** Cobra o serviço (criativo não paga). Falso = faltou moeda (já avisou). */
    private static boolean pay(ServerPlayer sp, TraderNpc npc, int cost) {
        if (sp.isCreative() || cost <= 0) return true;
        if (coins(sp) < cost) {
            sp.displayClientMessage(SettlementsLang.tr("trade.service.poor", cost).withColor(0xE8735E), true);
            npc.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
            return false;
        }
        Item coin = SettlementService.coinItem();
        sp.getInventory().clearOrCountMatchingItems(st -> st.is(coin), cost, sp.inventoryMenu.getCraftSlots());
        sp.inventoryMenu.broadcastChanges();
        return true;
    }

    /** Primeiro clique: mostra o preço e devolve false; segundo clique (em até 6 s) no mesmo serviço: devolve true. */
    private static boolean confirmed(ServerPlayer sp, String what, int cost, Component offer) {
        long now = sp.serverLevel().getGameTime();
        Pending p = PENDING.get(sp.getUUID());
        if (p != null && p.what().equals(what) && p.cost() == cost && now <= p.until()) {
            PENDING.remove(sp.getUUID());
            return true;
        }
        PENDING.put(sp.getUUID(), new Pending(what, cost, now + 120));
        sp.displayClientMessage(offer.copy().withColor(0xFFD86B), true);
        return false;
    }

    /** Agachar + clicar num comerciante de ofício. */
    public static InteractionResult service(ServerPlayer sp, TraderNpc npc) {
        Settlement s = npc.settlement();
        Job job = npc.job();
        int shop = s == null ? 1 : Math.max(1, shopLevel(s, job));
        switch (job) {
            case BLACKSMITH -> smith(sp, npc, s, shop);
            case HEALER -> heal(sp, npc, s, shop);
            case INNKEEPER -> rumor(sp, npc, s);
            case MECHANIC -> mechanic(sp, npc, s, shop);
            default -> chat(sp, npc, s, shop);
        }
        return InteractionResult.CONSUME;
    }

    /** Ferreiro: conserta a peça da mão; inteira, reforça (Inquebrável) até onde o nível da Ferraria deixa. */
    private static void smith(ServerPlayer sp, TraderNpc npc, @Nullable Settlement s, int shop) {
        ItemStack hand = sp.getMainHandItem();
        if (hand.isEmpty() || !hand.isDamageableItem()) {
            chat(sp, npc, s, shop);
            return;
        }
        if (hand.isDamaged()) {
            float frac = hand.getDamageValue() / (float) Math.max(1, hand.getMaxDamage());
            // ferraria melhor = conserto mais barato (−10% por nível); peça encantada dá mais trabalho
            int cost = Math.max(1, Math.round(frac * (hand.isEnchanted() ? 10 : 6) * (1.1F - 0.1F * shop)));
            if (!confirmed(sp, "repair", cost, SettlementsLang.tr("trade.smith.repair.ask", hand.getHoverName(), cost))) return;
            if (!pay(sp, npc, cost)) return;
            hand.setDamageValue(0);
            done(sp, npc, s, cost, SettlementsLang.tr("trade.smith.repair.done", hand.getHoverName()));
            return;
        }
        Holder<Enchantment> unbreaking = sp.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        int cap = shop >= 5 ? 3 : shop >= 3 ? 2 : shop >= 2 ? 1 : 0;
        int cur = hand.getEnchantments().getLevel(unbreaking);
        if (cur >= 3 || !unbreaking.value().canEnchant(hand)) {
            npc.say(SettlementsLang.tr("trade.smith.fine"));
            return;
        }
        if (cur >= cap) {
            sp.displayClientMessage(SettlementsLang.tr("trade.smith.reinforce.locked", cur == 0 ? 2 : cur == 1 ? 3 : 5).withColor(0xE8735E), true);
            return;
        }
        int cost = REINFORCE_COST[Math.min(REINFORCE_COST.length - 1, cur)];
        if (!confirmed(sp, "reinforce" + cur, cost, SettlementsLang.tr("trade.smith.reinforce.ask", hand.getHoverName(), cur + 1, cost))) return;
        if (!pay(sp, npc, cost)) return;
        hand.enchant(unbreaking, cur + 1);
        done(sp, npc, s, cost, SettlementsLang.tr("trade.smith.reinforce.done", hand.getHoverName(), cur + 1));
    }

    /** Curandeiro: vida cheia e sem efeitos ruins; casa de nível 3+ ainda deixa uma regeneração curta. */
    private static void heal(ServerPlayer sp, TraderNpc npc, @Nullable Settlement s, int shop) {
        List<Holder<net.minecraft.world.effect.MobEffect>> bad = new ArrayList<>();
        for (MobEffectInstance e : sp.getActiveEffects()) {
            if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) bad.add(e.getEffect());
        }
        if (sp.getHealth() >= sp.getMaxHealth() - 0.5F && bad.isEmpty()) {
            chat(sp, npc, s, shop);
            return;
        }
        int cost = 3;
        if (!confirmed(sp, "heal", cost, SettlementsLang.tr("trade.heal.ask", cost))) return;
        if (!pay(sp, npc, cost)) return;
        for (Holder<net.minecraft.world.effect.MobEffect> e : bad) sp.removeEffect(e);
        sp.setHealth(sp.getMaxHealth());
        if (shop >= 3) sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * (10 + 5 * shop), 0, false, true));
        sp.serverLevel().sendParticles(ParticleTypes.HEART, sp.getX(), sp.getY() + 1.2, sp.getZ(), 8, 0.4, 0.4, 0.4, 0.02);
        done(sp, npc, s, cost, SettlementsLang.tr("trade.heal.done"));
    }

    /** Taverneiro: um boato (de graça) — o que os batedores ouviram do próximo ataque ou uma dica verdadeira da cidade. */
    private static void rumor(ServerPlayer sp, TraderNpc npc, @Nullable Settlement s) {
        Component text;
        int days = s == null ? -1 : CityDefense.forecastDays(sp.server, s);
        if (days >= 0 && npc.getRandom().nextInt(3) == 0) {
            text = days == 0 ? SettlementsLang.tr("trade.rumor.raid.today") : SettlementsLang.tr("trade.rumor.raid", days);
        } else {
            text = SettlementsLang.tr("trade.rumor." + npc.getRandom().nextInt(RUMORS));
        }
        Dialogue.show(sp, "settlements.npc", npc.getDisplayName(), npc.job().color,
                List.of(Dialogue.Page.of(SettlementsLang.tr("trade.rumor.intro")), Dialogue.Page.of(text)));
        npc.triggerAnim("action", "wave");
    }

    /**
     * Onda 10 — Mecânico: conserta o veículo da mão ou o seu estacionado perto da Oficina, reconstrói um destruído e
     * resgata um perdido ({@link VehicleMechanic}). Um serviço por vez, sempre com o preço antes e o segundo clique.
     */
    private static void mechanic(ServerPlayer sp, TraderNpc npc, @Nullable Settlement s, int shop) {
        List<VehicleMechanic.Offer> all = VehicleMechanic.offers(sp, npc.position(), shop);
        if (all.isEmpty()) {
            chat(sp, npc, s, shop);
            return;
        }
        VehicleMechanic.Offer o = all.get(0);
        if (!confirmed(sp, o.key(), o.cost(), o.ask())) return;
        // refaz o orçamento: o veículo pode ter saído de perto entre os dois cliques
        VehicleMechanic.Offer now = VehicleMechanic.quote(sp, npc.position(), shop, o.service());
        if (now == null || !now.key().equals(o.key())) {
            chat(sp, npc, s, shop);
            return;
        }
        if (!pay(sp, npc, now.cost())) return;
        Component text = VehicleMechanic.perform(sp, now);
        if (text == null) {
            VehicleMechanic.refund(sp, SettlementService.coinItem(), now.cost());
            return;
        }
        npc.triggerAnim("action", "wave");
        done(sp, npc, s, now.cost(), text);
    }

    /** Conversa: o que o ofício faz e como anda a loja (nível, ofertas abertas, quando renova). */
    private static void chat(ServerPlayer sp, TraderNpc npc, @Nullable Settlement s, int shop) {
        Job job = npc.job();
        int tier = s == null ? 1 : s.level;
        int[] n = offerCount(job, s == null ? npc.style() : s.style, CityPace.today(sp.server), shop, tier);
        List<Dialogue.Page> pages = new ArrayList<>();
        pages.add(Dialogue.Page.of(SettlementsLang.tr("trade.talk." + job.id)));
        pages.add(Dialogue.Page.of(SettlementsLang.tr(n[0] < n[1] ? "trade.talk.shop" : "trade.talk.shop.full", shop, n[0], n[1])));
        // onda 10: o Mecânico lembra dos seus veículos (no mundo, guardados, destruídos)
        if (job == Job.MECHANIC) pages.add(Dialogue.Page.of(VehicleMechanic.summary(sp)));
        Dialogue.show(sp, "settlements.npc", npc.getDisplayName(), job.color, pages);
        npc.triggerAnim("action", "wave");
    }

    private static void done(ServerPlayer sp, TraderNpc npc, @Nullable Settlement s, int cost, Component text) {
        sp.displayClientMessage(text.copy().withColor(0x7DDA6B), true);
        ServerLevel sl = sp.serverLevel();
        sl.playSound(null, npc.blockPosition(), npc.job() == Job.BLACKSMITH || npc.job() == Job.MECHANIC ? SoundEvents.ANVIL_USE
                        : SoundEvents.BREWING_STAND_BREW,
                SoundSource.NEUTRAL, 0.7F, 1.1F);
        npc.say(line(npc.job(), "thanks", 2, npc.getRandom()));
        if (s != null && !sp.isCreative()) {
            fee(sp.server, s, cost);
            s.trades.putInt("Sales", s.trades.getInt("Sales") + 1);
            SettlementData.get(sp.server).setDirty();
        }
    }

    // =====================================================================================
    //  Painel e planejador
    // =====================================================================================
    /** Estado para a tela (sub-aba Comerciantes): uma linha por ofício — quem a cidade tem, quem falta e qual prédio traz. */
    public static void view(CompoundTag t, MinecraftServer server, Settlement s, HolderLookup.Provider reg) {
        roll(server, s);
        long day = CityPace.today(server);
        ListTag list = new ListTag();
        CompoundTag names = s.trades.getCompound("Names");
        for (Job job : Job.values()) {
            CompoundTag e = new CompoundTag();
            e.putString("Job", job.id);
            e.putInt("Color", job.color);
            int cell = workCell(s, job);
            // o prédio que traz o ofício: o que a cidade tem, senão o primeiro da lista (em qualquer estilo que o tenha)
            String key = cell >= 0 ? s.buildings.get(cell) : job.buildings[0];
            Blueprint bp = cell >= 0 ? s.blueprintAt(cell) : Blueprints.get(s.style, key);
            for (SettlementStyle o : SettlementStyle.values()) {
                if (bp != null) break;
                bp = Blueprints.get(o, key);
            }
            e.putString("Key", key);
            if (bp != null) {
                e.putString("Building", SettlementView.json(bp.displayName(), reg));
                e.putInt("MinLevel", bp.minLevel());
                e.putString("Rank", SettlementView.json(CityTier.name(bp.minLevel()), reg));
            }
            if (cell >= 0) {
                int shop = shopLevelAt(s, cell);
                int[] n = offerCount(job, s.style, day, shop, s.level);
                e.putInt("Shop", shop);
                e.putInt("Open", n[0]);
                e.putInt("Total", n[1]);
                e.putBoolean("Here", npcId(s, job) != null);
                if (names.contains(job.id)) e.putString("Name", names.getString(job.id));
                if (s.damaged.contains(cell)) e.putBoolean("Damaged", true);
            }
            list.add(e);
        }
        t.put("Trades", list);
        CompoundTag fee = new CompoundTag();
        fee.putInt("Pct", FEE_PCT);
        fee.putInt("Today", s.trades.getInt("FeeDay"));
        fee.putInt("Sales", s.trades.getInt("Sales"));
        fee.putInt("Last", s.trades.getInt("FeeLast"));
        fee.putInt("SalesLast", s.trades.getInt("SalesLast"));
        fee.putInt("Total", s.trades.getInt("FeeTotal"));
        t.put("TradeFee", fee);
    }

    /** Linha de "o que você ganha" ao subir para o degrau {@code to}: estoque maior e os itens raros que ele abre. */
    @Nullable
    public static Component rankGain(int to) {
        if (to <= 1) return null;
        int rare = 0;
        for (Job job : Job.values()) {
            for (Good g : catalog(job, SettlementStyle.KINGDOM, 0)) if (g.minTier() == to) rare++;
        }
        return rare > 0 ? SettlementsLang.tr("trade.gain.rare", rare) : SettlementsLang.tr("trade.gain.stock");
    }

    /** Linha de "o que você ganha" do planejador e da inauguração: o comerciante que o prédio traz e quantas ofertas. */
    @Nullable
    public static Component fxNote(String key, int level) {
        Job job = Job.ofBuilding(key);
        if (job == null) return null;
        return SettlementsLang.tr("trade.fx", job.displayName(), openByLevel(job)[Math.max(1, Math.min(5, level)) - 1]);
    }

    /** Ofertas que cada nível do prédio abre (índice = nível − 1), guardadas: a tela pede isto a cada quadro. */
    private static final Map<Job, int[]> OPEN_BY_LEVEL = new java.util.EnumMap<>(Job.class);

    private static synchronized int[] openByLevel(Job job) {
        int[] out = OPEN_BY_LEVEL.get(job);
        if (out != null) return out;
        out = new int[Settlement.MAX_BUILDING_LEVEL];
        // as raras ainda dependem do degrau da cidade: aqui conta só o que o NÍVEL DO PRÉDIO abre
        for (Good g : catalog(job, SettlementStyle.KINGDOM, 0)) {
            for (int l = Math.max(1, g.minShop()); l <= out.length; l++) out[l - 1]++;
        }
        OPEN_BY_LEVEL.put(job, out);
        return out;
    }

    // =====================================================================================
    //  Registro: ganchos do city_pace, tique, comandos de teste
    // =====================================================================================
    /** Chamado no init do módulo. */
    public static void register() {
        // onda 10: a Oficina Mecânica leva 2 dias (como a Ferraria e o Estábulo)
        CityPace.register("garage", 2F);
        // novo dia da cidade (coração carregado): prateleiras cheias, Diário e quem falta chega
        CityPace.onNewDay((sl, s, day, elapsed) -> {
            if (!s.own()) return;
            // o comando de teste (/mahou city days) dispara o gancho sem o relógio do mundo andar: uma SEGUNDA chamada
            // no mesmo dia do mundo enche as prateleiras de novo (a primeira é o amanhecer de verdade, que o carimbo já cobre)
            CompoundTag t = s.trades;
            if (t.contains("HookDay") && t.getLong("HookDay") == day && t.contains("Day") && t.getLong("Day") == day) {
                t.putLong("Day", day - 1);
            }
            t.putLong("HookDay", day);
            roll(sl.getServer(), s);
            maintain(sl, s, false);
        });
        // prédio de ofício pronto ou melhorado: o comerciante chega logo (ou a loja muda para o prédio melhor)
        CityPace.onWorkDone((sl, s, p, grand) -> {
            if (!s.own() || Job.ofBuilding(p.key) == null) return;
            if (!Project.KIND_BUILD.equals(p.kind) && !Project.KIND_UPGRADE.equals(p.kind)) return;
            maintain(sl, s, false);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tickServer(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> PENDING.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            PENDING.clear();
            RESPAWN.clear();
        });
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("city").then(Commands.literal("trades")
                .executes(c -> debug(c.getSource(), ""))
                .then(Commands.literal("spawn").executes(c -> debug(c.getSource(), "spawn")))
                .then(Commands.literal("restock").executes(c -> debug(c.getSource(), "restock")))
                .then(Commands.literal("clear").executes(c -> debug(c.getSource(), "clear"))))));
    }

    /** A cada 10 s: cuida dos comerciantes das cidades fundadas com o coração carregado (conta leve; no máximo 1 chegada). */
    private static void tickServer(MinecraftServer server) {
        if (server.getTickCount() % 200 != 90) return;
        for (Settlement s : SettlementData.get(server).all()) {
            if (!s.own()) continue;
            ServerLevel sl = SettlementService.level(server, s);
            if (sl == null || !sl.hasChunk(s.heart.getX() >> 4, s.heart.getZ() >> 4)) continue;
            maintain(sl, s, false);
        }
    }

    /**
     * {@code /mahou city trades} — lista os ofícios da cidade onde você está (ou da mais perto);
     * {@code spawn} traz já todos os que faltam; {@code restock} enche as prateleiras; {@code clear} manda todos embora
     * (voltam no próximo tique ou com {@code spawn}).
     */
    private static int debug(CommandSourceStack src, String op) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MinecraftServer server = p.server;
        Places.Place at = SettlementService.cityAt(p.serverLevel(), p.blockPosition());
        Settlement s = at == null ? null : SettlementService.city(server, at.id());
        if (s == null) {
            double best = Double.MAX_VALUE;
            for (Settlement o : SettlementData.get(server).all()) {
                double dist = o.heart.distSqr(p.blockPosition());
                if (o.dim == p.level().dimension() && o.own() && dist < best) {
                    best = dist;
                    s = o;
                }
            }
        }
        if (s == null || !s.own()) {
            src.sendFailure(Component.translatable("settlements.isekai_mahou.cmd.none"));
            return 0;
        }
        ServerLevel sl = SettlementService.level(server, s);
        Settlement city = s;
        switch (op) {
            case "spawn" -> {
                if (sl != null) maintain(sl, s, true);
            }
            case "restock" -> s.trades.remove("Used");
            case "clear" -> {
                for (Job job : Job.values()) {
                    UUID id = npcId(s, job);
                    if (id == null) continue;
                    Entity e = sl == null ? null : sl.getEntity(id);
                    if (e instanceof TraderNpc t) t.discard();
                    s.lastSeen.remove(id);
                    setNpc(s, job, null);
                }
            }
            default -> { }
        }
        roll(server, s);
        long day = CityPace.today(server);
        src.sendSuccess(() -> SettlementsLang.tr("trade.cmd.head", SettlementService.cityName(server, city), city.trades.getInt("Sales"),
                city.trades.getInt("FeeDay"), city.trades.getInt("FeeTotal")), false);
        for (Job job : Job.values()) {
            int cell = workCell(s, job);
            if (cell < 0) {
                src.sendSuccess(() -> SettlementsLang.tr("trade.cmd.missing", job.displayName(), job.buildings[0]), false);
                continue;
            }
            int shop = shopLevelAt(s, cell);
            int[] n = offerCount(job, s.style, day, shop, s.level);
            boolean here = npcId(s, job) != null;
            src.sendSuccess(() -> SettlementsLang.tr(here ? "trade.cmd.row" : "trade.cmd.row.away", job.displayName(), shop, n[0], n[1])
                    .withColor(job.color), false);
        }
        SettlementData.get(server).setDirty();
        SettlementService.refreshViewers(server, s);
        return 1;
    }

    private CityTrades() {}
}
