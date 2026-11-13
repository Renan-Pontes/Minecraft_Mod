package com.renanpontes.isekaimahou.feature.lore;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.lore.block.LoreSteleBlock;
import com.renanpontes.isekaimahou.feature.lore.item.LorePageItem;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registros do módulo lore: a <b>Estela Antiga</b> ({@code lore_stele}, bloco das ruínas), a <b>Página Perdida</b>
 * ({@code lore_page}), o componente que diz QUAL fragmento uma página carrega (ausente = sorteia ao ler) e os dados
 * de lore do jogador ({@link LoreData}).
 */
public final class LoreContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE, IsekaiMahou.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(
            NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);

    public static final DeferredBlock<LoreSteleBlock> LORE_STELE = BLOCKS.registerBlock("lore_stele", LoreSteleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F, 6.0F).sound(SoundType.STONE)
                    .requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(s -> s.getValue(LoreSteleBlock.INSCRIBED) ? 5 : 0));
    public static final DeferredItem<BlockItem> LORE_STELE_ITEM = ITEMS.registerSimpleBlockItem("lore_stele", LORE_STELE,
            new Item.Properties());

    /** Fragmento que a página carrega (id de {@link LoreFragments}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> FRAGMENT = COMPONENTS.registerComponentType(
            "lore_fragment", b -> b.persistent(Codec.string(1, 64)).networkSynchronized(ByteBufCodecs.stringUtf8(64)));

    public static final DeferredItem<LorePageItem> LORE_PAGE = ITEMS.register("lore_page",
            () -> new LorePageItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    /** Dados de lore do jogador (formato em {@link LoreData}); sobrevivem à morte. */
    public static final Supplier<AttachmentType<CompoundTag>> PLAYER_DATA = ATTACHMENTS.register("lore_player",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    /** Uma Página Perdida que já diz qual fragmento é (para recompensas de missão, chefes, comandos). */
    public static ItemStack pageOf(String fragmentId) {
        ItemStack stack = new ItemStack(LORE_PAGE.get());
        if (LoreFragments.get(fragmentId) != null) stack.set(FRAGMENT.get(), fragmentId);
        return stack;
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() != CoreRegistries.TAB_WORLD.getKey()) return;
            e.accept(LORE_PAGE.get());
            e.accept(LORE_STELE_ITEM.get());
        });
    }

    private LoreContent() {}
}
