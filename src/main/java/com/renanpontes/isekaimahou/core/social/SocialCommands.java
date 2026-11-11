package com.renanpontes.isekaimahou.core.social;

import java.util.Collection;
import java.util.UUID;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Party.Result;
import com.renanpontes.isekaimahou.core.social.SocialData.PartyRec;
import com.renanpontes.isekaimahou.core.social.SocialData.WarRec;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Comandos sociais (para todos): {@code /party}, {@code /p}, {@code /trade}, {@code /duel}. Op:
 * {@code /mahou social allegiance|leader|war|arena|parties}.
 */
final class SocialCommands {

    static void onRegister(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(party());
        d.register(Commands.literal("p").then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(c -> run(c, PartyService.chat(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "message")), ""))));
        d.register(request("trade", TradeService::request, TradeService::accept, TradeService::decline));
        d.register(request("duel", DuelService::request, DuelService::accept, DuelService::decline));
    }

    // =====================================================================================
    //  /party
    // =====================================================================================
    private static LiteralArgumentBuilder<CommandSourceStack> party() {
        SuggestionProvider<CommandSourceStack> members = (c, b) -> {
            ServerPlayer sp = c.getSource().getPlayer();
            PartyRec rec = sp == null ? null : PartyService.partyOf(sp);
            if (rec != null) for (UUID m : rec.members) if (!m.equals(sp.getUUID())) b.suggest(rec.name(m));
            return b.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> inviters = (c, b) -> {
            ServerPlayer sp = c.getSource().getPlayer();
            if (sp != null) {
                for (Requests.Request r : Requests.pendingFor(sp.server, sp.getUUID())) {
                    if (r.kind() == Requests.Kind.PARTY) b.suggest(r.fromName());
                }
            }
            return b.buildFuture();
        };
        return Commands.literal("party")
                .executes(SocialCommands::list)
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> {
                            ServerPlayer target = EntityArgument.getPlayer(c, "player");
                            return run(c, PartyService.invite(c.getSource().getPlayerOrException(), target), target.getGameProfile().getName());
                        })))
                .then(Commands.literal("accept")
                        .executes(c -> run(c, PartyService.accept(c.getSource().getPlayerOrException(), null), ""))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(inviters)
                                .executes(c -> run(c, PartyService.accept(c.getSource().getPlayerOrException(),
                                        requester(c, Requests.Kind.PARTY)), StringArgumentType.getString(c, "player")))))
                .then(Commands.literal("decline")
                        .executes(c -> run(c, PartyService.decline(c.getSource().getPlayerOrException(), null), ""))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(inviters)
                                .executes(c -> run(c, PartyService.decline(c.getSource().getPlayerOrException(),
                                        requester(c, Requests.Kind.PARTY)), StringArgumentType.getString(c, "player")))))
                .then(Commands.literal("leave").executes(c -> run(c, PartyService.leave(c.getSource().getPlayerOrException()), "")))
                .then(Commands.literal("disband").executes(c -> run(c, PartyService.disband(c.getSource().getPlayerOrException()), "")))
                .then(Commands.literal("kick").then(Commands.argument("member", StringArgumentType.word()).suggests(members)
                        .executes(c -> memberOp(c, PartyService::kick))))
                .then(Commands.literal("promote").then(Commands.argument("member", StringArgumentType.word()).suggests(members)
                        .executes(c -> memberOp(c, PartyService::promote))))
                .then(Commands.literal("list").executes(SocialCommands::list))
                .then(Commands.literal("chat")
                        .executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            SocialActions.onPartyOp(sp, SocialPayloads.OP_CHAT_TOGGLE, sp.getUUID());
                            return 1;
                        })
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(c -> run(c, PartyService.chat(c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "message")), ""))));
    }

    private static int memberOp(CommandContext<CommandSourceStack> c, BiFunction<ServerPlayer, UUID, Result> op)
            throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "member");
        PartyRec rec = PartyService.partyOf(sp);
        UUID target = null;
        if (rec != null) {
            for (UUID m : rec.members) if (rec.name(m).equalsIgnoreCase(name)) target = m;
        }
        if (target == null) return run(c, Result.NOT_MEMBER, name);
        return run(c, op.apply(sp, target), name);
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        PartyRec rec = PartyService.partyOf(sp);
        if (rec == null) {
            c.getSource().sendSuccess(() -> Component.translatable("social.isekai_mahou.party.none").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("social.isekai_mahou.party.list_header", rec.members.size(),
                com.renanpontes.isekaimahou.core.api.Party.MAX_MEMBERS).withColor(PartyService.PARTY_COLOR), false);
        for (UUID m : rec.members) {
            boolean online = sp.server.getPlayerList().getPlayer(m) != null;
            MutableComponent line = Component.literal(" • ").append(Component.literal(rec.name(m))
                    .withStyle(m.equals(rec.leader) ? ChatFormatting.GOLD : online ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
            if (m.equals(rec.leader)) line.append(Component.translatable("social.isekai_mahou.party.leader_tag").withStyle(ChatFormatting.GOLD));
            if (!online) line.append(Component.translatable("social.isekai_mahou.party.offline_tag").withStyle(ChatFormatting.DARK_GRAY));
            c.getSource().sendSuccess(() -> line, false);
        }
        return rec.members.size();
    }

    // =====================================================================================
    //  /trade e /duel
    // =====================================================================================
    @FunctionalInterface
    private interface Ask {
        Result apply(ServerPlayer from, ServerPlayer to);
    }

    @FunctionalInterface
    private interface Answer {
        Result apply(ServerPlayer player, @Nullable UUID from);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> request(String name, Ask ask, Answer accept, Answer decline) {
        Requests.Kind kind = "trade".equals(name) ? Requests.Kind.TRADE : Requests.Kind.DUEL;
        SuggestionProvider<CommandSourceStack> senders = (c, b) -> {
            ServerPlayer sp = c.getSource().getPlayer();
            if (sp != null) {
                for (Requests.Request r : Requests.pendingFor(sp.server, sp.getUUID())) if (r.kind() == kind) b.suggest(r.fromName());
            }
            return b.buildFuture();
        };
        return Commands.literal(name)
                .then(Commands.literal("accept")
                        .executes(c -> run(c, accept.apply(c.getSource().getPlayerOrException(), null), ""))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(senders)
                                .executes(c -> run(c, accept.apply(c.getSource().getPlayerOrException(), requester(c, kind)),
                                        StringArgumentType.getString(c, "player")))))
                .then(Commands.literal("decline")
                        .executes(c -> run(c, decline.apply(c.getSource().getPlayerOrException(), null), ""))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(senders)
                                .executes(c -> run(c, decline.apply(c.getSource().getPlayerOrException(), requester(c, kind)),
                                        StringArgumentType.getString(c, "player")))))
                .then(Commands.argument("target", EntityArgument.player()).executes(c -> {
                    ServerPlayer target = EntityArgument.getPlayer(c, "target");
                    return run(c, ask.apply(c.getSource().getPlayerOrException(), target), target.getGameProfile().getName());
                }));
    }

    /** UUID de quem mandou o pedido, pelo nome digitado (null = pega o mais recente; nome desconhecido = UUID vazio). */
    @Nullable
    private static UUID requester(CommandContext<CommandSourceStack> c, Requests.Kind kind) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "player");
        for (Requests.Request r : Requests.pendingFor(sp.server, sp.getUUID())) {
            if (r.kind() == kind && r.fromName().equalsIgnoreCase(name)) return r.from();
        }
        return new UUID(0, 0);
    }

    private static int run(CommandContext<CommandSourceStack> c, Result r, String name) {
        if (r.ok()) return 1;
        c.getSource().sendFailure(r.message(name));
        return 0;
    }

    // =====================================================================================
    //  /mahou social (op)
    // =====================================================================================
    static void registerOp() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("social")
                .then(Commands.literal("allegiance").then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("city", StringArgumentType.word()).suggests(SocialCommands::suggestCities)
                                .executes(c -> {
                                    Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "players");
                                    String city = StringArgumentType.getString(c, "city");
                                    for (ServerPlayer p : ps) AllegianceService.set(p, "none".equals(city) ? null : city);
                                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.social.allegiance",
                                            ps.size(), city), true);
                                    return ps.size();
                                }))))
                .then(Commands.literal("leader").then(Commands.argument("city", StringArgumentType.word()).suggests(SocialCommands::suggestCities)
                        .then(Commands.literal("none").executes(c -> {
                            String city = StringArgumentType.getString(c, "city");
                            AllegianceService.setLeader(c.getSource().getServer(), city, null);
                            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.social.leader_none", city), true);
                            return 1;
                        }))
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            String city = StringArgumentType.getString(c, "city");
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            AllegianceService.setLeader(c.getSource().getServer(), city, p.getUUID());
                            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.social.leader", city,
                                    p.getGameProfile().getName()), true);
                            return 1;
                        }))))
                .then(Commands.literal("war")
                        .then(Commands.literal("declare").then(Commands.argument("city", StringArgumentType.word()).suggests(SocialCommands::suggestCities)
                                .then(Commands.argument("target", StringArgumentType.word()).suggests(SocialCommands::suggestCities)
                                        .executes(c -> {
                                            boolean ok = AllegianceService.declare(c.getSource().getServer(),
                                                    StringArgumentType.getString(c, "city"), StringArgumentType.getString(c, "target"),
                                                    c.getSource().getPlayer());
                                            return feedback(c, ok, "command.isekai_mahou.social.war_declared", "command.isekai_mahou.social.war_fail");
                                        }))))
                        .then(Commands.literal("peace").then(Commands.argument("city", StringArgumentType.word()).suggests(SocialCommands::suggestCities)
                                .then(Commands.argument("target", StringArgumentType.word()).suggests(SocialCommands::suggestCities)
                                        .executes(c -> {
                                            boolean ok = AllegianceService.peace(c.getSource().getServer(),
                                                    StringArgumentType.getString(c, "city"), StringArgumentType.getString(c, "target"));
                                            return feedback(c, ok, "command.isekai_mahou.social.peace", "command.isekai_mahou.social.peace_fail");
                                        }))))
                        .then(Commands.literal("list").executes(c -> {
                            MinecraftServer server = c.getSource().getServer();
                            var wars = AllegianceService.wars(server);
                            if (wars.isEmpty()) {
                                c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.social.no_wars"), false);
                                return 0;
                            }
                            for (WarRec w : wars) c.getSource().sendSuccess(() -> AllegianceService.warLine(server, w), false);
                            return wars.size();
                        })))
                .then(Commands.literal("arena")
                        .then(Commands.literal("list").executes(c -> {
                            var all = BossArenaService.all();
                            if (all.isEmpty()) {
                                c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.social.no_arenas"), false);
                                return 0;
                            }
                            int now = c.getSource().getServer().getTickCount();
                            for (BossArenaService.Arena a : all) {
                                c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.social.arena_line", a.bossName,
                                        a.dim.location().toString(), (int) a.center.x, (int) a.center.y, (int) a.center.z, (int) a.radius,
                                        a.releasing() ? Component.translatable("command.isekai_mahou.social.arena_releasing",
                                                (a.ticksLeft(now) + 19) / 20) : Component.translatable("command.isekai_mahou.social.arena_active")),
                                        false);
                            }
                            return all.size();
                        }))
                        .then(Commands.literal("release").executes(c -> {
                            int n = 0;
                            for (BossArenaService.Arena a : BossArenaService.all()) if (BossArenaService.release(a.boss)) n++;
                            int count = n;
                            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.social.arena_released", count), true);
                            return n;
                        })))
                .then(Commands.literal("parties").executes(c -> {
                    var parties = SocialData.get(c.getSource().getServer()).parties();
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.social.parties", parties.size()), false);
                    for (PartyRec p : parties) {
                        StringBuilder sb = new StringBuilder();
                        for (UUID m : p.members) {
                            if (!sb.isEmpty()) sb.append(", ");
                            sb.append(p.name(m));
                            if (m.equals(p.leader)) sb.append('*');
                        }
                        String text = sb.toString();
                        c.getSource().sendSuccess(() -> Component.literal(" • " + text), false);
                    }
                    return parties.size();
                }))));
    }

    private static int feedback(CommandContext<CommandSourceStack> c, boolean ok, String okKey, String failKey) {
        String a = StringArgumentType.getString(c, "city"), b = StringArgumentType.getString(c, "target");
        if (ok) c.getSource().sendSuccess(() -> Component.translatable(okKey, a, b), true);
        else c.getSource().sendFailure(Component.translatable(failKey, a, b));
        return ok ? 1 : 0;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestCities(
            CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        ids.add("none");
        for (net.minecraft.server.level.ServerLevel level : c.getSource().getServer().getAllLevels()) {
            for (com.renanpontes.isekaimahou.core.api.Places.Place p : com.renanpontes.isekaimahou.core.api.Places.all(level)) {
                if ("city".equals(p.type()) || "capital".equals(p.type()) || "settlement".equals(p.type()) || "village".equals(p.type())) {
                    ids.add(p.id());
                }
            }
        }
        return SharedSuggestionProvider.suggest(ids, b);
    }

    private SocialCommands() {}
}
