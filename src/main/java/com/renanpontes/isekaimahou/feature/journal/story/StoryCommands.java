package com.renanpontes.isekaimahou.feature.journal.story;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * /mahou story status        — campanha ligada/desligada e quantas batidas já passaram (qualquer jogador)
 * /mahou story list          — todas as batidas, com ✔ nas que já passaram (op)
 * /mahou story play &lt;batida&gt; — mostra a batida agora, com cutscene se for de marco (op)
 * /mahou story reset         — apaga os marcadores da campanha: as batidas voltam a disparar (op)
 */
public final class StoryCommands {
    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("story")
                .then(Commands.literal("status").executes(StoryCommands::status))
                .then(MahouCommands.op("list").executes(StoryCommands::list))
                .then(MahouCommands.op("play")
                        .then(Commands.argument("beat", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(StoryBook.beats().stream().map(StoryBook.Beat::id), b))
                                .executes(StoryCommands::play)))
                .then(MahouCommands.op("reset").executes(c -> {
                    int n = StoryService.reset(c.getSource().getPlayerOrException());
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.story.reset", n), false);
                    return 1;
                }))));
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        StoryService.Prefs prefs = StoryService.prefs(p);
        Component campaign = onOff(prefs.campaign());
        Component scenes = onOff(prefs.scenes());
        int seen = StoryService.seen(MagicApi.get(p));
        int queued = StoryService.queued(p);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.story.status", campaign, scenes, seen,
                StoryBook.beats().size(), queued), false);
        return 1;
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "command.isekai_mahou.story.on" : "command.isekai_mahou.story.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        MagicData d = MagicApi.get(p);
        for (StoryBook.Beat b : StoryBook.beats()) {
            boolean seen = d.hasFlag(b.seenFlag());
            MutableComponent line = Component.literal((seen ? "✔ " : "· ") + b.id() + " — ")
                    .withStyle(seen ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                    .append(b.title().withStyle(s -> s.withColor(b.rgb())));
            c.getSource().sendSuccess(() -> line, false);
        }
        return StoryBook.beats().size();
    }

    private static int play(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "beat");
        StoryBook.Beat beat = StoryBook.byId(id);
        if (beat == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.story.unknown", id));
            return 0;
        }
        StoryService.deliver(p, beat);
        return 1;
    }

    private StoryCommands() {}
}
