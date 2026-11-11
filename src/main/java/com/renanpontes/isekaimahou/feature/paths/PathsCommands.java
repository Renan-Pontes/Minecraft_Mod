package com.renanpontes.isekaimahou.feature.paths;

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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Subcomandos:
 * <pre>
 * /mahou paths node &lt;elemento&gt; &lt;nó|all&gt;     (cheats) libera o nó sem requisitos (desperta o elemento e marca a Provação)
 * /mahou paths trial &lt;elemento&gt; &lt;rank&gt;      (cheats) conta a Provação como vencida (TrialsApi.markCleared)
 * /mahou paths info &lt;elemento&gt;                 mostra a árvore do elemento com Provações e feitos
 * </pre>
 */
public final class PathsCommands {

    public static void register() {
        MahouCommands.extend((root, buildCtx) -> root.then(Commands.literal("paths")
                .then(MahouCommands.op("node")
                        .then(Commands.argument("element", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(elementIds(), b))
                                .then(Commands.argument("node", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(nodeIdsPlusAll(), b))
                                        .executes(PathsCommands::grant))))
                .then(MahouCommands.op("trial")
                        .then(Commands.argument("element", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(elementIds(), b))
                                .then(Commands.argument("rank", IntegerArgumentType.integer(1, Progression.MAX_TRIAL_RANK))
                                        .executes(PathsCommands::clearTrial))))
                .then(Commands.literal("info")
                        .then(Commands.argument("element", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(elementIds(), b))
                                .executes(PathsCommands::info)))));
    }

    private static Element element(CommandContext<CommandSourceStack> c) {
        Element e = Element.byId(StringArgumentType.getString(c, "element"));
        if (e == null || e == Element.ARCANE) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.paths.bad_element"));
            return null;
        }
        return e;
    }

    private static int grant(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Element e = element(c);
        if (e == null) return 0;
        String nodeArg = StringArgumentType.getString(c, "node");
        List<PathNode> nodes = new ArrayList<>();
        if (nodeArg.equals("all")) {
            nodes.addAll(List.of(PathNode.values()));
        } else {
            PathNode n = PathNode.byId(nodeArg);
            if (n == null) {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.paths.bad_node"));
                return 0;
            }
            nodes.add(n);
        }
        MagicData d = MagicApi.get(player);
        if (!d.isAwakened(e)) MagicApi.awakenElement(player, e);
        int granted = 0;
        for (PathNode n : nodes) {
            // A corrente de Provações continua coerente: o nó conta como a Provação do rank vencida.
            if (n.trialRank() > 0) d.setFlag(TrialsApi.flag(e, n.trialRank()), true);
            // Com "all" o anúncio de cada nó viraria spam: só o resumo no final.
            if (PathProgress.unlock(player, e, n, nodes.size() == 1)) granted++;
        }
        d.markDirty();
        final int count = granted;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.paths.granted", count, e.displayName()), true);
        return count;
    }

    private static int clearTrial(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Element e = element(c);
        if (e == null) return 0;
        int rank = IntegerArgumentType.getInteger(c, "rank");
        if (!MagicApi.get(player).isAwakened(e)) MagicApi.awakenElement(player, e);
        TrialsApi.markCleared(player, e, rank);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.paths.trial_cleared", TrialsApi.title(e, rank)), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Element e = element(c);
        if (e == null) return 0;
        MagicData d = MagicApi.get(player);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.paths.info_header", e.displayName(),
                Math.max(0, d.elementLevel(e)), d.path().affinity(e).displayName()).withStyle(ChatFormatting.GOLD), false);
        for (PathNode n : PathNode.values()) {
            boolean has = d.hasNode(e, n.id);
            MutableComponent line = Component.literal(has ? " ✔ " : " ✘ ").withStyle(has ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .append(n.displayName().withStyle(has ? ChatFormatting.WHITE : ChatFormatting.GRAY));
            int rank = n.trialRank();
            if (!has) {
                List<PathReq> reqs = rank > 0 ? PathRules.trialEntry(e, rank) : PathRules.requirements(e, n);
                if (rank > 0) line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY)).append(TrialsApi.title(e, rank));
                for (PathReq r : reqs) appendReq(line, r, d, player);
            }
            c.getSource().sendSuccess(() -> line, false);
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.paths.info_feats").withStyle(ChatFormatting.GOLD), false);
        for (int stage = 1; stage <= PathRules.FEAT_STAGES; stage++) {
            boolean got = d.hasFlag(PathRules.featFlag(e, stage));
            MutableComponent line = Component.literal(got ? " ★ " : " ☆ ").withStyle(got ? ChatFormatting.GOLD : ChatFormatting.GRAY)
                    .append(Component.translatable("command.isekai_mahou.paths.info_feat_bonus", PathRules.featBonus(stage))
                            .withStyle(ChatFormatting.AQUA));
            for (PathReq r : PathRules.feats(e, stage, d.path())) appendReq(line, r, d, player);
            c.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static void appendReq(MutableComponent line, PathReq r, MagicData d, ServerPlayer player) {
        boolean met = r.met(d, player);
        line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(r.label().withStyle(met ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED));
        if (r.countable()) {
            line.append(Component.literal(" " + Math.min(r.current(d, player), r.target()) / r.divisor()
                    + "/" + r.target() / r.divisor()).withStyle(ChatFormatting.GRAY));
        }
    }

    private static List<String> elementIds() {
        return PathRules.pathElements().stream().map(e -> e.id).toList();
    }

    private static List<String> nodeIdsPlusAll() {
        List<String> l = new ArrayList<>();
        for (PathNode n : PathNode.values()) l.add(n.id);
        l.add("all");
        return l;
    }

    private PathsCommands() {}
}
