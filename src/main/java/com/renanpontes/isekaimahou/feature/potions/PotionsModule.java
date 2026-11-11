package com.renanpontes.isekaimahou.feature.potions;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Módulo "potions" (v1.0, §0.1): Poção de Vida e Poção de Mana (o frasco de mana restaura o recurso da classe:
 * Mana/Vigor/Foco) em 6 tiers — Menor, Comum, Maior, Superior, Suprema e Elixir Lendário — nas formas de beber,
 * arremessável e prolongada; recarga compartilhada; alquimia (Suporte de Poções), Caldeirão da Bruxa e bancada
 * (tiers altos com essências e itens de chefe); loot global de baús e chefes.
 *
 * <p>Ids para outros módulos (ex.: alquimista das cidades): {@code isekai_mahou:[splash_|long_]<health|mana>_potion_
 * <lesser|common|greater|superior|supreme|legendary>}; efeitos {@code isekai_mahou:vital_flow}, {@code arcane_flow}.
 */
public class PotionsModule implements Module {
    @Override
    public String id() {
        return "potions";
    }

    @Override
    public void init(IEventBus modBus) {
        PotionsContent.register(modBus);
        modBus.addListener(this::creativeTab);
        modBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(RegisterBrewingRecipesEvent.class, PotionsBrewing::register);
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CoreRegistries.TAB_MAGIC.getKey()) return;
        for (DeferredItem<Item> item : PotionsContent.all()) event.accept(item.get());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Dispensers arremessam os frascos de respingo (mapa do vanilla não é thread-safe → enqueueWork).
        event.enqueueWork(() -> {
            for (PotionKind kind : PotionKind.values()) {
                for (PotionTier tier : PotionTier.values()) {
                    DispenserBlock.registerProjectileBehavior(PotionsContent.item(kind, PotionForm.SPLASH, tier).get());
                }
            }
        });
    }
}
