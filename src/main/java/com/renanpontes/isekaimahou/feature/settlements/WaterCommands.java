package com.renanpontes.isekaimahou.feature.settlements;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Onda 8 (water_city) — comandos de teste da Cidade das Águas (entram em {@code /mahou city} pelo {@code CityDebugCommands}):
 * <pre>
 * /mahou city water                  estado da cidade mais perto: nível do mar, passarelas, enfeites, barcos
 * /mahou city water found [estilo]   funda uma Cidade das Águas na água para onde você olha (ou onde você está), sem a tela
 * /mahou city water finish           termina já passarelas, pontes, cais, enfeites e barcos (só em chunks carregados)
 * /mahou city water redo             tira os enfeites e refaz a planta do zero (as passarelas voltam aos poucos)
 * /mahou city water grow             força o crescimento: um anel a mais, sem requisitos
 * /mahou city water tp               leva você para a praça
 * /mahou city water drop             joga na água o NPC da cidade mais perto de você (ele volta ao piso em até 5 s)
 * </pre>
 * Os comandos comuns ({@code finish}, {@code tier}, {@code free}, {@code style}, {@code pause/resume/cancel}) valem igual.
 */
final class WaterCommands {
    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("water").executes(c -> run(c, 0))
                .then(Commands.literal("found").executes(c -> found(c, SettlementStyle.KINGDOM))
                        .then(Commands.argument("style", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (SettlementStyle st : SettlementStyle.values()) b.suggest(st.id);
                                    return b.buildFuture();
                                })
                                .executes(c -> {
                                    SettlementStyle st = SettlementStyle.byId(StringArgumentType.getString(c, "style"));
                                    return found(c, st == null ? SettlementStyle.KINGDOM : st);
                                })))
                .then(Commands.literal("finish").executes(c -> run(c, 1)))
                .then(Commands.literal("redo").executes(c -> run(c, 2)))
                .then(Commands.literal("grow").executes(c -> run(c, 3)))
                .then(Commands.literal("tp").executes(c -> run(c, 4)))
                .then(Commands.literal("drop").executes(c -> run(c, 5)));
    }

    /** A cidade onde você está, senão a mais perto na mesma dimensão. */
    @Nullable
    private static Settlement nearest(ServerPlayer p) {
        Places.Place at = SettlementService.cityAt(p.serverLevel(), p.blockPosition());
        Settlement s = at == null ? null : SettlementService.city(p.server, at.id());
        if (s != null) return s;
        double best = Double.MAX_VALUE;
        for (Settlement o : SettlementData.get(p.server).all()) {
            if (o.dim != p.level().dimension()) continue;
            double d = o.heart.distSqr(p.blockPosition());
            if (d < best) {
                best = d;
                s = o;
            }
        }
        return s;
    }

    /** O ponto de água para a fundação por comando: a água para onde você olha; senão, a mais perto de você (até 8 blocos). */
    @Nullable
    private static BlockPos waterNear(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        HitResult hr = p.pick(CityPlots.REACH, 1F, true);
        if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK && sl.getFluidState(bh.getBlockPos()).is(FluidTags.WATER)) {
            return bh.getBlockPos().above();
        }
        BlockPos at = p.blockPosition();
        for (int r = 0; r <= 8; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r) continue;
                    int y = WaterCity.surfaceY(sl, at.getX() + ox, at.getZ() + oz, at.getY());
                    if (y != Integer.MIN_VALUE) return new BlockPos(at.getX() + ox, y + 1, at.getZ() + oz);
                }
            }
        }
        return null;
    }

    private static int found(CommandContext<CommandSourceStack> c, SettlementStyle style) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BlockPos at = waterNear(p);
        if (at == null) {
            c.getSource().sendFailure(SettlementsLang.tr("water.found.aim"));
            return 0;
        }
        // onda 9: inventário cheio / lugar ruim aparecem no chat
        return SettlementCommands.report(c, SettlementService.giveFoundingItems(p, false), p, at, style, WaterCity.WATER);
    }

    private static int run(CommandContext<CommandSourceStack> c, int mode) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Settlement s = nearest(p);
        ServerLevel sl = s == null ? null : SettlementService.level(c.getSource().getServer(), s);
        if (s == null || sl == null || !s.water) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.water.only"));
            return 0;
        }
        switch (mode) {
            case 1 -> {
                int n = WaterWorks.finishNow(sl, s);
                c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.water.finished", n), true);
            }
            case 2 -> {
                WaterWorks.redo(sl, s);
                c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.water.redo"), true);
            }
            case 3 -> {
                if (s.radius >= CityLayout.MAX_RADIUS) {
                    c.getSource().sendFailure(SettlementsLang.tr("expand.max", 2 * s.radius + 1));
                    return 0;
                }
                s.radius++;
                SettlementService.registerPlace(sl, s);
            }
            case 4 -> {
                BlockPos to = WaterCity.firmOrPlaza(sl, s, s.heart.offset(2, 0, 2), 8);
                p.stopRiding();
                p.teleportTo(sl, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), p.getXRot());
            }
            case 5 -> {
                return drop(c, p, sl, s);
            }
            default -> {
            }
        }
        Component line = WaterWorks.debug(sl, s);
        c.getSource().sendSuccess(() -> line, false);
        SettlementService.refreshViewers(c.getSource().getServer(), s);
        return 1;
    }

    /** Joga na água o NPC da cidade mais perto do jogador (teste do "ninguém se perde"). */
    private static int drop(CommandContext<CommandSourceStack> c, ServerPlayer p, ServerLevel sl, Settlement s) {
        SettlementNpc best = null;
        double bd = Double.MAX_VALUE;
        for (SettlementNpc n : sl.getEntitiesOfClass(SettlementNpc.class, new AABB(p.blockPosition()).inflate(48, 24, 48), n -> s.id.equals(n.cityId()))) {
            double d = n.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = n;
            }
        }
        if (best == null) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.water.drop.none"));
            return 0;
        }
        int sea = WaterCity.seaY(s);
        for (int r = 1; r <= 24; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r) continue;
                    int x = best.getBlockX() + ox, z = best.getBlockZ() + oz;
                    if (!WaterCity.wet(sl, s, x, z) || !WaterCity.open(sl.getBlockState(new BlockPos(x, sea + 1, z)))) continue;
                    best.getNavigation().stop();
                    best.teleportTo(x + 0.5, sea, z + 0.5);
                    Component name = best.getDisplayName();
                    c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.water.drop", name), false);
                    return 1;
                }
            }
        }
        c.getSource().sendFailure(SettlementsLang.tr("cmd.water.drop.none"));
        return 0;
    }

    private WaterCommands() {}
}
