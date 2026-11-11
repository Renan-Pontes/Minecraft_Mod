package com.renanpontes.isekaimahou.feature.structures_c;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Descoberta de estruturas (mesma convenção do structures_b, para Diário/Guilda lerem tudo igual):
 * <ul>
 *   <li>1ª vez num TIPO de estrutura: título grande (nome + frase), som temático, XP cheio;</li>
 *   <li>nova INSTÂNCIA de um tipo já conhecido: aviso na barra de ação e 1/4 do XP;</li>
 *   <li>cada instância nova soma os contadores {@code explore.<estrutura>} e {@code explore.total};</li>
 *   <li>voltar a um lugar já explorado: só o nome na barra de ação ao entrar.</li>
 * </ul>
 * Usa só pacotes vanilla de título — nada de código de cliente.
 */
public final class StructureDiscovery {
    /** Instância em que cada jogador estava na última checagem (para avisar só ao ENTRAR). */
    private static final Map<UUID, String> CURRENT = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(StructureDiscovery::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> CURRENT.remove(e.getEntity().getUUID()));
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) return;
        // Uma vez por segundo, espalhado entre os jogadores.
        if ((player.tickCount + player.getId()) % 20 != 0) return;
        ServerLevel level = player.serverLevel();
        RpgStructure.Found found = RpgStructure.find(level, player.blockPosition(), true);
        UUID uuid = player.getUUID();
        if (found == null) {
            CURRENT.remove(uuid);
            return;
        }
        RpgStructure type = found.structure();
        String instance = level.dimension().location() + "|" + type.id + "|" + found.start().getChunkPos().toLong();
        if (instance.equals(CURRENT.put(uuid, instance))) return;

        Set<String> explored = player.getData(ScRegistries.EXPLORED);
        if (!explored.add(instance)) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.structures_c.entered", type.title()), true);
            return;
        }
        boolean firstOfKind = MagicApi.get(player).counter(type.counterKey()) == 0;
        MagicApi.addCounter(player, type.counterKey(), 1);
        MagicApi.addCounter(player, "explore.total", 1);
        if (firstOfKind) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    type.flavor().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            player.connection.send(new ClientboundSetTitleTextPacket(type.title().withStyle(ChatFormatting.BOLD)));
            player.playNotifySound(type.sound(), SoundSource.AMBIENT, 1.0F, 1.0F);
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.structures_c.discovered",
                    type.title(), type.discoveryXp).withStyle(ChatFormatting.GOLD));
            MagicApi.giveXp(player, type.discoveryXp, "explore");
        } else {
            int xp = Math.max(1, type.discoveryXp / 4);
            player.displayClientMessage(Component.translatable("message.isekai_mahou.structures_c.found_again",
                    type.title(), xp), true);
            player.playNotifySound(type.sound(), SoundSource.AMBIENT, 0.5F, 1.1F);
            MagicApi.giveXp(player, xp, "explore");
        }
    }

    private StructureDiscovery() {}
}
