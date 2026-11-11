package com.renanpontes.isekaimahou.feature.automation.block;

import com.renanpontes.isekaimahou.feature.automation.AutoRegistries;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu do Condensador: 9 espaços de combustível (3×3), a reserva e o inventário do jogador. */
public class ManaCondenserMenu extends AbstractContainerMenu {
    // Posições na tela (iguais à textura gui/automation_condenser.png)
    public static final int FUEL_X = 30, FUEL_Y = 20, INV_X = 8, INV_Y = 99, HOTBAR_Y = 157;

    private final Container container;
    private final ContainerData data;

    /** Cliente. */
    public ManaCondenserMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(ManaCondenserBlockEntity.SIZE), new SimpleContainerData(ManaCondenserBlockEntity.DATA_COUNT));
    }

    /** Servidor. */
    public ManaCondenserMenu(int id, Inventory inventory, ManaCondenserBlockEntity be) {
        this(id, inventory, be, be.data());
    }

    private ManaCondenserMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(AutoRegistries.MANA_CONDENSER_MENU.get(), id);
        this.container = container;
        this.data = data;
        checkContainerSize(container, ManaCondenserBlockEntity.SIZE);
        checkContainerDataCount(data, ManaCondenserBlockEntity.DATA_COUNT);
        container.startOpen(inventory.player);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                addSlot(new Slot(container, r * 3 + c, FUEL_X + c * 18, FUEL_Y + r * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return ManaCondenserBlockEntity.fuelValue(stack) > 0;
                    }
                });
            }
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c, INV_X + c * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    // ---------------------------------------------------------------- Estado (lido pela tela)
    public int buffer() { return data.get(ManaCondenserBlockEntity.DATA_BUFFER); }
    public int burnLeft() { return data.get(ManaCondenserBlockEntity.DATA_BURN_LEFT); }
    public int burnTotal() { return data.get(ManaCondenserBlockEntity.DATA_BURN_TOTAL); }
    public int batteries() { return data.get(ManaCondenserBlockEntity.DATA_BATTERIES); }

    /** Mana somado das baterias em alcance (vem em duas metades de 16 bits). */
    public int stored() {
        int lo = data.get(ManaCondenserBlockEntity.DATA_STORED_LO) & 0xFFFF;
        int hi = data.get(ManaCondenserBlockEntity.DATA_STORED_HI) & 0xFFFF;
        return (hi << 16) | lo;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int size = ManaCondenserBlockEntity.SIZE;
        if (index < size) {
            if (!moveItemStackTo(stack, size, slots.size(), true)) return ItemStack.EMPTY;
        } else if (ManaCondenserBlockEntity.fuelValue(stack) > 0) {
            if (!moveItemStackTo(stack, 0, size, false)) return ItemStack.EMPTY;
        } else if (index < size + 27) {
            if (!moveItemStackTo(stack, size + 27, slots.size(), false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, size, size + 27, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
