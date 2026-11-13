package com.renanpontes.isekaimahou.feature.flat_lands;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.flat_lands.block.OmikujiBoxBlock;
import com.renanpontes.isekaimahou.feature.flat_lands.effect.SakuraFortuneEffect;
import com.renanpontes.isekaimahou.feature.flat_lands.item.FortuneSlipItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do flat_lands: Caixa do Omikuji, Papel da Sorte e os 2 efeitos de sorte do dia. */
public final class FlatLandsContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    public static final DeferredBlock<OmikujiBoxBlock> OMIKUJI_BOX = BLOCKS.registerBlock("sakura_omikuji_box", OmikujiBoxBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5F, 3.0F).sound(SoundType.CHERRY_WOOD)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> OMIKUJI_BOX_ITEM = ITEMS.registerSimpleBlockItem("sakura_omikuji_box", OMIKUJI_BOX,
            new Item.Properties());
    public static final DeferredItem<FortuneSlipItem> FORTUNE_SLIP = ITEMS.registerItem("sakura_fortune_slip", FortuneSlipItem::new,
            new Item.Properties().stacksTo(16));

    public static final DeferredHolder<MobEffect, MobEffect> SAKURA_BLESSING = EFFECTS.register("sakura_blessing",
            () -> new SakuraFortuneEffect(true));
    public static final DeferredHolder<MobEffect, MobEffect> SAKURA_MISFORTUNE = EFFECTS.register("sakura_misfortune",
            () -> new SakuraFortuneEffect(false));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
    }

    private FlatLandsContent() {}
}
