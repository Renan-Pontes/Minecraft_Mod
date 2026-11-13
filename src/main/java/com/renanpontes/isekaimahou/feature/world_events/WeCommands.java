package com.renanpontes.isekaimahou.feature.world_events;

import java.util.Arrays;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.world_events.entity.AlienMob;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de teste (op, depois de {@code /mahou admin login}):
 * <pre>
 * /mahou world_events status
 * /mahou world_events start &lt;meteor|blood_moon|arcane_storm|earthquake|mana_storm&gt;
 * /mahou world_events stop
 * /mahou world_events auto &lt;true|false&gt;      sorteio automático
 * /mahou world_events chance &lt;0..1000&gt;       chance em % do normal (100 = padrão; 1000 = quase todo dia)
 * /mahou world_events site                    testa se há lugar livre para meteoro/fenda perto de você
 * /mahou world_events aliens                  invoca um grupo de invasores em você
 * </pre>
 */
public final class WeCommands {
    private WeCommands() {}

    static void register() {
        MahouCommands.extend((root, buildCtx) -> root.then(MahouCommands.op("world_events")
                .then(Commands.literal("status").executes(WeCommands::status))
                .then(Commands.literal("start").then(Commands.argument("event", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(WorldEventType.values()).map(t -> t.id), b))
                        .executes(WeCommands::start)))
                .then(Commands.literal("stop").executes(WeCommands::stop))
                .then(Commands.literal("auto").then(Commands.argument("on", BoolArgumentType.bool()).executes(WeCommands::auto)))
                .then(Commands.literal("chance").then(Commands.argument("percent", IntegerArgumentType.integer(0, 1000))
                        .executes(WeCommands::chance)))
                .then(Commands.literal("site").executes(WeCommands::site))
                .then(Commands.literal("aliens").executes(WeCommands::aliens))));
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        ServerLevel overworld = c.getSource().getServer().overworld();
        WeState s = WeState.get(c.getSource().getServer());
        WorldEventType type = s.type();
        Component active = type == null ? Component.translatable("command.isekai_mahou.world_events.none") : type.displayName();
        int secs = WeManager.ticksLeft(overworld) / 20;
        long day = overworld.getDayTime() / 24000L;
        String site = s.site == null ? "-" : s.site.toShortString();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.world_events.status", active, secs, site,
                s.auto, s.chancePct, day, s.nextAllowedDay, s.builtCount()), false);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        WorldEventType type = WorldEventType.byId(StringArgumentType.getString(c, "event"));
        if (type == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.world_events.unknown"));
            return 0;
        }
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel overworld = c.getSource().getServer().overworld();
        WeManager.stop(overworld, false);
        if (!WeManager.start(overworld, type, p, true)) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.world_events.no_site"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.world_events.started", type.displayName()), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> c) {
        boolean ok = WeManager.stop(c.getSource().getServer().overworld(), true);
        c.getSource().sendSuccess(() -> Component.translatable(ok ? "command.isekai_mahou.world_events.stopped"
                : "command.isekai_mahou.world_events.none"), true);
        return ok ? 1 : 0;
    }

    private static int auto(CommandContext<CommandSourceStack> c) {
        WeState s = WeState.get(c.getSource().getServer());
        s.auto = BoolArgumentType.getBool(c, "on");
        s.setDirty();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.world_events.auto", s.auto), true);
        return 1;
    }

    private static int chance(CommandContext<CommandSourceStack> c) {
        WeState s = WeState.get(c.getSource().getServer());
        s.chancePct = IntegerArgumentType.getInteger(c, "percent");
        s.setDirty();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.world_events.chance", s.chancePct), true);
        return 1;
    }

    private static int site(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        boolean here = WeSites.areaClean(level, p.blockPosition().below(), 8);
        BlockPos found = WeSites.findSite(level, p.blockPosition(), 40, 90, 8, 14);
        String at = found == null ? "-" : found.toShortString();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.world_events.site", here, at), false);
        return found == null ? 0 : 1;
    }

    private static int aliens(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        BlockPos at = p.blockPosition();
        AlienMob.spawnInvader(level, WeRegistries.ALIEN_OVERSEER.get(), at.offset(5, 0, 0));
        AlienMob.spawnInvader(level, WeRegistries.ALIEN_GRUNT.get(), at.offset(4, 0, 3));
        AlienMob.spawnInvader(level, WeRegistries.ALIEN_GRUNT.get(), at.offset(4, 0, -3));
        AlienMob.spawnInvader(level, WeRegistries.ALIEN_SPITTER.get(), at.offset(8, 0, 0));
        for (int i = 0; i < 3; i++) AlienMob.spawnInvader(level, WeRegistries.ALIEN_LARVA.get(), at.offset(3, 0, i - 1));
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.world_events.aliens"), true);
        return 1;
    }
}
