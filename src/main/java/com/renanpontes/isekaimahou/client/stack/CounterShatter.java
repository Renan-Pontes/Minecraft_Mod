package com.renanpontes.isekaimahou.client.stack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.stack.StackPayloads;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * O círculo rúnico que ESTILHAÇA quando uma conjuração é contrariada (cliente): um círculo mágico procedural (anéis,
 * runas e hexagrama) aparece brilhando e se parte em pedaços que voam, giram e somem. Um deitado no ponto do golpe e
 * outro de frente para a câmera no peito do conjurador. Desenhado no estágio das partículas (sem textura).
 */
final class CounterShatter {
    private static final int LIFE = 28;
    private static final int INTACT = 3;
    private static final int MAX_FX = 24;
    private static final double DRAW_RANGE = 96;

    private static final RenderType TYPE = RenderType.create("isekai_mahou_stack_shatter",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    /** Um pedaço: quads locais (relativos ao centro do pedaço) + para onde voa. */
    private record Piece(float[] quads, float cu, float cv, float du, float dv, float speed, float spin, float shade) {}

    private static final class Fx {
        final Vec3 center;
        final boolean billboard;
        final float radius;
        final int rgb;
        final double start;
        final List<Piece> pieces;
        final List<Piece> cracks;

        Fx(Vec3 center, boolean billboard, float radius, int rgb, double start, long seed) {
            this.center = center;
            this.billboard = billboard;
            this.radius = radius;
            this.rgb = rgb;
            this.start = start;
            this.pieces = new ArrayList<>();
            this.cracks = new ArrayList<>();
            build(this, RandomSource.create(seed));
        }
    }

    private static final List<Fx> FX = new ArrayList<>();
    private static ClientLevel fxLevel;

    static void spawn(StackPayloads.Countered p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        if (level != fxLevel) {
            FX.clear();
            fxLevel = level;
        }
        while (FX.size() >= MAX_FX) FX.remove(0);
        double now = ClientStack.ticks();
        int rgb = p.rgb() & 0xFFFFFF;
        float r = Mth.clamp(p.radius() * 0.75F, 1.2F, 5.5F);
        FX.add(new Fx(new Vec3(p.x(), p.y() + 0.08, p.z()), false, r, rgb, now, p.id() * 7919L));
        Entity caster = p.casterId() >= 0 ? level.getEntity(p.casterId()) : null;
        if (caster != null) {
            Vec3 chest = caster.position().add(0, caster.getBbHeight() * 0.55, 0);
            float cr = Mth.clamp(caster.getBbWidth() * 0.9F + 0.7F, 0.9F, 4.0F);
            FX.add(new Fx(chest, true, cr, rgb, now, p.id() * 104729L + 1));
        }
    }

    static void clear() {
        FX.clear();
        fxLevel = null;
    }

    static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FX.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != fxLevel) {
            FX.clear();
            return;
        }
        float partial = mc.isPaused() ? 0 : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = ClientStack.ticks() + partial;
        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        Vector3f left = cam.getLeftVector();
        Vector3f up = cam.getUpVector();

        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Iterator<Fx> it = FX.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            double age = now - fx.start;
            if (age >= LIFE || age < -1) {
                it.remove();
                continue;
            }
            if (fx.center.distanceToSqr(camPos) > DRAW_RANGE * DRAW_RANGE) continue;
            draw(buf, fx, (float) Math.max(0, age), camPos, left, up);
        }
        // Sempre fecha o buffer (sem vértices o build devolve null)
        MeshData mesh = buf.build();
        if (mesh != null) TYPE.draw(mesh);
    }

    // ------------------------------------------------------------------ desenho

    private static void draw(BufferBuilder buf, Fx fx, float age, Vec3 cam, Vector3f left, Vector3f up) {
        float t = age < INTACT ? 0F : Math.min(1F, (age - INTACT) / (LIFE - INTACT));
        float flash = age < INTACT ? 1F - age / INTACT * 0.4F : Math.max(0F, 0.6F - t * 2F);
        float alpha = (float) Math.pow(1F - t, 1.3F);
        int base = 0xFF000000 | fx.rgb;
        float out = 1F - (1F - t) * (1F - t);   // sai rápido e freia
        for (Piece p : fx.pieces) {
            float dist = fx.radius * 0.95F * out * p.speed;
            float ang = p.spin * t * 2.6F;
            float lift = fx.billboard ? -0.55F * t * t * fx.radius : 0.7F * t - 1.1F * t * t;
            int color = MahouUi.mix(MahouUi.lighten(base, 0.35F * p.shade), 0xFFFFFFFF, flash * 0.7F);
            emit(buf, fx, p, p.cu + p.du * dist, p.cv + p.dv * dist, ang, lift, color, alpha * 0.85F, cam, left, up);
        }
        // Rachaduras brilhantes só no instante da quebra
        if (age < INTACT + 4) {
            float ca = 1F - Math.max(0F, (age - INTACT) / 4F);
            for (Piece c : fx.cracks) emit(buf, fx, c, c.cu, c.cv, 0, 0, 0xFFFFFFFF, ca * 0.9F, cam, left, up);
        }
    }

    /** Escreve os quads do pedaço girados por {@code ang}, com o centro em (u,v) e deslocamento vertical {@code lift}. */
    private static void emit(BufferBuilder buf, Fx fx, Piece p, float u, float v, float ang, float lift, int argb, float alpha,
                             Vec3 cam, Vector3f left, Vector3f up) {
        int a = Mth.clamp(Math.round(((argb >>> 24) & 255) * alpha), 0, 255);
        if (a <= 2) return;
        int r = (argb >> 16) & 255, gr = (argb >> 8) & 255, b = argb & 255;
        float cs = Mth.cos(ang), sn = Mth.sin(ang);
        float[] q = p.quads;
        for (int i = 0; i + 1 < q.length; i += 2) {
            float lu = q[i] * cs - q[i + 1] * sn + u;
            float lv = q[i] * sn + q[i + 1] * cs + v;
            double x, y, z;
            if (fx.billboard) {
                x = fx.center.x + left.x() * lu + up.x() * (lv + lift);
                y = fx.center.y + left.y() * lu + up.y() * (lv + lift);
                z = fx.center.z + left.z() * lu + up.z() * (lv + lift);
            } else {
                x = fx.center.x + lu;
                y = fx.center.y + lift;
                z = fx.center.z + lv;
            }
            buf.addVertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, gr, b, a);
        }
    }

    // ------------------------------------------------------------------ geometria (círculo mágico procedural)

    private static void build(Fx fx, RandomSource rnd) {
        float R = fx.radius;
        // Anel externo (duas faixas finas) e anel interno, partidos em arcos
        ring(fx, rnd, R * 0.92F, R, 20, 0.06F);
        ring(fx, rnd, R * 0.80F, R * 0.84F, 16, 0.08F);
        ring(fx, rnd, R * 0.56F, R * 0.62F, 12, 0.07F);
        // Runas entre os anéis
        int runes = 12;
        for (int i = 0; i < runes; i++) {
            double a = (i + 0.5) / runes * Math.PI * 2;
            float rr = R * 0.70F;
            float cu = (float) Math.cos(a) * rr, cv = (float) Math.sin(a) * rr;
            float s = R * 0.055F;
            List<float[]> qs = new ArrayList<>();
            // Haste radial + traço cruzado + ponto (cada runa um pouco diferente)
            qs.add(rect(cu, cv, (float) a, s * 2.2F, s * 0.35F, 0, 0));
            int kind = rnd.nextInt(3);
            if (kind == 0) qs.add(rect(cu, cv, (float) (a + Math.PI / 2), s * 1.2F, s * 0.3F, s * 0.6F, 0));
            else if (kind == 1) qs.add(rect(cu, cv, (float) (a + Math.PI / 4), s * 1.3F, s * 0.3F, 0, 0));
            else qs.add(rect(cu, cv, (float) (a - Math.PI / 4), s * 1.3F, s * 0.3F, -s * 0.5F, 0));
            fx.pieces.add(piece(qs, cu, cv, rnd, 1.0F));
        }
        // Hexagrama (dois triângulos) inscrito no anel interno, cada lado partido em dois
        float hr = R * 0.56F;
        for (int tri = 0; tri < 2; tri++) {
            for (int k = 0; k < 3; k++) {
                double a0 = tri * Math.PI / 3 + k * Math.PI * 2 / 3 + Math.PI / 2;
                double a1 = a0 + Math.PI * 2 / 3;
                float x0 = (float) Math.cos(a0) * hr, y0 = (float) Math.sin(a0) * hr;
                float x1 = (float) Math.cos(a1) * hr, y1 = (float) Math.sin(a1) * hr;
                float mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
                line(fx, rnd, x0, y0, mx, my, R * 0.022F);
                line(fx, rnd, mx, my, x1, y1, R * 0.022F);
            }
        }
        // Rachaduras do centro para fora
        for (int k = 0; k < 7; k++) {
            double a = (k + rnd.nextFloat() * 0.7) / 7 * Math.PI * 2;
            float px = 0, py = 0;
            List<float[]> qs = new ArrayList<>();
            for (int s = 0; s < 4; s++) {
                a += (rnd.nextFloat() - 0.5F) * 0.8F;
                float step = R * (0.22F + rnd.nextFloat() * 0.08F);
                float nx = px + (float) Math.cos(a) * step, ny = py + (float) Math.sin(a) * step;
                qs.add(segmentQuad(px, py, nx, ny, R * 0.012F));
                px = nx;
                py = ny;
            }
            fx.cracks.add(new Piece(flatten(qs), 0, 0, 0, 0, 0, 0, 1));
        }
    }

    private static void ring(Fx fx, RandomSource rnd, float r0, float r1, int segments, float gap) {
        for (int i = 0; i < segments; i++) {
            double a0 = (i + gap) / segments * Math.PI * 2;
            double a1 = (i + 1 - gap) / segments * Math.PI * 2;
            double am = (a0 + a1) / 2;
            float rm = (r0 + r1) / 2;
            float cu = (float) Math.cos(am) * rm, cv = (float) Math.sin(am) * rm;
            List<float[]> qs = new ArrayList<>();
            int sub = 2;
            for (int k = 0; k < sub; k++) {
                double s0 = a0 + (a1 - a0) * k / sub, s1 = a0 + (a1 - a0) * (k + 1) / sub;
                qs.add(new float[] {
                        (float) Math.cos(s0) * r0 - cu, (float) Math.sin(s0) * r0 - cv,
                        (float) Math.cos(s1) * r0 - cu, (float) Math.sin(s1) * r0 - cv,
                        (float) Math.cos(s1) * r1 - cu, (float) Math.sin(s1) * r1 - cv,
                        (float) Math.cos(s0) * r1 - cu, (float) Math.sin(s0) * r1 - cv});
            }
            fx.pieces.add(piece(qs, cu, cv, rnd, 0.6F + rnd.nextFloat() * 0.4F));
        }
    }

    private static void line(Fx fx, RandomSource rnd, float x0, float y0, float x1, float y1, float hw) {
        float cu = (x0 + x1) / 2, cv = (y0 + y1) / 2;
        float[] q = segmentQuad(x0 - cu, y0 - cv, x1 - cu, y1 - cv, hw);
        List<float[]> qs = new ArrayList<>();
        qs.add(q);
        fx.pieces.add(piece(qs, cu, cv, rnd, 0.8F));
    }

    /** Quad fino de (x0,y0) a (x1,y1), meia largura {@code hw}. */
    private static float[] segmentQuad(float x0, float y0, float x1, float y1, float hw) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        float nx = len < 1.0E-4F ? 0 : -dy / len * hw, ny = len < 1.0E-4F ? hw : dx / len * hw;
        return new float[] {x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny};
    }

    /** Retângulo de comprimento {@code len} e meia largura {@code hw} girado por {@code ang}, deslocado (ox, oy) no próprio eixo. */
    private static float[] rect(float cu, float cv, float ang, float len, float hw, float ox, float oy) {
        float cs = Mth.cos(ang), sn = Mth.sin(ang);
        float[] base = {-len / 2 + ox, -hw + oy, len / 2 + ox, -hw + oy, len / 2 + ox, hw + oy, -len / 2 + ox, hw + oy};
        float[] out = new float[8];
        for (int i = 0; i < 8; i += 2) {
            out[i] = base[i] * cs - base[i + 1] * sn;
            out[i + 1] = base[i] * sn + base[i + 1] * cs;
        }
        return out;
    }

    private static Piece piece(List<float[]> quads, float cu, float cv, RandomSource rnd, float shade) {
        float len = Mth.sqrt(cu * cu + cv * cv);
        float du = len < 1.0E-4F ? 1 : cu / len, dv = len < 1.0E-4F ? 0 : cv / len;
        // Um pouco de desvio lateral: os pedaços não saem todos exatamente no raio
        float jitter = (rnd.nextFloat() - 0.5F) * 0.5F;
        float ju = du - dv * jitter, jv = dv + du * jitter;
        float jl = Mth.sqrt(ju * ju + jv * jv);
        return new Piece(flatten(quads), cu, cv, ju / jl, jv / jl, 0.6F + rnd.nextFloat() * 0.8F,
                (rnd.nextFloat() - 0.5F) * 2F, shade);
    }

    private static float[] flatten(List<float[]> quads) {
        float[] out = new float[quads.size() * 8];
        for (int i = 0; i < quads.size(); i++) System.arraycopy(quads.get(i), 0, out, i * 8, 8);
        return out;
    }

    private CounterShatter() {}
}
