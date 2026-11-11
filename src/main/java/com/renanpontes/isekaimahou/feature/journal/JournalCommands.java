package com.renanpontes.isekaimahou.feature.journal;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.journal.data.JournalBook;
import com.renanpontes.isekaimahou.feature.journal.data.JournalChapter;
import com.renanpontes.isekaimahou.feature.journal.data.JournalStep;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * /mahou journal status [jogador]     — capítulos e passos (qualquer jogador vê o próprio)
 * /mahou journal complete [jogadores] — conclui o capítulo atual (cheats)
 * /mahou journal complete_class [jogadores] — conclui o capítulo atual da rota da classe (cheats)
 * /mahou journal reset [jogadores]    — apaga o progresso do Diário (cheats)
 * /mahou journal guide                — chama o Espírito Guia sem recarga, voando até o alvo (cheats)
 * /mahou journal locate               — procura de novo o lugar do objetivo atual e mostra as coordenadas (cheats)
 * /mahou journal tutorial [jogadores] — o Espírito Guia mostra o tutorial de novo (cheats; não mexe nas flags)
 */
final class JournalCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("journal")
                .then(Commands.literal("status")
                        .executes(c -> status(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(c -> status(c, EntityArgument.getPlayer(c, "target")))))
                .then(MahouCommands.op("complete")
                        .executes(c -> complete(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> complete(c, EntityArgument.getPlayers(c, "targets")))))
                .then(MahouCommands.op("complete_class")
                        .executes(c -> completeClass(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> completeClass(c, EntityArgument.getPlayers(c, "targets")))))
                .then(MahouCommands.op("reset")
                        .executes(c -> reset(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> reset(c, EntityArgument.getPlayers(c, "targets")))))
                .then(MahouCommands.op("guide").executes(c -> {
                    JournalService.summonAndGuide(c.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(MahouCommands.op("locate").executes(c -> locate(c, c.getSource().getPlayerOrException())))
                .then(MahouCommands.op("tutorial")
                        .executes(c -> tutorial(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> tutorial(c, EntityArgument.getPlayers(c, "targets")))))));
    }

    private static int locate(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        JournalBook book = JournalBook.server();
        MagicData d = MagicApi.get(p);
        JournalChapter ch = book.active(d);
        JournalStep step = ch == null ? null : JournalBook.currentStep(ch, d, p);
        if (step == null || step.locate() == null) {
            // Sem lugar na história: tenta o objetivo da rota da classe.
            JournalChapter cls = book.classActive(d);
            JournalStep clsStep = cls == null || !d.hasFlag(cls.begunFlag()) ? null : JournalBook.currentStep(cls, d, p);
            if (clsStep != null && clsStep.locate() != null) {
                ch = cls;
                step = clsStep;
            }
        }
        final JournalChapter fch = ch;
        final JournalStep fstep = step;
        if (step == null || step.locate() == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.journal.locate_none"));
            return 0;
        }
        BlockPos pos = JournalLocator.forceSearch(p, fch, fstep);
        if (pos == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.journal.locate_missing", fstep.title(fch)));
            return 0;
        }
        int dist = (int) Math.round(Math.sqrt(pos.distToCenterSqr(p.getX(), pos.getY() + 0.5, p.getZ())));
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.journal.locate_found", fstep.title(fch),
                pos.getX(), pos.getY(), pos.getZ(), dist), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        JournalBook book = JournalBook.server();
        MagicData d = MagicApi.get(p);
        CommandSourceStack src = c.getSource();
        if (book.isEmpty()) {
            src.sendFailure(Component.translatable("command.isekai_mahou.journal.empty"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.journal.status", p.getDisplayName(),
                book.doneCount(d), book.visibleCount(d)).withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < book.chapters().size(); i++) {
            if (!book.visible(i, d)) continue;
            JournalChapter ch = book.chapters().get(i);
            JournalBook.State state = book.state(i, d);
            int n = i + 1;
            String stateKey = "screen.isekai_mahou.journal.state." + state.name().toLowerCase(java.util.Locale.ROOT);
            src.sendSuccess(() -> Component.translatable("screen.isekai_mahou.journal.chapter_n", n).append(": ").append(ch.title())
                    .append(" — ").append(Component.translatable(stateKey))
                    .withStyle(s -> s.withColor(state == JournalBook.State.LOCKED ? 0x777777 : ch.color())), false);
            if (state != JournalBook.State.ACTIVE) continue;
            for (JournalStep s : ch.visibleSteps()) {
                boolean done = d.hasFlag(s.flag(ch));
                int cur = Math.min(s.condition().current(d, p), s.condition().target());
                MutableComponent line = Component.literal(done ? "  ✔ " : "  ◇ ").append(s.title(ch))
                        .append(Component.literal(" (" + cur + "/" + s.condition().target() + ")").withStyle(ChatFormatting.DARK_GRAY));
                if (s.optional()) line.append(" ").append(Component.translatable("screen.isekai_mahou.journal.optional"));
                src.sendSuccess(() -> line.withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
            }
        }
        // Rota da classe
        List<JournalChapter> track = book.classTrack(d.path());
        if (!track.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("screen.isekai_mahou.journal.section.class", d.path().displayName())
                    .withStyle(ChatFormatting.GOLD), false);
        }
        for (int i = 0; i < track.size(); i++) {
            JournalChapter ch = track.get(i);
            JournalBook.State state = JournalBook.state(track, i, d);
            String stateKey = "screen.isekai_mahou.journal.state." + state.name().toLowerCase(java.util.Locale.ROOT);
            src.sendSuccess(() -> Component.literal(" ").append(ch.title()).append(" — ").append(Component.translatable(stateKey))
                    .withStyle(s -> s.withColor(state == JournalBook.State.LOCKED ? 0x777777 : ch.color())), false);
            if (state != JournalBook.State.ACTIVE) continue;
            for (JournalStep s : ch.visibleSteps()) {
                boolean done = d.hasFlag(s.flag(ch));
                int cur = Math.min(s.condition().current(d, p), s.condition().target());
                MutableComponent line = Component.literal(done ? "  ✔ " : "  ◇ ").append(s.title(ch))
                        .append(Component.literal(" (" + cur + "/" + s.condition().target() + ")").withStyle(ChatFormatting.DARK_GRAY));
                if (s.optional()) line.append(" ").append(Component.translatable("screen.isekai_mahou.journal.optional"));
                src.sendSuccess(() -> line.withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
            }
        }
        return book.doneCount(d);
    }

    private static int completeClass(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        int n = 0;
        for (ServerPlayer p : players) {
            JournalChapter ch = JournalService.forceComplete(p, true);
            if (ch == null) {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.journal.no_class_chapter", p.getDisplayName()));
                continue;
            }
            n++;
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.journal.completed", ch.title(), p.getDisplayName()), true);
        }
        return n;
    }

    private static int complete(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) throws CommandSyntaxException {
        int n = 0;
        for (ServerPlayer p : players) {
            JournalChapter ch = JournalService.forceComplete(p);
            if (ch == null) {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.journal.no_chapter", p.getDisplayName()));
                continue;
            }
            n++;
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.journal.completed", ch.title(), p.getDisplayName()), true);
        }
        return n;
    }

    private static int tutorial(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        int n = 0;
        for (ServerPlayer p : players) {
            if (MagicApi.get(p).path() == MagicPath.NONE) {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.journal.tutorial_none", p.getDisplayName()));
                continue;
            }
            JournalTutorial.open(p, false);
            n++;
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.journal.tutorial", p.getDisplayName()), true);
        }
        return n;
    }

    private static int reset(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            int removed = JournalService.reset(p);
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.journal.reset", p.getDisplayName(), removed), true);
        }
        return players.size();
    }

    private JournalCommands() {}
}
