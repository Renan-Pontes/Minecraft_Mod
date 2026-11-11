package com.renanpontes.isekaimahou.feature.character;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Rewards;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * De onde vêm as Relíquias Raciais (temáticas e nada comuns).
 * <ul>
 *   <li><b>Baús</b>: {@link LootTableLoadEvent} adiciona uma pool com chance às tabelas listadas (do mod e, v1.0.1,
 *   algumas vanilla — mina abandonada).</li>
 *   <li><b>Criaturas</b>: {@link LivingDropsEvent} por id do tipo de entidade, só se um jogador matou. Chefes
 *   ({@code chance >= 1}) entregam a relíquia DIRETO a quem matou (nada cai no vazio da arena).</li>
 * </ul>
 * Obs.: não usa modificador de loot global porque {@code data/neoforge/loot_modifiers/global_loot_modifiers.json} já é
 * de outro módulo (potions, em {@code src/main/merge}) e a tarefa {@code mergeData} só junta tags ({@code "values"}):
 * um 2º arquivo com {@code "entries"} quebraria o build.
 */
public final class RelicDrops {
    private record Drop(Race race, float chance) {}

    /** Baú (id completo da tabela de loot) → relíquia e chance. */
    private static final Map<ResourceLocation, Drop> CHESTS = new LinkedHashMap<>();
    /** Tipo de entidade (id) → relíquia e chance (1 = garantida, chefe). */
    private static final Map<ResourceLocation, Drop> MOBS = new LinkedHashMap<>();
    /** Prefixo dos baús das cidades (exceto as sub-tabelas {@code city/sub/*}). */
    private static final String CITY_PREFIX = "chests/city/";
    private static final float CITY_CHANCE = 0.05F;

    static {
        // Humano — guildas e cidades
        chest("chests/guild_hall_village", Race.HUMAN, 0.12F);
        chest("chests/guild_hall_village_house", Race.HUMAN, 0.05F);
        chest("chests/city/guild", Race.HUMAN, 0.15F);
        chest("chests/city/castle", Race.HUMAN, 0.12F);
        // Elfo — fadas, lagos e círculos druídicos
        chest("chests/fairy_lake", Race.ELF, 0.15F);
        chest("chests/druid_circle", Race.ELF, 0.15F);
        chest("chests/fey_court", Race.ELF, 0.10F);
        chest("chests/fey_ring", Race.ELF, 0.08F);
        mob("fairy", Race.ELF, 0.04F);
        // Anão — kobolds e suas tocas
        chest("chests/kobold_den", Race.DWARF, 0.15F);
        mob("kobold", Race.DWARF, 0.025F);
        // Meio-Orc — orcs e o Senhor Orc
        chest("chests/orc_camp", Race.HALF_ORC, 0.10F);
        mob("orc", Race.HALF_ORC, 0.025F);
        mob("orc_berserker", Race.HALF_ORC, 0.04F);
        mob("orc_lord", Race.HALF_ORC, 1F);
        // Demônio — patronos e o Trono do Abismo
        chest("chests/abyss_throne", Race.DEMON, 0.35F);
        mob("imprisoned_patron", Race.DEMON, 1F);
        mob("patron_ember_lord", Race.DEMON, 0.5F);
        mob("patron_void_lady", Race.DEMON, 0.5F);
        mob("patron_drowned_king", Race.DEMON, 0.5F);
        // Dragonato — dragões
        chest("chests/wyvern_peak", Race.DRAGONBORN, 0.12F);
        mob("wyvern_king", Race.DRAGONBORN, 1F);
        mob("primordial_dragon", Race.DRAGONBORN, 1F);
        // Bestial — feras
        chest("chests/manticore_den", Race.BEASTFOLK, 0.20F);
        chest("chests/lizardman_village", Race.BEASTFOLK, 0.08F);
        mob("manticore", Race.BEASTFOLK, 1F);
        mob("dire_wolf", Race.BEASTFOLK, 0.03F);
        mob("lizardman", Race.BEASTFOLK, 0.03F);
        mob("werewolf", Race.BEASTFOLK, 0.05F);
        // v1.0.1 — Aracnídeo: aranhas, rastejantes e minas abandonadas
        vanillaMob("cave_spider", Race.ARACHNID, 0.03F);
        vanillaMob("spider", Race.ARACHNID, 0.015F);
        mob("void_crawler", Race.ARACHNID, 0.05F);
        vanillaChest("chests/abandoned_mineshaft", Race.ARACHNID, 0.06F);
        // Fantasma: fantasmas, vexes, o Lich e a Cripta Esquecida
        vanillaMob("phantom", Race.PHANTOM, 0.04F);
        vanillaMob("vex", Race.PHANTOM, 0.04F);
        mob("lich", Race.PHANTOM, 1F);
        chest("chests/forgotten_crypt", Race.PHANTOM, 0.12F);
        chest("chests/forgotten_crypt_sarcophagus", Race.PHANTOM, 0.15F);
        // Anjo: o Serafim, sentinelas angelicais, querubins e o santuário celestial
        mob("seraph", Race.ANGEL, 1F);
        mob("angel_sentinel", Race.ANGEL, 0.05F);
        mob("cherub", Race.ANGEL, 0.03F);
        chest("chests/celestial_cathedral", Race.ANGEL, 0.15F);
        chest("chests/celestial_stairway", Race.ANGEL, 0.10F);
        // Slime: o Rei Slime, slimes e cubos de magma
        mob("king_slime", Race.SLIME, 1F);
        vanillaMob("slime", Race.SLIME, 0.01F);
        vanillaMob("magma_cube", Race.SLIME, 0.015F);
    }

    private static void chest(String path, Race r, float chance) {
        CHESTS.put(IsekaiMahou.id(path), new Drop(r, chance));
    }

    private static void vanillaChest(String path, Race r, float chance) {
        CHESTS.put(ResourceLocation.withDefaultNamespace(path), new Drop(r, chance));
    }

    private static void mob(String path, Race r, float chance) {
        MOBS.put(IsekaiMahou.id(path), new Drop(r, chance));
    }

    private static void vanillaMob(String path, Race r, float chance) {
        MOBS.put(ResourceLocation.withDefaultNamespace(path), new Drop(r, chance));
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(RelicDrops::onLootLoad);
        NeoForge.EVENT_BUS.addListener(RelicDrops::onDrops);
    }

    @Nullable
    private static Drop chestDrop(ResourceLocation table) {
        Drop d = CHESTS.get(table);
        if (d != null) return d;
        if (!IsekaiMahou.MODID.equals(table.getNamespace())) return null;
        String path = table.getPath();
        if (path.startsWith(CITY_PREFIX) && !path.startsWith(CITY_PREFIX + "sub/")) return new Drop(Race.HUMAN, CITY_CHANCE);
        return null;
    }

    private static void onLootLoad(LootTableLoadEvent e) {
        Drop d = chestDrop(e.getName());
        if (d == null) return;
        e.getTable().addPool(LootPool.lootPool()
                .name(IsekaiMahou.MODID + ":character_relic")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(d.chance()))
                .add(LootItem.lootTableItem(CharacterContent.relic(d.race()).get()))
                .build());
    }

    private static void onDrops(LivingDropsEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player || dead.level().isClientSide) return;
        Drop d = MOBS.get(BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()));
        if (d == null) return;
        ServerPlayer killer = killer(e);
        if (killer == null) return;
        ItemStack relic = new ItemStack(CharacterContent.relic(d.race()).get());
        if (d.chance() >= 1F) {
            Rewards.give(killer, relic);
            return;
        }
        if (dead.getRandom().nextFloat() >= d.chance()) return;
        ItemEntity item = new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.5, dead.getZ(), relic);
        item.setDefaultPickUpDelay();
        e.getDrops().add(item);
    }

    @Nullable
    private static ServerPlayer killer(LivingDropsEvent e) {
        Entity src = e.getSource().getEntity();
        if (src instanceof ServerPlayer sp && !(sp instanceof FakePlayer)) return sp;
        LivingEntity credit = e.getEntity().getKillCredit();
        if (credit instanceof ServerPlayer sp && !(sp instanceof FakePlayer)) return sp;
        return null;
    }

    private RelicDrops() {}
}
