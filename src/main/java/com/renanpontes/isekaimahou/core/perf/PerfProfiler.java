package com.renanpontes.isekaimahou.core.perf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * "/mahou perf [segundos]" (op): mede por N segundos o tempo de tick do servidor (média e pior tick), de cada dimensão
 * e de cada TIPO de entidade (soma do tick de todas as instâncias) e mostra os mais pesados — para achar o mob/chefe/
 * dimensão que está causando lag sem precisar de profiler externo. Fora da medição os ouvintes só leem um booleano.
 * <p>
 * O tempo da dimensão inclui as entidades dela; o tempo das entidades é o do {@code tick()} de cada uma (IA, física,
 * ataques e os {@code EntityTickEvent} de todos os mods).
 */
public final class PerfProfiler {
    private static final int DEFAULT_SECONDS = 10;
    private static final int MAX_SECONDS = 60;
    private static final int TOP = 8;

    private static boolean active;
    private static int ticksLeft;
    private static int ticksMeasured;
    private static CommandSourceStack requester;

    private static long serverStart;
    private static long serverTotal;
    private static long serverWorst;
    private static final Map<Level, Long> LEVEL_START = new IdentityHashMap<>();
    private static final Map<ResourceLocation, long[]> LEVELS = new HashMap<>();
    private static final Map<Entity, Long> ENTITY_START = new IdentityHashMap<>();
    /** Por tipo: [nanos totais, ticks de entidade, pior tick único]. */
    private static final Map<EntityType<?>, long[]> ENTITIES = new IdentityHashMap<>();

    public static void init() {
        var bus = NeoForge.EVENT_BUS;
        // Mais alta prioridade no Pre e mais baixa no Post: mede também os ouvintes dos outros mods
        bus.addListener(EventPriority.HIGHEST, ServerTickEvent.Pre.class, e -> {
            if (active) serverStart = System.nanoTime();
        });
        bus.addListener(EventPriority.LOWEST, ServerTickEvent.Post.class, PerfProfiler::serverPost);
        bus.addListener(EventPriority.HIGHEST, LevelTickEvent.Pre.class, e -> {
            if (active && !e.getLevel().isClientSide) LEVEL_START.put(e.getLevel(), System.nanoTime());
        });
        bus.addListener(EventPriority.LOWEST, LevelTickEvent.Post.class, PerfProfiler::levelPost);
        bus.addListener(EventPriority.HIGHEST, EntityTickEvent.Pre.class, e -> {
            if (active && !e.getEntity().level().isClientSide) ENTITY_START.put(e.getEntity(), System.nanoTime());
        });
        bus.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, PerfProfiler::entityPost);
        bus.addListener((ServerStoppedEvent e) -> reset());

        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("perf")
                .executes(c -> start(c, DEFAULT_SECONDS))
                .then(Commands.argument("segundos", IntegerArgumentType.integer(2, MAX_SECONDS))
                        .executes(c -> start(c, IntegerArgumentType.getInteger(c, "segundos"))))));
    }

    private static int start(CommandContext<CommandSourceStack> c, int seconds) {
        if (active) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.perf.busy"));
            return 0;
        }
        reset();
        requester = c.getSource();
        ticksLeft = seconds * 20;
        active = true;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.perf.start", seconds)
                .withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static void serverPost(ServerTickEvent.Post e) {
        if (!active) return;
        long dt = System.nanoTime() - serverStart;
        serverTotal += dt;
        serverWorst = Math.max(serverWorst, dt);
        ticksMeasured++;
        if (--ticksLeft <= 0) finish(e.getServer());
    }

    private static void levelPost(LevelTickEvent.Post e) {
        if (!active || e.getLevel().isClientSide) return;
        Long t0 = LEVEL_START.remove(e.getLevel());
        if (t0 == null) return;
        long[] acc = LEVELS.computeIfAbsent(e.getLevel().dimension().location(), k -> new long[2]);
        long dt = System.nanoTime() - t0;
        acc[0] += dt;
        acc[1] = Math.max(acc[1], dt);
    }

    private static void entityPost(EntityTickEvent.Post e) {
        if (!active) return;
        Long t0 = ENTITY_START.remove(e.getEntity());
        if (t0 == null) return;
        long dt = System.nanoTime() - t0;
        long[] acc = ENTITIES.computeIfAbsent(e.getEntity().getType(), k -> new long[3]);
        acc[0] += dt;
        acc[1]++;
        acc[2] = Math.max(acc[2], dt);
    }

    private static void finish(MinecraftServer server) {
        active = false;
        CommandSourceStack to = requester;
        int ticks = Math.max(1, ticksMeasured);
        double seconds = ticks / 20.0;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("command.isekai_mahou.perf.header", fmt(seconds, 0),
                fmt(serverTotal / 1.0E6 / ticks, 2), fmt(serverWorst / 1.0E6, 1)).withStyle(ChatFormatting.GOLD));

        List<Map.Entry<ResourceLocation, long[]>> levels = new ArrayList<>(LEVELS.entrySet());
        levels.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        for (Map.Entry<ResourceLocation, long[]> en : levels) {
            lines.add(Component.translatable("command.isekai_mahou.perf.level", en.getKey().toString(),
                    fmt(en.getValue()[0] / 1.0E6 / ticks, 2), fmt(en.getValue()[1] / 1.0E6, 1)).withStyle(ChatFormatting.GRAY));
        }

        List<Map.Entry<EntityType<?>, long[]>> ents = new ArrayList<>(ENTITIES.entrySet());
        ents.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        lines.add(Component.translatable("command.isekai_mahou.perf.entities").withStyle(ChatFormatting.YELLOW));
        for (int i = 0; i < Math.min(TOP, ents.size()); i++) {
            EntityType<?> type = ents.get(i).getKey();
            long[] acc = ents.get(i).getValue();
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            double perTick = acc[0] / 1.0E6 / ticks;
            double avgCount = acc[1] / (double) ticks;
            double each = acc[1] == 0 ? 0 : acc[0] / 1.0E3 / acc[1];
            ChatFormatting color = perTick >= 2.0 ? ChatFormatting.RED : perTick >= 0.5 ? ChatFormatting.GOLD : ChatFormatting.WHITE;
            boolean ours = IsekaiMahou.MODID.equals(id.getNamespace());
            lines.add(Component.translatable("command.isekai_mahou.perf.entity", (ours ? "★ " : "") + id,
                    fmt(perTick, 2), fmt(avgCount, 1), fmt(each, 0), fmt(acc[2] / 1.0E6, 1)).withStyle(color));
        }
        lines.add(Component.translatable("command.isekai_mahou.perf.footer").withStyle(ChatFormatting.DARK_GRAY));
        IsekaiMahou.LOGGER.info("[/mahou perf] resultado:");
        for (Component l : lines) {
            IsekaiMahou.LOGGER.info("  {}", l.getString());
            if (to != null) to.sendSystemMessage(l);
        }
        reset();
    }

    private static String fmt(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    private static void reset() {
        active = false;
        ticksLeft = 0;
        ticksMeasured = 0;
        serverTotal = 0;
        serverWorst = 0;
        requester = null;
        LEVEL_START.clear();
        LEVELS.clear();
        ENTITY_START.clear();
        ENTITIES.clear();
    }

    private PerfProfiler() {}
}
