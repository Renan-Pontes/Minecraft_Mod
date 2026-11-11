package com.renanpontes.isekaimahou.feature.class_gambler;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_gambler.effect.GamblerEffect;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.CasinoDomainEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.GamblerDiceEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.GumTetherEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.IdleDeathDomainEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.RouletteWheelEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.SlotMachineEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.ThrownCardEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.item.CasinoChipItem;
import com.renanpontes.isekaimahou.feature.class_gambler.item.GamblerCardItem;
import com.renanpontes.isekaimahou.feature.class_gambler.item.GamblerDeckItem;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Itens, entidades e efeitos do Apostador (o traje fica em {@code outfit/GamblerOutfits}). */
public final class GbRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    // ------------------------------------------------------------------ itens
    /** Arma do kit (id exigido pelo MagicPath): o Baralho Arcano, igual ao grau I. */
    public static final DeferredItem<GamblerDeckItem> DECK = ITEMS.register("gambler_deck",
            () -> new GamblerDeckItem(1, new Item.Properties().stacksTo(1)));
    /** Graus do baralho para a Guilda/Provações ({@code ClassKits.tieredWeaponPrefix}) e os ranks da Casa. */
    public static final DeferredItem<GamblerDeckItem> DECK_T1 = deck(1, Rarity.COMMON);
    public static final DeferredItem<GamblerDeckItem> DECK_T2 = deck(2, Rarity.UNCOMMON);
    public static final DeferredItem<GamblerDeckItem> DECK_T3 = deck(3, Rarity.RARE);
    public static final DeferredItem<GamblerDeckItem> DECK_T4 = deck(4, Rarity.EPIC);
    public static final DeferredItem<GamblerCardItem> CARD = ITEMS.register("gambler_card",
            () -> new GamblerCardItem(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<CasinoChipItem> CASINO_CHIP = ITEMS.register("casino_chip",
            () -> new CasinoChipItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));

    private static DeferredItem<GamblerDeckItem> deck(int tier, Rarity rarity) {
        Item.Properties p = new Item.Properties().stacksTo(1).rarity(rarity);
        if (tier >= 4) p = p.fireResistant();
        Item.Properties props = p;
        return ITEMS.register("gambler_deck_t" + tier, () -> new GamblerDeckItem(tier, props));
    }

    /** Baralho do grau (1..4). */
    public static GamblerDeckItem deckOfTier(int tier) {
        return switch (Math.max(1, Math.min(4, tier))) {
            case 2 -> DECK_T2.get();
            case 3 -> DECK_T3.get();
            case 4 -> DECK_T4.get();
            default -> DECK_T1.get();
        };
    }

    // ------------------------------------------------------------------ entidades (todas temporárias)
    public static final Supplier<EntityType<ThrownCardEntity>> THROWN_CARD = ENTITIES.register("thrown_card",
            () -> EntityType.Builder.<ThrownCardEntity>of(ThrownCardEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(5).build("thrown_card"));
    public static final Supplier<EntityType<GamblerDiceEntity>> DICE = ENTITIES.register("gambler_dice",
            () -> EntityType.Builder.<GamblerDiceEntity>of(GamblerDiceEntity::new, MobCategory.MISC)
                    .sized(0.45F, 0.45F).clientTrackingRange(6).updateInterval(2).build("gambler_dice"));
    public static final Supplier<EntityType<RouletteWheelEntity>> ROULETTE = ENTITIES.register("roulette_wheel",
            () -> EntityType.Builder.<RouletteWheelEntity>of(RouletteWheelEntity::new, MobCategory.MISC).fireImmune()
                    .sized(2.4F, 0.5F).clientTrackingRange(8).updateInterval(20).build("roulette_wheel"));
    public static final Supplier<EntityType<SlotMachineEntity>> SLOT_MACHINE = ENTITIES.register("slot_machine",
            () -> EntityType.Builder.<SlotMachineEntity>of(SlotMachineEntity::new, MobCategory.MISC).fireImmune()
                    .sized(1.3F, 2.3F).clientTrackingRange(8).updateInterval(20).build("slot_machine"));
    public static final Supplier<EntityType<CasinoDomainEntity>> CASINO_DOMAIN = ENTITIES.register("casino_domain",
            () -> EntityType.Builder.<CasinoDomainEntity>of(CasinoDomainEntity::new, MobCategory.MISC).fireImmune()
                    .sized(2.0F, 0.5F).clientTrackingRange(10).updateInterval(20).build("casino_domain"));

    /** Goma Elástica (homenagem ao mágico de cartas): o elo rosa entre você e o alvo. */
    public static final Supplier<EntityType<GumTetherEntity>> GUM_TETHER = ENTITIES.register("gambler_gum_tether",
            () -> EntityType.Builder.<GumTetherEntity>of(GumTetherEntity::new, MobCategory.MISC).fireImmune().noSave()
                    .sized(0.2F, 0.2F).clientTrackingRange(8).updateInterval(1).build("gambler_gum_tether"));
    /** Domínio da Morte Ociosa (forma G do rank V). */
    public static final Supplier<EntityType<IdleDeathDomainEntity>> IDLE_DEATH = ENTITIES.register("idle_death_domain",
            () -> EntityType.Builder.<IdleDeathDomainEntity>of(IdleDeathDomainEntity::new, MobCategory.MISC).fireImmune().noSave()
                    .sized(2.0F, 0.5F).clientTrackingRange(10).updateInterval(10).build("idle_death_domain"));

    // ------------------------------------------------------------------ efeitos
    /** JACKPOT: mais rápido e com sorte nos saques; a sorte infinita em si é do JackpotService. */
    public static final DeferredHolder<MobEffect, MobEffect> JACKPOT = EFFECTS.register("gambler_jackpot",
            () -> new GamblerEffect(MobEffectCategory.BENEFICIAL, 0xFF4AD2)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("gambler_jackpot_speed"), 0.2,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.LUCK, IsekaiMahou.id("gambler_jackpot_luck"), 3.0,
                            AttributeModifier.Operation.ADD_VALUE));
    /** Sortudo: +Sorte nas rolagens e nos saques; chance de esquivar golpes. */
    public static final DeferredHolder<MobEffect, MobEffect> LUCKY = EFFECTS.register("gambler_lucky",
            () -> new GamblerEffect(MobEffectCategory.BENEFICIAL, 0x6FD88A)
                    .addAttributeModifier(Attributes.LUCK, IsekaiMahou.id("gambler_lucky_luck"), 1.0,
                            AttributeModifier.Operation.ADD_VALUE));
    /** Azarado: recebe mais dano, erra golpes e rola pior (−2 de Sorte). */
    public static final DeferredHolder<MobEffect, MobEffect> JINXED = EFFECTS.register("gambler_jinxed",
            () -> new GamblerEffect(MobEffectCategory.HARMFUL, 0x5A3A78)
                    .addAttributeModifier(Attributes.LUCK, IsekaiMahou.id("gambler_jinxed_luck"), -1.0,
                            AttributeModifier.Operation.ADD_VALUE));

    /** Disfarce (Textura Surpresa): invisível para os monstros de longe; o próximo golpe é uma surpresa (+60%). */
    public static final DeferredHolder<MobEffect, MobEffect> DISGUISE = EFFECTS.register("gambler_disguise",
            () -> new GamblerEffect(MobEffectCategory.BENEFICIAL, 0xB05AE0));

    /** Ordem da aba criativa (o traje entra depois, pelo GamblerOutfits). */
    public static List<Holder<Item>> creativeItems() {
        return List.of(DECK, DECK_T1, DECK_T2, DECK_T3, DECK_T4, CARD, CASINO_CHIP);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
    }

    private GbRegistries() {}
}
