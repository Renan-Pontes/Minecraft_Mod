package com.renanpontes.isekaimahou.feature.ritual_mage;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_mage.block.ResearchTableBlock;
import com.renanpontes.isekaimahou.feature.ritual_mage.block.ResearchTableBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_mage.block.ResearchTableMenu;
import com.renanpontes.isekaimahou.feature.ritual_mage.item.ArcaneLensItem;
import com.renanpontes.isekaimahou.feature.ritual_mage.item.MageAtlasItem;
import com.renanpontes.isekaimahou.feature.ritual_mage.item.ResearchNote;
import com.renanpontes.isekaimahou.feature.ritual_mage.item.ResearchNoteItem;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.MageResearchData;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do Ritual do Mago (Pesquisa Arcana). */
public final class MageRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

    /** Fenômeno + elemento guardados numa Nota de Pesquisa. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResearchNote>> NOTE = COMPONENTS.registerComponentType(
            "mage_research_note", b -> b.persistent(ResearchNote.CODEC).networkSynchronized(ResearchNote.STREAM_CODEC));

    /** Atlas do jogador: fenômenos observados e teses concluídas (sobrevive à morte). */
    public static final Supplier<AttachmentType<MageResearchData>> RESEARCH = ATTACHMENTS.register("mage_research",
            () -> AttachmentType.builder(() -> new MageResearchData()).serialize(MageResearchData.CODEC).copyOnDeath().build());

    public static final DeferredBlock<ResearchTableBlock> RESEARCH_TABLE = BLOCKS.registerBlock("research_table", ResearchTableBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5F).sound(SoundType.WOOD)
                    .noOcclusion().lightLevel(s -> 6).ignitedByLava());

    public static final DeferredItem<BlockItem> RESEARCH_TABLE_ITEM = ITEMS.register("research_table",
            () -> new ResearchTableBlock.TableItem(RESEARCH_TABLE.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<ArcaneLensItem> ARCANE_LENS = ITEMS.registerItem("arcane_lens", ArcaneLensItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<ResearchNoteItem> RESEARCH_NOTE = ITEMS.registerItem("research_note", ResearchNoteItem::new,
            new Item.Properties());
    public static final DeferredItem<MageAtlasItem> MAGE_ATLAS = ITEMS.registerItem("mage_atlas", MageAtlasItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<ResearchTableBlockEntity>> RESEARCH_TABLE_BE = BLOCK_ENTITIES.register("research_table",
            () -> BlockEntityType.Builder.of(ResearchTableBlockEntity::new, RESEARCH_TABLE.get()).build(null));

    public static final Supplier<MenuType<ResearchTableMenu>> RESEARCH_TABLE_MENU = MENUS.register("research_table",
            () -> IMenuTypeExtension.create(ResearchTableMenu::new));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    private MageRegistries() {}
}
