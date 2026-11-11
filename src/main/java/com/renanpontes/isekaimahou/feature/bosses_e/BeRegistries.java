package com.renanpontes.isekaimahou.feature.bosses_e;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialChain;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialDragon;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialShade;
import com.renanpontes.isekaimahou.feature.bosses_e.item.PrimordialHeartItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo bosses_e: Dragão Primordial, Clone de Sombra, Âncora das Correntes, Coração do Dragão e os ovos. */
public final class BeRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    public static final Supplier<EntityType<PrimordialDragon>> PRIMORDIAL_DRAGON = ENTITIES.register(PrimordialDragon.ID,
            () -> EntityType.Builder.of(PrimordialDragon::new, MobCategory.MONSTER).sized(5.0F, 4.5F).eyeHeight(3.8F)
                    .fireImmune().clientTrackingRange(16).updateInterval(2).build(PrimordialDragon.ID));
    public static final Supplier<EntityType<PrimordialShade>> PRIMORDIAL_SHADE = ENTITIES.register("primordial_shade",
            () -> EntityType.Builder.<PrimordialShade>of(PrimordialShade::new, MobCategory.MISC).sized(3.0F, 2.0F)
                    .fireImmune().clientTrackingRange(12).updateInterval(2).noSave().build("primordial_shade"));
    /** Âncora das Correntes do Abismo (v1.0): acorrenta um jogador; temporária, nunca salva. */
    public static final Supplier<EntityType<PrimordialChain>> PRIMORDIAL_CHAIN = ENTITIES.register(PrimordialChain.ID,
            () -> EntityType.Builder.of(PrimordialChain::new, MobCategory.MONSTER).sized(1.2F, 3.0F).eyeHeight(2.2F)
                    .fireImmune().clientTrackingRange(10).updateInterval(3).noSave().build(PrimordialChain.ID));

    public static final DeferredItem<PrimordialHeartItem> PRIMORDIAL_DRAGON_HEART = ITEMS.register("primordial_dragon_heart",
            () -> new PrimordialHeartItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<DeferredSpawnEggItem> PRIMORDIAL_DRAGON_EGG = ITEMS.register("primordial_dragon_spawn_egg",
            () -> new DeferredSpawnEggItem(PRIMORDIAL_DRAGON, 0x2C1A46, 0xFFB347, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> PRIMORDIAL_CHAIN_EGG = ITEMS.register("primordial_chain_spawn_egg",
            () -> new DeferredSpawnEggItem(PRIMORDIAL_CHAIN, 0x1A1424, 0x9A4FD8, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    private BeRegistries() {}
}
