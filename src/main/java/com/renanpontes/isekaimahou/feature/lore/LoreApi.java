package com.renanpontes.isekaimahou.feature.lore;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Porta de entrada do módulo lore para os outros módulos (campanha, missões, recompensas). Tudo no SERVIDOR; no
 * cliente use {@code feature.lore.client.ClientLore}.
 * <pre>
 * LoreApi.has(player, "sages_3");            // tem o fragmento?
 * LoreApi.grant(player, "herald_1");         // entrega direto (abre a página na tela dele)
 * player.addItem(LoreApi.page("demon_2"));   // Página Perdida de um fragmento certo
 * player.addItem(LoreApi.page());            // Página Perdida comum (sorteia ao ler)
 * </pre>
 */
public final class LoreApi {
    public static boolean has(ServerPlayer player, String fragmentId) {
        return LoreData.has(LoreService.data(player), fragmentId);
    }

    /** Quantos fragmentos o jogador tem (de {@link LoreFragments#total()}). */
    public static int count(ServerPlayer player) {
        return LoreData.count(LoreService.data(player));
    }

    public static boolean setComplete(ServerPlayer player, String setId) {
        LoreFragments.LoreSet set = LoreFragments.set(setId);
        return set != null && LoreData.setComplete(LoreService.data(player), set);
    }

    /** A entrada da Enciclopédia está aberta para o jogador? (só as Verdades Ocultas fecham.) */
    public static boolean entryOpen(ServerPlayer player, String entryId) {
        LoreBook.Entry e = LoreBook.entry(entryId);
        return e != null && LoreData.entryOpen(LoreService.data(player), e);
    }

    /** Entrega o fragmento (revela o lugar, XP, tela). False se o id não existe ou ele já tinha. */
    public static boolean grant(ServerPlayer player, String fragmentId) {
        LoreFragments.Fragment f = LoreFragments.get(fragmentId);
        return f != null && LoreService.discover(player, f);
    }

    /** Página Perdida de um fragmento específico. */
    public static ItemStack page(String fragmentId) {
        return LoreContent.pageOf(fragmentId);
    }

    /** Página Perdida comum. */
    public static ItemStack page() {
        return new ItemStack(LoreContent.LORE_PAGE.get());
    }

    private LoreApi() {}
}
