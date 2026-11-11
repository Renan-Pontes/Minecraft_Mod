package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.Arrays;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.class_knight.oath.KnightOaths;
import com.renanpontes.isekaimahou.feature.class_knight.oath.Req;
import com.renanpontes.isekaimahou.feature.class_knight.outfit.KnightOutfitTier;
import com.renanpontes.isekaimahou.feature.class_knight.outfit.KnightOutfitRewards;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de teste (op): {@code /mahou knight oath <1..5>} presta os Juramentos até o rank (sem oferendas),
 * {@code /mahou knight sword <id>} desperta uma Espada Lendária, {@code /mahou knight outfit <grau>} entrega a armadura,
 * {@code /mahou knight status} lista o progresso.
 */
public final class KnightCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("knight")
                .then(Commands.literal("oath").then(Commands.argument("rank", IntegerArgumentType.integer(1, KnightOaths.MAX_RANK))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            int target = IntegerArgumentType.getInteger(c, "rank");
                            MagicData d = MagicApi.get(p);
                            for (KnightOaths.Rank r : KnightOaths.RANKS) {
                                if (r.number() <= target && KnightOaths.rank(d) < r.number()) KnightOaths.grantRank(p, r, null, null);
                            }
                            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.class_knight.oath",
                                    KnightOaths.roman(target)), false);
                            return 1;
                        })))
                .then(Commands.literal("sword").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(LegendSword.values()).map(LegendSword::key), b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            LegendSword s = LegendSword.byId(StringArgumentType.getString(c, "id"));
                            if (s == null) {
                                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.class_knight.unknown_sword"));
                                return 0;
                            }
                            KnightOaths.awaken(p, s, null, null);
                            return 1;
                        })))
                .then(Commands.literal("outfit").then(Commands.argument("tier", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(KnightOutfitTier.values()).map(t -> t.id), b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String id = StringArgumentType.getString(c, "tier");
                            for (KnightOutfitTier t : KnightOutfitTier.values()) {
                                if (t.id.equals(id)) {
                                    KnightOutfitRewards.deliver(p, t);
                                    return 1;
                                }
                            }
                            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.class_knight.unknown_tier"));
                            return 0;
                        })))
                .then(Commands.literal("status").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    KnightCounters.flush(p);
                    MagicData d = MagicApi.get(p);
                    p.sendSystemMessage(Component.translatable("command.isekai_mahou.class_knight.status",
                            KnightOaths.roman(Math.max(1, KnightOaths.rank(d))), KnightOaths.rank(d),
                            d.counter(KnightCounters.RUNE_PROC), d.counter(KnightCounters.SWORD_KILL),
                            d.counter(KnightCounters.BOSSES), d.counter(KnightCounters.LEGEND_SWORDS)).withStyle(ChatFormatting.GOLD));
                    KnightOaths.Rank next = KnightOaths.next(d);
                    if (next != null) {
                        for (Req r : next.reqs()) {
                            p.sendSystemMessage(Component.literal(r.met(d) ? " ✔ " : " ✘ ").append(r.text(d))
                                    .withStyle(r.met(d) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                        }
                    }
                    return 1;
                }))));
    }

    private KnightCommands() {}
}
