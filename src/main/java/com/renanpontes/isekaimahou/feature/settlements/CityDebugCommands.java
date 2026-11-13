package com.renanpontes.isekaimahou.feature.settlements;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de op do gerenciamento de cidades (v1.4) — juntam-se ao {@code /mahou city} do módulo cities:
 * <pre>
 * /mahou city govern               você passa a governar a cidade mais perto (fundada, conquistada ou de reino)
 * /mahou city food &lt;n&gt;             estoque de comida      /mahou city fuel &lt;n&gt;   combustível
 * /mahou city people &lt;n&gt;           habitantes             /mahou city approval &lt;0-100&gt;   apoio da população
 * /mahou city day                  roda a conta diária agora (necessidades, impostos, população, ataques)
 * /mahou city finish               termina na hora todas as obras
 * /mahou city level &lt;1-5&gt;          nível do centro (porte da cidade)
 * /mahou city blevel &lt;1-5&gt;         nível do prédio do terreno onde você está
 * /mahou city radius &lt;2-12&gt;        tamanho da cidade fundada em anéis (2 = 5×5, 3 = 7×7...)
 * /mahou city raid [now|stop]      começa um ataque (aviso de 2 min, ou já) / encerra
 * /mahou city raids on|off         liga/desliga os ataques de monstros no mundo
 * /mahou city requests             sorteia novos Pedidos do Povo
 * /mahou city builders             (v1.0) chama já todos os construtores que cabem na cidade
 * /mahou city decor on|off|clear   (v1.0) enfeites de festa: liga, desliga aos poucos, tira tudo na hora
 * /mahou city terrain              (v1.0) altura do lote onde você está, a de referência e o desnível com o vizinho de dentro
 * /mahou city happy                (v1.0) felicidade: os motivos de hoje, um por linha, e os requisitos de expansão
 * /mahou city festival [end]       (v1.0) começa um festival de 2 dias agora (sem custo nem espera) / encerra
 * /mahou city caravan              (v1.0) todas as caravanas em viagem voltam agora
 * /mahou city goals [new]          (v1.0) cumpre os pedidos dos moradores atuais / sorteia outros
 * /mahou city stash                (v1.0) roda a produção diária dos prédios (mercadorias no armazém)
 * /mahou city free &lt;prédio&gt; [0-3]   (v1.0 onda 4) põe na fila o prédio em POSIÇÃO LIVRE, centrado onde você está (0-3 = para onde vira)
 * /mahou city rotate               (v1.0 onda 4) gira 90° o prédio do lote onde você está (mudança de lugar, sem cobrar)
 * /mahou city plots                (v1.0 onda 4) lista os prédios em posição livre da cidade
 * /mahou city tier                 (onda 5) degrau da cidade, andamento da reforma, requisitos e ganhos do próximo
 * /mahou city tier up | &lt;1-5&gt;      (onda 5) sobe um degrau / põe no degrau, sem requisitos: cerimônia + reforma
 * /mahou city renew [now]          (onda 5) recomeça a reforma do degrau aos poucos / faz a cidade inteira na hora
 * /mahou city magic hasten|erect|shape [n]  (v1.0 onda 4) roda a magia de obra onde você está, sem montar a magia
 *                                  (mesmas regras e custo de mana; n = Amplificar/Enfraquecer, -4 a 4)
 * /mahou city days &lt;n&gt;              (onda 6) passam n dias para as OBRAS da cidade (sem mexer no relógio do mundo):
 *                                  a fila anda, o resumo da manhã sai e o gancho de "novo dia" dispara
 * /mahou city pace                 (onda 6) ritmo do servidor, velocidade da cidade e cada obra: dia X de Y, etapa, quanto falta
 * /mahou city pace &lt;0.05-10&gt;       (onda 6) muda a opção "ritmo da cidade" (multiplicador da duração) e grava na config
 * /mahou city sky                  (onda 7) Ilha Celeste da cidade mais perto: altitude, terra pronta, geração (anel, %)
 * /mahou city sky found [estilo]   (onda 7) funda uma Ilha Celeste acima de onde você está (dá a Carta e o Núcleo se faltarem)
 * /mahou city sky finish           (onda 7) termina a terra da ilha agora (só em chunks carregados)
 * /mahou city sky grow             (onda 7) força o crescimento: um anel a mais, sem requisitos — a ilha ganha a terra nova aos poucos
 * /mahou city sky regen            (onda 7) refaz a passada do raio atual desde o coração (completa colunas que faltam)
 * /mahou city sky tp               (onda 7) leva você para a praça da ilha
 * /mahou city sky pier             (onda 7) refaz o Cais Celeste e o marco no chão agora e mostra onde fica a Corrente Ascendente
 * /mahou city sky down             (onda 7) leva você ao marco no chão, ao lado da Corrente Ascendente
 * /mahou city sky drop             (onda 7) derruba da ilha o NPC da cidade mais perto (ele volta para casa em até 5 s)
 * /mahou city water [found [estilo]|finish|redo|grow|tp|drop]   (onda 8) Cidade das Águas — veja {@link WaterCommands}
 * /mahou city pause [n|all]        (onda 7) pausa a primeira obra andando / a nº n / todas — ver {@link CityPauseCommands}
 * /mahou city resume [n|all]       (onda 7) retoma a primeira obra pausada / a nº n / todas
 * /mahou city cancel [n]           (onda 7) lista a fila (o que dá para cancelar) / desiste da obra nº n em qualquer etapa
 * (também valem: dormir e /time add 24000 — as obras seguem o relógio do Overworld)
 * </pre>
 */
public final class CityDebugCommands {
    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("city")
                .then(Commands.literal("govern").executes(CityDebugCommands::govern))
                .then(Commands.literal("food").then(Commands.argument("n", IntegerArgumentType.integer(0, 99999)).executes(c -> set(c, s -> {
                    s.food = IntegerArgumentType.getInteger(c, "n");
                }))))
                .then(Commands.literal("fuel").then(Commands.argument("n", IntegerArgumentType.integer(0, 99999)).executes(c -> set(c, s -> {
                    s.fuel = IntegerArgumentType.getInteger(c, "n");
                }))))
                .then(Commands.literal("people").then(Commands.argument("n", IntegerArgumentType.integer(2, 999)).executes(c -> set(c, s -> {
                    s.people = IntegerArgumentType.getInteger(c, "n");
                }))))
                .then(Commands.literal("approval").then(Commands.argument("n", IntegerArgumentType.integer(0, 100)).executes(c -> set(c, s -> {
                    s.affinity = IntegerArgumentType.getInteger(c, "n");
                }))))
                .then(Commands.literal("day").executes(c -> set(c, s -> {
                    s.lastDay = SettlementService.day(c.getSource().getServer());
                    Lordship.daily(c.getSource().getServer(), s);
                    CityPace.dawn(c.getSource().getServer(), s);
                })))
                // onda 6 (city_pace): pular dias de obra e ver/mudar o ritmo
                .then(Commands.literal("days").then(Commands.argument("n", IntegerArgumentType.integer(1, 40))
                        .executes(c -> days(c, IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("pace").executes(CityDebugCommands::paceInfo)
                        .then(Commands.argument("mult", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.05, 10.0)).executes(c -> {
                            double v = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "mult");
                            if (!CityPaceConfig.setPace(v)) {
                                c.getSource().sendFailure(SettlementsLang.tr("cmd.pace.fail"));
                                return 0;
                            }
                            c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.pace.set", String.format(java.util.Locale.ROOT, "%.2f", CityPace.paceMult())), true);
                            return 1;
                        })))
                .then(Commands.literal("finish").executes(c -> {
                    Settlement s = nearest(c);
                    if (s == null) return 0;
                    ServerLevel sl = SettlementService.level(c.getSource().getServer(), s);
                    int n = sl == null ? 0 : ConstructionEngine.finishAll(sl, s);
                    c.getSource().sendSuccess(() -> Component.translatable("settlements.isekai_mahou.cmd.finished", n), true);
                    return n;
                }))
                // onda 7 (city_pause): pausar, retomar e desistir de obras — /mahou city pause|resume [n|all], cancel <n>
                .then(CityPauseCommands.pause()).then(CityPauseCommands.resume()).then(CityPauseCommands.cancel())
                // onda 8 (city_looks): vitrine dos níveis lado a lado e o visual antigo/novo dos prédios
                .then(CityLooks.showcase()).then(CityLooks.looks())
                .then(Commands.literal("level").then(Commands.argument("n", IntegerArgumentType.integer(1, Blueprints.MAX_LEVEL)).executes(c -> set(c, s -> {
                    s.level = IntegerArgumentType.getInteger(c, "n");
                    s.points = Math.max(s.points, Blueprints.pointsFor(s.style, s.level));
                }))))
                .then(Commands.literal("radius").then(Commands.argument("n", IntegerArgumentType.integer(CityLayout.START_RADIUS, CityLayout.MAX_RADIUS)).executes(c -> set(c, s -> {
                    s.radius = IntegerArgumentType.getInteger(c, "n");
                    ServerLevel sl = SettlementService.level(c.getSource().getServer(), s);
                    if (sl != null) SettlementService.registerPlace(sl, s);
                }))))
                .then(Commands.literal("blevel").then(Commands.argument("n", IntegerArgumentType.integer(1, Settlement.MAX_BUILDING_LEVEL)).executes(c -> {
                    Settlement s = nearest(c);
                    if (s == null) return 0;
                    int cell = cellAt(c.getSource().getPlayerOrException(), s);
                    if (cell < 0 || !s.buildings.containsKey(cell) || cell == CityLayout.CENTER_CELL) {
                        c.getSource().sendFailure(SettlementsLang.tr("cmd.no_building"));
                        return 0;
                    }
                    s.bLevels.put(cell, IntegerArgumentType.getInteger(c, "n"));
                    return ok(c, s);
                })))
                .then(Commands.literal("raid").executes(c -> raid(c, false))
                        .then(Commands.literal("now").executes(c -> raid(c, true)))
                        .then(Commands.literal("stop").executes(c -> {
                            Settlement s = nearest(c);
                            ServerLevel sl = s == null ? null : SettlementService.level(c.getSource().getServer(), s);
                            if (sl != null) CityRaids.stop(sl, s);
                            return 1;
                        })))
                .then(Commands.literal("raids")
                        .then(Commands.literal("on").executes(c -> raids(c, true)))
                        .then(Commands.literal("off").executes(c -> raids(c, false))))
                .then(Commands.literal("requests").executes(c -> set(c, s -> CityEconomy.refreshRequests(c.getSource().getServer(), s, true))))
                .then(Commands.literal("builders").executes(CityDebugCommands::builders))
                // onda 8 (teste): põe a próxima muralha na fila, como o botão do painel (mesmas regras)
                .then(Commands.literal("wall").executes(c -> {
                    Settlement s = nearest(c);
                    if (s == null) return 0;
                    boolean queued = SettlementService.queueWall(c.getSource().getPlayerOrException(), s, SettlementService.nextWall(s));
                    c.getSource().sendSuccess(() -> Component.literal(queued ? "wall queued" : "wall NOT queued (see action bar)"), false);
                    return queued ? 1 : 0;
                }))
                .then(Commands.literal("decor")
                        .then(Commands.literal("on").executes(c -> decor(c, 1)))
                        .then(Commands.literal("off").executes(c -> decor(c, 0)))
                        .then(Commands.literal("clear").executes(c -> decor(c, -1))))
                .then(Commands.literal("terrain").executes(CityDebugCommands::terrain))
                .then(Commands.literal("happy").executes(CityDebugCommands::happy))
                .then(Commands.literal("festival").executes(c -> set(c, s -> {
                    long today = SettlementService.day(c.getSource().getServer());
                    s.festivalDay = today;
                    s.festivals++;
                    CityDecor.setUntil(s, today + Lordship.FESTIVAL_DAYS - 1);
                })).then(Commands.literal("end").executes(c -> set(c, s -> {
                    s.festivalDay = -1;
                    CityDecor.set(s, false);
                }))))
                .then(Commands.literal("caravan").executes(c -> {
                    Settlement s = nearest(c);
                    if (s == null) return 0;
                    int n = CityCaravans.finishAll(c.getSource().getServer(), s);
                    c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.caravans", n), true);
                    return ok(c, s);
                }))
                .then(Commands.literal("goals").executes(c -> set(c, CityGoals::forceComplete))
                        .then(Commands.literal("new").executes(c -> set(c, s -> {
                            s.goals.clear();
                            CityGoals.refill(c.getSource().getServer(), s);
                        }))))
                .then(Commands.literal("free").then(Commands.argument("key", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(c -> free(c, 0))
                        .then(Commands.argument("rot", IntegerArgumentType.integer(0, 3)).executes(c -> free(c, IntegerArgumentType.getInteger(c, "rot")))
                                // onda 7 (teste): altura escolhida (−4..+4 da praça), como o seletor do painel
                                .then(Commands.argument("height", IntegerArgumentType.integer(-4, 4)).executes(c -> free(c,
                                        IntegerArgumentType.getInteger(c, "rot"), IntegerArgumentType.getInteger(c, "height")))))))
                .then(Commands.literal("rotate").executes(CityDebugCommands::rotate))
                .then(Commands.literal("magic")
                        .then(magic("hasten", com.renanpontes.isekaimahou.feature.settlements.magic.CityMagic.HASTEN))
                        .then(magic("erect", com.renanpontes.isekaimahou.feature.settlements.magic.CityMagic.ERECT))
                        .then(magic("shape", com.renanpontes.isekaimahou.feature.settlements.magic.CityMagic.SHAPE)))
                .then(Commands.literal("plots").executes(CityDebugCommands::plots))
                // onda 7 (sky_island): Ilha Celeste — estado, fundar, terminar a terra, crescer, refazer, subir
                .then(Commands.literal("sky").executes(CityDebugCommands::skyInfo)
                        .then(Commands.literal("found").executes(c -> skyFound(c, SettlementStyle.KINGDOM))
                                .then(Commands.argument("style", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (SettlementStyle st : SettlementStyle.values()) b.suggest(st.id);
                                            return b.buildFuture();
                                        })
                                        .executes(c -> {
                                            SettlementStyle st = SettlementStyle.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "style"));
                                            return skyFound(c, st == null ? SettlementStyle.KINGDOM : st);
                                        })))
                        .then(Commands.literal("finish").executes(c -> sky(c, 0)))
                        .then(Commands.literal("grow").executes(c -> sky(c, 1)))
                        .then(Commands.literal("regen").executes(c -> sky(c, 2)))
                        // onda 7 (sky_access): cais e Corrente Ascendente — refazer/ver, ir ao marco no chão, derrubar um NPC
                        .then(Commands.literal("pier").executes(c -> skyAccess(c, 0)))
                        .then(Commands.literal("down").executes(c -> skyAccess(c, 1)))
                        .then(Commands.literal("drop").executes(c -> skyAccess(c, 2)))
                        // onda 7 (sky_ui): enfeites da ilha — estado, construir já o que falta, tirar e refazer
                        .then(Commands.literal("decor").executes(c -> skyDecor(c, 0))
                                .then(Commands.literal("now").executes(c -> skyDecor(c, 1)))
                                .then(Commands.literal("redo").executes(c -> skyDecor(c, 2))))
                        .then(Commands.literal("tp").executes(c -> sky(c, 3)))
                        // onda 9 (isles): cria já a ilha do terreno (i, j) do arquipélago, sem obra (e as ilhotas até a praça)
                        .then(Commands.literal("isle")
                                .then(Commands.argument("i", IntegerArgumentType.integer(-CityLayout.MAX_RADIUS, CityLayout.MAX_RADIUS))
                                        .then(Commands.argument("j", IntegerArgumentType.integer(-CityLayout.MAX_RADIUS, CityLayout.MAX_RADIUS))
                                                .executes(c -> skyIsle(c, IntegerArgumentType.getInteger(c, "i"),
                                                        IntegerArgumentType.getInteger(c, "j")))))))
                // onda 8 (water_city): Cidade das Águas — estado, fundar na água, terminar/refazer passarelas e cais, crescer, praça
                .then(WaterCommands.node())
                .then(Commands.literal("tier").executes(CityDebugCommands::tierInfo)
                        .then(Commands.literal("up").executes(c -> tierSet(c, 0)))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, CityTier.MAX))
                                .executes(c -> tierSet(c, IntegerArgumentType.getInteger(c, "n")))))
                // onda 5 (city_styles): /mahou city style → lista; /mahou city style <id> → Reforma na hora, sem custo
                // teste: o Senhor da cidade mais próxima deixa o poder (igual ao botão do painel) — libera fundar outra
                .then(Commands.literal("abandon").executes(c -> {
                    Settlement s = nearest(c);
                    // sem Senhor não há o que abandonar (chamar de novo devolveria o poder ao único cidadão)
                    if (s == null || !s.own() || s.owner == null) {
                        c.getSource().sendFailure(SettlementsLang.tr("cmd.founded_only"));
                        return 0;
                    }
                    SettlementService.stepDown(c.getSource().getServer(), s, true);
                    return ok(c, s);
                }))
                .then(Commands.literal("style").executes(CityDebugCommands::styleInfo)
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (SettlementStyle st : SettlementStyle.values()) b.suggest(st.id);
                                    return b.buildFuture();
                                })
                                .executes(CityDebugCommands::styleSet)))
                .then(Commands.literal("renew").executes(c -> set(c, CityRenewal::restart))
                        .then(Commands.literal("now").executes(CityDebugCommands::renewNow)))
                .then(Commands.literal("stash").executes(c -> set(c, s -> CityStash.dailyProduction(c.getSource().getServer(), s,
                        CityEconomy.stats(c.getSource().getServer(), s)))))));
    }

    /**
     * Onda 7 (sky_access): {@code mode} 0 = refaz o Cais Celeste e o marco agora e mostra o estado; 1 = leva você ao
     * marco no chão (debaixo da Corrente Ascendente); 2 = derruba da ilha o NPC da cidade mais perto de você (ele tem de
     * voltar para casa sozinho em até 5 s).
     */
    private static int skyAccess(CommandContext<CommandSourceStack> c, int mode) throws CommandSyntaxException {
        Settlement s = nearest(c);
        ServerLevel sl = s == null ? null : SettlementService.level(c.getSource().getServer(), s);
        if (s == null || sl == null || !s.sky) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.sky.only"));
            return 0;
        }
        return SkyAccess.debug(c.getSource(), sl, s, mode);
    }

    /**
     * Onda 7 (sky_ui): enfeites da Ilha Celeste (cachoeiras, ilhotas, cristais, raízes). {@code mode} 0 = estado;
     * 1 = constrói já o que falta (chunks carregados); 2 = tira tudo o que é dos enfeites e refaz.
     */
    private static int skyDecor(CommandContext<CommandSourceStack> c, int mode) throws CommandSyntaxException {
        Settlement s = nearest(c);
        ServerLevel sl = s == null ? null : SettlementService.level(c.getSource().getServer(), s);
        if (s == null || sl == null || !s.sky) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.sky.only"));
            return 0;
        }
        Component line = mode == 2 ? SkyDecor.redo(sl, s) : SkyDecor.debug(sl, s, mode == 1);
        c.getSource().sendSuccess(() -> line, false);
        return 1;
    }

    /**
     * Onda 9 (isles): {@code /mahou city sky isle <i> <j>} — cria a ilha do terreno (i, j) do arquipélago na hora, sem
     * obra nenhuma (as ilhotas de passagem até a praça e as pontes vêm juntas). Só cidade do céu de ilhas.
     */
    private static int skyIsle(CommandContext<CommandSourceStack> c, int i, int j) throws CommandSyntaxException {
        Settlement s = nearest(c);
        ServerLevel sl = s == null ? null : SettlementService.level(c.getSource().getServer(), s);
        if (s == null || sl == null || !s.sky || !s.isles) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.isle.only"));
            return 0;
        }
        if (i == 0 && j == 0) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.isle.center"));
            return 0;
        }
        boolean made = SkyIsles.force(sl, s, i, j);
        c.getSource().sendSuccess(() -> SettlementsLang.tr(made ? "cmd.isle.forced" : "cmd.isle.exists", i, j), true);
        Component line = SkyCity.debug(c.getSource().getServer(), s);
        c.getSource().sendSuccess(() -> line, false);
        SettlementService.refreshViewers(c.getSource().getServer(), s);
        return made ? 1 : 0;
    }

    /** Onda 7 (sky_island): estado da Ilha Celeste da cidade mais perto (altitude, terra pronta, geração em andamento). */
    private static int skyInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null) return 0;
        Component line = SkyCity.debug(c.getSource().getServer(), s);
        c.getSource().sendSuccess(() -> line, false);
        return s.sky ? 1 : 0;
    }

    /** Onda 7: funda uma Ilha Celeste acima de onde você está, sem a tela (dá a Carta e o Núcleo Celeste se faltarem). */
    private static int skyFound(CommandContext<CommandSourceStack> c, SettlementStyle style) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        // onda 9: inventário cheio / lugar ruim aparecem no chat
        return SettlementCommands.report(c, SettlementService.giveFoundingItems(p, true), p, p.blockPosition(), style, SkyCity.SKYLAND);
    }

    /**
     * Onda 7: {@code mode} 0 = termina a terra da ilha agora; 1 = força o crescimento (um anel a mais, sem requisitos);
     * 2 = refaz a passada do raio atual desde o coração; 3 = leva você para a praça da ilha.
     */
    private static int sky(CommandContext<CommandSourceStack> c, int mode) throws CommandSyntaxException {
        Settlement s = nearest(c);
        ServerLevel sl = s == null ? null : SettlementService.level(c.getSource().getServer(), s);
        if (s == null || sl == null || !s.sky) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.sky.only"));
            return 0;
        }
        var server = c.getSource().getServer();
        switch (mode) {
            case 0 -> {
                int n = SkyCity.finishNow(sl, s);
                c.getSource().sendSuccess(() -> SettlementsLang.tr(SkyCity.generating(s) ? "cmd.sky.partial" : "cmd.sky.finished", n), true);
            }
            case 1 -> {
                if (s.radius >= CityLayout.MAX_RADIUS) {
                    c.getSource().sendFailure(SettlementsLang.tr("expand.max", 2 * s.radius + 1));
                    return 0;
                }
                s.radius++;
                SettlementService.registerPlace(sl, s);
                SkyCity.radiusChanged(sl, s);
            }
            case 2 -> SkyCity.regenerate(sl, s);
            default -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                if (!SkyCity.hasLand(s, s.heart.getX() + 2, s.heart.getZ() + 2)) {
                    c.getSource().sendFailure(SettlementsLang.tr("cmd.sky.no_land"));
                    return 0;
                }
                p.teleportTo(sl, s.heart.getX() + 2.5, sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        s.heart.getX() + 2, s.heart.getZ() + 2), s.heart.getZ() + 2.5, p.getYRot(), p.getXRot());
                p.resetFallDistance();
            }
        }
        Component line = SkyCity.debug(server, s);
        c.getSource().sendSuccess(() -> line, false);
        SettlementService.refreshViewers(server, s);
        return 1;
    }

    /** Onda 6 (city_pace): passam {@code n} dias para as obras da cidade (o relógio do mundo não muda). */
    private static int days(CommandContext<CommandSourceStack> c, int n) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null || !s.own()) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.founded_only"));
            return 0;
        }
        var server = c.getSource().getServer();
        CityPace.advance(server, s, n, false);
        ServerLevel sl = SettlementService.level(server, s);
        if (sl != null && sl.hasChunk(s.heart.getX() >> 4, s.heart.getZ() >> 4)) CityPace.fireNewDay(sl, s, SettlementService.day(server), n);
        CityPace.dawn(server, s);
        c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.days", n), true);
        for (Component line : CityPace.debug(server, s)) c.getSource().sendSuccess(() -> line, false);
        SettlementService.refreshViewers(server, s);
        return n;
    }

    /** Onda 6 (city_pace): ritmo do servidor, velocidade da cidade e a fila de obras em dias. */
    private static int paceInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null) return 0;
        for (Component line : CityPace.debug(c.getSource().getServer(), s)) c.getSource().sendSuccess(() -> line, false);
        return 1;
    }

    /** Onda 5 (city_styles): estilo da cidade, custo da Reforma e os estilos que existem. */
    private static int styleInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null) return 0;
        c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.style.now", s.style.displayName(), CityReform.cost(s), CityReform.pending(s)), false);
        for (SettlementStyle st : SettlementStyle.values()) {
            c.getSource().sendSuccess(() -> Component.literal(" " + st.id + " — ").append(st.displayName()).append(": ")
                    .append(st.bonus()), false);
        }
        return 1;
    }

    private static int styleSet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Settlement s = nearest(c);
        ServerLevel sl = s == null ? null : SettlementService.level(c.getSource().getServer(), s);
        SettlementStyle st = SettlementStyle.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id"));
        if (s == null || sl == null || !s.own() || st == null) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.founded_only"));
            return 0;
        }
        CityReform.apply(sl, s, st, true);
        return ok(c, s);
    }

    /** Onda 5: degrau da cidade, andamento da reforma, requisitos do próximo degrau e o que ele libera. */
    private static int tierInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null) return 0;
        var server = c.getSource().getServer();
        int pct = CityRenewal.percent(s);
        c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.tier", SettlementService.cityName(server, s), CityTier.name(s.level), s.level,
                pct < 0 ? "-" : pct + "%", s.look), false);
        for (CityEconomy.Req r : CityEconomy.centerReqs(server, s)) {
            c.getSource().sendSuccess(() -> Component.literal(r.ok() ? "  \u2714 " : "  \u2718 ").append(r.text()), false);
        }
        if (s.own() && s.level < CityTier.MAX) {
            for (Component g : CityTier.gains(s, s.level + 1)) {
                c.getSource().sendSuccess(() -> Component.literal("  + ").append(g), false);
            }
        }
        return s.level;
    }

    /** Onda 5: põe a cidade num degrau (0 = o próximo) sem requisitos nem custo, com a cerimônia e a reforma. */
    private static int tierSet(CommandContext<CommandSourceStack> c, int level) throws CommandSyntaxException {
        Settlement s = nearest(c);
        ServerLevel sl = s == null ? null : SettlementService.level(c.getSource().getServer(), s);
        if (s == null || sl == null || !s.own()) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.founded_only"));
            return 0;
        }
        CityTier.set(sl, s, level <= 0 ? s.level + 1 : level);
        return ok(c, s);
    }

    /** Onda 5: reforma a cidade inteira na hora (ruas, postes, praça, fachadas) e põe as muralhas na fila. */
    private static int renewNow(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Settlement s = nearest(c);
        ServerLevel sl = s == null ? null : SettlementService.level(c.getSource().getServer(), s);
        if (s == null || sl == null || !s.own()) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.founded_only"));
            return 0;
        }
        int n = CityRenewal.runNow(sl, s);
        c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.renewed", n), true);
        return n;
    }

    /** v1.0 (city_magic): um efeito de obra pelo comando, com Amplificar/Enfraquecer opcional. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> magic(String name, String kind) {
        return Commands.literal(name)
                .executes(c -> {
                    com.renanpontes.isekaimahou.feature.settlements.magic.CityMagic.debug(c.getSource().getPlayerOrException(), kind, 0);
                    return 1;
                })
                .then(Commands.argument("n", IntegerArgumentType.integer(-4, 4)).executes(c -> {
                    com.renanpontes.isekaimahou.feature.settlements.magic.CityMagic.debug(c.getSource().getPlayerOrException(), kind,
                            IntegerArgumentType.getInteger(c, "n"));
                    return 1;
                }));
    }

    private interface Edit {
        void apply(Settlement s) throws CommandSyntaxException;
    }

    private static int set(CommandContext<CommandSourceStack> c, Edit edit) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null) return 0;
        edit.apply(s);
        return ok(c, s);
    }

    private static int ok(CommandContext<CommandSourceStack> c, Settlement s) {
        var server = c.getSource().getServer();
        c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.city", SettlementService.cityName(server, s), s.level, s.people, s.food,
                s.fuel, s.affinity, s.treasury), true);
        SettlementService.refreshViewers(server, s);
        return 1;
    }

    /** A cidade governada onde você está, senão a mais perto na mesma dimensão. */
    @Nullable
    private static Settlement nearest(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Places.Place at = SettlementService.cityAt(p.serverLevel(), p.blockPosition());
        Settlement s = at == null ? null : SettlementService.city(p.server, at.id());
        if (s == null) {
            double best = Double.MAX_VALUE;
            for (Settlement o : SettlementData.get(p.server).all()) {
                if (o.dim != p.level().dimension()) continue;
                double d = o.heart.distSqr(p.blockPosition());
                if (d < best) {
                    best = d;
                    s = o;
                }
            }
        }
        if (s == null) c.getSource().sendFailure(Component.translatable("settlements.isekai_mahou.cmd.none"));
        return s;
    }

    private static int cellAt(ServerPlayer p, Settlement s) {
        // v1.0 (onda 4): dentro de um prédio em posição livre, o lote é ele
        int free = CityPlots.plotAt(s, p.getBlockX(), p.getBlockZ());
        if (CityLayout.isFree(free)) return free;
        int dx = CityLayout.indexAt(s, p.getBlockX() - s.heart.getX());
        int dz = CityLayout.indexAt(s, p.getBlockZ() - s.heart.getZ());
        if (dx == Integer.MIN_VALUE || dz == Integer.MIN_VALUE || Math.max(Math.abs(dx), Math.abs(dz)) > s.radius) return -1;
        return CityLayout.cellAt(s, dx, dz);
    }

    /** Prédio em posição livre, centrado onde o jogador está (as mesmas regras da tela: mostra o motivo se não couber). */
    private static int free(CommandContext<CommandSourceStack> c, int rot) throws CommandSyntaxException {
        return free(c, rot, null);
    }

    private static int free(CommandContext<CommandSourceStack> c, int rot, @Nullable Integer height) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null) return 0;
        ServerPlayer p = c.getSource().getPlayerOrException();
        String key = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "key");
        boolean done = CityPlots.build(p, s, key, s.style.id, p.getBlockX() - s.heart.getX(), p.getBlockZ() - s.heart.getZ(),
                net.minecraft.world.level.block.Rotation.values()[rot], height);
        if (done) SettlementService.refreshViewers(p.server, s);
        return done ? 1 : 0;
    }

    /** Gira 90° o prédio do lote onde o jogador está (testa o caminho de mudar um prédio; o cofre recebe o custo antes). */
    private static int rotate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null) return 0;
        ServerPlayer p = c.getSource().getPlayerOrException();
        int cell = cellAt(p, s);
        if (cell < 0 || !s.buildings.containsKey(cell) || cell == CityLayout.CENTER_CELL) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.no_building"));
            return 0;
        }
        s.treasury = Math.min(Settlement.MAX_TREASURY, s.treasury + CityPlots.moveCost(s.levelOf(cell)));
        boolean done = CityPlots.move(p, s, cell, CityLayout.offsetX(cell), CityLayout.offsetZ(cell),
                CityLayout.facingHeart(cell).getRotated(net.minecraft.world.level.block.Rotation.CLOCKWISE_90));
        if (!done) s.treasury -= CityPlots.moveCost(s.levelOf(cell));
        SettlementService.refreshViewers(p.server, s);
        return done ? 1 : 0;
    }

    private static int plots(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null) return 0;
        java.util.List<Integer> free = CityPlots.freeOccupied(s);
        c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.plots", free.size()), false);
        for (int f : free) {
            String key = s.buildings.getOrDefault(f, s.projectAt(f) != null ? s.projectAt(f).key + "*" : "?");
            c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.plot", key, CityLayout.freeDx(f), CityLayout.freeDz(f),
                    CityLayout.freeRot(f).ordinal(), s.levelOf(f)), false);
        }
        return free.size();
    }

    /** Você governa a cidade mais perto: fundada (vira dono), conquistada ou de reino (coroação). */
    private static int govern(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel sl = p.serverLevel();
        Places.Place best = null;
        double bd = Double.MAX_VALUE;
        for (Places.Place pl : Places.all(sl)) {
            if (pl.dim() != sl.dimension() || !SettlementService.isCity(pl)) continue;
            double d = pl.center().distSqr(p.blockPosition());
            if (d < bd) {
                bd = d;
                best = pl;
            }
        }
        if (best == null) {
            c.getSource().sendFailure(Component.translatable("settlements.isekai_mahou.cmd.none"));
            return 0;
        }
        Settlement s = SettlementService.city(p.server, best.id());
        if (s != null && s.own()) {
            s.owner = p.getUUID();
            s.ownerName = p.getGameProfile().getName();
            s.council.remove(p.getUUID());
            Allegiance.setLeader(p.server, s.id, p.getUUID());
            Allegiance.set(p, s.id);
            SettlementService.sendState(p, s.id, true);
            return ok(c, s);
        }
        if (Conquest.conquerable(best)) {
            Conquest.crown(p, best);
            return 1;
        }
        c.getSource().sendFailure(SettlementsLang.tr("cmd.not_governable"));
        return 0;
    }

    private static int raid(CommandContext<CommandSourceStack> c, boolean now) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null || !s.own()) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.own_only"));
            return 0;
        }
        ServerLevel sl = SettlementService.level(c.getSource().getServer(), s);
        if (sl == null || !CityRaids.forceStart(sl, s, now)) return 0;
        return 1;
    }

    /** Enche as vagas de construtores agora (sem esperar a chegada aos poucos). */
    private static int builders(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null || !s.own()) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.own_only"));
            return 0;
        }
        ServerLevel sl = SettlementService.level(c.getSource().getServer(), s);
        int cap = s.cap(Settlement.Role.BUILDER);
        for (int i = 0; sl != null && i < Settlement.MAX_BUILDERS && s.population(Settlement.Role.BUILDER) < cap; i++) {
            SettlementService.spawnNpc(sl, s, Settlement.Role.BUILDER, s.heart.offset(i % 5 - 2, 0, 3 + i / 5), s.heart);
        }
        int now = s.population(Settlement.Role.BUILDER);
        c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.builders", now, cap), true);
        SettlementService.refreshViewers(c.getSource().getServer(), s);
        return now;
    }

    /** Enfeites de festa: 1 liga, 0 desliga (saem aos poucos), -1 tira tudo na hora. */
    private static int decor(CommandContext<CommandSourceStack> c, int mode) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null) return 0;
        ServerLevel sl = SettlementService.level(c.getSource().getServer(), s);
        if (mode < 0 && sl != null) CityDecor.clearNow(sl, s);
        else CityDecor.set(s, mode > 0);
        c.getSource().sendSuccess(() -> SettlementsLang.tr(mode > 0 ? "cmd.decor.on" : "cmd.decor.off", CityDecor.placed(s)), true);
        return 1;
    }

    /** Mostra a altura do lote onde o jogador está, a de referência (vizinho de dentro) e o nível da rua calculado. */
    private static int terrain(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null) return 0;
        ServerPlayer p = c.getSource().getPlayerOrException();
        int cell = cellAt(p, s);
        if (cell < 0 || cell == CityLayout.CENTER_CELL) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.no_building"));
            return 0;
        }
        Integer base = CityTerrain.levelOf(s, cell);
        Integer street = CityTerrain.streetLevel(p.serverLevel(), s, cell);
        int ref = CityTerrain.reference(s, cell);
        c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.terrain", cell, base == null ? "-" : Integer.toString(base), ref,
                street == null ? "-" : Integer.toString(street)), false);
        return 1;
    }

    /** Felicidade em texto: valor, humor, cada motivo de hoje e os requisitos do próximo anel. */
    private static int happy(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Settlement s = nearest(c);
        if (s == null) return 0;
        var server = c.getSource().getServer();
        CityEconomy.Stats st = CityEconomy.stats(server, s);
        var factors = CityHappiness.forecast(server, s, st);
        int total = CityHappiness.total(factors);
        c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.happy", s.affinity, SettlementsLang.tr("happy.mood." + CityHappiness.mood(s.affinity)),
                (total >= 0 ? "+" : "") + total), false);
        for (CityHappiness.Factor f : factors) {
            c.getSource().sendSuccess(() -> Component.literal("  " + (f.delta() > 0 ? "+" : "") + f.delta() + " ")
                    .append(SettlementsLang.tr("happy.f." + f.key())), false);
        }
        for (CityEconomy.Req r : CityGrowth.expandReqs(server, s)) {
            c.getSource().sendSuccess(() -> Component.literal(r.ok() ? "  \u2714 " : "  \u2718 ").append(r.text()), false);
        }
        return s.affinity;
    }

    private static int raids(CommandContext<CommandSourceStack> c, boolean on) {
        SettlementData.get(c.getSource().getServer()).raidsEnabled = on;
        c.getSource().sendSuccess(() -> SettlementsLang.tr(on ? "cmd.raids.on" : "cmd.raids.off"), true);
        return 1;
    }

    private CityDebugCommands() {}
}
