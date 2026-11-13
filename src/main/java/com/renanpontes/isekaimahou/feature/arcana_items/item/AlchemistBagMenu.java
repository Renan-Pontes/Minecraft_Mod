package com.renanpontes.isekaimahou.feature.arcana_items.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Tela da Bolsa do Alquimista (lado do servidor): o layout de um baú de 3 fileiras do vanilla — o cliente abre a tela
 * de baú comum, sem tela própria. Cada mudança é gravada na hora no item; o espaço onde a bolsa está fica travado
 * enquanto a tela está aberta (para ela não ser movida/guardada dentro de si mesma).
 */
public class AlchemistBagMenu extends AbstractContainerMenu {
    private static final int ROWS = 3;

    private final ServerPlayer owner;
    private final ItemStack bag;
    /** Índice no inventário do jogador (0–8 barra de atalhos, 40 = mão secundária). */
    private final int bagSlot;
    private final SimpleContainer items;

    AlchemistBagMenu(int id, Inventory playerInv, ServerPlayer owner, int bagSlot) {
        super(MenuType.GENERIC_9x3, id);
        this.owner = owner;
        this.bagSlot = bagSlot;
        this.bag = playerInv.getItem(bagSlot);
        this.items = new SimpleContainer(AlchemistBagItem.SLOTS) {
            @Override
            public void setChanged() {
                super.setChanged();
                AlchemistBagItem.save(bag, getItems());
            }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return AlchemistBagItem.accepts(stack);
            }
        };
        var loaded = AlchemistBagItem.load(bag);
        for (int i = 0; i < AlchemistBagItem.SLOTS; i++) items.getItems().set(i, loaded.get(i));

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(items, c + r * 9, 8 + c * 18, 18 + r * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return AlchemistBagItem.accepts(stack);
                    }
                });
            }
        }
        int shift = (ROWS - 4) * 18;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 103 + r * 18 + shift));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInv, c, 8 + c * 18, 161 + shift));
    }

    /** O espaço da própria bolsa não se mexe com a tela aberta (clique, tecla de número ou troca de mão). */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < slots.size()) {
            Slot s = slots.get(slotId);
            if (s.container == player.getInventory() && s.getContainerSlot() == bagSlot) return;
        }
        if (type == ClickType.SWAP && button == bagSlot) return;
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int bagSlots = AlchemistBagItem.SLOTS;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        if (slot.container == player.getInventory() && slot.getContainerSlot() == bagSlot) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < bagSlots) {
            if (!moveItemStackTo(stack, bagSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!AlchemistBagItem.accepts(stack) || !moveItemStackTo(stack, 0, bagSlots, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    /** Vale enquanto a MESMA bolsa continuar no mesmo espaço (se ela sair de lá por qualquer caminho, a tela fecha). */
    @Override
    public boolean stillValid(Player player) {
        return player == owner && owner.isAlive() && !bag.isEmpty() && owner.getInventory().getItem(bagSlot) == bag;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (owner.getInventory().getItem(bagSlot) == bag) AlchemistBagItem.save(bag, items.getItems());
        owner.serverLevel().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.6F, 0.9F);
    }
}
