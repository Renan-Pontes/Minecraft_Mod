package com.renanpontes.isekaimahou.feature.ritual_mage.block;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.ritual_mage.MageRegistries;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.Thesis;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.ThesisPuzzle;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Menu da Mesa de Pesquisa: 18 espaços de notas, 1 de papel e o inventário do jogador.
 * O estado da tese/grade vem por {@link ContainerData}; as ações do minijogo usam {@link #clickMenuButton}.
 */
public class ResearchTableMenu extends AbstractContainerMenu {
    // Posições na tela (iguais à textura gui/mage_research_table.png)
    public static final int NOTES_X = 178, NOTES_Y = 150, PAPER_X = 300, PAPER_Y = 150, INV_X = 8, INV_Y = 150, HOTBAR_Y = 208;

    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    @Nullable private final ResearchTableBlockEntity table;
    private final BlockPos pos;

    private ThesisPuzzle cachedPuzzle;
    private int cachedThesis = -2, cachedSeed = -1;

    /** Cliente. */
    public ResearchTableMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(ResearchTableBlockEntity.SIZE), new SimpleContainerData(ResearchTableBlockEntity.DATA_COUNT),
                ContainerLevelAccess.NULL, null, buf.readBlockPos());
    }

    /** Servidor. */
    public ResearchTableMenu(int id, Inventory inventory, ResearchTableBlockEntity table) {
        this(id, inventory, table.items(), table.data(), ContainerLevelAccess.create(table.getLevel(), table.getBlockPos()),
                table, table.getBlockPos());
    }

    private ResearchTableMenu(int id, Inventory inventory, Container container, ContainerData data, ContainerLevelAccess access,
                              @Nullable ResearchTableBlockEntity table, BlockPos pos) {
        super(MageRegistries.RESEARCH_TABLE_MENU.get(), id);
        this.container = container;
        this.data = data;
        this.access = access;
        this.table = table;
        this.pos = pos;
        checkContainerSize(container, ResearchTableBlockEntity.SIZE);
        checkContainerDataCount(data, ResearchTableBlockEntity.DATA_COUNT);
        container.startOpen(inventory.player);

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 6; c++) {
                addSlot(new Slot(container, r * 6 + c, NOTES_X + c * 18, NOTES_Y + r * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.is(MageRegistries.RESEARCH_NOTE.get());
                    }
                });
            }
        }
        addSlot(new Slot(container, ResearchTableBlockEntity.PAPER_SLOT, PAPER_X, PAPER_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.PAPER);
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
    public int thesisIndex() { return data.get(ResearchTableBlockEntity.DATA_THESIS); }
    public int seed() { return data.get(ResearchTableBlockEntity.DATA_SEED); }
    public int shelves() { return data.get(ResearchTableBlockEntity.DATA_SHELVES); }

    @Nullable
    public Thesis thesis() { return Thesis.byIndex(thesisIndex()); }

    public Element[] placed() {
        byte[] g = new byte[ThesisPuzzle.CELLS];
        for (int i = 0; i < g.length; i++) g[i] = (byte) data.get(ResearchTableBlockEntity.DATA_GRID + i);
        return ResearchTableBlockEntity.decodeGrid(g);
    }

    @Nullable
    public ThesisPuzzle puzzle() {
        Thesis t = thesis();
        if (t == null) return null;
        if (cachedPuzzle == null || cachedThesis != thesisIndex() || cachedSeed != seed()) {
            cachedPuzzle = ThesisPuzzle.generate(t, seed());
            cachedThesis = thesisIndex();
            cachedSeed = seed();
        }
        return cachedPuzzle;
    }

    public int notesOf(Element e) { return ResearchTableBlockEntity.countNotes(container, e); }
    public int paper() { return ResearchTableBlockEntity.countPaper(container); }

    // ---------------------------------------------------------------- Ações
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (table == null || !(player instanceof ServerPlayer sp)) return false;
        table.handleAction(sp, id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int size = ResearchTableBlockEntity.SIZE;
        if (index < size) {
            if (!moveItemStackTo(stack, size, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.is(MageRegistries.RESEARCH_NOTE.get())) {
            if (!moveItemStackTo(stack, 0, ResearchTableBlockEntity.NOTE_SLOTS, false)) return ItemStack.EMPTY;
        } else if (stack.is(Items.PAPER)) {
            if (!moveItemStackTo(stack, ResearchTableBlockEntity.PAPER_SLOT, ResearchTableBlockEntity.PAPER_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
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
        return stillValid(access, player, MageRegistries.RESEARCH_TABLE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
