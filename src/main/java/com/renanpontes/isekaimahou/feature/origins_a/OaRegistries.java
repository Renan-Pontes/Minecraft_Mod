package com.renanpontes.isekaimahou.feature.origins_a;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.origins_a.familiar.FamiliarCatEntity;
import com.renanpontes.isekaimahou.feature.origins_a.familiar.FamiliarCrowEntity;
import com.renanpontes.isekaimahou.feature.origins_a.rune.ExplosiveSealEntity;
import com.renanpontes.isekaimahou.feature.origins_a.rune.TeleportCircleEntity;
import com.renanpontes.isekaimahou.feature.origins_a.rune.TempEnchant;
import com.renanpontes.isekaimahou.feature.origins_a.rune.WardEntity;
import com.renanpontes.isekaimahou.feature.origins_a.shape.Shape;
import com.renanpontes.isekaimahou.feature.origins_a.shape.ShapeshiftEffect;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo origins_a (Runas, Maldições, Metamorfose). */
public final class OaRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    // ---------------- Dados do jogador ----------------
    public static final Supplier<AttachmentType<OaData>> DATA = ATTACHMENTS.register("origins_a",
            () -> AttachmentType.builder(() -> new OaData()).serialize(OaData.CODEC).copyOnDeath().build());

    // ---------------- Componente: encantamento temporário guardado no item ----------------
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TempEnchant.Data>> TEMP_ENCHANT =
            COMPONENTS.registerComponentType("temp_enchantment",
                    b -> b.persistent(TempEnchant.Data.CODEC).networkSynchronized(TempEnchant.Data.STREAM_CODEC));

    // ---------------- Entidades: runas ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<ExplosiveSealEntity>> EXPLOSIVE_SEAL = ENTITIES.register("origin_explosive_seal",
            () -> EntityType.Builder.<ExplosiveSealEntity>of(ExplosiveSealEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.125F).clientTrackingRange(8).updateInterval(20).fireImmune().build("origin_explosive_seal"));
    public static final DeferredHolder<EntityType<?>, EntityType<TeleportCircleEntity>> TELEPORT_CIRCLE = ENTITIES.register("origin_teleport_circle",
            () -> EntityType.Builder.<TeleportCircleEntity>of(TeleportCircleEntity::new, MobCategory.MISC)
                    .sized(2.0F, 0.125F).clientTrackingRange(10).updateInterval(20).fireImmune().build("origin_teleport_circle"));
    public static final DeferredHolder<EntityType<?>, EntityType<WardEntity>> WARD = ENTITIES.register("origin_ward",
            () -> EntityType.Builder.<WardEntity>of(WardEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.125F).clientTrackingRange(10).updateInterval(20).fireImmune().build("origin_ward"));

    // ---------------- Entidades: familiares ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<FamiliarCrowEntity>> FAMILIAR_CROW = ENTITIES.register("familiar_crow",
            () -> EntityType.Builder.<FamiliarCrowEntity>of(FamiliarCrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.55F).clientTrackingRange(10).build("familiar_crow"));
    public static final DeferredHolder<EntityType<?>, EntityType<FamiliarCatEntity>> FAMILIAR_CAT = ENTITIES.register("familiar_cat",
            () -> EntityType.Builder.<FamiliarCatEntity>of(FamiliarCatEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.7F).clientTrackingRange(10).build("familiar_cat"));

    public static final DeferredItem<DeferredSpawnEggItem> CROW_EGG = ITEMS.register("familiar_crow_spawn_egg",
            () -> new DeferredSpawnEggItem(FAMILIAR_CROW, 0x1B1B26, 0x7A4FC8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CAT_EGG = ITEMS.register("familiar_cat_spawn_egg",
            () -> new DeferredSpawnEggItem(FAMILIAR_CAT, 0x141414, 0x7CFF6A, new Item.Properties()));

    // ---------------- Efeitos: Metamorfose ----------------
    public static final Map<Shape, DeferredHolder<MobEffect, ShapeshiftEffect>> SHAPE_EFFECTS = new EnumMap<>(Shape.class);

    static {
        for (Shape s : Shape.values()) {
            SHAPE_EFFECTS.put(s, EFFECTS.register(s.effectId(), () -> new ShapeshiftEffect(s)));
        }
    }

    // ---------------- Efeitos: Maldições ----------------
    /** Marca do Boneco de Vodu (o vínculo em si fica em VoodooLinks). */
    public static final DeferredHolder<MobEffect, MobEffect> VOODOO_BOUND = EFFECTS.register("voodoo_bound",
            () -> new OaMarkEffect(MobEffectCategory.HARMFUL, 0xB0303A));
    /** Mau-Olhado: azar (sorte -2), erra ataques e, se for jogador, colhe menos. */
    public static final DeferredHolder<MobEffect, MobEffect> EVIL_EYE = EFFECTS.register("evil_eye",
            () -> new OaMarkEffect(MobEffectCategory.HARMFUL, 0x3FBF4A)
                    .addAttributeModifier(Attributes.LUCK, IsekaiMahou.id("effect.evil_eye"), -2.0, AttributeModifier.Operation.ADD_VALUE));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        ATTACHMENTS.register(modBus);
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(FAMILIAR_CROW.get(), FamiliarCrowEntity.createAttributes().build());
            e.put(FAMILIAR_CAT.get(), FamiliarCatEntity.createAttributes().build());
        });
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
                e.accept(CROW_EGG.get());
                e.accept(CAT_EGG.get());
            }
        });
    }

    /** Efeito simples (marca/ícone): o comportamento fica nos eventos do módulo. */
    public static class OaMarkEffect extends MobEffect {
        public OaMarkEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    private OaRegistries() {}
}
