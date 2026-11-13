package com.renanpontes.isekaimahou.feature.space_magic;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Inventário Dimensional (lado do servidor): o mesmo layout de um baú do vanilla com 1 a 6 fileiras (o cliente abre a
 * tela de baú comum), ligado aos dados do jogador. Cada mudança é gravada na hora — assim nada se perde se você morrer
 * ou cair a conexão com a tela aberta. Não aceita Shulker, sacolas nem itens que guardam outros itens.
 */
public class ItemBoxMenu extends AbstractContainerMenu {
    /** Itens que não entram (além de Shulker e de tudo que tem conteúdo): tag do módulo, editável por datapack. */
    public static final TagKey<Item> BANNED = TagKey.create(Registries.ITEM, IsekaiMahou.id("space_magic_item_box_banned"));

    private static final TagKey<Item> SHULKERS = ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "shulker_boxes"));

    private final ServerPlayer owner;
    private final BoxContainer box;
    private final int rows;

    private ItemBoxMenu(int id, Inventory playerInv, ServerPlayer owner, int rows) {
        super(menuType(rows), id);
        this.owner = owner;
        this.rows = rows;
        this.box = new BoxContainer(owner, rows * 9);
        box.startOpen(owner);
        int shift = (rows - 4) * 18;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) addSlot(new BoxSlot(box, c + r * 9, 8 + c * 18, 18 + r * 18));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 103 + r * 18 + shift));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInv, c, 8 + c * 18, 161 + shift));
    }

    private static MenuType<?> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    /** Abre o Item Box do jogador (tamanho pelo Círculo atual). */
    public static void open(ServerPlayer sp) {
        int circle = MagicApi.get(sp).circle();
        int rows = SpaceData.boxRows(circle);
        Component title = Component.translatable("container.isekai_mahou.space_magic.item_box", rows * 9);
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ItemBoxMenu(id, inv, sp, rows), title));
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    /** O item pode ir para o Item Box? (nada de "inventário dentro do inventário") */
    public static boolean allowed(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.is(BANNED) || stack.is(SHULKERS)) return false;
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) return false;
        return !stack.has(DataComponents.CONTAINER) && !stack.has(DataComponents.BUNDLE_CONTENTS);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int boxSlots = rows * 9;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < boxSlots) {
            if (!moveItemStackTo(stack, boxSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!allowed(stack) || !moveItemStackTo(stack, 0, boxSlots, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && owner.isAlive();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        box.stopOpen(player);
        box.save();
        if (player instanceof ServerPlayer sp) {
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENDER_CHEST_CLOSE, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
    }

    /** Recipiente com os espaços visíveis; os espaços além do tamanho atual ficam guardados intactos. */
    private static final class BoxContainer extends SimpleContainer {
        private final ServerPlayer owner;
        private final NonNullList<ItemStack> all;

        BoxContainer(ServerPlayer owner, int size) {
            super(size);
            this.owner = owner;
            this.all = SpaceData.loadBox(owner, owner.registryAccess());
            for (int i = 0; i < size; i++) getItems().set(i, all.get(i));
        }

        @Override
        public void setChanged() {
            super.setChanged();
            save();
        }

        void save() {
            for (int i = 0; i < getContainerSize(); i++) all.set(i, getItem(i));
            SpaceData.saveBox(owner, all, owner.registryAccess());
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return allowed(stack);
        }
    }

    private static final class BoxSlot extends Slot {
        BoxSlot(BoxContainer c, int index, int x, int y) {
            super(c, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return allowed(stack);
        }
    }
}
