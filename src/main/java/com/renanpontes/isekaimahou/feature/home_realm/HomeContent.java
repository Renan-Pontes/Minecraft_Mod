package com.renanpontes.isekaimahou.feature.home_realm;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Itens da Dimensão Casa. O id {@code home_key} é fixo: o estalajadeiro das cidades vende o item por esse id. */
public final class HomeContent {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    /** Chave do Lar: usar uma vez libera a Dimensão Casa para sempre (a chave é consumida). */
    public static final DeferredItem<Item> HOME_KEY = ITEMS.register("home_key",
            () -> new HomeKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    private HomeContent() {}
}
