package com.renanpontes.isekaimahou.feature.familiars;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo familiars: os dados do jogador, o corpo do familiar e o Incenso do Vínculo. */
public final class FamiliarContent {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    public static final Supplier<AttachmentType<FamiliarData>> DATA = ATTACHMENTS.register("familiars",
            () -> AttachmentType.serializable(FamiliarData::new).copyOnDeath().build());

    /** Categoria MISC + noSave: não conta no limite de criaturas e nunca fica gravado no chunk. */
    public static final DeferredHolder<EntityType<?>, EntityType<FamiliarEntity>> FAMILIAR = ENTITIES.register("familiar_spirit",
            () -> EntityType.Builder.<FamiliarEntity>of(FamiliarEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.55F).clientTrackingRange(8).updateInterval(2).noSave().fireImmune().build("familiar_spirit"));

    public static final DeferredItem<BondIncenseItem> BOND_INCENSE = ITEMS.register("bond_incense",
            () -> new BondIncenseItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    private FamiliarContent() {}

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(FAMILIAR.get(), FamiliarEntity.createAttributes().build()));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreRegistries.TAB_RITUALS.getKey()) e.accept(BOND_INCENSE.get());
        });
    }

    public static FamiliarData data(Player p) {
        return p.getData(DATA);
    }
}
