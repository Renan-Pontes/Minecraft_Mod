package com.renanpontes.isekaimahou.feature.glyphs_classes_a;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de teste (op):
 * <pre>
 * /mahou glyphs grant glyphs_classes_a [jogadores]   ensina os 16 glifos de classe deste módulo
 * /mahou glyphs check glyphs_classes_a [jogadores]   roda a regra de desbloqueio por rank/Círculo agora
 * </pre>
 */
public final class GcaCommands {
    private static final String MODULE = "glyphs_classes_a";

    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("glyphs")
                .then(Commands.literal("grant").then(Commands.literal(MODULE)
                        .executes(c -> grant(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> grant(c, EntityArgument.getPlayers(c, "targets"))))))
                .then(Commands.literal("check").then(Commands.literal(MODULE)
                        .executes(c -> check(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> check(c, EntityArgument.getPlayers(c, "targets"))))))));
    }

    private static int grant(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) throws CommandSyntaxException {
        List<ResourceLocation> ids = GcaGlyphs.all();
        for (ServerPlayer p : players) {
            for (ResourceLocation id : ids) MagicApi.learnGlyphSilently(p, id);
        }
        int n = players.size();
        c.getSource().sendSuccess(() -> Component.literal("✦ " + ids.size() + " glifos de classe (" + MODULE + ") → " + n + " jogador(es)"), true);
        return n;
    }

    private static int check(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) GcaUnlocks.check(p);
        c.getSource().sendSuccess(() -> Component.literal("✦ desbloqueio por rank/Círculo conferido"), false);
        return players.size();
    }

    private GcaCommands() {}
}
