package com.renanpontes.isekaimahou.feature.ritual_mage;

import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.ritual_mage.item.ResearchNoteItem;
import com.renanpontes.isekaimahou.feature.ritual_mage.network.MagePayloads;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.PhenomenaManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Listeners do módulo: dados recarregáveis, sincronização do Atlas, aba criativa e kit do Mago. */
public final class MageEvents {

    public static void register(IEventBus modBus) {
        modBus.addListener(MageEvents::creativeTab);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(PhenomenaManager.INSTANCE));
        // Dispara ao entrar no mundo (só o jogador novo) e a cada /reload (todos)
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> e.getRelevantPlayers().forEach(MagePayloads::syncPhenomena));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) MagePayloads.syncAtlas(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) MagePayloads.syncAtlas(sp);
        });

        // Kit do Mago: lente, atlas, mesa, papel e estantes para a primeira tese avançada
        ClassKits.addItem(MagicPath.MAGE, () -> new ItemStack(MageRegistries.ARCANE_LENS.get()));
        ClassKits.addItem(MagicPath.MAGE, () -> new ItemStack(MageRegistries.MAGE_ATLAS.get()));
        ClassKits.addItem(MagicPath.MAGE, () -> new ItemStack(MageRegistries.RESEARCH_TABLE_ITEM.get()));
        ClassKits.addItem(MagicPath.MAGE, () -> new ItemStack(Items.PAPER, 16));
        ClassKits.addItem(MagicPath.MAGE, () -> new ItemStack(Items.BOOKSHELF, 6));
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_RITUALS.getKey()) return;
        e.accept(MageRegistries.ARCANE_LENS.get());
        e.accept(MageRegistries.MAGE_ATLAS.get());
        e.accept(MageRegistries.RESEARCH_TABLE_ITEM.get());
        for (Element el : Element.values()) e.accept(ResearchNoteItem.of(ResearchNoteItem.UNKNOWN, el, 1));
    }

    private MageEvents() {}
}
