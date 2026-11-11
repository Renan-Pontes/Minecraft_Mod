package com.renanpontes.isekaimahou.feature.creatures_b;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.CultistEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.KoboldEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.LizardmanEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.MinotaurEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.OgreEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.OrcBerserkerEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.OrcEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.OrcLordEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.TrollEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.WanderingMageEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.WerewolfEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.item.OrcWarHornItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo creatures_b: entidades, ovos, drops, armas dos chefes e o Chifre de Guerra Orc. */
public final class CbRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    // ---------------- Entidades ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<KoboldEntity>> KOBOLD =
            mob("kobold", KoboldEntity::new, MobCategory.MONSTER, 0.6F, 1.2F, 8);
    public static final DeferredHolder<EntityType<?>, EntityType<OrcEntity>> ORC =
            mob("orc", OrcEntity::new, MobCategory.MONSTER, 0.8F, 2.1F, 8);
    public static final DeferredHolder<EntityType<?>, EntityType<OrcBerserkerEntity>> ORC_BERSERKER =
            mob("orc_berserker", OrcBerserkerEntity::new, MobCategory.MONSTER, 0.8F, 2.1F, 8);
    public static final DeferredHolder<EntityType<?>, EntityType<OrcLordEntity>> ORC_LORD =
            mob("orc_lord", OrcLordEntity::new, MobCategory.MONSTER, 1.1F, 2.8F, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<OgreEntity>> OGRE =
            mob("ogre", OgreEntity::new, MobCategory.MONSTER, 1.6F, 3.1F, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<TrollEntity>> TROLL =
            mob("troll", TrollEntity::new, MobCategory.MONSTER, 1.2F, 2.6F, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<LizardmanEntity>> LIZARDMAN =
            mob("lizardman", LizardmanEntity::new, MobCategory.MONSTER, 0.7F, 2.0F, 8);
    public static final DeferredHolder<EntityType<?>, EntityType<MinotaurEntity>> MINOTAUR =
            mob("minotaur", MinotaurEntity::new, MobCategory.MONSTER, 1.4F, 2.8F, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<WerewolfEntity>> WEREWOLF =
            mob("werewolf", WerewolfEntity::new, MobCategory.MONSTER, 0.9F, 2.3F, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<CultistEntity>> CULTIST =
            mob("cultist", CultistEntity::new, MobCategory.MONSTER, 0.6F, 1.95F, 8);
    public static final DeferredHolder<EntityType<?>, EntityType<WanderingMageEntity>> WANDERING_MAGE =
            mob("wandering_mage", WanderingMageEntity::new, MobCategory.CREATURE, 0.6F, 1.95F, 10);

    // ---------------- Drops ----------------
    public static final DeferredItem<Item> ORC_TUSK = ITEMS.registerSimpleItem("orc_tusk", new Item.Properties());
    public static final DeferredItem<Item> TROLL_HIDE = ITEMS.registerSimpleItem("troll_hide", new Item.Properties());
    public static final DeferredItem<Item> LIZARD_SCALE = ITEMS.registerSimpleItem("lizard_scale", new Item.Properties());
    public static final DeferredItem<Item> MINOTAUR_HORN = ITEMS.registerSimpleItem("minotaur_horn", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> WEREWOLF_FANG = ITEMS.registerSimpleItem("werewolf_fang", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> OGRE_TOOTH = ITEMS.registerSimpleItem("ogre_tooth", new Item.Properties());

    // ---------------- Armas dos chefes ----------------
    public static final DeferredItem<SwordItem> ORC_LORD_CLEAVER = ITEMS.register("orc_lord_cleaver",
            () -> new SwordItem(Tiers.DIAMOND, new Item.Properties().rarity(Rarity.EPIC)
                    .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 5, -3.0F))));
    public static final DeferredItem<AxeItem> MINOTAUR_LABRYS = ITEMS.register("minotaur_labrys",
            () -> new AxeItem(Tiers.DIAMOND, new Item.Properties().rarity(Rarity.EPIC)
                    .attributes(DiggerItem.createAttributes(Tiers.DIAMOND, 6.5F, -3.1F))));
    public static final DeferredItem<OrcWarHornItem> ORC_WAR_HORN = ITEMS.register("orc_war_horn",
            () -> new OrcWarHornItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // ---------------- Ovos ----------------
    public static final Map<String, DeferredItem<DeferredSpawnEggItem>> EGGS = new LinkedHashMap<>();

    static {
        egg("kobold", KOBOLD, 0xA3482D, 0xD9A15C);
        egg("orc", ORC, 0x6E9A3E, 0x6B4A2B);
        egg("orc_berserker", ORC_BERSERKER, 0x5E7F31, 0xB3241B);
        egg("orc_lord", ORC_LORD, 0x4B4F55, 0xD8B13A);
        egg("ogre", OGRE, 0xB79A6A, 0x6E5233);
        egg("troll", TROLL, 0x7E8C76, 0x4E7A2A);
        egg("lizardman", LIZARDMAN, 0x4F8A3A, 0xD7C46A);
        egg("minotaur", MINOTAUR, 0x6B4326, 0xE6DCC2);
        egg("werewolf", WEREWOLF, 0x5C564F, 0xFFD84A);
        egg("cultist", CULTIST, 0x2E1C40, 0x8A4FC8);
        egg("wandering_mage", WANDERING_MAGE, 0x2F5D8A, 0xE8C25A);
    }

    private static <T extends Mob> DeferredHolder<EntityType<?>, EntityType<T>> mob(String id, EntityType.EntityFactory<T> factory,
                                                                                    MobCategory cat, float w, float h, int range) {
        return ENTITIES.register(id, () -> EntityType.Builder.of(factory, cat).sized(w, h).clientTrackingRange(range).build(id));
    }

    private static void egg(String id, Supplier<? extends EntityType<? extends Mob>> type, int bg, int fg) {
        EGGS.put(id, ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, bg, fg, new Item.Properties())));
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(CbRegistries::attributes);
        modBus.addListener(CbRegistries::spawnPlacements);
        modBus.addListener(CbRegistries::creativeTab);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(KOBOLD.get(), KoboldEntity.createAttributes().build());
        e.put(ORC.get(), OrcEntity.createAttributes().build());
        e.put(ORC_BERSERKER.get(), OrcBerserkerEntity.createAttributes().build());
        e.put(ORC_LORD.get(), OrcLordEntity.createAttributes().build());
        e.put(OGRE.get(), OgreEntity.createAttributes().build());
        e.put(TROLL.get(), TrollEntity.createAttributes().build());
        e.put(LIZARDMAN.get(), LizardmanEntity.createAttributes().build());
        e.put(MINOTAUR.get(), MinotaurEntity.createAttributes().build());
        e.put(WEREWOLF.get(), WerewolfEntity.createAttributes().build());
        e.put(CULTIST.get(), CultistEntity.createAttributes().build());
        e.put(WANDERING_MAGE.get(), WanderingMageEntity.createAttributes().build());
    }

    private static void spawnPlacements(RegisterSpawnPlacementsEvent e) {
        var ground = SpawnPlacementTypes.ON_GROUND;
        var hm = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        var op = RegisterSpawnPlacementsEvent.Operation.REPLACE;
        e.register(KOBOLD.get(), ground, hm, KoboldEntity::checkSpawn, op);
        e.register(ORC.get(), ground, hm, Monster::checkMonsterSpawnRules, op);
        e.register(ORC_BERSERKER.get(), ground, hm, Monster::checkMonsterSpawnRules, op);
        e.register(ORC_LORD.get(), ground, hm, Monster::checkMonsterSpawnRules, op);
        e.register(OGRE.get(), ground, hm, Monster::checkMonsterSpawnRules, op);
        e.register(TROLL.get(), ground, hm, Monster::checkMonsterSpawnRules, op);
        e.register(LIZARDMAN.get(), ground, hm, LizardmanEntity::checkSpawn, op);
        e.register(MINOTAUR.get(), ground, hm, Monster::checkMonsterSpawnRules, op);
        e.register(WEREWOLF.get(), ground, hm, WerewolfEntity::checkSpawn, op);
        e.register(CULTIST.get(), ground, hm, CultistEntity::checkSpawn, op);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_CREATURES.getKey()) return;
        CreativeModeTab.Output out = e;
        EGGS.values().forEach(i -> out.accept(i.get()));
        out.accept(ORC_TUSK.get());
        out.accept(TROLL_HIDE.get());
        out.accept(LIZARD_SCALE.get());
        out.accept(OGRE_TOOTH.get());
        out.accept(WEREWOLF_FANG.get());
        out.accept(MINOTAUR_HORN.get());
        out.accept(ORC_WAR_HORN.get());
        out.accept(ORC_LORD_CLEAVER.get());
        out.accept(MINOTAUR_LABRYS.get());
    }

    private CbRegistries() {}
}
