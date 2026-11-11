package com.renanpontes.isekaimahou.feature.automation.block;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.automation.AutoRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu do Pedestal Rúnico: 1 espaço de Pergaminho de Magia, configuração por botões e o inventário do jogador. */
public class RunicPedestalMenu extends AbstractContainerMenu {
    // Posições na tela (iguais à textura gui/automation_pedestal.png)
    public static final int SCROLL_X = 18, SCROLL_Y = 34, INV_X = 8, INV_Y = 154, HOTBAR_Y = 212;

    private final Container container;
    private final ContainerData data;
    @Nullable private final RunicPedestalBlockEntity pedestal;
    private final BlockPos pos;

    /** Cliente. */
    public RunicPedestalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(1), new SimpleContainerData(RunicPedestalBlockEntity.D_COUNT), null, buf.readBlockPos());
    }

    /** Servidor. */
    public RunicPedestalMenu(int id, Inventory inventory, RunicPedestalBlockEntity be) {
        this(id, inventory, be, be.data(), be, be.getBlockPos());
    }

    private RunicPedestalMenu(int id, Inventory inventory, Container container, ContainerData data,
                              @Nullable RunicPedestalBlockEntity pedestal, BlockPos pos) {
        super(AutoRegistries.RUNIC_PEDESTAL_MENU.get(), id);
        this.container = container;
        this.data = data;
        this.pedestal = pedestal;
        this.pos = pos;
        checkContainerSize(container, 1);
        checkContainerDataCount(data, RunicPedestalBlockEntity.D_COUNT);
        container.startOpen(inventory.player);
        addSlot(new Slot(container, 0, SCROLL_X, SCROLL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(CoreRegistries.SPELL_SCROLL.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c, INV_X + c * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    // ---------------------------------------------------------------- Estado (lido pela tela)
    public BlockPos pos() { return pos; }
    public int interval() { return data.get(RunicPedestalBlockEntity.D_INTERVAL); }
    public PedestalDirection direction() { return PedestalDirection.byIndex(data.get(RunicPedestalBlockEntity.D_DIRECTION)); }
    public int dx() { return data.get(RunicPedestalBlockEntity.D_DX) - RunicPedestalBlockEntity.MAX_OFFSET; }
    public int dy() { return data.get(RunicPedestalBlockEntity.D_DY) - RunicPedestalBlockEntity.MAX_OFFSET; }
    public int dz() { return data.get(RunicPedestalBlockEntity.D_DZ) - RunicPedestalBlockEntity.MAX_OFFSET; }
    public RedstoneMode redstone() { return RedstoneMode.byIndex(data.get(RunicPedestalBlockEntity.D_REDSTONE)); }
    public PedestalStatus status() { return PedestalStatus.byIndex(data.get(RunicPedestalBlockEntity.D_STATUS)); }
    public int cost() { return data.get(RunicPedestalBlockEntity.D_COST); }
    public int batteries() { return data.get(RunicPedestalBlockEntity.D_BATTERIES); }
    public int timer() { return data.get(RunicPedestalBlockEntity.D_TIMER); }
    public boolean powered() { return data.get(RunicPedestalBlockEntity.D_POWERED) != 0; }

    public Element element() {
        Element[] v = Element.values();
        int i = data.get(RunicPedestalBlockEntity.D_ELEMENT);
        return i >= 0 && i < v.length ? v[i] : Element.ARCANE;
    }

    public int mana() { return join(RunicPedestalBlockEntity.D_MANA_LO, RunicPedestalBlockEntity.D_MANA_HI); }
    public int casts() { return join(RunicPedestalBlockEntity.D_CASTS_LO, RunicPedestalBlockEntity.D_CASTS_HI); }

    /** Os dados vão em pedaços de 16 bits: junta as duas metades. */
    private int join(int lo, int hi) {
        return ((data.get(hi) & 0xFFFF) << 16) | (data.get(lo) & 0xFFFF);
    }

    public boolean hasScroll() { return !slots.get(0).getItem().isEmpty(); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (pedestal == null || !(player instanceof ServerPlayer sp)) return false;
        pedestal.handleButton(sp, id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.is(CoreRegistries.SPELL_SCROLL.get()) && !slots.get(0).hasItem()) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (index < 28) {
            if (!moveItemStackTo(stack, 28, slots.size(), false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 1, 28, false)) {
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
