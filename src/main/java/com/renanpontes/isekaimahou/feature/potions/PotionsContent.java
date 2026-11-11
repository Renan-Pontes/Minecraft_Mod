package com.renanpontes.isekaimahou.feature.potions;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.potions.effect.FlowEffect;
import com.renanpontes.isekaimahou.feature.potions.entity.ThrownMahouPotion;
import com.renanpontes.isekaimahou.feature.potions.item.MahouPotionItem;
import com.renanpontes.isekaimahou.feature.potions.item.MahouSplashPotionItem;
import com.renanpontes.isekaimahou.feature.potions.loot.PotionsLootModifier;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo potions: 36 frascos (2 tipos × 3 formas × 6 tiers), 2 efeitos de fluxo, o frasco arremessado e o loot. */
public final class PotionsContent {
    private PotionsContent() {}

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);
    static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, IsekaiMahou.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> VITAL_FLOW = EFFECTS.register("vital_flow",
            () -> new FlowEffect(PotionKind.HEALTH, 0xE8384F));
    public static final DeferredHolder<MobEffect, MobEffect> ARCANE_FLOW = EFFECTS.register("arcane_flow",
            () -> new FlowEffect(PotionKind.MANA, 0x5A7BFF));

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownMahouPotion>> THROWN_POTION = ENTITIES.register(
            "thrown_mahou_potion", () -> EntityType.Builder.<ThrownMahouPotion>of(ThrownMahouPotion::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build("thrown_mahou_potion"));

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<PotionsLootModifier>> POTIONS_LOOT =
            LOOT.register("potions_loot", () -> PotionsLootModifier.CODEC);

    /** [tipo][forma][tier] → item. */
    private static final Map<PotionKind, Map<PotionForm, Map<PotionTier, DeferredItem<Item>>>> TABLE = new EnumMap<>(PotionKind.class);
    /** Todos os frascos na ordem da aba criativa (tipo → tier → forma). */
    static final List<DeferredItem<Item>> ALL = new ArrayList<>();

    static {
        for (PotionKind kind : PotionKind.values()) {
            Map<PotionForm, Map<PotionTier, DeferredItem<Item>>> byForm = new EnumMap<>(PotionForm.class);
            TABLE.put(kind, byForm);
            for (PotionForm form : PotionForm.values()) byForm.put(form, new EnumMap<>(PotionTier.class));
            for (PotionTier tier : PotionTier.values()) {
                for (PotionForm form : PotionForm.values()) {
                    DeferredItem<Item> item = ITEMS.register(form.itemId(kind, tier), () -> create(kind, form, tier));
                    byForm.get(form).put(tier, item);
                    ALL.add(item);
                }
            }
        }
    }

    private static Item create(PotionKind kind, PotionForm form, PotionTier tier) {
        Item.Properties props = new Item.Properties().stacksTo(16).rarity(tier.rarity);
        if (tier.legendary()) props.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return form == PotionForm.SPLASH ? new MahouSplashPotionItem(kind, tier, props) : new MahouPotionItem(kind, form, tier, props);
    }

    public static DeferredItem<Item> item(PotionKind kind, PotionForm form, PotionTier tier) {
        return TABLE.get(kind).get(form).get(tier);
    }

    public static List<DeferredItem<Item>> all() {
        return ALL;
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        ENTITIES.register(modBus);
        LOOT.register(modBus);
    }
}
