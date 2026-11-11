package com.renanpontes.isekaimahou.client.ux;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.ArenaBounds;
import com.renanpontes.isekaimahou.core.ux.UxPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Cópia no cliente das arenas ({@link ArenaBounds}) da dimensão onde o jogador está (o servidor manda a lista inteira
 * a cada mudança). {@link #focus} escolhe a arena relevante agora: a de borda mais perto entre as que o jogador está
 * dentro ou quase (até o raio de aviso).
 */
public final class ClientArenas {
    private static ResourceKey<Level> dimension;
    private static List<ArenaBounds.Arena> arenas = List.of();

    /** Arena relevante + distância da borda (positiva dentro, negativa fora). */
    public record Focus(ArenaBounds.Arena arena, double edgeDistance) {
        public boolean inside() {
            return edgeDistance >= 0;
        }

        /** 0 = longe (no limite do aviso), 1 = em cima da borda (ou fora). */
        public float closeness() {
            float d = (float) Math.max(0D, edgeDistance);
            return 1F - Math.min(1F, d / arena.warnDistance());
        }
    }

    static void apply(UxPayloads.ArenaSync p) {
        dimension = p.dim();
        arenas = List.copyOf(p.arenas());
    }

    /** Arenas conhecidas da dimensão atual (vazia se a lista é de outra dimensão). */
    public static List<ArenaBounds.Arena> all() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || dimension == null || !mc.level.dimension().equals(dimension)) return List.of();
        return arenas;
    }

    @Nullable
    public static Focus focus(Vec3 pos) {
        Focus best = null;
        for (ArenaBounds.Arena a : all()) {
            if (!a.inHeight(pos.y, 4)) continue;
            double d = a.edgeDistance(pos);
            if (d > a.warnDistance() || d < -a.warnDistance()) continue;
            if (best == null || Math.abs(d) < Math.abs(best.edgeDistance())) best = new Focus(a, d);
        }
        return best;
    }

    static void clear() {
        dimension = null;
        arenas = List.of();
    }

    private ClientArenas() {}
}
