package com.renanpontes.isekaimahou.feature.ritual_druid;

import java.util.Iterator;

import com.renanpontes.isekaimahou.core.MagicApi;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Contador próprio {@code feat.trees_planted}: uma muda plantada pelo jogador só conta quando VIRA ÁRVORE
 * (o bloco vira tronco). Assim plantar e arrancar a mesma muda não vale nada.
 */
public final class DruidEvents {
    private static final int POLL_INTERVAL = 100;

    /** Muda plantada por um jogador: fica na lista de espera dele. */
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockState placed = event.getPlacedBlock();
        GlobalPos at = GlobalPos.of(player.level().dimension(), event.getPos().immutable());
        if (placed.is(BlockTags.SAPLINGS)) {
            DruidGrove.of(player).addPending(at);
        } else if (placed.is(BlockTags.LOGS)) {
            // Tronco colocado à mão no lugar da muda (arrancar a muda e pôr um tronco) não conta como árvore
            DruidGrove.of(player).pending().remove(at);
        }
    }

    /** De tempos em tempos confere as mudas do jogador na dimensão onde ele está. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % POLL_INTERVAL != 0) return;
        DruidGrove grove = DruidGrove.of(player);
        if (grove.pending().isEmpty()) return;
        ServerLevel level = player.serverLevel();
        int grown = 0;
        Iterator<GlobalPos> it = grove.pending().iterator();
        while (it.hasNext()) {
            GlobalPos gp = it.next();
            if (!gp.dimension().equals(level.dimension()) || !level.isLoaded(gp.pos())) continue;
            BlockState s = level.getBlockState(gp.pos());
            if (s.is(BlockTags.SAPLINGS)) continue;     // ainda crescendo
            if (s.is(BlockTags.LOGS)) grown++;            // virou árvore!
            it.remove();                                  // virou árvore ou foi arrancada
        }
        if (grown > 0) {
            int total = MagicApi.addCounter(player, DruidStages.TREES_COUNTER, grown);
            MagicApi.giveXp(player, 2 * grown, "nature");
            player.displayClientMessage(Component.translatable("message.isekai_mahou.ritual_druid.tree_grown", total)
                    .withStyle(ChatFormatting.DARK_GREEN), true);
        }
    }

    private DruidEvents() {}
}
