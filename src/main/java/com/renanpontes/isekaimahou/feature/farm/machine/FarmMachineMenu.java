package com.renanpontes.isekaimahou.feature.farm.machine;

import com.renanpontes.isekaimahou.feature.farm.FarmRegistries;

import net.minecraft.core.BlockPos;
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

/** Tela das fazendas: entrada, combustível, 3×3 de produção, reserva de mana e o inventário do jogador. */
public class FarmMachineMenu extends AbstractContainerMenu {
    // Posições (iguais à textura gui/farm_machine.png, 176×176)
    public static final int INPUT_X = 44, INPUT_Y = 22, FUEL_X = 44, FUEL_Y = 52, OUT_X = 100, OUT_Y = 18;
    public static final int INV_X = 8, INV_Y = 94, HOTBAR_Y = 152;

    private final Container container;
    private final ContainerData data;
    public final MachineKind kind;
    public final BlockPos pos;

    /** Cliente. */
    public FarmMachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, Header.read(buf));
    }

    private record Header(BlockPos pos, MachineKind kind) {
        static Header read(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            return new Header(pos, MachineKind.byIndex(buf.readVarInt()));
        }
    }

    private FarmMachineMenu(int id, Inventory inventory, Header h) {
        this(id, inventory, new SimpleContainer(FarmMachineBlockEntity.SIZE) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return FarmMachineBlockEntity.roughlyAccepts(h.kind, slot, stack);
            }
        }, new SimpleContainerData(FarmMachineBlockEntity.DATA_COUNT), h.pos, h.kind);
    }

    /** Servidor. */
    public FarmMachineMenu(int id, Inventory inventory, FarmMachineBlockEntity be) {
        this(id, inventory, be, be.data(), be.getBlockPos(), be.kind());
    }

    private FarmMachineMenu(int id, Inventory inventory, Container container, ContainerData data, BlockPos pos, MachineKind kind) {
        super(FarmRegistries.MACHINE_MENU.get(), id);
        this.container = container;
        this.data = data;
        this.kind = kind;
        this.pos = pos;
        checkContainerSize(container, FarmMachineBlockEntity.SIZE);
        checkContainerDataCount(data, FarmMachineBlockEntity.DATA_COUNT);
        container.startOpen(inventory.player);
        addSlot(new Slot(container, FarmMachineBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(FarmMachineBlockEntity.SLOT_INPUT, stack);
            }

            @Override
            public int getMaxStackSize() {
                return kind == MachineKind.FISH_POND || kind == MachineKind.ESSENCE ? 1 : 64;
            }
        });
        addSlot(new Slot(container, FarmMachineBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(FarmMachineBlockEntity.SLOT_FUEL, stack);
            }
        });
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                addSlot(new Slot(container, FarmMachineBlockEntity.FIRST_OUT + r * 3 + c, OUT_X + c * 18, OUT_Y + r * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
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
    public int mana() { return data.get(FarmMachineBlockEntity.DATA_MANA); }
    public int capacity() { return Math.max(1, data.get(FarmMachineBlockEntity.DATA_CAP)); }
    public int progress() { return data.get(FarmMachineBlockEntity.DATA_PROGRESS); }
    public int length() { return data.get(FarmMachineBlockEntity.DATA_LENGTH); }
    public int charges() { return data.get(FarmMachineBlockEntity.DATA_CHARGES); }
    public int batteries() { return data.get(FarmMachineBlockEntity.DATA_BATTERIES); }

    public FarmMachineBlockEntity.Status status() {
        int s = data.get(FarmMachineBlockEntity.DATA_STATUS);
        FarmMachineBlockEntity.Status[] v = FarmMachineBlockEntity.Status.values();
        return s >= 0 && s < v.length ? v[s] : FarmMachineBlockEntity.Status.IDLE;
    }

    public ItemStack inputStack() {
        return container.getItem(FarmMachineBlockEntity.SLOT_INPUT);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int size = FarmMachineBlockEntity.SIZE;
        if (index < size) {
            if (!moveItemStackTo(stack, size, slots.size(), true)) return ItemStack.EMPTY;
        } else if (container.canPlaceItem(FarmMachineBlockEntity.SLOT_INPUT, stack)
                && moveItemStackTo(stack, FarmMachineBlockEntity.SLOT_INPUT, FarmMachineBlockEntity.SLOT_INPUT + 1, false)) {
            // foi para a entrada
        } else if (container.canPlaceItem(FarmMachineBlockEntity.SLOT_FUEL, stack)
                && moveItemStackTo(stack, FarmMachineBlockEntity.SLOT_FUEL, FarmMachineBlockEntity.SLOT_FUEL + 1, false)) {
            // foi para o combustível
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
