package com.renanpontes.isekaimahou.feature.guild.hunt;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.feature.guild.ActiveQuest;
import com.renanpontes.isekaimahou.feature.guild.GuildData;
import com.renanpontes.isekaimahou.feature.guild.GuildService;
import com.renanpontes.isekaimahou.feature.guild.quest.GuildQuest;
import com.renanpontes.isekaimahou.feature.guild.quest.GuildQuests;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@code /mahou guild hunt ...} (operadores, testes das Áreas de Caça):
 * <ul>
 *   <li>{@code here} — cria AGORA, onde você está, a área da 1ª missão de caça ativa (troca a que existir);</li>
 *   <li>{@code scout} — procura uma área (150–500 blocos) para cada missão de caça ativa que ainda não tem;</li>
 *   <li>{@code list} — suas áreas (nome, distância, fase, onda);</li>
 *   <li>{@code clear} — apaga todas as suas áreas (as missões continuam);</li>
 *   <li>{@code sweep} — roda a limpeza de áreas vencidas/órfãs.</li>
 * </ul>
 */
public final class HuntCommands {
    private static final String KEY = "command.isekai_mahou.guild.hunt.";

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("hunt")
                .then(Commands.literal("here").executes(HuntCommands::here))
                .then(Commands.literal("scout").executes(HuntCommands::scout))
                .then(Commands.literal("list").executes(HuntCommands::list))
                .then(Commands.literal("clear").executes(HuntCommands::clear))
                .then(Commands.literal("sweep").executes(c -> {
                    int n = HuntingGrounds.sweep(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.translatable(KEY + "swept", n), false);
                    return n;
                }));
    }

    private static List<ActiveQuest> quests(GuildData d) {
        List<ActiveQuest> all = new ArrayList<>(d.active());
        if (d.trial() != null) all.add(d.trial());
        return all;
    }

    private static int here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.serverLevel();
        GuildData d = GuildService.data(sp);
        for (ActiveQuest a : quests(d)) {
            GuildQuest q = GuildQuests.get(a.id);
            if (q == null || !HuntRules.eligible(level, q)) continue;
            HuntingGrounds.finish(sp, d, a, HuntingGrounds.Reason.ABANDONED);
            List<EntityType<?>> mobs = HuntRules.huntableMobs(level, q);
            BlockPos p = sp.blockPosition();
            BlockPos center = new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
            HuntingGrounds.placeAt(sp, a, q, center, mobs, HuntRules.elementOf(level.getBiome(center)), false);
            c.getSource().sendSuccess(() -> Component.translatable(KEY + "created", q.titleComponent()), true);
            return 1;
        }
        c.getSource().sendFailure(Component.translatable(KEY + "none"));
        return 0;
    }

    private static int scout(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        GuildData d = GuildService.data(sp);
        int made = 0;
        for (ActiveQuest a : quests(d)) {
            GuildQuest q = GuildQuests.get(a.id);
            if (q == null || a.hunt != null) continue;
            HuntingGrounds.create(sp, a, q, true);
            if (a.hunt != null) made++;
        }
        final int total = made;
        if (total == 0) c.getSource().sendFailure(Component.translatable(KEY + "none"));
        else c.getSource().sendSuccess(() -> Component.translatable(KEY + "scouted", total), false);
        return total;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        int n = 0;
        for (ActiveQuest a : quests(GuildService.data(sp))) {
            HuntState h = a.hunt;
            if (h == null) continue;
            n++;
            int dist = sp.level().dimension() == h.dim
                    ? (int) Math.sqrt(sp.blockPosition().distSqr(new BlockPos(h.center.getX(), sp.getBlockY(), h.center.getZ()))) : -1;
            Component phase = Component.translatable(KEY + (h.phase == HuntState.FIGHT ? "phase_fight" : "phase_travel"), h.wave);
            Component line = Component.translatable(KEY + "line", HuntingGrounds.placeName(h), h.center.getX(), h.center.getY(),
                    h.center.getZ(), dist, phase);
            c.getSource().sendSuccess(() -> line, false);
        }
        if (n == 0) c.getSource().sendFailure(Component.translatable(KEY + "empty"));
        return n;
    }

    private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        GuildData d = GuildService.data(sp);
        int n = 0;
        for (ActiveQuest a : quests(d)) {
            if (a.hunt == null) continue;
            HuntingGrounds.finish(sp, d, a, HuntingGrounds.Reason.ABANDONED);
            n++;
        }
        final int total = n;
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "cleared", total), true);
        GuildService.sync(sp, true);
        return total;
    }

    private HuntCommands() {}
}
