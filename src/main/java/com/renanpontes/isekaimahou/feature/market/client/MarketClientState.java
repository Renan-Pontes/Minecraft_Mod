package com.renanpontes.isekaimahou.feature.market.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.feature.market.MarketNet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Cópia no cliente do Mercado: anúncios, caixa de retirada e a última mensagem de resultado. */
final class MarketClientState {
    static List<MarketNet.Entry> listings = List.of();
    /** Lista chegando em partes (só vira {@link #listings} quando a última parte chega). */
    @Nullable private static List<MarketNet.Entry> building;
    /** Versão da lista que o cliente tem (-1 = nenhuma). */
    static int version = -1;
    /** Sobe a cada lista nova (a aba refaz o filtro). */
    static int revision;
    static int boxCoins;
    static List<ItemStack> boxItems = List.of();
    @Nullable static Component result;
    static boolean resultOk;
    static long resultAt;

    static void onListings(MarketNet.Listings p) {
        if (p.first()) building = new ArrayList<>();
        if (building == null) return;
        building.addAll(p.list());
        if (p.last()) {
            listings = building;
            building = null;
            version = p.version();
            revision++;
        }
    }

    static void onBox(MarketNet.BoxSync p) {
        boxCoins = p.coins();
        boxItems = p.items();
    }

    static void onResult(MarketNet.Result p) {
        result = p.message();
        resultOk = p.ok();
        resultAt = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        // Fora da aba (comando, tela fechada no meio): uma linha na barra de ação
        if (!(mc.screen instanceof StatusScreen s && MarketClient.TAB.equals(s.currentTab()))) {
            mc.gui.setOverlayMessage(p.message(), false);
        }
    }

    /** Pede a lista se a do servidor for outra. */
    static void query() {
        PacketDistributor.sendToServer(new MarketNet.Query(version));
    }

    static void reset() {
        listings = List.of();
        building = null;
        version = -1;
        revision++;
        boxCoins = 0;
        boxItems = List.of();
        result = null;
    }

    private MarketClientState() {}
}
