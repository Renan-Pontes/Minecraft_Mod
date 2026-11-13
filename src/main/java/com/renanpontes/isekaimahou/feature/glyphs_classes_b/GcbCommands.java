package com.renanpontes.isekaimahou.feature.glyphs_classes_b;

import java.util.Collection;
import java.util.List;

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
 * Comando de teste (op): {@code /mahou glyphs grant glyphs_classes_b [jogadores]} — ensina os 14 glifos do módulo.
 * Outros módulos da v1.4 podem pendurar o próprio id no mesmo {@code /mahou glyphs grant} (o Brigadier junta os nós).
 */
public final class GcbCommands {
    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("glyphs").then(Commands.literal("grant")
                .then(Commands.literal(GcbIds.MODULE)
                        .executes(c -> grant(c.getSource(), List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> grant(c.getSource(), EntityArgument.getPlayers(c, "targets"))))))));
    }

    private static int grant(CommandSourceStack src, Collection<ServerPlayer> players) throws CommandSyntaxException {
        for (ServerPlayer p : players) {
            for (ResourceLocation id : GcbIds.ALL) MagicApi.learnGlyphSilently(p, id);
        }
        src.sendSuccess(() -> Component.translatable(GcbIds.MSG + "granted", GcbIds.ALL.size(), players.size()), true);
        return players.size();
    }

    private GcbCommands() {}
}
