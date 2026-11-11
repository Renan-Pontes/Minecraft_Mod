package com.renanpontes.isekaimahou.feature.structures_b.world;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.structures_b.StructuresBContent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Descoberta de locais: quando o jogador entra numa peça de uma estrutura deste módulo pela primeira vez
 * (por instância), mostra o nome como título, dá XP de nível e soma os contadores
 * {@code explore.<estrutura>} e {@code explore.total} (usados por Diário/Guilda).
 * A primeira visita a um TIPO de estrutura ganha título grande + dica; as seguintes só a barra de ação.
 */
public final class StructureDiscovery {
    private static final int INTERVAL = 20;

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % INTERVAL != 7 || player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        StructureManager structures = level.structureManager();
        if (!structures.hasAnyStructureAt(pos)) return;

        StructureStart start = structures.getStructureWithPieceAt(pos,
                holder -> holder.unwrapKey().map(k -> ModStructure.byId(k.location()) != null).orElse(false));
        if (!start.isValid()) return;
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ModStructure type = ModStructure.byId(registry.getKey(start.getStructure()));
        if (type == null) return;

        String instance = level.dimension().location() + "|" + type.path + "|" + start.getChunkPos().toLong();
        if (!player.getData(StructuresBContent.EXPLORED).add(instance)) return;
        discover(player, type);
    }

    private static void discover(ServerPlayer player, ModStructure type) {
        String counter = "explore." + type.path;
        boolean firstOfKind = MagicApi.get(player).counter(counter) == 0;
        MagicApi.addCounter(player, counter, 1);
        MagicApi.addCounter(player, "explore.total", 1);

        Component name = type.displayName().withColor(type.rgb);
        if (firstOfKind) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(12, 70, 24));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.translatable("message.isekai_mahou.structure_discovered").withStyle(ChatFormatting.GRAY)));
            player.connection.send(new ClientboundSetTitleTextPacket(name));
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.structure_discovered_chat", name));
            player.sendSystemMessage(type.hint().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            player.playNotifySound(type.sound, SoundSource.AMBIENT, 0.9F, type.pitch);
            MagicApi.giveXp(player, type.firstXp(), "explore");
        } else {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.structure_found_again", name), true);
            player.playNotifySound(type.sound, SoundSource.AMBIENT, 0.5F, type.pitch);
            MagicApi.giveXp(player, type.repeatXp(), "explore");
        }
    }

    private StructureDiscovery() {}
}
