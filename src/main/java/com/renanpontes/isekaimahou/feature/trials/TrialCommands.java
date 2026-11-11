package com.renanpontes.isekaimahou.feature.trials;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.TrialsApi;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /mahou trial ... — para todos: start &lt;elemento&gt; [rank] · invite &lt;jogador&gt; · join &lt;jogador&gt; · leave · info.
 * Com cheats: complete · abort · list · reset &lt;elemento|all&gt;.
 */
final class TrialCommands {

    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("trial")
                .then(Commands.literal("start").then(Commands.argument("element", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(awakened(c.getSource()), b))
                        .executes(c -> start(c, 0))
                        .then(Commands.argument("rank", IntegerArgumentType.integer(1, Progression.MAX_TRIAL_RANK))
                                .executes(c -> start(c, IntegerArgumentType.getInteger(c, "rank"))))))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> TrialManager.invite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player")) ? 1 : 0)))
                .then(Commands.literal("join").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> TrialManager.join(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player")) ? 1 : 0)))
                .then(Commands.literal("leave").executes(c -> {
                    TrialManager.leave(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("info").executes(TrialCommands::info))
                .then(MahouCommands.op("complete").executes(TrialCommands::complete))
                .then(MahouCommands.op("abort").executes(TrialCommands::abort))
                .then(MahouCommands.op("list").executes(TrialCommands::list))
                .then(MahouCommands.op("reset").then(Commands.argument("element", StringArgumentType.word())
                        .suggests((c, b) -> {
                            List<String> ids = allElements();
                            ids.add("all");
                            return SharedSuggestionProvider.suggest(ids, b);
                        })
                        .executes(TrialCommands::reset)))));
    }

    private static List<String> allElements() {
        List<String> ids = new ArrayList<>();
        for (Element e : Element.values()) if (e != Element.ARCANE) ids.add(e.id);
        return ids;
    }

    /** Sugestões: elementos despertados de quem digita (ou todos, no console). */
    private static List<String> awakened(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) return allElements();
        MagicData d = MagicApi.get(p);
        List<String> ids = new ArrayList<>();
        for (Element e : Element.values()) if (e != Element.ARCANE && d.isAwakened(e)) ids.add(e.id);
        return ids.isEmpty() ? allElements() : ids;
    }

    private static int start(CommandContext<CommandSourceStack> c, int rank) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "element");
        Element e = Element.byId(id);
        if (e == null || e == Element.ARCANE) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.trials.unknown_element", id));
            return 0;
        }
        int r = rank;
        if (r <= 0) {
            r = TrialsApi.nextRank(MagicApi.get(p), e);
            if (r == 0) r = Progression.MAX_TRIAL_RANK;   // tudo concluído: repete o último como Desafio
        }
        TrialsApi.request(p, e, r);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        TrialInstance inst = TrialManager.of(p.getUUID());
        if (inst != null) {
            p.sendSystemMessage(Component.translatable("command.isekai_mahou.trials.current", TrialsApi.title(inst.element, inst.rank),
                    inst.phase().displayName(),
                    inst.hostName(), inst.members.size(), TrialInstance.MAX_PARTY).withStyle(ChatFormatting.AQUA));
        }
        TrialNotifier.list(p);
        return 1;
    }

    private static int complete(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        TrialInstance inst = TrialManager.of(c.getSource().getPlayerOrException().getUUID());
        if (inst == null) {
            c.getSource().sendFailure(Component.translatable("message.isekai_mahou.trials.not_in_trial"));
            return 0;
        }
        if (!inst.phase().combat()) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.trials.complete_not_fighting"));
            return 0;
        }
        inst.victory();
        return 1;
    }

    private static int abort(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        TrialInstance inst = TrialManager.of(c.getSource().getPlayerOrException().getUUID());
        if (inst == null) {
            c.getSource().sendFailure(Component.translatable("message.isekai_mahou.trials.not_in_trial"));
            return 0;
        }
        inst.close("message.isekai_mahou.trials.closed_aborted");
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<TrialInstance> all = TrialManager.all();
        if (all.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.trials.list_empty").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (TrialInstance i : all) {
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.trials.list_entry", i.index,
                    TrialsApi.title(i.element, i.rank),
                    i.phase().displayName(),
                    i.hostName(), i.members.size()), false);
        }
        return all.size();
    }

    /** Apaga as conclusões (e os avisos) de Provação do jogador — para testar de novo. */
    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String arg = StringArgumentType.getString(c, "element");
        MagicData d = MagicApi.get(p);
        List<Element> targets = new ArrayList<>();
        if (arg.equals("all")) {
            for (Element e : Element.values()) if (e != Element.ARCANE) targets.add(e);
        } else {
            Element e = Element.byId(arg);
            if (e == null || e == Element.ARCANE) {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.trials.unknown_element", arg));
                return 0;
            }
            targets.add(e);
        }
        for (Element e : targets) {
            for (int r = 1; r <= Progression.MAX_TRIAL_RANK; r++) {
                d.setFlag(TrialsApi.flag(e, r), false);
                d.setFlag("trials.notified." + e.id + "." + r, false);
            }
        }
        d.markDirty();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.trials.reset", arg).withStyle(ChatFormatting.GREEN), false);
        return targets.size();
    }

    private TrialCommands() {}
}
