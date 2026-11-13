package com.renanpontes.isekaimahou.feature.cities;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.cities.block.CityHeartBlockEntity;
import com.renanpontes.isekaimahou.feature.cities.entity.CityMerchant;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;
import com.renanpontes.isekaimahou.feature.cities.entity.Profession;
import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Comandos de op das cidades:
 * <pre>
 * /mahou city list                          cidades conhecidas (nome, id, posição, população, cercos)
 * /mahou city info                          reino, Pedra de Teleporte, altares e varredura da cidade onde você está
 * /mahou city spawnnpc &lt;tipo&gt; [profissão]   guard | citizen | merchant | lord
 * /mahou city siege [stop]                  começa/termina o Cerco na cidade onde você está
 * /mahou city wanted clear [jogadores]      tira o "Procurado" e as marcas de reincidência da guarda
 * /mahou city place capital|capital_sahra|capital_nordheim|city   gera uma capital/cidade AQUI
 * --- release 1.0 ---
 * /mahou city start info|find|here|tp|clear cidade inicial: estado / procura de novo a partir do nascimento original /
 *                                           procura a partir de onde você está / teleporta para lá / desfaz
 * /mahou city inn rest|rent &lt;dias&gt;|status|expire   estalagem sem estalajadeiro nem moedas (descanso, quarto, estado, vencer)
 * /mahou city quest list|start &lt;id&gt;|step|status|reset   sidequests: modelos, começar com o morador mais perto, pular passo...
 * /mahou city routine on|off|status          rotina dos moradores (taverna à noite, lojas fechadas de madrugada)
 * /mahou city fixtures [retry]               blocos de serviço da praça (Quadro do Mercado; altares nas capitais): onde
 *                                           estão / esquece os que sumiram e tenta pôr de novo agora
 * </pre>
 */
public final class CityCommands {
    private static final List<String> TYPES = List.of("guard", "citizen", "merchant", "lord");

    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("city")
                .then(Commands.literal("list").executes(CityCommands::list))
                .then(Commands.literal("info").executes(CityCommands::info))
                .then(Commands.literal("spawnnpc")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(TYPES, b))
                                .executes(c -> spawn(c, null))
                                .then(Commands.argument("profession", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(Profession.values()).map(p -> p.id), b))
                                        .executes(c -> spawn(c, StringArgumentType.getString(c, "profession"))))))
                .then(Commands.literal("siege")
                        .executes(c -> siege(c, false))
                        .then(Commands.literal("stop").executes(c -> siege(c, true))))
                .then(Commands.literal("wanted").then(Commands.literal("clear")
                        .executes(c -> clearWanted(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> clearWanted(c, EntityArgument.getPlayers(c, "targets"))))))
                .then(Commands.literal("place")
                        .then(Commands.literal("capital").executes(c -> place(c, "capital_city")))
                        .then(Commands.literal("capital_sahra").executes(c -> place(c, "capital_sahra")))
                        .then(Commands.literal("capital_nordheim").executes(c -> place(c, "capital_nordheim")))
                        .then(Commands.literal("city").executes(c -> place(c, "guild_city"))))
                // ---------------- release 1.0 ----------------
                .then(Commands.literal("start")
                        .then(Commands.literal("info").executes(CityCommands::startInfo))
                        .then(Commands.literal("find").executes(c -> startFind(c, false)))
                        .then(Commands.literal("here").executes(c -> startFind(c, true)))
                        .then(Commands.literal("tp").executes(c -> {
                            boolean ok = CityStart.teleport(c.getSource().getPlayerOrException());
                            return feedback(c, ok, ok ? "start.tp" : "start.none");
                        }))
                        .then(Commands.literal("clear").executes(c -> {
                            CityStart.clear(c.getSource().getServer());
                            return feedback(c, true, "start.cleared");
                        })))
                .then(Commands.literal("inn")
                        .then(Commands.literal("rest").executes(c -> {
                            CityInn.applyRest(c.getSource().getPlayerOrException());
                            return feedback(c, true, "inn.rested");
                        }))
                        .then(Commands.literal("rent").then(Commands.argument("days", IntegerArgumentType.integer(1, 30))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    CityInn.debugRent(p, IntegerArgumentType.getInteger(c, "days"));
                                    return innStatus(c, p);
                                })))
                        .then(Commands.literal("status").executes(c -> innStatus(c, c.getSource().getPlayerOrException())))
                        .then(Commands.literal("expire").executes(c -> {
                            CityInn.expire(c.getSource().getPlayerOrException(), false);
                            return feedback(c, true, "inn.expired");
                        })))
                .then(Commands.literal("quest")
                        .then(Commands.literal("list").executes(c -> {
                            c.getSource().sendSuccess(() -> Component.literal(String.join(", ", CityQuests.ids())), false);
                            return CityQuests.ids().size();
                        }))
                        .then(Commands.literal("start").then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(CityQuests.ids(), b))
                                .executes(CityCommands::questStart)))
                        .then(Commands.literal("step").executes(c -> {
                            boolean ok = CityQuests.debugStep(c.getSource().getPlayerOrException());
                            return feedback(c, ok, ok ? "quest.stepped" : "quest.no_active");
                        }))
                        .then(Commands.literal("status").executes(c -> {
                            Component st = CityQuests.debugStatus(c.getSource().getPlayerOrException());
                            if (st == null) return feedback(c, false, "quest.no_active");
                            c.getSource().sendSuccess(() -> st, false);
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(c -> {
                            CityQuests.debugReset(c.getSource().getPlayerOrException());
                            return feedback(c, true, "quest.reset");
                        })))
                .then(Commands.literal("fixtures")
                        .executes(c -> fixtures(c, false))
                        .then(Commands.literal("retry").executes(c -> fixtures(c, true))))
                .then(Commands.literal("routine")
                        .then(Commands.literal("on").executes(c -> routine(c, true)))
                        .then(Commands.literal("off").executes(c -> routine(c, false)))
                        .then(Commands.literal("status").executes(CityCommands::routineStatus)))));
    }

    // =====================================================================================
    //  Release 1.0: cidade inicial, estalagem, sidequests e rotina
    // =====================================================================================
    /** Resposta curta de um comando de teste ({@code command.isekai_mahou.cities.<key>}). */
    private static int feedback(CommandContext<CommandSourceStack> c, boolean ok, String key) {
        Component msg = Component.translatable("command.isekai_mahou.cities." + key);
        if (ok) c.getSource().sendSuccess(() -> msg, false);
        else c.getSource().sendFailure(msg);
        return ok ? 1 : 0;
    }

    private static int startInfo(CommandContext<CommandSourceStack> c) {
        CityWorld w = CityWorld.get(c.getSource().getServer());
        BlockPos spawn = c.getSource().getServer().overworld().getSharedSpawnPos();
        String state = switch (w.startState) {
            case CityWorld.START_SET -> "set";
            case CityWorld.START_NONE -> "not_found";
            case CityWorld.START_OFF -> "off";
            default -> "undecided";
        };
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.start.info",
                Component.translatable("command.isekai_mahou.cities.start.state." + state),
                spawn.getX(), spawn.getY(), spawn.getZ(),
                w.originalSpawn == null ? "-" : w.originalSpawn.getX() + " " + w.originalSpawn.getY() + " " + w.originalSpawn.getZ()), false);
        return w.startState == CityWorld.START_SET ? 1 : 0;
    }

    /** Procura de novo a cidade inicial: a partir do nascimento original ou ({@code here}) de onde o comando foi dado. */
    private static int startFind(CommandContext<CommandSourceStack> c, boolean here) {
        CityWorld w = CityWorld.get(c.getSource().getServer());
        BlockPos from = here ? BlockPos.containing(c.getSource().getPosition())
                : w.originalSpawn != null ? w.originalSpawn : c.getSource().getServer().overworld().getSharedSpawnPos();
        boolean ok = CityStart.choose(c.getSource().getServer(), from);
        if (!ok) return feedback(c, false, "start.not_found");
        return startInfo(c);
    }

    private static int innStatus(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        Optional<BlockPos> room = CityInn.room(p);
        if (room.isEmpty()) return feedback(c, false, "inn.no_room");
        BlockPos b = room.get();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.inn.status", b.getX(), b.getY(), b.getZ(),
                CityInn.daysLeft(p)), false);
        return 1;
    }

    /** Começa o pedido com o morador mais perto (até 24 blocos) como quem pediu, sem checar se ele ofereceria. */
    private static int questStart(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CityQuests.Quest q = CityQuests.byId(StringArgumentType.getString(c, "id"));
        if (q == null) return feedback(c, false, "quest.bad_id");
        CityNpc best = null;
        for (CityNpc n : p.serverLevel().getEntitiesOfClass(CityNpc.class, p.getBoundingBox().inflate(24), CityNpc::isAlive)) {
            if (best == null || n.distanceToSqr(p) < best.distanceToSqr(p)) best = n;
        }
        if (best == null) return feedback(c, false, "quest.no_npc");
        CityQuests.debugReset(p);
        CityQuests.start(p, best, q);
        CityNpc giver = best;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.quest.started",
                Component.translatable(CityQuests.KEY + q.id() + ".title"), giver.getDisplayName()), false);
        return 1;
    }

    /** Blocos de serviço da praça da cidade onde o jogador está ({@link CityFixtures}). */
    private static int fixtures(CommandContext<CommandSourceStack> c, boolean retry) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel sl = p.serverLevel();
        CityHeartBlockEntity heart = heartAt(sl, p.blockPosition());
        if (heart == null) return feedback(c, false, "not_in_city");
        if (retry) {
            int placed = CityFixtures.retry(sl, heart);
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.fixtures.retried", placed), true);
        }
        int found = 0;
        for (CityFixtures.Kind kind : CityFixtures.Kind.values()) {
            // Nome do bloco pelo registro (módulo ausente: mostra o id)
            Component name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(IsekaiMahou.id(kind.blockId))
                    .<Component>map(net.minecraft.world.level.block.Block::getName).orElse(Component.literal(kind.blockId));
            BlockPos at = heart.fixturePos(kind);
            MutableComponent line;
            if (at != null) {
                boolean gone = sl.isLoaded(at) && !CityFixtures.present(sl, kind, at);
                line = Component.translatable(gone ? "command.isekai_mahou.cities.fixtures.gone" : "command.isekai_mahou.cities.fixtures.at",
                        name, at.getX(), at.getY(), at.getZ());
                String tp = "/tp @s " + at.getX() + " " + at.getY() + " " + (at.getZ() + 2);
                line.withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tp))));
                if (!gone) found++;
            } else if (!heart.fixtureDone(kind)) {
                line = Component.translatable("command.isekai_mahou.cities.fixtures.pending", name);
            } else {
                line = Component.translatable(kind.capitalOnly && !heart.capital() ? "command.isekai_mahou.cities.fixtures.capital_only"
                        : "command.isekai_mahou.cities.fixtures.no_room", name);
            }
            c.getSource().sendSuccess(() -> line, false);
        }
        return found;
    }

    private static int routine(CommandContext<CommandSourceStack> c, boolean on) {
        CityRoutine.setEnabled(c.getSource().getServer(), on);
        return routineStatus(c);
    }

    private static int routineStatus(CommandContext<CommandSourceStack> c) {
        ServerLevel sl = c.getSource().getLevel();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.routine.status",
                Component.translatable(CityRoutine.enabled() ? "command.isekai_mahou.cities.routine.on" : "command.isekai_mahou.cities.routine.off"),
                CityRoutine.phase(sl).name(), sl.getDayTime() % 24000L), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        ServerLevel sl = c.getSource().getLevel();
        CityData data = CityData.get(sl);
        int n = 0;
        for (Places.Place p : Places.all(sl)) {
            if (!p.type().equals("city") && !p.type().equals("capital")) continue;
            n++;
            CityData.City city = data.existing(p.id());
            int pop = city == null ? 0 : city.total();
            int dead = city == null ? 0 : city.dead.size();
            int sieges = city == null ? 0 : city.sieges;
            int won = city == null ? 0 : city.siegesWon;
            BlockPos ct = p.center();
            MutableComponent line = Component.translatable("command.isekai_mahou.cities.list_line", p.name().copy().withColor(p.color()),
                    p.id(), ct.getX(), ct.getY(), ct.getZ(), pop, dead, won, sieges);
            String tp = "/tp @s " + ct.getX() + " " + (ct.getY() + 2) + " " + (ct.getZ() + 3);
            line.withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tp))));
            c.getSource().sendSuccess(() -> line, false);
        }
        int total = n;
        if (total == 0) c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.list_empty"), false);
        return total;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, String profession) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel sl = p.serverLevel();
        String type = StringArgumentType.getString(c, "type");
        EntityType<? extends CityNpc> et = switch (type) {
            case "guard" -> CitiesContent.CITY_GUARD.get();
            case "merchant" -> CitiesContent.CITY_MERCHANT.get();
            case "lord" -> CitiesContent.CITY_LORD.get();
            case "citizen" -> CitiesContent.CITIZEN.get();
            default -> null;
        };
        if (et == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.cities.bad_type", String.join(", ", TYPES)));
            return 0;
        }
        CityNpc npc = et.create(sl);
        if (npc == null) return 0;
        if (npc instanceof CityMerchant m && profession != null) {
            Profession prof = Profession.byId(profession);
            if (prof == null) {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.cities.bad_profession"));
                return 0;
            }
            m.setProfession(prof);
        }
        npc.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot() + 180.0F, 0.0F);
        npc.finalizeSpawn(sl, sl.getCurrentDifficultyAt(npc.blockPosition()), MobSpawnType.COMMAND, null);
        sl.addFreshEntity(npc);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.spawned", npc.getDisplayName()), true);
        return 1;
    }

    private static int siege(CommandContext<CommandSourceStack> c, boolean stop) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel sl = p.serverLevel();
        CityHeartBlockEntity heart = heartAt(sl, p.blockPosition());
        if (heart == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.cities.not_in_city"));
            return 0;
        }
        if (stop) {
            CitySiege.stop(sl, heart.placeId(), true);
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.siege_stopped"), true);
            return 1;
        }
        if (!CitySiege.start(sl, heart)) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.cities.siege_running"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.siege_started"), true);
        return 1;
    }

    /** Reino, pedra, altares e varredura da cidade onde o jogador está. */
    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel sl = p.serverLevel();
        CityHeartBlockEntity heart = heartAt(sl, p.blockPosition());
        if (heart == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.cities.not_in_city"));
            return 0;
        }
        CityKingdom k = heart.kingdom();
        Reputation.Faction f = Reputation.faction(k.factionId);
        Component kingdom = f != null ? f.coloredName() : CityRealm.shortName(k);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.info.head",
                heart.cityName(sl).withColor(CityRealm.placeColor(k, heart.capital())), kingdom, heart.placeId()), false);
        BlockPos st = heart.stone();
        c.getSource().sendSuccess(() -> st == null ? Component.translatable("command.isekai_mahou.cities.info.no_stone")
                : Component.translatable("command.isekai_mahou.cities.info.stone", st.getX(), st.getY(), st.getZ()), false);
        if (heart.altars().isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.info.no_altar"), false);
        }
        for (BlockPos a : heart.altars()) {
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.info.altar", a.getX(), a.getY(), a.getZ()), false);
        }
        c.getSource().sendSuccess(() -> Component.translatable(heart.sweepDone() ? "command.isekai_mahou.cities.info.swept"
                : "command.isekai_mahou.cities.info.sweeping", heart.purgedTotal()), false);
        List<String> wanted = CityCrimes.wantedIn(p);
        if (!wanted.isEmpty()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < wanted.size(); i++) {
                if (i > 0) names.append(", ");
                CityKingdom wk = CityRealm.byFaction(wanted.get(i));
                names.append(wk != null ? CityRealm.shortName(wk).withColor(CityRealm.color(wk)) : Component.literal(wanted.get(i)));
            }
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.info.wanted", names), false);
        }
        return 1;
    }

    /** Coração da cidade onde a posição está (o centro do lugar é o próprio coração). */
    public static CityHeartBlockEntity heartAt(ServerLevel sl, BlockPos pos) {
        Places.Place place = CityPlaces.cityAt(sl, pos);
        // Coração num chunk descarregado (cidade enorme): não força o carregamento do chunk só para perguntar.
        if (place == null || !sl.isLoaded(place.center())) return null;
        return sl.getBlockEntity(place.center()) instanceof CityHeartBlockEntity h ? h : null;
    }

    private static int clearWanted(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
        for (ServerPlayer p : targets) {
            CityCrimes.clearAllWanted(p);
            CityConfront.clearStrikes(p);
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.wanted_cleared", targets.size()), true);
        return targets.size();
    }

    /** Gera a estrutura da cidade na posição do jogador (como o /place structure, carregando os chunks). */
    private static int place(CommandContext<CommandSourceStack> c, String structure) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel sl = p.serverLevel();
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, IsekaiMahou.id(structure));
        Optional<Holder.Reference<Structure>> holder = sl.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(key);
        if (holder.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.cities.no_structure", key.location().toString()));
            return 0;
        }
        ChunkGenerator gen = sl.getChunkSource().getGenerator();
        BlockPos pos = p.blockPosition();
        StructureStart start = holder.get().value().generate(sl.registryAccess(), gen, gen.getBiomeSource(),
                sl.getChunkSource().randomState(), sl.getStructureManager(), sl.getSeed(), new ChunkPos(pos), 0, sl, b -> true);
        if (!start.isValid()) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.cities.place_failed"));
            return 0;
        }
        BoundingBox bb = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(bb.minX()), SectionPos.blockToSectionCoord(bb.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(bb.maxX()), SectionPos.blockToSectionCoord(bb.maxZ()));
        // Carrega (ou gera) os chunks da área antes de colocar as peças.
        ChunkPos.rangeClosed(min, max).forEach(cp -> sl.getChunk(cp.x, cp.z));
        ChunkPos.rangeClosed(min, max).forEach(cp -> start.placeInChunk(sl, sl.structureManager(), gen, sl.getRandom(),
                new BoundingBox(cp.getMinBlockX(), sl.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX(), sl.getMaxBuildHeight(),
                        cp.getMaxBlockZ()), cp));
        int cx = (bb.minX() + bb.maxX()) / 2;
        int cz = (bb.minZ() + bb.maxZ()) / 2;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.cities.placed",
                Component.translatable(switch (structure) {
                    case "capital_city" -> "place.isekai_mahou.cities.capital";
                    case "capital_sahra" -> "place.isekai_mahou.cities.capital.sahra";
                    case "capital_nordheim" -> "place.isekai_mahou.cities.capital.nordheim";
                    default -> "command.isekai_mahou.cities.a_city";
                }),
                cx, cz).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private CityCommands() {}
}
