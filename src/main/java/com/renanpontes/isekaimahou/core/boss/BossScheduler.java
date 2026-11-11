package com.renanpontes.isekaimahou.core.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Agendador do kit de chefes (só servidor): telegrafias ativas, callbacks atrasados e tarefas simples.
 * Roda no {@link ServerTickEvent.Post}; o tempo é o {@code gameTime} do nível (para junto com /tick freeze).
 * Tudo some no {@link ServerStoppedEvent} e quando o nível descarrega.
 */
final class BossScheduler {
    /** Limite de formas só-visuais ativas no servidor (as com callback sempre entram). */
    private static final int MAX_ACTIVE = 4096;

    private static final class Active {
        final int id;
        final ResourceKey<Level> dim;
        final Telegraph.Shape shape;
        final long start;
        @Nullable final Consumer<List<LivingEntity>> onHit;
        Vec3 center;
        boolean locked;
        boolean fired;

        Active(int id, ResourceKey<Level> dim, Telegraph.Shape shape, long start, @Nullable Consumer<List<LivingEntity>> onHit) {
            this.id = id;
            this.dim = dim;
            this.shape = shape;
            this.start = start;
            this.onHit = onHit;
            this.center = shape.center();
        }
    }

    private record Task(ResourceKey<Level> dim, long due, Runnable action) {}

    private static final Map<Integer, Active> ACTIVE = new LinkedHashMap<>();
    private static final List<Task> TASKS = new ArrayList<>();
    private static int nextId = 1;
    private static boolean warnedFull;

    static int add(ServerLevel level, Telegraph.Shape shape, @Nullable Consumer<List<LivingEntity>> onHit) {
        if (onHit == null && ACTIVE.size() >= MAX_ACTIVE) {
            if (!warnedFull) {
                warnedFull = true;
                IsekaiMahou.LOGGER.warn("Telegraph: limite de {} formas ativas atingido; novas formas visuais ignoradas", MAX_ACTIVE);
            }
            return -1;
        }
        int id = nextId++;
        if (nextId == Integer.MAX_VALUE) nextId = 1;
        Active a = new Active(id, level.dimension(), shape, level.getGameTime(), onHit);
        ACTIVE.put(id, a);
        Vec3 c = a.center;
        // follow: manda a posição ATUAL do alvo (a Shape pode ter sido montada antes)
        Vec3 p1 = shape.follow != null ? c : shape.a, p2 = shape.b;
        PacketDistributor.sendToPlayersNear(level, null, c.x, c.y, c.z, Telegraph.VIEW_RANGE + shape.extent(),
                new BossPayloads.Show(id, shape.kind.ordinal(), shape.flags, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z,
                        shape.radius, shape.inner, shape.yawDeg, shape.angleDeg, shape.width,
                        shape.warmup, shape.hold, shape.rgb, shape.follow != null ? shape.follow.getId() : -1, shape.lockTicks(),
                        (float) shape.above));
        return id;
    }

    static void cancel(int id, boolean flash) {
        Active a = ACTIVE.remove(id);
        if (a != null) sendEnd(a, flash);
    }

    static void cancelOwned(Entity owner) {
        Iterator<Active> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Active a = it.next();
            if (a.shape.owner == owner) {
                it.remove();
                sendEnd(a, false);
            }
        }
    }

    static boolean isActive(int id) {
        return ACTIVE.containsKey(id);
    }

    @Nullable
    static Vec3 centerOf(int id) {
        Active a = ACTIVE.get(id);
        return a == null ? null : a.center;
    }

    static void schedule(ServerLevel level, int delayTicks, Runnable action) {
        TASKS.add(new Task(level.dimension(), level.getGameTime() + Math.max(0, delayTicks), action));
    }

    /** Entidades vivas (não espectadoras, sem o dono) dentro da forma centrada em {@code center}. */
    static List<LivingEntity> collect(ServerLevel level, Telegraph.Shape shape, Vec3 center) {
        return level.getEntitiesOfClass(LivingEntity.class, shape.bounds(center),
                e -> e.isAlive() && !e.isSpectator() && e != shape.owner && shape.containsAt(e, center));
    }

    // ------------------------------------------------------------------ eventos

    static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty() && TASKS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        List<Runnable> run = new ArrayList<>();

        Iterator<Active> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Active a = it.next();
            ServerLevel level = server.getLevel(a.dim);
            if (level == null) {
                it.remove();
                continue;
            }
            Telegraph.Shape s = a.shape;
            long elapsed = level.getGameTime() - a.start;
            // Quem disparou o callback num tick anterior sai agora (centerOf ainda vale DENTRO do callback)
            if (elapsed >= s.warmup + s.hold && (a.onHit == null || a.fired)) {
                it.remove();
                continue;
            }
            // Dono morto/sumido: o ataque some sem acertar
            if (s.owner != null && !alive(s.owner)) {
                it.remove();
                sendEnd(a, false);
                continue;
            }
            if (s.follow != null && !a.locked) {
                Entity f = s.follow;
                boolean gone = f.isRemoved() || f.level() != level;
                if (!gone) a.center = f.position();
                // Trava no fim: o jogador vê o círculo parar e ainda tem tempo de sair dele
                if (gone || elapsed >= s.warmup - s.lockTicks()) {
                    a.locked = true;
                    PacketDistributor.sendToPlayersNear(level, null, a.center.x, a.center.y, a.center.z,
                            Telegraph.VIEW_RANGE + s.extent(), new BossPayloads.Lock(a.id, a.center.x, a.center.y, a.center.z));
                }
            }
            if (a.onHit != null && !a.fired && elapsed >= s.warmup) {
                a.fired = true;
                List<LivingEntity> hits = collect(level, s, a.center);
                Consumer<List<LivingEntity>> cb = a.onHit;
                run.add(() -> cb.accept(hits));
            }
        }

        if (!TASKS.isEmpty()) {
            Iterator<Task> ti = TASKS.iterator();
            while (ti.hasNext()) {
                Task t = ti.next();
                ServerLevel level = server.getLevel(t.dim);
                if (level == null) {
                    ti.remove();
                } else if (level.getGameTime() >= t.due) {
                    ti.remove();
                    run.add(t.action);
                }
            }
        }

        // Fora do laço: callbacks podem criar novas formas/tarefas sem quebrar a iteração
        for (Runnable r : run) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.error("Erro num callback do kit de chefes", ex);
            }
        }
    }

    static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ResourceKey<Level> dim = level.dimension();
        ACTIVE.values().removeIf(a -> a.dim == dim);
        TASKS.removeIf(t -> t.dim == dim);
    }

    static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        TASKS.clear();
        warnedFull = false;
    }

    private static boolean alive(Entity e) {
        return !e.isRemoved() && (!(e instanceof LivingEntity le) || le.isAlive());
    }

    private static void sendEnd(Active a, boolean flash) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.getLevel(a.dim);
        if (level == null) return;
        PacketDistributor.sendToPlayersNear(level, null, a.center.x, a.center.y, a.center.z,
                Telegraph.VIEW_RANGE + a.shape.extent(), new BossPayloads.Cancel(a.id, flash));
    }

    private BossScheduler() {}
}
