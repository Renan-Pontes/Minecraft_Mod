package com.renanpontes.isekaimahou.feature.class_quincy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /mahou quincy} (qualquer jogador): estado da classe e do próximo rank · {@code accept}: aceita um Auswählen.
 * Com cheats: {@code rank <0-5>} (dá as recompensas que faltam), {@code reishi <n>}, {@code letter <id>},
 * {@code feats} (completa os feitos do próximo rank para testar), {@code forge} (release 1.0: estado do espaço de
 * receita do Vollständig).
 */
public final class QuincyCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("quincy")
                .executes(c -> status(c.getSource().getPlayerOrException()))
                .then(Commands.literal("accept").executes(c -> Auswahlen.accept(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(MahouCommands.op("rank").then(Commands.argument("rank", IntegerArgumentType.integer(0, Quincy.MAX_RANK)).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    int target = IntegerArgumentType.getInteger(c, "rank");
                    MagicData d = MagicApi.get(sp);
                    if (target < Quincy.rank(d)) {
                        MagicApi.addCounter(sp, Quincy.RANK, target - d.counter(Quincy.RANK));
                    } else {
                        for (int r = Quincy.rank(d) + 1; r <= target; r++) QuincyRanks.grant(sp, QuincyRanks.rank(r));
                    }
                    c.getSource().sendSuccess(() -> Quincy.msg("cmd_rank", target), false);
                    return 1;
                })))
                .then(MahouCommands.op("reishi").then(Commands.argument("amount", IntegerArgumentType.integer(0, 99)).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    Quincy.takeAllReishi(sp);
                    Quincy.addReishi(sp, IntegerArgumentType.getInteger(c, "amount"));
                    c.getSource().sendSuccess(() -> Quincy.reishiLine(MagicApi.get(sp)), false);
                    return 1;
                })))
                .then(MahouCommands.op("letter").then(Commands.argument("letter", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Quincy.Letter.values()).map(l -> l.id), b))
                        .executes(c -> {
                            Quincy.Letter l = Quincy.Letter.byId(StringArgumentType.getString(c, "letter"));
                            if (l == null) {
                                c.getSource().sendFailure(Quincy.msg("cmd_letter_unknown"));
                                return 0;
                            }
                            QuincyRanks.chooseLetter(c.getSource().getPlayerOrException(), l, true);
                            return 1;
                        })))
                .then(MahouCommands.op("forge").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    com.renanpontes.isekaimahou.glyph.SpellRecipe r = QuincyForge.recipe(sp);
                    Component recipe = r == null ? Component.translatable("screen.isekai_mahou.release_forge.none")
                            : r.name().isEmpty() ? Component.translatable("screen.isekai_mahou.tforge.unnamed") : Component.literal(r.name());
                    sp.sendSystemMessage(Component.translatable("message.isekai_mahou.release_forge.debug",
                            Component.translatable("screen.isekai_mahou.tforge.slot.vollstandig"), yesNo(QuincyForge.canShape(MagicApi.get(sp))),
                            recipe, yesNo(QuincyForge.active(sp))).withStyle(ChatFormatting.GRAY));
                    return 1;
                }))
                .then(MahouCommands.op("feats").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    QuincyRanks.Rank next = QuincyRanks.rank(Quincy.rank(MagicApi.get(sp)) + 1);
                    if (next == null) return 0;
                    for (QuincyRanks.Req q : next.reqs()) {
                        int missing = q.amount() - q.current().applyAsInt(MagicApi.get(sp));
                        if (missing <= 0) continue;
                        String key = switch (q.key()) {
                            case "kills" -> Quincy.KILLS;
                            case "absorbed" -> Quincy.ABSORBED;
                            case "auswahlen" -> Quincy.AUSWAHLEN;
                            case "schrift" -> Quincy.SCHRIFT_USES;
                            default -> "boss.quincy_test";
                        };
                        MagicApi.addCounter(sp, key, missing);
                    }
                    c.getSource().sendSuccess(() -> Quincy.msg("cmd_feats", Quincy.roman(next.number())), false);
                    return 1;
                }))));
    }

    private static Component yesNo(boolean b) {
        return Component.translatable(b ? "gui.yes" : "gui.no").withStyle(b ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static int status(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        int rank = Quincy.rank(d);
        List<Component> lines = new ArrayList<>();
        lines.add(Quincy.msg("status_head", Quincy.roman(rank), Quincy.rankTitle(rank)).withColor(Quincy.RGB));
        lines.add(Quincy.reishiLine(d));
        Quincy.Letter l = Quincy.letter(d);
        lines.add(Quincy.msg("status_letter", l == null ? Quincy.msg("status_no_letter") : l.displayName()).withStyle(ChatFormatting.GRAY));
        QuincyRanks.Rank next = QuincyRanks.rank(rank + 1);
        if (next != null) {
            List<Component> missing = new ArrayList<>();
            boolean ok = QuincyRanks.meets(d, next, missing);
            lines.add(Quincy.msg(ok ? "status_ready" : "status_next", Quincy.roman(next.number())).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            for (Component m : missing) lines.add(Component.literal(" ✘ ").withStyle(ChatFormatting.RED).append(m.copy().withStyle(ChatFormatting.GRAY)));
        }
        for (Component c : lines) sp.sendSystemMessage(c);
        return 1;
    }

    private QuincyCommands() {}
}
