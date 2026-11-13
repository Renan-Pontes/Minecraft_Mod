package com.renanpontes.isekaimahou.feature.espada.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaFx;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * SÓ CLIENTE. Geometria desenhada no mundo para os golpes grandes dos Espada (o que partícula não faz bem):
 * <ul>
 *   <li><b>feixes</b> (Cero, Cero Oscuras, Gran Rey Cero): fitas viradas para a câmera em camadas — brilho aditivo por
 *       fora, miolo branco; o Oscuras tem miolo NEGRO (camada translúcida normal) com bordas verdes;</li>
 *   <li><b>orbes</b> de carga que crescem na mão (seguem a entidade);</li>
 *   <li><b>lanças</b> (Lanza del Relámpago na mão e cravada no chão);</li>
 *   <li><b>raios</b> em zigue-zague (explosão da Lanza, crepitar do Cero Oscuras) — v1.3.2.</li>
 * </ul>
 * Tudo no estágio AFTER_PARTICLES, com limite de quantidade e distância ({@link FxQuality#range()}).
 */
public final class EspadaBeams {
    private static final int MAX = 48;

    private static final RenderType ADD = RenderType.create("isekai_mahou_espada_add", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));
    private static final RenderType DARK = RenderType.create("isekai_mahou_espada_dark", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    /** Tipos de coisa desenhada. */
    private enum Kind { BEAM, ORB, LANCE_PLANTED, BOLT }

    private static final class Item {
        final Kind kind;
        Vec3 a, b;
        final float width;
        final int color, style, life;
        final int entity;
        int age;
        /** Raio: pontos do zigue-zague (de a até b). */
        @Nullable
        Vec3[] pts;

        Item(Kind kind, Vec3 a, Vec3 b, float width, int color, int style, int life, int entity) {
            this.kind = kind;
            this.a = a;
            this.b = b;
            this.width = width;
            this.color = color;
            this.style = style;
            this.life = Math.max(1, life);
            this.entity = entity;
        }
    }

    private static final List<Item> ITEMS = new ArrayList<>();
    @Nullable
    private static ClientLevel itemsLevel;

    private static void add(Item it) {
        ClientLevel l = Minecraft.getInstance().level;
        if (l != itemsLevel) {
            ITEMS.clear();
            itemsLevel = l;
        }
        if (ITEMS.size() >= MAX) ITEMS.remove(0);
        ITEMS.add(it);
    }

    public static void beam(Vec3 from, Vec3 to, float width, int life, int color, int style) {
        add(new Item(Kind.BEAM, from, to, width, color, style, life, -1));
    }

    /** Orbe de carga: cresce até {@code size} em {@code life} ticks; segue a entidade (mão) se houver. */
    public static void orb(Vec3 at, float size, int life, int color, int style, int entity) {
        add(new Item(Kind.ORB, at, Vec3.ZERO, size, color, style, life, entity));
    }

    /** Lança cravada no chão, inclinada, brilhando até a explosão. */
    public static void lancePlanted(Vec3 at, int life) {
        Vec3 tilt = new Vec3(0.25, 1, 0.15).normalize();
        add(new Item(Kind.LANCE_PLANTED, at.subtract(tilt.scale(0.5)), at.add(tilt.scale(5.5)), 0.6F, EspadaFx.ULQ_GREEN, 0, life, -1));
    }

    /**
     * Raio em zigue-zague de {@code from} a {@code to} ({@code jag} = desvio máximo em blocos), brilho da cor por fora
     * e miolo branco; pisca e some em {@code life} ticks.
     */
    public static void lightning(Vec3 from, Vec3 to, float width, int life, int color, double jag) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.2) return;
        int n = Mth.clamp((int) (len / 0.9), 4, 28);
        Vec3 f = d.scale(1.0 / len);
        Vec3 r1 = f.cross(new Vec3(0, 1, 0));
        if (r1.lengthSqr() < 1.0E-4) r1 = new Vec3(1, 0, 0);
        r1 = r1.normalize();
        Vec3 r2 = r1.cross(f).normalize();
        Vec3[] pts = new Vec3[n + 1];
        pts[0] = from;
        pts[n] = to;
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            double k = jag * (1.0 - Math.abs(2 * t - 1) * 0.4);
            pts[i] = from.add(d.scale(t)).add(r1.scale((EspadaFxKit.RND.nextDouble() - 0.5) * 2 * k))
                    .add(r2.scale((EspadaFxKit.RND.nextDouble() - 0.5) * 2 * k));
        }
        Item it = new Item(Kind.BOLT, from, to, width, color, 0, life, -1);
        it.pts = pts;
        add(it);
    }

    static void tick() {
        if (ITEMS.isEmpty()) return;
        if (Minecraft.getInstance().level != itemsLevel) {
            ITEMS.clear();
            return;
        }
        Iterator<Item> it = ITEMS.iterator();
        while (it.hasNext()) {
            Item i = it.next();
            if (++i.age > i.life) it.remove();
        }
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ITEMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != itemsLevel) return;
        float partial = mc.isPaused() ? 0 : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        double range = FxQuality.range() + 32;

        // Primeiro o miolo escuro (translúcido normal), depois o brilho aditivo por cima.
        BufferBuilder dark = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean anyDark = false;
        for (Item i : ITEMS) {
            if (!resolve(level, i, partial) || !visible(i, cp, range)) continue;
            float k = fade(i, partial);
            if (i.kind == Kind.BEAM && i.style == EspadaFx.STYLE_DARK) {
                ribbon(dark, cp, i.a, i.b, i.width * 0.72F * k, 0x000000, 0.92F);
                ribbon(dark, cp, i.a, i.b, i.width * 0.95F * k, 0x041008, 0.45F);
                anyDark = true;
            } else if (i.kind == Kind.ORB && i.style == EspadaFx.STYLE_DARK) {
                float s = orbSize(i, partial);
                disk(dark, cam, cp, i.a, s * 0.8F, 0x000000, 0.95F);
                anyDark = true;
            }
        }
        draw(dark, anyDark, DARK);

        BufferBuilder add = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        boolean hi = FxQuality.atLeast(Graphics.HIGH);
        float time = (level.getGameTime() % 1000 + partial);
        for (Item i : ITEMS) {
            if (!resolve(level, i, partial) || !visible(i, cp, range)) continue;
            any = true;
            float k = fade(i, partial);
            float wob = 1F + 0.08F * Mth.sin(time * 1.7F + i.age);
            switch (i.kind) {
                case BEAM -> {
                    float w = i.width * k * wob;
                    if (i.style == EspadaFx.STYLE_DARK) {
                        // Bordas verdes do Cero Oscuras.
                        Vec3 side = sideOf(cp, i.a, i.b);
                        float off = i.width * 0.42F * k;
                        ribbon(add, cp, i.a.add(side.scale(off)), i.b.add(side.scale(off)), w * 0.18F, EspadaFx.ULQ_GREEN, 0.9F);
                        ribbon(add, cp, i.a.subtract(side.scale(off)), i.b.subtract(side.scale(off)), w * 0.18F, EspadaFx.ULQ_GREEN, 0.9F);
                        if (hi) ribbon(add, cp, i.a, i.b, w * 1.25F, EspadaFx.ULQ_DEEP, 0.35F);
                    } else {
                        int outer = i.style == EspadaFx.STYLE_BLOOD ? 0x2F6BFF : i.color;
                        ribbon(add, cp, i.a, i.b, w, outer, 0.55F);
                        ribbon(add, cp, i.a, i.b, w * 0.55F, light(outer, 0.45F), 0.75F);
                        ribbon(add, cp, i.a, i.b, w * 0.22F, 0xFFFFFF, 0.95F);
                        if (i.style == EspadaFx.STYLE_BLOOD) {
                            // Veias de sangue enroladas no feixe.
                            Vec3 side = sideOf(cp, i.a, i.b);
                            float s1 = Mth.sin(time * 0.9F) * i.width * 0.3F * k;
                            ribbon(add, cp, i.a.add(side.scale(s1)), i.b.subtract(side.scale(s1)), w * 0.14F, EspadaFx.BLOOD, 0.9F);
                            ribbon(add, cp, i.a.subtract(side.scale(s1)), i.b.add(side.scale(s1)), w * 0.1F, 0xFF3040, 0.8F);
                        }
                    }
                }
                case ORB -> {
                    float s = orbSize(i, partial) * wob;
                    if (i.style == EspadaFx.STYLE_DARK) {
                        ring(add, cam, cp, i.a, s * 0.8F, s * 1.05F, EspadaFx.ULQ_GREEN, 0.9F);
                        if (hi) ring(add, cam, cp, i.a, s * 1.05F, s * 1.6F, EspadaFx.ULQ_DEEP, 0.3F);
                    } else {
                        int c = i.style == EspadaFx.STYLE_BLOOD ? 0x2F6BFF : i.color;
                        disk(add, cam, cp, i.a, s * 1.5F, c, 0.25F);
                        disk(add, cam, cp, i.a, s, c, 0.6F);
                        disk(add, cam, cp, i.a, s * 0.45F, 0xFFFFFF, 0.95F);
                        if (i.style == EspadaFx.STYLE_BLOOD) ring(add, cam, cp, i.a, s * 0.9F, s * 1.05F, EspadaFx.BLOOD, 0.9F);
                    }
                }
                case BOLT -> {
                    if (i.pts == null) break;
                    // Pisca: some e volta a cada 2 ticks no começo.
                    float flick = (i.age % 3 == 2) ? 0.45F : 1F;
                    for (int q = 0; q + 1 < i.pts.length; q++) {
                        ribbon(add, cp, i.pts[q], i.pts[q + 1], i.width * k * flick, i.color, 0.6F);
                        ribbon(add, cp, i.pts[q], i.pts[q + 1], i.width * 0.35F * k, 0xF2FFF6, 0.95F * flick);
                    }
                }
                case LANCE_PLANTED -> {
                    float grow = Mth.clamp((i.age + partial) / 4F, 0.2F, 1F);
                    float flick = 0.85F + 0.15F * Mth.sin(time * 3.1F);
                    ribbon(add, cp, i.a, i.b, 0.9F * grow * flick, EspadaFx.ULQ_GREEN, 0.5F);
                    ribbon(add, cp, i.a, i.b, 0.35F * grow, 0xE8FFF0, 0.95F);
                    // Ponta em losango.
                    Vec3 dir = i.b.subtract(i.a).normalize();
                    ribbon(add, cp, i.b, i.b.add(dir.scale(1.2 * grow)), 0.5F * grow, light(EspadaFx.ULQ_GREEN, 0.4F), 0.9F);
                    disk(add, cam, cp, i.a.add(0, 0.2, 0), 2.2F * flick, EspadaFx.ULQ_GREEN, 0.35F);
                }
            }
        }
        draw(add, any, ADD);
    }

    /** Atualiza posições que seguem entidades; devolve false se a coisa não pode ser desenhada. */
    private static boolean resolve(ClientLevel level, Item i, float partial) {
        if (i.entity < 0 || i.kind != Kind.ORB) return true;
        Entity e = level.getEntity(i.entity);
        if (e == null) return true;
        double x = Mth.lerp(partial, e.xo, e.getX()), y = Mth.lerp(partial, e.yo, e.getY()), z = Mth.lerp(partial, e.zo, e.getZ());
        float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
        Vec3 fwd = Vec3.directionFromRotation(0, yaw);
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        // O orbe fica na ponta da mão direita (segue o chefe virando).
        i.a = new Vec3(x, y + e.getBbHeight() * 0.74, z).add(fwd.scale(0.85)).add(right.scale(-0.3));
        return true;
    }

    private static boolean visible(Item i, Vec3 cam, double range) {
        Vec3 mid = i.kind == Kind.BEAM ? i.a.add(i.b).scale(0.5) : i.a;
        double r = range + (i.kind == Kind.BEAM ? i.a.distanceTo(i.b) * 0.5 : 0);
        return mid.distanceToSqr(cam) <= r * r;
    }

    /** Entra rápido (2 ticks) e afina no último terço da vida. */
    private static float fade(Item i, float partial) {
        float t = i.age + partial;
        if (i.kind == Kind.ORB) return 1F;
        float in = Mth.clamp(t / 2F, 0F, 1F);
        float out = Mth.clamp((i.life - t) / (i.life * 0.35F), 0F, 1F);
        return in * out;
    }

    private static float orbSize(Item i, float partial) {
        float t = Mth.clamp((i.age + partial) / i.life, 0F, 1F);
        return i.width * (0.15F + 0.85F * t * t);
    }

    private static Vec3 sideOf(Vec3 cam, Vec3 a, Vec3 b) {
        Vec3 dir = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = dir.cross(cam.subtract(mid));
        if (side.lengthSqr() < 1.0E-6) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6) side = new Vec3(1, 0, 0);
        return side.normalize();
    }

    /** Fita de {@code a} a {@code b} virada para a câmera. */
    private static void ribbon(BufferBuilder buf, Vec3 cam, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        if (width <= 0.001F) return;
        Vec3 s = sideOf(cam, a, b).scale(width * 0.5);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255, al = (int) (Mth.clamp(alpha, 0F, 1F) * 255);
        vtx(buf, cam, a.add(s), r, g, bl, al);
        vtx(buf, cam, a.subtract(s), r, g, bl, al);
        vtx(buf, cam, b.subtract(s), r, g, bl, al);
        vtx(buf, cam, b.add(s), r, g, bl, al);
    }

    /** Disco virado para a câmera (leque de quads degenerados), mais claro no centro. */
    private static void disk(BufferBuilder buf, Camera camera, Vec3 cam, Vec3 c, float radius, int rgb, float alpha) {
        ring(buf, camera, cam, c, 0F, radius, rgb, alpha);
    }

    private static void ring(BufferBuilder buf, Camera camera, Vec3 cam, Vec3 c, float inner, float outer, int rgb, float alpha) {
        Vector3f l = camera.getLeftVector(), u = camera.getUpVector();
        Vec3 left = new Vec3(l.x(), l.y(), l.z()), up = new Vec3(u.x(), u.y(), u.z());
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        int aIn = (int) (Mth.clamp(alpha, 0F, 1F) * 255), aOut = inner <= 0 ? aIn / 3 : aIn;
        int seg = 18;
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            Vec3 d0 = left.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
            Vec3 d1 = left.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            vtx(buf, cam, c.add(d0.scale(inner)), r, g, bl, aIn);
            vtx(buf, cam, c.add(d0.scale(outer)), r, g, bl, aOut);
            vtx(buf, cam, c.add(d1.scale(outer)), r, g, bl, aOut);
            vtx(buf, cam, c.add(d1.scale(inner)), r, g, bl, aIn);
        }
    }

    private static void vtx(BufferBuilder buf, Vec3 cam, Vec3 p, int r, int g, int b, int a) {
        buf.addVertex((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).setColor(r, g, b, a);
    }

    private static void draw(BufferBuilder buf, boolean any, RenderType type) {
        MeshData mesh = buf.build();
        if (mesh == null) return;
        if (any) type.draw(mesh);
        else mesh.close();
    }

    private static int light(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r + (255 - r) * f);
        g = (int) (g + (255 - g) * f);
        b = (int) (b + (255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    private EspadaBeams() {}
}
