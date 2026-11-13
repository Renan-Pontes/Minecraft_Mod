package com.renanpontes.isekaimahou.feature.renown;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Reputation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * O título <b>no chat</b>: {@code <[Título] Nome> mensagem}.
 * <p>
 * O nome do chat é o "nome de exibição" do jogador, e é esse nome que muitos sistemas guardam (dono de cidade, recordes,
 * mensagens de chefe). Para o título NÃO vazar para esses lugares, ele só entra no nome durante os poucos ticks em que
 * uma mensagem de chat do jogador está sendo enviada; depois o nome volta ao normal.
 */
final class RenownChat {
    /** Ticks com o título no nome depois de uma mensagem (o envio do chat pode atrasar alguns ticks com o servidor lento). */
    private static final int WINDOW = 6;
    /** Jogadores com mensagem saindo → tick do servidor em que ela foi escrita. */
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private RenownChat() {}

    static void onChat(ServerChatEvent e) {
        ServerPlayer sp = e.getPlayer();
        if (Reputation.selectedTitleKey(sp) == null) return;
        PENDING.put(sp.getUUID(), sp.server.getTickCount());
        sp.refreshDisplayName();
    }

    static void onNameFormat(PlayerEvent.NameFormat e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !PENDING.containsKey(sp.getUUID())) return;
        String key = Reputation.selectedTitleKey(sp);
        if (key == null) return;
        Component tag = Component.literal("[").withStyle(ChatFormatting.GRAY).append(RenownTitles.display(key)).append("] ");
        e.setDisplayname(Component.empty().append(tag).append(e.getDisplayname()));
    }

    static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        int now = server.getTickCount();
        for (Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Integer> en = it.next();
            if (now - en.getValue() < WINDOW) continue;
            it.remove();
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp != null) sp.refreshDisplayName();
        }
    }

    static void clear() {
        PENDING.clear();
    }
}
