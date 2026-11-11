package com.renanpontes.isekaimahou.feature.cities;

import com.renanpontes.isekaimahou.feature.cities.trade.CityTrades;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Dados das cidades guardados no jogador ({@code PlayerPersisted/isekai_mahou_cities}, sobrevivem à morte): rumor
 * ativo, fiança devida, quarto alugado, recargas. E as contas de moedas (Moeda da Guilda).
 */
public final class CityPlayer {
    private static final String KEY = "isekai_mahou_cities";

    public static CompoundTag tag(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, persisted);
        CompoundTag t = persisted.getCompound(KEY);
        if (!persisted.contains(KEY)) persisted.put(KEY, t);
        return t;
    }

    // =====================================================================================
    //  Moedas
    // =====================================================================================
    public static int coins(Player p) {
        Item coin = CityTrades.coin();
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(coin)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (s.is(coin)) n += s.getCount();
        return n;
    }

    /** Tira {@code amount} moedas; false (sem tirar nada) se não houver o bastante. Criativo não paga. */
    public static boolean pay(ServerPlayer p, int amount) {
        if (amount <= 0 || p.getAbilities().instabuild) return true;
        if (coins(p) < amount) return false;
        Item coin = CityTrades.coin();
        int left = amount;
        for (ItemStack s : p.getInventory().items) {
            if (left <= 0) break;
            if (!s.is(coin)) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
        }
        for (ItemStack s : p.getInventory().offhand) {
            if (left <= 0) break;
            if (!s.is(coin)) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
        }
        p.getInventory().setChanged();
        return true;
    }

    private CityPlayer() {}
}
