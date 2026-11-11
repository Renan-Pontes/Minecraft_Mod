package com.renanpontes.isekaimahou.feature.creatures_d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ArcaneGolem;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.CreepingShade;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.CrystalMole;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ElementalBolt;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.EmberImp;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.LumenSprite;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.MossGolem;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.Salamander;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.SolarSentinel;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.StormHawk;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.Sylph;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.TideSerpent;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.Undine;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.VoidBat;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.WanderingSpirit;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo: tipos de entidade e ovos de spawn. */
public final class CdEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    /** Ovos na ordem em que aparecem na aba Criaturas. */
    public static final List<DeferredItem<DeferredSpawnEggItem>> EGGS = new ArrayList<>();

    // ---------------- Fogo ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<Salamander>> SALAMANDER = mob("salamander", Salamander::new,
            MobCategory.MONSTER, 0.9F, 0.6F, b -> b.fireImmune(), 0xD9542B, 0xFFD84A);
    public static final DeferredHolder<EntityType<?>, EntityType<EmberImp>> EMBER_IMP = mob("ember_imp", EmberImp::new,
            MobCategory.MONSTER, 0.6F, 0.95F, b -> b.fireImmune(), 0x5A0F0B, 0xFF8A2B);
    // ---------------- Água ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<Undine>> UNDINE = mob("undine", Undine::new,
            MobCategory.WATER_CREATURE, 0.7F, 1.8F, b -> b, 0x2E8FD6, 0xBFF4FF);
    public static final DeferredHolder<EntityType<?>, EntityType<TideSerpent>> TIDE_SERPENT = mob("tide_serpent", TideSerpent::new,
            MobCategory.MONSTER, 1.3F, 0.8F, b -> b.clientTrackingRange(10), 0x1F5F73, 0x7FF5E6);
    // ---------------- Terra ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<MossGolem>> MOSS_GOLEM = mob("moss_golem", MossGolem::new,
            MobCategory.MONSTER, 1.3F, 2.3F, b -> b, 0x6E7368, 0x6FB04A);
    public static final DeferredHolder<EntityType<?>, EntityType<CrystalMole>> CRYSTAL_MOLE = mob("crystal_mole", CrystalMole::new,
            MobCategory.MONSTER, 0.8F, 0.6F, b -> b, 0x4A3326, 0xB58CF0);
    // ---------------- Vento ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<Sylph>> SYLPH = mob("sylph", Sylph::new,
            MobCategory.MONSTER, 0.6F, 1.5F, b -> b, 0xC8F5E6, 0x6FD8B5);
    public static final DeferredHolder<EntityType<?>, EntityType<StormHawk>> STORM_HAWK = mob("storm_hawk", StormHawk::new,
            MobCategory.MONSTER, 0.9F, 0.8F, b -> b.clientTrackingRange(10), 0x3E4C68, 0x9FE8FF);
    // ---------------- Luz ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<LumenSprite>> LUMEN_SPRITE = mob("lumen_sprite", LumenSprite::new,
            MobCategory.AMBIENT, 0.45F, 0.45F, b -> b, 0xFFF2B0, 0xFFC93A);
    public static final DeferredHolder<EntityType<?>, EntityType<SolarSentinel>> SOLAR_SENTINEL = mob("solar_sentinel", SolarSentinel::new,
            MobCategory.MONSTER, 0.9F, 1.6F, b -> b.fireImmune(), 0xD9C08A, 0xFFB300);
    // ---------------- Sombra ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<CreepingShade>> CREEPING_SHADE = mob("creeping_shade", CreepingShade::new,
            MobCategory.MONSTER, 1.0F, 0.7F, b -> b, 0x140B1E, 0x8A4FC8);
    public static final DeferredHolder<EntityType<?>, EntityType<VoidBat>> VOID_BAT = mob("void_bat", VoidBat::new,
            MobCategory.MONSTER, 0.7F, 0.8F, b -> b, 0x1B1029, 0xFF5CF0);
    // ---------------- Arcanos ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<WanderingSpirit>> WANDERING_SPIRIT = mob("wandering_spirit", WanderingSpirit::new,
            MobCategory.AMBIENT, 0.6F, 1.6F, b -> b, 0xCBB8FF, 0x6B4FC8);
    public static final DeferredHolder<EntityType<?>, EntityType<ArcaneGolem>> ARCANE_GOLEM = mob("arcane_golem", ArcaneGolem::new,
            MobCategory.MONSTER, 1.4F, 2.7F, b -> b.clientTrackingRange(10), 0x3E3A4C, 0xB98CFF);

    // ---------------- Projétil ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<ElementalBolt>> ELEMENTAL_BOLT = ENTITIES.register("elemental_bolt",
            () -> EntityType.Builder.<ElementalBolt>of(ElementalBolt::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(5).build("elemental_bolt"));

    private static <T extends Mob> DeferredHolder<EntityType<?>, EntityType<T>> mob(String id, EntityType.EntityFactory<T> factory,
            MobCategory category, float width, float height, UnaryOperator<EntityType.Builder<T>> extra, int eggBase, int eggSpots) {
        DeferredHolder<EntityType<?>, EntityType<T>> holder = ENTITIES.register(id,
                () -> extra.apply(EntityType.Builder.of(factory, category).sized(width, height).clientTrackingRange(8)).build(id));
        EGGS.add(ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(holder, eggBase, eggSpots, new Item.Properties())));
        return holder;
    }

    private CdEntities() {}
}
