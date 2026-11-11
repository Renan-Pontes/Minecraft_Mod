package com.renanpontes.isekaimahou.feature.class_knight.oath;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Oferenda do altar (item por id + quantidade). Os ids são resolvidos na hora: um item de outro módulo ausente
 * simplesmente não é cobrado (nunca trava a progressão).
 */
public record Offer(String itemId, int count) {
    public Item item() {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return Items.AIR;
        return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
    }

    public boolean available() {
        return item() != Items.AIR;
    }

    public ItemStack icon() {
        Item it = item();
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it, count);
    }

    /** Quantos o jogador tem no inventário. */
    public int have(Player player) {
        Item it = item();
        if (it == Items.AIR) return count;
        int n = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(it)) n += st.getCount();
        }
        return n;
    }

    public static boolean allPresent(Player player, List<Offer> offers) {
        if (player.getAbilities().instabuild) return true;
        for (Offer o : offers) {
            if (o.have(player) < o.count) return false;
        }
        return true;
    }

    /** Tira as oferendas do inventário (criativo: nada). Chame só depois de {@link #allPresent}. */
    public static void consume(Player player, List<Offer> offers) {
        if (player.getAbilities().instabuild) return;
        Inventory inv = player.getInventory();
        for (Offer o : offers) {
            Item it = o.item();
            if (it == Items.AIR) continue;
            int left = o.count;
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack st = inv.getItem(i);
                if (!st.is(it)) continue;
                int take = Math.min(left, st.getCount());
                st.shrink(take);
                left -= take;
            }
        }
        inv.setChanged();
    }
}
