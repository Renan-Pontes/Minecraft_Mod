package com.renanpontes.isekaimahou.client.ux;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.ArenaBounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Borda das arenas ({@link ArenaBounds}) no mundo: uma parede de luz translúcida na cor da arena, com faixas que
 * sobem devagar (runas de energia), desenhada SÓ perto do jogador (um trecho de ~28 blocos para cada lado do ponto da
 * borda mais próximo) e cada vez mais forte conforme ele chega perto. Some longe da borda. Mais umas faíscas de pó na
 * cor da arena subindo pela parede (menos com "Reduzir partículas").
 */
final class ArenaWallRenderer {
    /** Meio comprimento (blocos) do trecho de parede desenhado. */
    private static final double SPAN = 28;
    private static final int SLICES = 9;
    private static final double BELOW = 3, ABOVE = 7;

    /** Translúcida, sem cull (vê dos dois lados), testa profundidade sem escrever; alvo de partículas (Fabuloso). */
    private static final RenderType WALL = RenderType.create("isekai_mahou_arena_wall",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!MahouConfig.safe(MahouConfig.SHOW_ARENA_BOUNDS)) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;
        List<ArenaBounds.Arena> arenas = ClientArenas.all();
        if (arenas.isEmpty()) return;
        float partial = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 p = player.getPosition(partial);
        Vec3 cam = e.getCamera().getPosition();
        double time = level.getGameTime() + partial;

        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (ArenaBounds.Arena a : arenas) {
            if (!a.showWall() || !a.inHeight(p.y, 8)) continue;
            double d = a.edgeDistance(p);
            double range = a.warnDistance() + 4;
            if (Math.abs(d) > range) continue;
            float strength = Mth.clamp(1F - (float) (Math.abs(d) / range), 0F, 1F);
            strength = 0.18F + 0.82F * strength * strength;
            if (d < 0) strength = 1F; // fora: parede bem visível para achar o caminho de volta
            for (List<double[]> line : perimeter(a, p)) drawLine(buf, a, line, p, cam, time, strength);
        }
        MeshData mesh = buf.build();
        if (mesh != null) WALL.draw(mesh);
    }

    /** Trechos (polilinhas x,z) da borda perto de {@code p}. */
    private static List<List<double[]>> perimeter(ArenaBounds.Arena a, Vec3 p) {
        List<List<double[]>> out = new ArrayList<>();
        Vec3 c = a.center();
        if (a.shape() == ArenaBounds.Shape.CIRCLE) {
            double r = a.radiusX();
            double theta = Math.atan2(p.z - c.z, p.x - c.x);
            double span = Math.min(Math.PI, SPAN / r);
            int steps = Mth.clamp((int) Math.ceil(2 * span * r), 8, 160);
            List<double[]> line = new ArrayList<>(steps + 1);
            for (int i = 0; i <= steps; i++) {
                double t = theta - span + 2 * span * i / steps;
                line.add(new double[] {c.x + Math.cos(t) * r, c.z + Math.sin(t) * r});
            }
            out.add(line);
            return out;
        }
        double x0 = c.x - a.radiusX(), x1 = c.x + a.radiusX(), z0 = c.z - a.radiusZ(), z1 = c.z + a.radiusZ();
        double[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        for (int s = 0; s < 4; s++) {
            double[] from = corners[s], to = corners[(s + 1) % 4];
            double len = Math.hypot(to[0] - from[0], to[1] - from[1]);
            if (len < 1.0E-3) continue;
            double ux = (to[0] - from[0]) / len, uz = (to[1] - from[1]) / len;
            double proj = (p.x - from[0]) * ux + (p.z - from[1]) * uz;
            double perp = Math.abs((p.x - from[0]) * uz - (p.z - from[1]) * ux);
            if (perp > SPAN) continue;
            double a0 = Math.max(0, proj - SPAN), a1 = Math.min(len, proj + SPAN);
            if (a1 - a0 < 0.5) continue;
            int steps = Mth.clamp((int) Math.ceil(a1 - a0), 1, 120);
            List<double[]> line = new ArrayList<>(steps + 1);
            for (int i = 0; i <= steps; i++) {
                double t = a0 + (a1 - a0) * i / steps;
                line.add(new double[] {from[0] + ux * t, from[1] + uz * t});
            }
            out.add(line);
        }
        return out;
    }

    private static void drawLine(BufferBuilder buf, ArenaBounds.Arena a, List<double[]> line, Vec3 p, Vec3 cam, double time,
                                 float strength) {
        double yb = Math.floor(p.y) - BELOW, yt = Math.floor(p.y) + ABOVE;
        if (a.hasVerticalLimit()) {
            yb = Math.max(yb, a.minY());
            yt = Math.min(yt, a.maxY());
        }
        if (yt - yb < 0.5) return;
        int rgb = a.rgb();
        for (int i = 0; i + 1 < line.size(); i++) {
            double[] q0 = line.get(i), q1 = line.get(i + 1);
            float f0 = horizontalFade(q0, p), f1 = horizontalFade(q1, p);
            if (f0 <= 0.01F && f1 <= 0.01F) continue;
            for (int s = 0; s < SLICES; s++) {
                double ya = yb + (yt - yb) * s / SLICES, yc = yb + (yt - yb) * (s + 1) / SLICES;
                float va = verticalFade(s / (float) SLICES), vc = verticalFade((s + 1) / (float) SLICES);
                int c00 = color(rgb, strength * f0 * va * shimmer(time, ya, i));
                int c10 = color(rgb, strength * f1 * va * shimmer(time, ya, i + 1));
                int c11 = color(rgb, strength * f1 * vc * shimmer(time, yc, i + 1));
                int c01 = color(rgb, strength * f0 * vc * shimmer(time, yc, i));
                vertex(buf, cam, q0[0], ya, q0[1], c00);
                vertex(buf, cam, q1[0], ya, q1[1], c10);
                vertex(buf, cam, q1[0], yc, q1[1], c11);
                vertex(buf, cam, q0[0], yc, q0[1], c01);
            }
        }
    }

    private static void vertex(BufferBuilder buf, Vec3 cam, double x, double y, double z, int argb) {
        buf.addVertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(argb);
    }

    /** Some com a distância (na horizontal) até o jogador: inteira até 6 blocos, nada a partir de ~28. */
    private static float horizontalFade(double[] q, Vec3 p) {
        double h = Math.hypot(q[0] - p.x, q[1] - p.z);
        return Mth.clamp(1F - (float) ((h - 6) / (SPAN - 6)), 0F, 1F);
    }

    /** Transparente em cima e embaixo, cheia no meio. */
    private static float verticalFade(float v) {
        return (float) Math.pow(Math.max(0F, Mth.sin(v * Mth.PI)), 0.7F);
    }

    /** Faixas de energia subindo devagar + colunas mais claras a cada ~2 blocos. */
    private static float shimmer(double time, double y, int column) {
        float band = 0.55F + 0.45F * Mth.sin((float) (y * 0.9 - time * 0.12));
        float rib = (column % 2 == 0) ? 1F : 0.78F;
        return band * rib;
    }

    private static int color(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 0x8C), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Faíscas subindo pela parede perto do jogador (chamado a cada tick do cliente). */
    static void particles(Minecraft mc, ClientArenas.Focus focus, RandomSource random) {
        if (mc.level == null || mc.player == null || !focus.arena().showWall()) return;
        boolean reduced = MahouConfig.safe(MahouConfig.REDUCED_PARTICLES);
        float close = focus.inside() ? focus.closeness() : 1F;
        if (close < 0.35F || random.nextFloat() > (reduced ? 0.25F : 0.8F) * close) return;
        ArenaBounds.Arena a = focus.arena();
        Vec3 p = mc.player.position();
        Vec3 edge = a.nearestEdge(p);
        // ao longo da parede: no círculo, perpendicular ao raio; no retângulo, paralelo ao lado
        double dx = edge.x - a.center().x, dz = edge.z - a.center().z;
        double tx, tz;
        if (a.shape() == ArenaBounds.Shape.CIRCLE) {
            double len = Math.max(1.0E-3, Math.hypot(dx, dz));
            tx = -dz / len;
            tz = dx / len;
        } else {
            boolean xFace = Math.abs(Math.abs(dx) - a.radiusX()) < 1.0E-3;
            tx = xFace ? 0 : 1;
            tz = xFace ? 1 : 0;
        }
        double along = (random.nextDouble() - 0.5) * 12;
        double y = Math.floor(p.y) - 1 + random.nextDouble() * 5;
        if (a.hasVerticalLimit()) y = Mth.clamp(y, a.minY(), a.maxY());
        int rgb = a.rgb();
        Vector3f color = new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
        mc.level.addParticle(new DustParticleOptions(color, 0.9F), edge.x + tx * along, y, edge.z + tz * along, 0, 0.03, 0);
    }

    private ArenaWallRenderer() {}
}
