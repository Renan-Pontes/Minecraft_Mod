package com.renanpontes.isekaimahou.feature.arcana_items;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.event.PlaceEvent;
import com.renanpontes.isekaimahou.feature.arcana_items.item.AlchemistBagItem;
import com.renanpontes.isekaimahou.feature.arcana_items.item.AlchemistBagMenu;
import com.renanpontes.isekaimahou.feature.arcana_items.item.ManaNecklaceItem;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Eventos de jogo do módulo: última cidade, coleta da bolsa, mana que acompanha a melhoria do colar, dicas. */
final class ArcanaEvents {
    static void register() {
        NeoForge.EVENT_BUS.addListener((PlaceEvent.Enter e) -> ReturnTravel.onPlaceEnter(e));
        NeoForge.EVENT_BUS.addListener(ArcanaEvents::onPickup);
        NeoForge.EVENT_BUS.addListener(ArcanaEvents::onCrafted);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ManaNecklaceItem.forget(e.getEntity().getUUID()));
    }

    /**
     * Coleta da Bolsa do Alquimista: um reagente apanhado do chão vai direto para a bolsa (se ela estiver no
     * inventário com a coleta ligada, o dono for de classe Mágica e a tela dela não estiver aberta).
     */
    private static void onPickup(ItemEntityPickupEvent.Pre e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp) || sp.isSpectator()) return;
        ItemEntity ie = e.getItemEntity();
        ItemStack stack = ie.getItem();
        if (ie.hasPickUpDelay() || !AlchemistBagItem.accepts(stack)) return;
        if (ie.getTarget() != null && !ie.getTarget().equals(sp.getUUID())) return;
        if (sp.containerMenu instanceof AlchemistBagMenu) return;
        ItemStack bag = findBag(sp.getInventory());
        if (bag.isEmpty() || !AlchemistBagItem.canUse(MagicApi.get(sp).path())) return;
        int moved = AlchemistBagItem.insert(bag, stack);
        if (moved <= 0) return;
        sp.take(ie, moved);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.4F, 1.3F);
        if (stack.isEmpty()) {
            ie.discard();
            e.setCanPickup(TriState.FALSE);
        } else {
            ie.setItem(stack);
        }
    }

    private static ItemStack findBag(Inventory inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ArcanaContent.ALCHEMIST_BAG.get()) && AlchemistBagItem.collecting(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    /** Melhorar o colar na bancada leva junto a mana guardada no colar antigo; itens novos mostram a dica uma vez. */
    private static void onCrafted(PlayerEvent.ItemCraftedEvent e) {
        ItemStack out = e.getCrafting();
        if (out.getItem() instanceof ManaNecklaceItem necklace) {
            Container grid = e.getInventory();
            int carried = 0;
            for (int i = 0; i < grid.getContainerSize(); i++) {
                ItemStack in = grid.getItem(i);
                if (in.getItem() instanceof ManaNecklaceItem) carried += ManaNecklaceItem.stored(in);
            }
            if (carried > 0) necklace.setStored(out, Math.max(ManaNecklaceItem.stored(out), carried));
        } else if (out.is(ArcanaContent.BLANK_RECIPE_SCROLL.get())) {
            Hints.show(e.getEntity(), "arcana_items.recipe_scroll");
        } else if (out.is(ArcanaContent.RETURN_SCROLL.get())) {
            Hints.show(e.getEntity(), "arcana_items.return_scroll");
        }
    }

    private ArcanaEvents() {}
}
