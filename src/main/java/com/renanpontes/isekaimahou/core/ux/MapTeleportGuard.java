package com.renanpontes.isekaimahou.core.ux;

import java.util.List;
import java.util.Set;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.CommandEvent;

/**
 * Bloqueio de teleporte por mapa/comando ({@link MahouConfig#BLOCK_MAP_TELEPORT}, padrão ligado). Os minimapas
 * (Xaero's Minimap / World Map, JourneyMap) teleportam mandando um comando ({@code /tp @s x y z},
 * {@code /execute ... run tp ...}, {@code /jtp}) em nome do jogador: aqui o comando é cancelado quando quem mandou é um
 * jogador fora do criativo/espectador, com a mensagem "Use as Pedras de Teleporte". Console, blocos de comando,
 * funções e jogadores no criativo continuam podendo usar.
 */
public final class MapTeleportGuard {
    /** Comandos (raiz) que teleportam. "jtp" = JourneyMap no servidor. */
    private static final Set<String> TELEPORT_ROOTS = Set.of("tp", "teleport", "jtp");

    static void onCommand(CommandEvent e) {
        if (!MahouConfig.safe(MahouConfig.BLOCK_MAP_TELEPORT)) return;
        ParseResults<CommandSourceStack> parse = e.getParseResults();
        if (parse == null) return;
        CommandSourceStack source = parse.getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        if (sp.isCreative() || sp.isSpectator()) return;
        if (!teleports(parse.getContext())) return;
        e.setCanceled(true);
        sp.sendSystemMessage(Component.translatable("ux.isekai_mahou.teleport_blocked").withStyle(ChatFormatting.RED));
        sp.sendSystemMessage(Component.translatable("ux.isekai_mahou.teleport_blocked_hint").withStyle(ChatFormatting.GRAY));
        sp.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.7F, 0.6F);
    }

    /** Algum comando da cadeia (o principal ou o que vem depois de {@code execute ... run}) é de teleporte? */
    private static boolean teleports(CommandContextBuilder<CommandSourceStack> ctx) {
        int guard = 0;
        while (ctx != null && guard++ < 32) {
            List<ParsedCommandNode<CommandSourceStack>> nodes = ctx.getNodes();
            if (!nodes.isEmpty() && nodes.get(0).getNode() instanceof LiteralCommandNode<CommandSourceStack> lit
                    && TELEPORT_ROOTS.contains(lit.getLiteral())) {
                return true;
            }
            // "execute ... run tp": cada redirecionamento ("as", "in", "run") abre um contexto filho; o comando depois do
            // "run" é o primeiro nó de um deles
            ctx = ctx.getChild();
        }
        return false;
    }

    private MapTeleportGuard() {}
}
