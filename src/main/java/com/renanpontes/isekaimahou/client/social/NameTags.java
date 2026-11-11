package com.renanpontes.isekaimahou.client.social;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.social.ClientSocialView;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

/** Marca no nome de outros jogadores: ✦ verde = do seu grupo, ⚔ vermelho = cidade inimiga (guerra), ⚔ laranja = duelo. */
final class NameTags {

    static void onNameTag(RenderNameTagEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || p == mc.player) return;
        UUID id = p.getUUID();
        MutableComponent prefix = null;
        if (id.equals(ClientSocialView.duelOpponent())) {
            prefix = Component.literal("⚔ ").withColor(0xFF9A4A);
        } else if (ClientSocialView.isEnemy(id)) {
            prefix = Component.literal("⚔ ").withColor(0xFF4A3A);
        } else if (ClientSocialView.isMember(id)) {
            prefix = Component.literal("✦ ").withColor(0x7CFF8A);
        }
        if (prefix != null) e.setContent(Component.empty().append(prefix).append(e.getContent()));
    }

    private NameTags() {}
}
