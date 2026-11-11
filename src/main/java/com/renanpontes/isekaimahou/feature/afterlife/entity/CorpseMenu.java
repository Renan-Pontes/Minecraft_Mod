package com.renanpontes.isekaimahou.feature.afterlife.entity;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Tela do Corpo (lado do servidor): mesmo layout do baú grande do vanilla (o cliente abre a tela de baú de 6 fileiras),
 * mas só deixa TIRAR — nada entra no corpo (assim ele esvazia e some, e não vira um baú infinito).
 */
public class CorpseMenu extends AbstractContainerMenu {
    private static final int ROWS = 6;
    private final CorpseEntity corpse;
    private final Container container;

    CorpseMenu(int id, Inventory playerInv, CorpseEntity corpse) {
        super(MenuType.GENERIC_9x6, id);
        this.corpse = corpse;
        this.container = corpse.container();
        corpse.onMenuOpened();
        int shift = (ROWS - 4) * 18;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < 9; c++) addSlot(new TakeOnlySlot(container, c + r * 9, 8 + c * 18, 18 + r * 18));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 103 + r * 18 + shift));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInv, c, 8 + c * 18, 161 + shift));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int corpseSlots = ROWS * 9;
        if (index < 0 || index >= corpseSlots) return ItemStack.EMPTY; // do inventário para o corpo: não entra
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, corpseSlots, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return corpse.isAlive() && !corpse.inChallenge() && player.distanceToSqr(corpse) <= 64.0 && corpse.canLoot(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        corpse.onMenuClosed();
    }

    /** Espaço do corpo: só retirar. */
    private static final class TakeOnlySlot extends Slot {
        TakeOnlySlot(Container c, int index, int x, int y) {
            super(c, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
