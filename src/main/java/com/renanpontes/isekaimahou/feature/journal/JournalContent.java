package com.renanpontes.isekaimahou.feature.journal;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.journal.entity.GuideSpiritEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo Diário: a entidade do Espírito Guia e o ovo dela. */
public final class JournalContent {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);

    public static final Supplier<EntityType<GuideSpiritEntity>> GUIDE_SPIRIT = ENTITIES.register("guide_spirit",
            () -> EntityType.Builder.<GuideSpiritEntity>of(GuideSpiritEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.25F).eyeHeight(0.9F).clientTrackingRange(8).fireImmune().build("guide_spirit"));

    public static final DeferredItem<DeferredSpawnEggItem> GUIDE_SPIRIT_EGG = ITEMS.register("guide_spirit_spawn_egg",
            () -> new DeferredSpawnEggItem(GUIDE_SPIRIT, 0xBFF4FF, 0xFFE27A, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(GUIDE_SPIRIT.get(), GuideSpiritEntity.createAttributes().build()));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) e.accept(GUIDE_SPIRIT_EGG.get());
        });
    }

    private JournalContent() {}
}
