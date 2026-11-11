package com.renanpontes.isekaimahou.core.social;

import javax.annotation.Nullable;

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

/**
 * Tela de troca segura (os dois lados). Slots 0–11: a MINHA oferta (editável); 12–23: a oferta do outro (só ver);
 * depois o inventário. Botões ({@code clickMenuButton}): 0 = confirmar/desconfirmar, 1 = cancelar.
 * Dados sincronizados: [0] eu confirmei, [1] o outro confirmou, [2] ticks de trava (mudança recente), [3] o outro está aqui.
 */
public class TradeMenu extends AbstractContainerMenu {
    public static final int OFFER_SLOTS = 12;
    public static final int COLS = 4;
    public static final int BUTTON_CONFIRM = 0, BUTTON_CANCEL = 1;
    /** Posições (pixels) usadas pela tela. */
    public static final int MY_X = 10, THEIR_X = 154, GRID_Y = 30, INV_X = 37, INV_Y = 140;

    @Nullable final TradeService.Session session;
    final boolean sideA;
    private final Container mine;
    private final Container theirs;
    private final ContainerData data;
    /** Nome do outro jogador (cliente: vem do pacote de abertura). */
    public final String partnerName;

    /** Cliente. */
    public TradeMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, null, true, new SimpleContainer(OFFER_SLOTS), new SimpleContainer(OFFER_SLOTS), new SimpleContainerData(4),
                buf.readUtf(64));
    }

    /** Servidor. */
    TradeMenu(int id, Inventory inv, TradeService.Session session, boolean sideA) {
        this(id, inv, session, sideA, session.offer(sideA), session.offer(!sideA), session.data(sideA), session.name(!sideA));
    }

    private TradeMenu(int id, Inventory inv, @Nullable TradeService.Session session, boolean sideA, Container mine, Container theirs,
                      ContainerData data, String partnerName) {
        super(SocialCore.TRADE_MENU.get(), id);
        this.session = session;
        this.sideA = sideA;
        this.mine = mine;
        this.theirs = theirs;
        this.data = data;
        this.partnerName = partnerName;
        for (int i = 0; i < OFFER_SLOTS; i++) {
            addSlot(new OfferSlot(mine, i, MY_X + (i % COLS) * 18, GRID_Y + (i / COLS) * 18));
        }
        for (int i = 0; i < OFFER_SLOTS; i++) {
            addSlot(new ViewSlot(theirs, i, THEIR_X + (i % COLS) * 18, GRID_Y + (i / COLS) * 18));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    public boolean iConfirmed() { return data.get(0) != 0; }

    public boolean partnerConfirmed() { return data.get(1) != 0; }

    /** Ticks até poder confirmar de novo (alguém mexeu nas ofertas). */
    public int lockTicks() { return data.get(2); }

    public boolean partnerPresent() { return data.get(3) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (session == null) return false;
        if (id == BUTTON_CONFIRM) {
            session.toggleConfirm(sideA);
            return true;
        }
        if (id == BUTTON_CANCEL) {
            session.cancel(null, player.getGameProfile().getName(), true);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int invStart = OFFER_SLOTS * 2, invEnd = invStart + 36;
        if (index < OFFER_SLOTS) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else if (index < OFFER_SLOTS * 2) {
            return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, 0, OFFER_SLOTS, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return session == null || session.valid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Fechou a tela (ou foi fechada): a troca acaba e cada um recebe a própria oferta de volta
        if (session != null) session.cancel(player.getUUID(), player.getGameProfile().getName(), false);
    }

    /** Slot da minha oferta: trava depois da troca concluída/cancelada. */
    private final class OfferSlot extends Slot {
        OfferSlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // Itens de classe são intransferíveis (ver SoulBinding)
            if (com.renanpontes.isekaimahou.core.item.SoulBinding.isClassItem(stack)) return false;
            return session == null || session.open();
        }

        @Override
        public boolean mayPickup(Player player) {
            return session == null || session.open();
        }
    }

    /** Slot da oferta do outro: só olhar. */
    private static final class ViewSlot extends Slot {
        ViewSlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
