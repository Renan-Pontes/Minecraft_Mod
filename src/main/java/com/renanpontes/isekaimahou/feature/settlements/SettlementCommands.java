package com.renanpontes.isekaimahou.feature.settlements;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Comandos de op (testes):
 * <pre>
 * /mahou settlements list                    cidades governadas (dono, nível, afinidade, cofre, obras)
 * /mahou settlements charter                 ganha uma Carta de Fundação
 * /mahou settlements finish                  termina na hora as obras da cidade onde você está
 * /mahou settlements level &lt;1-5&gt;             nível (e prosperidade) da cidade onde você está
 * /mahou settlements affinity &lt;0-100&gt;        afinidade do povo
 * /mahou settlements coins &lt;n&gt;               põe moedas no cofre
 * /mahou settlements day                     roda a conta diária agora (impostos/afinidade)
 * /mahou settlements oath &lt;jogador&gt;          completa o Juramento do jogador (dá o Selo)
 * /mahou settlements crown &lt;jogador&gt;         torna o jogador Senhor da cidade de reino onde ELE está
 * /mahou settlements found                    (teste) funda uma cidade Reino onde você está, sem a tela
 * /mahou settlements found sky [estilo]       (onda 7, teste) funda uma ILHA CELESTE acima de onde você está (dá a Carta e
 *                                             o Núcleo Celeste se faltarem); estilo = kingdom, desert, nordic, oriental...
 * </pre>
 */
public final class SettlementCommands {
    public static void register() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("settlements")
                .then(Commands.literal("list").executes(SettlementCommands::list))
                .then(Commands.literal("charter").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.getInventory().add(new ItemStack(SettlementsContent.FOUNDING_CHARTER.get()));
                    return 1;
                }))
                .then(Commands.literal("found").executes(c -> {
                    // teste: funda uma cidade (estilo Reino) onde você está, sem a tela da Carta
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    // onda 9: inventário cheio / lugar ruim não falham mais em silêncio — o motivo vai para o chat
                    return report(c, SettlementService.giveFoundingItems(p, false), p, p.blockPosition(), SettlementStyle.KINGDOM, SkyCity.GROUND);
                }).then(Commands.literal("sky").executes(c -> foundSky(c, SettlementStyle.KINGDOM))
                        .then(Commands.argument("style", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (SettlementStyle st : SettlementStyle.values()) b.suggest(st.id);
                                    return b.buildFuture();
                                })
                                .executes(c -> {
                                    SettlementStyle st = SettlementStyle.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "style"));
                                    return foundSky(c, st == null ? SettlementStyle.KINGDOM : st);
                                }))))
                .then(Commands.literal("finish").executes(c -> {
                    Settlement s = here(c);
                    ServerLevel sl = c.getSource().getLevel();
                    int n = s == null || !s.own() ? 0 : ConstructionEngine.finishAll(sl, s);
                    c.getSource().sendSuccess(() -> Component.translatable("settlements.isekai_mahou.cmd.finished", n), true);
                    return n;
                }))
                .then(Commands.literal("level").then(Commands.argument("n", IntegerArgumentType.integer(1, Blueprints.MAX_LEVEL)).executes(c -> {
                    Settlement s = here(c);
                    if (s == null) return 0;
                    s.level = IntegerArgumentType.getInteger(c, "n");
                    s.points = Math.max(s.points, Blueprints.pointsFor(s.style, s.level));
                    return ok(c, s);
                })))
                .then(Commands.literal("affinity").then(Commands.argument("n", IntegerArgumentType.integer(0, Settlement.MAX_AFFINITY)).executes(c -> {
                    Settlement s = here(c);
                    if (s == null) return 0;
                    s.affinity = IntegerArgumentType.getInteger(c, "n");
                    return ok(c, s);
                })))
                .then(Commands.literal("coins").then(Commands.argument("n", IntegerArgumentType.integer(1, 99999)).executes(c -> {
                    Settlement s = here(c);
                    if (s == null) return 0;
                    s.treasury = Math.min(Settlement.MAX_TREASURY, s.treasury + IntegerArgumentType.getInteger(c, "n"));
                    return ok(c, s);
                })))
                .then(Commands.literal("day").executes(c -> {
                    Settlement s = here(c);
                    if (s == null) return 0;
                    Lordship.daily(c.getSource().getServer(), s);
                    return ok(c, s);
                }))
                .then(Commands.literal("oath").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    SettlementData.Oath o = SettlementData.get(p.server).oath(p.getUUID());
                    if (o == null) {
                        Places.Place at = SettlementService.cityAt(p.serverLevel(), p.blockPosition());
                        if (!Conquest.conquerable(at)) return 0;
                        o = SettlementData.get(p.server).startOath(p.getUUID(), at.id());
                    }
                    o.kills = Conquest.KILLS_NEEDED;
                    o.vigil = Conquest.VIGIL_NEEDED;
                    o.tribute = Conquest.TRIBUTE_NEEDED - 1;
                    p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.EMERALD, 1));
                    Conquest.payTribute(p);
                    return 1;
                })))
                .then(Commands.literal("crown").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    Places.Place at = SettlementService.cityAt(p.serverLevel(), p.blockPosition());
                    if (!Conquest.conquerable(at)) return 0;
                    Conquest.crown(p, at);
                    return 1;
                })))));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        var server = c.getSource().getServer();
        for (Settlement s : SettlementData.get(server).all()) {
            c.getSource().sendSuccess(() -> Component.translatable("settlements.isekai_mahou.cmd.line",
                    SettlementService.cityName(server, s), s.id, s.ownerName, s.level, s.affinity, s.treasury, s.projects.size(),
                    s.totalPopulation()), false);
        }
        return 1;
    }

    private static Settlement here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Places.Place at = SettlementService.cityAt(p.serverLevel(), p.blockPosition());
        Settlement s = at == null ? null : SettlementService.city(p.server, at.id());
        if (s == null) c.getSource().sendFailure(Component.translatable("settlements.isekai_mahou.cmd.none"));
        return s;
    }

    private static int ok(CommandContext<CommandSourceStack> c, Settlement s) {
        c.getSource().sendSuccess(() -> Component.translatable("settlements.isekai_mahou.cmd.ok", SettlementService.cityName(
                c.getSource().getServer(), s), s.level, s.affinity, s.treasury), true);
        return 1;
    }

    /** Onda 7 (teste): funda uma Ilha Celeste do estilo dado acima de onde o jogador está, sem a tela. */
    private static int foundSky(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c, SettlementStyle style)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        return report(c, SettlementService.giveFoundingItems(p, true), p, p.blockPosition(), style, SkyCity.SKYLAND);
    }

    /**
     * Onda 9 (fundar mais fácil): funda pelo comando e, se não der, diz o motivo NO CHAT (antes ele só piscava acima da
     * barra de itens — com o inventário cheio parecia que o comando não fazia nada). {@code given} = problema ao dar a
     * Carta/Núcleo (inventário cheio).
     */
    public static int report(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,
                             @javax.annotation.Nullable Component given, ServerPlayer p, net.minecraft.core.BlockPos at, SettlementStyle style,
                             String terrain) {
        Component problem = given != null ? given : SettlementService.found(p, at, style, "", 0, terrain);
        if (problem != null) {
            c.getSource().sendFailure(problem);
            return 0;
        }
        return 1;
    }

    private SettlementCommands() {}
}
