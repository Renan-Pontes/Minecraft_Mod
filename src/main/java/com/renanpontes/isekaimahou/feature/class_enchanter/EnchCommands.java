package com.renanpontes.isekaimahou.feature.class_enchanter;

import java.util.Arrays;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterOutfit;
import com.renanpontes.isekaimahou.feature.class_enchanter.rite.EnchanterRanks;
import com.renanpontes.isekaimahou.feature.class_enchanter.rite.EnchanterRite;

import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de teste (operadores): {@code /mahou enchanter status | fill | rank <0-5> | outfit <grau>}.
 * <ul>
 *   <li>{@code fill}: completa as tarefas do próximo rank (os contadores) — a oferenda e a mesa continuam valendo;</li>
 *   <li>{@code rank n}: vai direto ao rank n (dando os glifos/recompensas dos ranks no caminho);</li>
 *   <li>{@code outfit grau}: veste o traje do grau (novice, adept, master, sovereign).</li>
 * </ul>
 */
public final class EnchCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("enchanter")
                .then(Commands.literal("status").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    MagicData d = MagicApi.get(p);
                    int rank = EnchanterRanks.current(d);
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.enchanter.status", rank,
                            d.counter(Enchants.C_APPLIED), d.counter(Enchants.C_ALLY), d.counter(Enchants.C_NULLIFIED),
                            d.counter(Enchants.C_KILLS), d.counter(Enchants.C_OVERDRIVE), d.counter(Enchants.C_RESTRICTED),
                            d.counter(Enchants.C_BOSS)), false);
                    return rank;
                }))
                .then(Commands.literal("fill").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    MagicData d = MagicApi.get(p);
                    EnchanterRanks.Rank r = EnchanterRanks.get(EnchanterRanks.current(d) + 1);
                    if (r == null) return 0;
                    for (EnchanterRanks.Req q : r.reqs()) {
                        int miss = q.amount() - q.have(d);
                        if (miss > 0) MagicApi.addCounter(p, q.counter(), miss);
                    }
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.enchanter.fill", r.title()), false);
                    return 1;
                }))
                .then(Commands.literal("rank").then(Commands.argument("n", IntegerArgumentType.integer(0, Enchants.MAX_RANK)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    MagicData d = MagicApi.get(p);
                    int target = IntegerArgumentType.getInteger(c, "n");
                    int cur = EnchanterRanks.current(d);
                    if (target < cur) {
                        MagicApi.addCounter(p, Enchants.RANK, target - cur);
                    } else {
                        for (int r = cur + 1; r <= target; r++) {
                            EnchanterRanks.Rank spec = EnchanterRanks.get(r);
                            if (spec != null) EnchanterRite.grant(p, spec);
                        }
                    }
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.enchanter.rank", target), false);
                    return target;
                })))
                .then(Commands.literal("outfit").then(Commands.argument("tier", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(EnchanterOutfit.values()).map(t -> t.id), b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            EnchanterOutfit t = EnchanterOutfit.byId(StringArgumentType.getString(c, "tier"));
                            if (t == null) return 0;
                            EnchanterClass.deliverOutfit(p, t);
                            return 1;
                        })))));
    }

    private EnchCommands() {}
}
