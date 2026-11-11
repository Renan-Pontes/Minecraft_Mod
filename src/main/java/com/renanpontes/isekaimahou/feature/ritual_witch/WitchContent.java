package com.renanpontes.isekaimahou.feature.ritual_witch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.ritual_witch.block.WitchCauldronBlock;
import com.renanpontes.isekaimahou.feature.ritual_witch.block.WitchCauldronBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_witch.entity.CurseBottleEntity;
import com.renanpontes.isekaimahou.feature.ritual_witch.item.BottledCurseItem;
import com.renanpontes.isekaimahou.feature.ritual_witch.item.RevelationPotionItem;
import com.renanpontes.isekaimahou.feature.ritual_witch.item.RitualSpoonItem;
import com.renanpontes.isekaimahou.feature.ritual_witch.item.WitchElixirItem;
import com.renanpontes.isekaimahou.feature.ritual_witch.item.WitchRecipeBookItem;
import com.renanpontes.isekaimahou.feature.ritual_witch.item.WitchRecipePageItem;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Todos os registros do Ritual da Bruxa (bloco, itens, entidade, efeito, componente). */
public final class WitchContent {
    private static final String MODID = IsekaiMahou.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    // ---------------- Componente: id da receita guardada numa página ----------------
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> RECIPE_ID =
            COMPONENTS.registerComponentType("witch_recipe",
                    b -> b.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));

    // ---------------- Efeito: Fluxo de Mana (regeneração de mana maior) ----------------
    public static final DeferredHolder<MobEffect, MobEffect> MANA_FLOW = EFFECTS.register("witch_mana_flow",
            () -> new WitchEffect(MobEffectCategory.BENEFICIAL, 0x5A7BFF)
                    .addAttributeModifier(CoreRegistries.MANA_REGEN, IsekaiMahou.id("effect.witch_mana_flow"), 0.6,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE));

    // ---------------- Bloco: Caldeirão Ritual ----------------
    public static final DeferredBlock<WitchCauldronBlock> CAULDRON = BLOCKS.registerBlock("witch_cauldron", WitchCauldronBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(2.5F, 6.0F)
                    .sound(SoundType.METAL).noOcclusion()
                    .lightLevel(s -> s.getValue(WitchCauldronBlock.LIT) ? 7 : 0));
    public static final DeferredItem<BlockItem> CAULDRON_ITEM = ITEMS.registerSimpleBlockItem("witch_cauldron", CAULDRON);

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<WitchCauldronBlockEntity>> CAULDRON_BE = BLOCK_ENTITIES.register("witch_cauldron",
            () -> BlockEntityType.Builder.of(WitchCauldronBlockEntity::new, CAULDRON.get()).build(null));

    // ---------------- Entidade: garrafa de maldição arremessada ----------------
    public static final Supplier<EntityType<CurseBottleEntity>> CURSE_BOTTLE = ENTITIES.register("witch_curse_bottle",
            () -> EntityType.Builder.<CurseBottleEntity>of(CurseBottleEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build("witch_curse_bottle"));

    // ---------------- Ferramentas do ritual ----------------
    public static final DeferredItem<RitualSpoonItem> RITUAL_SPOON = ITEMS.register("ritual_spoon",
            () -> new RitualSpoonItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<WitchRecipePageItem> RECIPE_PAGE = ITEMS.register("witch_recipe_page",
            () -> new WitchRecipePageItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<WitchRecipeBookItem> RECIPE_BOOK = ITEMS.register("witch_recipe_book",
            () -> new WitchRecipeBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // ---------------- Ingredientes próprios ----------------
    public static final DeferredItem<Item> MANDRAKE_ROOT = ITEMS.registerSimpleItem("witch_mandrake_root", new Item.Properties());
    public static final DeferredItem<Item> MOONPETAL = ITEMS.registerSimpleItem("witch_moonpetal", new Item.Properties());
    public static final DeferredItem<Item> BAT_WING = ITEMS.registerSimpleItem("witch_bat_wing", new Item.Properties());
    public static final DeferredItem<Item> TOAD_EYE = ITEMS.registerSimpleItem("witch_toad_eye", new Item.Properties());
    public static final DeferredItem<Item> GRAVE_DUST = ITEMS.registerSimpleItem("witch_grave_dust", new Item.Properties());

    // ---------------- Elixires (bebíveis) ----------------
    // Receita fácil (fogo baixo, sem lua) e rende 3 doses: fica na faixa da maçã dourada, não acima dela
    public static final DeferredItem<WitchElixirItem> ELIXIR_VITALITY = elixir("witch_elixir_vitality", WitchElixirItem.Special.NONE,
            () -> List.of(new MobEffectInstance(MobEffects.REGENERATION, 600, 0), new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0),
                    new MobEffectInstance(MobEffects.SATURATION, 1, 1)));
    public static final DeferredItem<WitchElixirItem> ELIXIR_OWL_EYES = elixir("witch_elixir_owl_eyes", WitchElixirItem.Special.REVEAL,
            () -> List.of(new MobEffectInstance(MobEffects.NIGHT_VISION, 7200, 0)));
    public static final DeferredItem<WitchElixirItem> ELIXIR_TIDES = elixir("witch_elixir_tides", WitchElixirItem.Special.NONE,
            () -> List.of(new MobEffectInstance(MobEffects.WATER_BREATHING, 7200, 0), new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 2400, 0),
                    new MobEffectInstance(MobEffects.CONDUIT_POWER, 3600, 0)));
    public static final DeferredItem<WitchElixirItem> ELIXIR_FEATHER = elixir("witch_elixir_feather", WitchElixirItem.Special.NONE,
            () -> List.of(new MobEffectInstance(MobEffects.SLOW_FALLING, 3600, 0), new MobEffectInstance(MobEffects.JUMP, 3600, 1)));
    public static final DeferredItem<WitchElixirItem> ELIXIR_MANA = elixir("witch_elixir_mana", WitchElixirItem.Special.MANA,
            () -> List.of(new MobEffectInstance(MANA_FLOW, 2400, 0)));
    public static final DeferredItem<WitchElixirItem> ELIXIR_SHADOW_VEIL = elixir("witch_elixir_shadow_veil", WitchElixirItem.Special.NONE,
            () -> List.of(new MobEffectInstance(MobEffects.INVISIBILITY, 3600, 0), new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 3600, 0),
                    new MobEffectInstance(MobEffects.NIGHT_VISION, 3600, 0)));
    public static final DeferredItem<WitchElixirItem> ELIXIR_STONE_SKIN = elixir("witch_elixir_stone_skin", WitchElixirItem.Special.NONE,
            () -> List.of(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2400, 1), new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2400, 0),
                    new MobEffectInstance(MobEffects.ABSORPTION, 1200, 1)));

    // ---------------- Maldições engarrafadas (arremessáveis) ----------------
    public static final DeferredItem<BottledCurseItem> CURSE_FRAILTY = curse("bottled_curse_frailty", 0x8E9A5B, false,
            () -> List.of(new MobEffectInstance(MobEffects.WEAKNESS, 400, 1), new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 400, 0),
                    new MobEffectInstance(CoreRegistries.EXPOSED, 200, 0)));
    public static final DeferredItem<BottledCurseItem> CURSE_HEX = curse("bottled_curse_hex", 0x6A1F8A, false,
            // Corrompido II = 1,5 de dano por segundo: 160 ticks ≈ 12 de dano em área (240 passava de 18)
            () -> List.of(new MobEffectInstance(CoreRegistries.CORRUPTED, 160, 1), new MobEffectInstance(MobEffects.WEAKNESS, 240, 0),
                    new MobEffectInstance(MobEffects.DARKNESS, 120, 0)));
    public static final DeferredItem<BottledCurseItem> CURSE_MIRE = curse("bottled_curse_mire", 0x5B4632, false,
            () -> List.of(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2), new MobEffectInstance(CoreRegistries.ROOTED, 60, 0),
                    new MobEffectInstance(CoreRegistries.SOAKED, 200, 0)));
    public static final DeferredItem<BottledCurseItem> CURSE_BLIGHT = curse("bottled_curse_blight", 0x4E6B1F, true,
            () -> List.of(new MobEffectInstance(MobEffects.POISON, 200, 1), new MobEffectInstance(MobEffects.HUNGER, 400, 1),
                    new MobEffectInstance(MobEffects.WITHER, 160, 0), new MobEffectInstance(MobEffects.CONFUSION, 160, 0)));

    // ---------------- Poções de Revelação (tiers 1..5) ----------------
    public static final List<DeferredItem<RevelationPotionItem>> REVELATIONS = new ArrayList<>();

    static {
        Rarity[] rarity = {Rarity.UNCOMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.RARE, Rarity.EPIC};
        for (int t = 1; t <= RevelationPotionItem.MAX_TIER; t++) {
            final int tier = t;
            REVELATIONS.add(ITEMS.register("revelation_potion_" + t,
                    () -> new RevelationPotionItem(tier, new Item.Properties().stacksTo(1).rarity(rarity[tier - 1]))));
        }
    }

    private static DeferredItem<WitchElixirItem> elixir(String id, WitchElixirItem.Special special, Supplier<List<MobEffectInstance>> effects) {
        return ITEMS.register(id, () -> new WitchElixirItem(effects, special, new Item.Properties().stacksTo(16)));
    }

    private static DeferredItem<BottledCurseItem> curse(String id, int color, boolean lingering, Supplier<List<MobEffectInstance>> effects) {
        return ITEMS.register(id, () -> new BottledCurseItem(effects, color, lingering, new Item.Properties().stacksTo(16)));
    }

    /** Tudo o que aparece na aba criativa de Rituais, na ordem. */
    public static List<Item> creativeItems() {
        List<Item> out = new ArrayList<>();
        out.add(CAULDRON_ITEM.get());
        out.add(RITUAL_SPOON.get());
        out.add(RECIPE_BOOK.get());
        out.add(RECIPE_PAGE.get());
        out.add(MANDRAKE_ROOT.get());
        out.add(MOONPETAL.get());
        out.add(BAT_WING.get());
        out.add(TOAD_EYE.get());
        out.add(GRAVE_DUST.get());
        out.add(ELIXIR_VITALITY.get());
        out.add(ELIXIR_OWL_EYES.get());
        out.add(ELIXIR_TIDES.get());
        out.add(ELIXIR_FEATHER.get());
        out.add(ELIXIR_MANA.get());
        out.add(ELIXIR_SHADOW_VEIL.get());
        out.add(ELIXIR_STONE_SKIN.get());
        out.add(CURSE_FRAILTY.get());
        out.add(CURSE_HEX.get());
        out.add(CURSE_MIRE.get());
        out.add(CURSE_BLIGHT.get());
        REVELATIONS.forEach(r -> out.add(r.get()));
        return out;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        COMPONENTS.register(modBus);
    }

    /** MobEffect tem construtor protegido: subclasse simples (o comportamento vem dos modificadores). */
    public static class WitchEffect extends MobEffect {
        public WitchEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    private WitchContent() {}
}
