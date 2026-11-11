package com.renanpontes.isekaimahou.core.api;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ux.ArenaService;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Limites de arena (v1.0 — A3). Um módulo (Provações, chefes, duelos...) registra a área onde a luta acontece e o
 * núcleo mostra para os jogadores da dimensão: uma parede de runas translúcida na borda (só perto dela) e, ao se
 * aproximar, o aviso "Limite da arena: N blocos" no meio da tela. Só VISUAL + consultas: quem quiser prender o jogador
 * dentro usa {@link Arena#clampInside} / {@link Arena#contains} no próprio tick.
 *
 * <p>Servidor. As arenas NÃO são salvas: registre de novo quando a luta/instância recomeçar (ex.: ao carregar a
 * instância da Provação). Tudo some ao parar o servidor. Ids são únicos no servidor inteiro (registrar o mesmo id
 * substitui, inclusive se mudar de dimensão).
 *
 * <pre>{@code
 * ArenaBounds.register(level, "trial_" + instanceId, center, 24, 0xFFB547,
 *         Component.translatable("trials.isekai_mahou.arena"));
 * ...
 * ArenaBounds.unregister(level.getServer(), "trial_" + instanceId);
 * }</pre>
 */
public final class ArenaBounds {
    /** Distância padrão (blocos) em que o aviso e a parede aparecem. */
    public static final int DEFAULT_WARN = 10;
    /** Máximo de arenas por dimensão enviadas ao cliente (as primeiras registradas). */
    public static final int MAX_SYNCED = 128;

    public enum Shape { CIRCLE, BOX }

    /**
     * Uma arena. {@code CIRCLE}: círculo horizontal de raio {@code radiusX} em volta de {@code center} (radiusZ = radiusX).
     * {@code BOX}: retângulo de meia-largura {@code radiusX} × {@code radiusZ} centrado em {@code center}.
     * Altura: de {@code minY} a {@code maxY} (padrão: sem limite vertical).
     *
     * @param rgb          cor da parede e do aviso (RGB)
     * @param name         nome mostrado no aviso (pode ser {@link Component#empty()})
     * @param warnDistance a partir de quantos blocos da borda o aviso/parede aparecem
     * @param showWall     desenhar a parede de runas (false = só o aviso de distância)
     */
    public record Arena(String id, ResourceKey<Level> dimension, Shape shape, Vec3 center, double radiusX, double radiusZ,
                        double minY, double maxY, int rgb, Component name, int warnDistance, boolean showWall) {

        public Arena {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("arena sem id");
            if (dimension == null || center == null) throw new IllegalArgumentException("arena " + id + " sem dimensão/centro");
            shape = shape == null ? Shape.CIRCLE : shape;
            radiusX = Math.max(1D, radiusX);
            radiusZ = shape == Shape.CIRCLE ? radiusX : Math.max(1D, radiusZ);
            if (maxY < minY) {
                double t = minY;
                minY = maxY;
                maxY = t;
            }
            rgb &= 0xFFFFFF;
            name = name == null ? Component.empty() : name;
            warnDistance = Mth.clamp(warnDistance, 1, 64);
        }

        /** Arena circular com os padrões (cor dourada, sem nome, aviso a 10 blocos, parede ligada, sem limite vertical). */
        public static Arena circle(String id, ResourceKey<Level> dimension, Vec3 center, double radius) {
            return new Arena(id, dimension, Shape.CIRCLE, center, radius, radius, -Double.MAX_VALUE, Double.MAX_VALUE,
                    0xFFC85A, Component.empty(), DEFAULT_WARN, true);
        }

        /** Arena retangular cobrindo a caixa (horizontal) — a altura da caixa vira o limite vertical. */
        public static Arena box(String id, ResourceKey<Level> dimension, AABB box) {
            Vec3 c = box.getCenter();
            return new Arena(id, dimension, Shape.BOX, c, box.getXsize() / 2D, box.getZsize() / 2D, box.minY, box.maxY,
                    0xFFC85A, Component.empty(), DEFAULT_WARN, true);
        }

        public Arena withColor(int rgb) {
            return new Arena(id, dimension, shape, center, radiusX, radiusZ, minY, maxY, rgb, name, warnDistance, showWall);
        }

        public Arena withName(Component name) {
            return new Arena(id, dimension, shape, center, radiusX, radiusZ, minY, maxY, rgb, name, warnDistance, showWall);
        }

        public Arena withWarnDistance(int blocks) {
            return new Arena(id, dimension, shape, center, radiusX, radiusZ, minY, maxY, rgb, name, blocks, showWall);
        }

        public Arena withVertical(double minY, double maxY) {
            return new Arena(id, dimension, shape, center, radiusX, radiusZ, minY, maxY, rgb, name, warnDistance, showWall);
        }

        public Arena withWall(boolean wall) {
            return new Arena(id, dimension, shape, center, radiusX, radiusZ, minY, maxY, rgb, name, warnDistance, wall);
        }

        /** Raio (círculo) ou a maior meia-largura (retângulo). */
        public double size() {
            return Math.max(radiusX, radiusZ);
        }

        public boolean hasVerticalLimit() {
            return minY > -1.0E9 || maxY < 1.0E9;
        }

        /** Dentro da altura da arena (com folga de {@code slack} blocos). */
        public boolean inHeight(double y, double slack) {
            return y >= minY - slack && y <= maxY + slack;
        }

        /**
         * Distância horizontal até a borda: positiva DENTRO da arena (quanto falta para chegar na borda), negativa
         * FORA (quanto passou).
         */
        public double edgeDistance(Vec3 p) {
            double dx = p.x - center.x, dz = p.z - center.z;
            if (shape == Shape.CIRCLE) return radiusX - Math.sqrt(dx * dx + dz * dz);
            double ox = Math.abs(dx) - radiusX, oz = Math.abs(dz) - radiusZ;
            if (ox <= 0 && oz <= 0) return -Math.max(ox, oz);
            return -Math.sqrt(Math.max(0, ox) * Math.max(0, ox) + Math.max(0, oz) * Math.max(0, oz));
        }

        /** Dentro (horizontal e vertical). */
        public boolean contains(Vec3 p) {
            return inHeight(p.y, 0) && edgeDistance(p) >= 0;
        }

        /** Ponto da borda mais perto de {@code p} (na altura de {@code p}). */
        public Vec3 nearestEdge(Vec3 p) {
            double dx = p.x - center.x, dz = p.z - center.z;
            if (shape == Shape.CIRCLE) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 1.0E-4) return new Vec3(center.x + radiusX, p.y, center.z);
                return new Vec3(center.x + dx / d * radiusX, p.y, center.z + dz / d * radiusX);
            }
            double cx = Mth.clamp(dx, -radiusX, radiusX), cz = Mth.clamp(dz, -radiusZ, radiusZ);
            boolean inside = Math.abs(dx) <= radiusX && Math.abs(dz) <= radiusZ;
            if (inside) {
                // empurra para o lado mais perto
                if (radiusX - Math.abs(dx) < radiusZ - Math.abs(dz)) cx = Math.copySign(radiusX, dx == 0 ? 1 : dx);
                else cz = Math.copySign(radiusZ, dz == 0 ? 1 : dz);
            }
            return new Vec3(center.x + cx, p.y, center.z + cz);
        }

        /** {@code p} trazido para dentro da arena, a pelo menos {@code margin} blocos da borda (e dentro da altura). */
        public Vec3 clampInside(Vec3 p, double margin) {
            double y = hasVerticalLimit() ? Mth.clamp(p.y, minY, maxY) : p.y;
            double dx = p.x - center.x, dz = p.z - center.z;
            if (shape == Shape.CIRCLE) {
                double max = Math.max(0, radiusX - margin);
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= max) return new Vec3(p.x, y, p.z);
                double k = d < 1.0E-4 ? 0 : max / d;
                return new Vec3(center.x + dx * k, y, center.z + dz * k);
            }
            double mx = Math.max(0, radiusX - margin), mz = Math.max(0, radiusZ - margin);
            return new Vec3(center.x + Mth.clamp(dx, -mx, mx), y, center.z + Mth.clamp(dz, -mz, mz));
        }
    }

    // =====================================================================================
    //  Registro (servidor)
    // =====================================================================================

    /** Registra (ou substitui) uma arena circular. Devolve a arena registrada. */
    public static Arena register(ServerLevel level, String id, Vec3 center, double radius, int rgb, @Nullable Component name) {
        return register(level.getServer(), Arena.circle(id, level.dimension(), center, radius).withColor(rgb)
                .withName(name == null ? Component.empty() : name));
    }

    /** Registra (ou substitui) uma arena retangular (a altura da caixa vira o limite vertical). */
    public static Arena registerBox(ServerLevel level, String id, AABB box, int rgb, @Nullable Component name) {
        return register(level.getServer(), Arena.box(id, level.dimension(), box).withColor(rgb)
                .withName(name == null ? Component.empty() : name));
    }

    /** Registra (ou substitui) uma arena montada à mão e avisa os jogadores da dimensão. */
    public static Arena register(MinecraftServer server, Arena arena) {
        return ArenaService.register(server, arena);
    }

    /** Remove a arena (some da tela dos jogadores). Id desconhecido: nada acontece. */
    public static void unregister(MinecraftServer server, String id) {
        ArenaService.unregister(server, id);
    }

    /** Atalho de {@link #unregister(MinecraftServer, String)}. */
    public static void unregister(ServerLevel level, String id) {
        ArenaService.unregister(level.getServer(), id);
    }

    public static Optional<Arena> get(String id) {
        return ArenaService.get(id);
    }

    /** Arenas registradas na dimensão do nível (ordem de registro). */
    public static List<Arena> all(Level level) {
        return ArenaService.in(level.dimension());
    }

    /** A menor arena da dimensão que contém {@code pos} (ou vazio). */
    public static Optional<Arena> at(Level level, Vec3 pos) {
        Arena best = null;
        for (Arena a : ArenaService.in(level.dimension())) {
            if (a.contains(pos) && (best == null || a.size() < best.size())) best = a;
        }
        return Optional.ofNullable(best);
    }

    private ArenaBounds() {}
}
