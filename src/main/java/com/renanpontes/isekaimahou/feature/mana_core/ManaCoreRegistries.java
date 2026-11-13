package com.renanpontes.isekaimahou.feature.mana_core;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.mana_core.block.ManaCoreBlock;
import com.renanpontes.isekaimahou.feature.mana_core.block.ManaCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.mana_core.block.ManaCoreItem;
import com.renanpontes.isekaimahou.feature.mana_core.block.ManaCoreMenu;
import com.renanpontes.isekaimahou.feature.mana_core.item.ManaConductorItem;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo mana_core: Núcleo de Mana (bloco/item/BE/menu), Condutor, efeito, componentes e a sobrecarga. */
public final class ManaCoreRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

    // ---------------------------------------------------------------- Componentes
    /** Mana guardado num Núcleo quebrado (o item mantém a carga). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CORE_MANA = COMPONENTS.registerComponentType(
            "core_mana", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** Núcleo sintonizado no Condutor de Mana (mundo + posição). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> CONDUCTOR_LINK = COMPONENTS.registerComponentType(
            "conductor_link", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    // ---------------------------------------------------------------- Sobrecarga (mana acima do máximo)
    /** Não passa pela morte: morreu, a sobrecarga se dissipa. */
    public static final Supplier<AttachmentType<Float>> OVERCHARGE = ATTACHMENTS.register("mana_overcharge",
            () -> AttachmentType.builder(() -> 0F).serialize(Codec.FLOAT).build());

    // ---------------------------------------------------------------- Bloco / itens
    public static final DeferredBlock<ManaCoreBlock> MANA_CORE = BLOCKS.registerBlock("mana_core", ManaCoreBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 12.0F).sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)
                    .lightLevel(s -> 3 + s.getValue(ManaCoreBlock.FILL) * 2));

    public static final DeferredItem<ManaCoreItem> MANA_CORE_ITEM = ITEMS.register("mana_core",
            () -> new ManaCoreItem(MANA_CORE.get(), new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<ManaConductorItem> MANA_CONDUCTOR = ITEMS.register("mana_conductor",
            () -> new ManaConductorItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<ManaCoreBlockEntity>> MANA_CORE_BE = BLOCK_ENTITIES.register("mana_core",
            () -> BlockEntityType.Builder.of(ManaCoreBlockEntity::new, MANA_CORE.get()).build(null));

    public static final Supplier<MenuType<ManaCoreMenu>> MANA_CORE_MENU = MENUS.register("mana_core",
            () -> IMenuTypeExtension.create(ManaCoreMenu::new));

    // ---------------------------------------------------------------- Efeito
    public static final DeferredHolder<MobEffect, ManaIntoxicationEffect> MANA_INTOXICATION = EFFECTS.register("mana_intoxication",
            ManaIntoxicationEffect::new);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        EFFECTS.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    private ManaCoreRegistries() {}
}
