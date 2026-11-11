package com.renanpontes.isekaimahou.feature.class_smith;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeFamily;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeMaterial;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;
import com.renanpontes.isekaimahou.feature.class_smith.item.ForgedBladeItem;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de teste do Ferreiro (op):
 * <ul>
 *   <li>{@code /mahou smith info [jogadores]} — rank e tarefas do próximo rank;</li>
 *   <li>{@code /mahou smith rank <1-5> [jogadores]} — sobe direto até o rank (glifos, XP, itens de cada rank);</li>
 *   <li>{@code /mahou smith blade} — dá uma lâmina de exemplo de cada família (para ver os modelos);</li>
 *   <li>{@code /mahou smith starter [jogadores]} — refaz glifos e magias prontas da classe.</li>
 * </ul>
 */
public final class SmithCommands {

    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("smith")
                .then(Commands.literal("info")
                        .executes(c -> info(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> info(c, EntityArgument.getPlayers(c, "players")))))
                .then(Commands.literal("rank")
                        .then(Commands.argument("rank", IntegerArgumentType.integer(1, SmithRanks.MAX_RANK))
                                .executes(c -> rank(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(c -> rank(c, EntityArgument.getPlayers(c, "players"))))))
                .then(Commands.literal("blade").executes(SmithCommands::blades))
                .then(Commands.literal("starter")
                        .executes(c -> starter(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> starter(c, EntityArgument.getPlayers(c, "players")))))));
    }

    private static int info(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        for (ServerPlayer sp : players) {
            MagicData d = MagicApi.get(sp);
            int rank = SmithProgress.rank(d);
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.class_smith.info", sp.getDisplayName(),
                    SmithRanks.roman(rank), d.path().displayName()).withStyle(ChatFormatting.GOLD), false);
            SmithRanks.Rank next = SmithRanks.rank(rank + 1);
            if (next == null) continue;
            for (SmithRanks.Task t : next.tasks()) {
                c.getSource().sendSuccess(() -> Component.literal((t.done(d) ? " ✔ " : " ✘ ") + t.value(d) + "/" + t.goal() + " ")
                        .withStyle(t.done(d) ? ChatFormatting.GREEN : ChatFormatting.GRAY).append(t.text()), false);
            }
        }
        return players.size();
    }

    private static int rank(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        int target = IntegerArgumentType.getInteger(c, "rank");
        int n = 0;
        for (ServerPlayer sp : players) {
            if (MagicApi.get(sp).path() != MagicPath.SMITH) {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.class_smith.not_smith", sp.getDisplayName()));
                continue;
            }
            while (SmithProgress.rank(MagicApi.get(sp)) < target) {
                SmithRanks.Rank r = SmithRanks.rank(SmithProgress.rank(MagicApi.get(sp)) + 1);
                if (r == null) break;
                SmithRanks.grant(sp, r, sp.position());
            }
            n++;
        }
        final int done = n;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.class_smith.rank_set", done, target), true);
        return n;
    }

    private static int blades(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        BladeMaterial[] mats = BladeMaterial.VALUES;
        int i = 0;
        for (BladeFamily f : BladeFamily.VALUES) {
            ForgedBlade b = ForgedBlade.DEFAULT.withFamily(f).withMaterial(mats[i % mats.length]).withQuality(5)
                    .withPart(ForgedBlade.Part.LENGTH, 1 + i % 4).withPart(ForgedBlade.Part.WIDTH, i % 3)
                    .withPart(ForgedBlade.Part.CURVE, (i + 1) % 3).withPart(ForgedBlade.Part.TIP, 1)
                    .withPart(ForgedBlade.Part.SERRATION, i % 2).withSmith(sp.getGameProfile().getName());
            if (i % 2 == 0) b = b.withRune(com.renanpontes.isekaimahou.core.Element.BASE.get(i % 6)).withFinish(ForgedBlade.Finish.RUNIC);
            ItemStackGive.give(sp, ForgedBladeItem.create(b));
            i++;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.class_smith.blades"), false);
        return 1;
    }

    private static int starter(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        for (ServerPlayer sp : players) SmithStarter.reset(sp);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.class_smith.starter", players.size()), true);
        return players.size();
    }

    /** Dá o item (ou joga no chão se o inventário estiver cheio). */
    private static final class ItemStackGive {
        static void give(ServerPlayer sp, net.minecraft.world.item.ItemStack stack) {
            if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        }
    }

    private SmithCommands() {}
}
