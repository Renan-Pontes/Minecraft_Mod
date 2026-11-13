package com.renanpontes.isekaimahou.feature.renown;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.reputation.ReputationService;
import com.renanpontes.isekaimahou.feature.renown.RenownData.PlayerRow;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /mahou renown ...}
 * <ul>
 *   <li>qualquer jogador: {@code board} (abre o Ranking);</li>
 *   <li>op (teste): {@code status}; {@code check} (checa os títulos agora); {@code grant|revoke <id|all>};
 *       {@code record <chefe> <segundos>} (grava um tempo de chefe para você); {@code timer on|off} (cronômetro no HUD);
 *       {@code fake <n>} / {@code unfake} (jogadores de mentira para ver o quadro cheio); {@code clearrecords}.</li>
 * </ul>
 */
final class RenownCommands {
    /** Nome dos jogadores de mentira do {@code fake} (para o {@code unfake} achar). */
    private static final String FAKE_PREFIX = "Teste_";

    private RenownCommands() {}

    static void register() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("renown")
                .then(Commands.literal("board").executes(c -> {
                    RenownRanking.sendBoard(c.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(MahouCommands.op("status").executes(RenownCommands::status))
                .then(MahouCommands.op("check").executes(c -> {
                    int n = RenownTitles.check(c.getSource().getPlayerOrException());
                    return done(c, "renown.isekai_mahou.cmd.checked", n);
                }))
                .then(MahouCommands.op("grant").then(Commands.argument("title", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(titleIds(), b))
                        .executes(c -> setTitles(c, true))))
                .then(MahouCommands.op("revoke").then(Commands.argument("title", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(titleIds(), b))
                        .executes(c -> setTitles(c, false))))
                .then(MahouCommands.op("record").then(Commands.argument("boss", ResourceLocationArgument.id())
                        .suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 36000)).executes(c -> {
                            ResourceLocation boss = ResourceLocationArgument.getId(c, "boss");
                            int seconds = IntegerArgumentType.getInteger(c, "seconds");
                            BossTimer.debugRecord(c.getSource().getPlayerOrException(), boss.toString(), seconds * 20);
                            return done(c, "renown.isekai_mahou.cmd.recorded", boss.toString(), BossTimer.format(seconds * 20));
                        }))))
                .then(MahouCommands.op("timer")
                        .then(Commands.literal("on").executes(c -> {
                            BossTimer.debugTimer(c.getSource().getPlayerOrException(), true);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(c -> {
                            BossTimer.debugTimer(c.getSource().getPlayerOrException(), false);
                            return 1;
                        })))
                .then(MahouCommands.op("fake").then(Commands.argument("n", IntegerArgumentType.integer(1, 40)).executes(c -> {
                    int n = IntegerArgumentType.getInteger(c, "n");
                    fake(RenownData.get(c.getSource().getServer()), n);
                    return done(c, "renown.isekai_mahou.cmd.fake", n);
                })))
                .then(MahouCommands.op("unfake").executes(c -> {
                    RenownData data = RenownData.get(c.getSource().getServer());
                    int before = data.players.size();
                    data.players.values().removeIf(r -> r.name.startsWith(FAKE_PREFIX));
                    data.setDirty();
                    return done(c, "renown.isekai_mahou.cmd.unfake", before - data.players.size());
                }))
                .then(MahouCommands.op("clearrecords").executes(c -> {
                    RenownData data = RenownData.get(c.getSource().getServer());
                    int n = data.bosses.size();
                    data.bosses.clear();
                    for (PlayerRow r : data.players.values()) r.bossBest.clear();
                    data.setDirty();
                    return done(c, "renown.isekai_mahou.cmd.cleared", n);
                }))));
    }

    private static List<String> titleIds() {
        List<String> ids = new ArrayList<>();
        ids.add("all");
        for (RenownTitles.Def d : RenownTitles.all()) ids.add(d.id());
        return ids;
    }

    private static int setTitles(CommandContext<CommandSourceStack> c, boolean grant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String arg = StringArgumentType.getString(c, "title");
        MagicData d = MagicApi.get(p);
        int n = 0;
        for (RenownTitles.Def def : RenownTitles.all()) {
            if (!arg.equals("all") && !arg.equals(def.id())) continue;
            boolean has = Reputation.hasTitle(p, def.key());
            if (grant && !has) {
                ReputationService.grantTitle(p, def.key(), false);
                n++;
            } else if (!grant && has) {
                if (def.key().equals(Reputation.selectedTitleKey(p))) Reputation.selectTitle(p, null);
                d.setFlag(ReputationService.TITLE_FLAG + def.key(), false);
                n++;
            }
        }
        d.markDirty();
        RenownTitles.sendList(p);
        return done(c, grant ? "renown.isekai_mahou.cmd.granted" : "renown.isekai_mahou.cmd.revoked", n);
    }

    private static int status(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        RenownData data = RenownData.get(p.server);
        PlayerRow r = RenownRanking.snapshot(p);
        int titles = 0;
        for (RenownTitles.Def def : RenownTitles.all()) if (Reputation.hasTitle(p, def.key())) titles++;
        c.getSource().sendSuccess(() -> Component.translatable("renown.isekai_mahou.cmd.status", data.players.size(), data.bosses.size(),
                BossTimer.active(), r.level, r.bossKills, r.towers, r.abyss, r.bossBest.size()).withStyle(ChatFormatting.GRAY), false);
        int owned = titles;
        c.getSource().sendSuccess(() -> Component.translatable("renown.isekai_mahou.cmd.status_titles", owned, RenownTitles.all().size(),
                RenownTitles.current(p.getUUID())).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** Jogadores de mentira com números variados (só na foto do ranking; nada no mundo). */
    private static void fake(RenownData data, int n) {
        Random rnd = new Random();
        List<RenownTitles.Def> defs = RenownTitles.all();
        String[] paths = {"mage", "witch", "warlock", "druid", "necromancer"};
        for (int i = 1; i <= n; i++) {
            String name = FAKE_PREFIX + i;
            PlayerRow r = data.row(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            r.name = name;
            r.level = 2 + rnd.nextInt(150);
            r.circle = Math.max(1, Math.min(10, r.level / 10));
            r.path = paths[rnd.nextInt(paths.length)];
            r.bossKills = rnd.nextInt(80);
            r.bossKinds = Math.min(r.bossKills, rnd.nextInt(25));
            r.towers = rnd.nextInt(6);
            r.towerFloors = r.towers * 10 + rnd.nextInt(10);
            r.abyss = rnd.nextInt(4) == 0 ? rnd.nextInt(120) : 0;
            r.title = rnd.nextBoolean() ? defs.get(rnd.nextInt(defs.size())).key() : "";
        }
        data.setDirty();
    }

    private static int done(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatable(key, args).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
