package com.renanpontes.isekaimahou.feature.flat_lands;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Comandos de teste (cheats):
 * <pre>
 * /mahou flat_lands give                      caixa do Omikuji + 8 pepitas de ouro (oferendas)
 * /mahou flat_lands omikuji reset [jogadores] libera tirar a sorte de novo hoje
 * /mahou flat_lands omikuji draw [sorte]      tira a sorte agora (daikichi, chukichi, kichi, shokichi, suekichi, kyo, daikyo)
 * </pre>
 * Achar os biomas/estrutura: {@code /locate biome isekai_mahou:sakura_fields} ·
 * {@code /locate structure isekai_mahou:sakura_torii_shrine} · {@code /place structure isekai_mahou:sakura_torii_shrine}.
 */
public final class FlatLandsCommands {
    public static void init() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("flat_lands")
                .then(Commands.literal("give").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    give(p, new ItemStack(FlatLandsContent.OMIKUJI_BOX_ITEM.get()));
                    give(p, new ItemStack(Items.GOLD_NUGGET, 8));
                    return 1;
                }))
                .then(Commands.literal("omikuji")
                        .then(Commands.literal("reset")
                                .executes(c -> reset(c.getSource(), List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> reset(c.getSource(), EntityArgument.getPlayers(c, "targets")))))
                        .then(Commands.literal("draw")
                                .executes(c -> draw(c.getSource(), null))
                                .then(Commands.argument("fortune", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(Omikuji.Fortune.values()).map(f -> f.id), b))
                                        .executes(c -> draw(c.getSource(), StringArgumentType.getString(c, "fortune"))))))));
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    private static int reset(CommandSourceStack src, Collection<ServerPlayer> players) {
        players.forEach(Omikuji::reset);
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.flat_lands.reset", players.size()), true);
        return players.size();
    }

    private static int draw(CommandSourceStack src, String id) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Omikuji.Fortune forced = null;
        if (id != null) {
            forced = Omikuji.Fortune.byId(id);
            if (forced == null) {
                src.sendFailure(Component.translatable("command.isekai_mahou.flat_lands.unknown", id));
                return 0;
            }
        }
        Omikuji.draw(p, p.blockPosition(), forced);
        return 1;
    }

    private FlatLandsCommands() {}
}
