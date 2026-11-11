package com.renanpontes.isekaimahou.client.boss;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.core.boss.BossPayloads;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Uma telegrafia no cliente. A forma é descrita num domínio (u, v):
 * círculo/anel/cone → u = raio, v = ângulo (rad); linha → u = distância ao longo, v = deslocamento lateral.
 * As alturas do chão são amostradas numa grade (u, v) uma vez (ou quando um {@code follow} anda) e interpoladas,
 * para o decalque "deitar" sobre degraus, lajes e superfícies de água.
 */
final class TelegraphShape {
    static final int FLASH_TICKS = 8;
    static final int FADE_TICKS = 6;
    static final int CANCEL_FADE = 5;
    /** Altura acima do chão (o polygon offset faz o resto contra z-fighting). */
    private static final float LIFT = 0.02F;
    private static final float TWO_PI = (float) (Math.PI * 2);

    final int id;
    final Telegraph.Kind kind;
    final int flags;
    final int warmup;
    final int hold;
    final int rgb;
    final int entityId;
    final int lockTicks;
    final float radius;
    final float inner;
    final float width;
    /** Até quantos blocos acima do Y o decalque sobe (= topo da faixa de acerto do servidor). */
    final float up;
    /** Relógio do cliente quando chegou. */
    final double born;
    /** Centro (círculo/anel/cone) ou início (linha). Muda no {@code follow} até travar. */
    Vec3 a;
    final Vec3 b;
    boolean locked;
    double endedAt = -1;
    boolean endFlash;

    // Domínio e grade de alturas
    private final float u0, u1, v0, v1;
    private final int nu, nv;
    private final float[] heights;
    /** Yaw do cone em rad (0 nas formas redondas). Seno/cosseno pela tabela do Mth: rápido o bastante por vértice. */
    private final float baseYaw;
    private final double dirX, dirZ;
    private Vec3 sampledAt;

    TelegraphShape(BossPayloads.Show p, double born) {
        this.id = p.id();
        this.kind = Telegraph.Kind.values()[Mth.clamp(p.kind(), 0, Telegraph.Kind.values().length - 1)];
        this.flags = p.flags();
        this.warmup = Math.max(1, p.warmup());
        this.hold = Math.max(0, p.hold());
        this.rgb = p.rgb() & 0xFFFFFF;
        this.entityId = p.entityId();
        this.lockTicks = p.lock();
        this.radius = p.radius();
        this.inner = p.inner();
        this.width = p.width();
        this.up = Mth.clamp(p.above(), 0.5F, 4.0F);
        this.born = born;
        this.a = new Vec3(p.ax(), p.ay(), p.az());
        this.b = new Vec3(p.bx(), p.by(), p.bz());

        if (kind == Telegraph.Kind.LINE) {
            double dx = b.x - a.x, dz = b.z - a.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-3) {
                dirX = 0;
                dirZ = 1;
            } else {
                dirX = dx / len;
                dirZ = dz / len;
            }
            baseYaw = 0;
            u0 = 0;
            u1 = (float) Math.max(0.25, len);
            v0 = -width * 0.5F;
            v1 = width * 0.5F;
            nu = Mth.clamp(Mth.ceil(u1 / 0.6F), 1, 96);
            nv = Mth.clamp(Mth.ceil(width / 0.6F), 1, 16);
        } else {
            dirX = 0;
            dirZ = 1;
            u0 = kind == Telegraph.Kind.RING ? inner : 0;
            u1 = radius;
            if (kind == Telegraph.Kind.CONE) {
                baseYaw = (float) Math.toRadians(p.yaw());
                float half = (float) Math.toRadians(p.angle()) * 0.5F;
                v0 = -half;
                v1 = half;
            } else {
                baseYaw = 0;
                v0 = 0;
                v1 = TWO_PI;
            }
            nu = Mth.clamp(Mth.ceil((u1 - u0) / 0.6F), 1, 32);
            nv = Mth.clamp(Mth.ceil((v1 - v0) * u1 / 0.55F), 12, 112);
        }
        heights = new float[(nu + 1) * (nv + 1)];
    }

    boolean isFollow() { return entityId >= 0; }

    boolean has(int flag) { return (flags & flag) != 0; }

    /** Centro aproximado (para distância de desenho). */
    Vec3 middle() {
        return kind == Telegraph.Kind.LINE ? a.add(b).scale(0.5) : a;
    }

    double extent() {
        return kind == Telegraph.Kind.LINE ? u1 * 0.5 + width : radius;
    }

    // ------------------------------------------------------------------ tempo

    /** Idade em ticks (+1: compensa o tick de viagem do pacote, para o enchimento terminar junto com o golpe). */
    double age(double now) {
        return now - born + 1.0;
    }

    boolean expired(double now) {
        if (endedAt >= 0) return now - endedAt > Math.max(CANCEL_FADE, FLASH_TICKS);
        return age(now) > warmup + hold + Math.max(FADE_TICKS, FLASH_TICKS) + 1;
    }

    /** Segue a entidade até travar; reamostra o chão quando anda. */
    void updateFollow(ClientLevel level, double now, float partial) {
        if (!isFollow() || locked) return;
        Entity e = level.getEntity(entityId);
        if (e != null) a = e.getPosition(partial);
        if (age(now) >= warmup - lockTicks) locked = true;
    }

    void ensureSampled(ClientLevel level) {
        if (sampledAt != null && sampledAt.distanceToSqr(a) < 0.01) return;
        sampledAt = a;
        if (has(Telegraph.FLAG_FLAT)) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // O desenho só acompanha degraus baixos: subir em pilares/paredes (até 'up' do servidor) virava "rampas"
        // quase verticais que pareciam placas sólidas em pé. O acerto no servidor continua com a faixa inteira.
        float drawUp = Math.min(up, 1.25F);
        for (int i = 0; i <= nu; i++) {
            float u = u0 + (u1 - u0) * i / nu;
            for (int j = 0; j <= nv; j++) {
                float v = v0 + (v1 - v0) * j / nv;
                heights[i * (nv + 1) + j] = groundAt(level, m, px(u, v), refY(u), pz(u, v), drawUp);
            }
        }
    }

    /**
     * Altura do decalque numa coluna: entre as superfícies EXPOSTAS (ar em cima) de {@code refY + up} até
     * {@code refY − 5} — a mesma faixa em que o servidor acerta —, a mais perto de {@code refY}. Assim ele aparece
     * em cima de degraus/pilares que também levam o golpe (lá é a única superfície exposta), fica no piso de uma casa
     * em vez de no telhado, e nunca dentro de um bloco. Fluido conta só na superfície (água rasa); camadas finas sem
     * colisão (neve, placas de pressão, trilhos) contam pelo desenho, senão o decalque ficaria por baixo delas.
     */
    static float groundAt(ClientLevel level, BlockPos.MutableBlockPos m, double x, double refY, double z, float up) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        double maxTop = refY + up + 0.05;
        int top = Mth.floor(refY + up);
        m.set(bx, top + 1, bz);
        boolean open = level.getBlockState(m).getCollisionShape(level, m).isEmpty();
        double best = Double.NaN;
        for (int y = top; y >= top - 5 - Mth.ceil(up); y--) {
            m.set(bx, y, bz);
            BlockState st = level.getBlockState(m);
            VoxelShape s = st.getCollisionShape(level, m);
            double surface = Double.NaN;
            if (s.isEmpty()) {
                FluidState fluid = st.getFluidState();
                // Fluido e camada fina cobrem o bloco de baixo (o fundo do lago / a grama sob a neve não contam)
                boolean covers = !fluid.isEmpty();
                if (!fluid.isEmpty()) {
                    if (open) {
                        m.set(bx, y + 1, bz);
                        boolean onTop = level.getFluidState(m).isEmpty();
                        m.set(bx, y, bz);
                        if (onTop) surface = y + fluid.getHeight(level, m);
                    }
                } else {
                    VoxelShape vis = st.getShape(level, m);
                    if (!vis.isEmpty() && vis.max(Direction.Axis.Y) <= 0.25) {
                        covers = true;
                        if (open) surface = y + vis.max(Direction.Axis.Y);
                    }
                }
                open = !covers;
            } else {
                if (open) {
                    double h = s.max(Direction.Axis.Y);
                    // Neve em camadas, areia das almas, lama: a colisão fica um pouco abaixo do desenho
                    VoxelShape vis = st.getShape(level, m);
                    if (!vis.isEmpty()) {
                        double hv = vis.max(Direction.Axis.Y);
                        if (hv > h && hv <= h + 0.2) h = hv;
                    }
                    surface = y + h;
                }
                // O bloco de baixo está coberto por este
                open = false;
            }
            if (!Double.isNaN(surface) && surface <= maxTop
                    && (Double.isNaN(best) || Math.abs(surface - refY) < Math.abs(best - refY))) {
                best = surface;
            }
        }
        return Double.isNaN(best) ? (float) refY : (float) best;
    }

    // ------------------------------------------------------------------ geometria

    private double px(float u, float v) {
        if (kind == Telegraph.Kind.LINE) return a.x + dirX * u - dirZ * v;
        return a.x - Mth.sin(baseYaw + v) * u;
    }

    private double pz(float u, float v) {
        if (kind == Telegraph.Kind.LINE) return a.z + dirZ * u + dirX * v;
        return a.z + Mth.cos(baseYaw + v) * u;
    }

    private double refY(float u) {
        if (kind == Telegraph.Kind.LINE) return a.y + (b.y - a.y) * Mth.clamp(u / u1, 0, 1);
        return a.y;
    }

    private double heightAt(float u, float v) {
        if (has(Telegraph.FLAG_FLAT) || sampledAt == null) return refY(u) + LIFT;
        float fu = Mth.clamp((u - u0) / (u1 - u0), 0, 1) * nu;
        float fv = Mth.clamp((v - v0) / (v1 - v0), 0, 1) * nv;
        int i = Math.min(nu - 1, (int) fu), j = Math.min(nv - 1, (int) fv);
        float tu = fu - i, tv = fv - j;
        int row = nv + 1;
        float h00 = heights[i * row + j], h01 = heights[i * row + j + 1];
        float h10 = heights[(i + 1) * row + j], h11 = heights[(i + 1) * row + j + 1];
        return Mth.lerp(tu, Mth.lerp(tv, h00, h01), Mth.lerp(tv, h10, h11)) + LIFT;
    }

    private float cellU() { return (u1 - u0) / nu; }

    private float cellV() { return (v1 - v0) / nv; }

    private void vertex(VertexConsumer vc, Vec3 cam, float u, float v, int argb) {
        vc.addVertex((float) (px(u, v) - cam.x), (float) (heightAt(u, v) - cam.y), (float) (pz(u, v) - cam.z)).setColor(argb);
    }

    private void vertex(VertexConsumer vc, Vec3 cam, float u, float v, float s, float t, int argb) {
        vc.addVertex((float) (px(u, v) - cam.x), (float) (heightAt(u, v) - cam.y), (float) (pz(u, v) - cam.z))
                .setUv(s, t).setColor(argb);
    }

    /** Cor por vértice (gradientes suaves nas bordas). */
    private interface Shade { int argb(float u, float v); }

    /** Coordenada de textura por vértice: devolve s; t vem de {@link Tex#t}. */
    private interface Tex {
        float s(float u, float v);
        float t(float u, float v);
    }

    /** Retângulo [ua,ub]×[va,vb] do domínio, subdividido na resolução da grade (segue o relevo). */
    private void band(VertexConsumer vc, Vec3 cam, float ua, float ub, float va, float vb, Shade shade) {
        if (ub - ua < 1.0E-4 || vb - va < 1.0E-5) return;
        int su = Math.max(1, Mth.ceil((ub - ua) / cellU()));
        int sv = Math.max(1, Mth.ceil((vb - va) / cellV()));
        for (int k = 0; k < su; k++) {
            float ua0 = ua + (ub - ua) * k / su, ua1 = ua + (ub - ua) * (k + 1) / su;
            for (int l = 0; l < sv; l++) {
                float va0 = va + (vb - va) * l / sv, va1 = va + (vb - va) * (l + 1) / sv;
                vertex(vc, cam, ua0, va0, shade.argb(ua0, va0));
                vertex(vc, cam, ua1, va0, shade.argb(ua1, va0));
                vertex(vc, cam, ua1, va1, shade.argb(ua1, va1));
                vertex(vc, cam, ua0, va1, shade.argb(ua0, va1));
            }
        }
    }

    private void bandTex(VertexConsumer vc, Vec3 cam, float ua, float ub, float va, float vb, Tex tex, int argb) {
        if (ub - ua < 1.0E-4 || vb - va < 1.0E-5) return;
        int su = Math.max(1, Mth.ceil((ub - ua) / cellU()));
        int sv = Math.max(1, Mth.ceil((vb - va) / cellV()));
        for (int k = 0; k < su; k++) {
            float ua0 = ua + (ub - ua) * k / su, ua1 = ua + (ub - ua) * (k + 1) / su;
            for (int l = 0; l < sv; l++) {
                float va0 = va + (vb - va) * l / sv, va1 = va + (vb - va) * (l + 1) / sv;
                vertex(vc, cam, ua0, va0, tex.s(ua0, va0), tex.t(ua0, va0), argb);
                vertex(vc, cam, ua1, va0, tex.s(ua1, va0), tex.t(ua1, va0), argb);
                vertex(vc, cam, ua1, va1, tex.s(ua1, va1), tex.t(ua1, va1), argb);
                vertex(vc, cam, ua0, va1, tex.s(ua0, va1), tex.t(ua0, va1), argb);
            }
        }
    }

    /** Borda radial do cone com largura constante no mundo (em v a largura é w/u). */
    private void radialEdge(VertexConsumer vc, Vec3 cam, float edgeV, float sign, float w, int argb) {
        int su = Math.max(1, Mth.ceil((u1 - u0) / cellU()));
        float maxDv = (v1 - v0) * 0.5F;
        for (int k = 0; k < su; k++) {
            float ua = u0 + (u1 - u0) * k / su, ub = u0 + (u1 - u0) * (k + 1) / su;
            float da = Math.min(maxDv, w / Math.max(ua, w * 2)), db = Math.min(maxDv, w / Math.max(ub, w * 2));
            vertex(vc, cam, ua, edgeV, argb);
            vertex(vc, cam, ub, edgeV, argb);
            vertex(vc, cam, ub, edgeV + sign * db, argb);
            vertex(vc, cam, ua, edgeV + sign * da, argb);
        }
    }

    // ------------------------------------------------------------------ desenho

    /** Degradê linear de alpha ao longo de u. */
    private static Shade rampU(float from, float to, float alphaFrom, float alphaTo, int rgb) {
        float span = Math.max(1.0E-4F, to - from);
        return (u, v) -> argb(Mth.lerp(Mth.clamp((u - from) / span, 0, 1), alphaFrom, alphaTo), rgb);
    }

    /** Degradê linear de alpha ao longo de v. */
    private static Shade rampV(float from, float to, float alphaFrom, float alphaTo, int rgb) {
        float span = Math.max(1.0E-4F, to - from);
        return (u, v) -> argb(Mth.lerp(Mth.clamp((v - from) / span, 0, 1), alphaFrom, alphaTo), rgb);
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | rgb;
    }

    private static int mixWhite(int rgb, float t) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        r = (int) (r + (255 - r) * t);
        g = (int) (g + (255 - g) * t);
        bl = (int) (bl + (255 - bl) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Envelope 0..1: aparece em 3 ticks, some no fim (ou ao ser cancelada). */
    private float life(double now) {
        double age = age(now);
        float in = (float) Mth.clamp(age / 3.0, 0.25, 1.0);
        float out;
        if (endedAt >= 0) out = (float) (1 - (now - endedAt) / CANCEL_FADE);
        else out = (float) (1 - Math.max(0, age - warmup - hold) / FADE_TICKS);
        return in * Mth.clamp(out, 0, 1);
    }

    private float edgeWidth() {
        return kind == Telegraph.Kind.LINE ? Mth.clamp(0.08F + width * 0.03F, 0.08F, 0.25F)
                : Mth.clamp(0.10F + u1 * 0.012F, 0.10F, 0.30F);
    }

    /** Camadas de cor: área, enchimento, frente, bordas com brilho, linha decorativa e flash final. */
    void drawColor(VertexConsumer vc, Vec3 cam, double now) {
        double age = age(now);
        float life = life(now);
        boolean safe = has(Telegraph.FLAG_SAFE);
        float p = (float) Mth.clamp(age / warmup, 0, 1);
        // Cancelada no meio do aquecimento continua com o visual de "enchendo" enquanto some
        boolean charging = age < warmup;
        float pulse = 0.5F + 0.5F * Mth.sin((float) (age * (safe ? 0.12 : 0.22 + 0.75 * p * p)));
        float bw = edgeWidth();
        int light = mixWhite(rgb, 0.45F);

        if (life > 0.004F) {
            if (safe) drawSafe(vc, cam, life, p, pulse, bw, light);
            else drawDanger(vc, cam, life, p, pulse, bw, light, charging, age);
        }
        drawFlash(vc, cam, now, age, bw);
    }

    private void drawDanger(VertexConsumer vc, Vec3 cam, float life, float p, float pulse, float bw, int light,
                            boolean charging, double age) {
        // Área: parte cheia (até a frente) mais forte; o resto bem leve mostra o tamanho total
        if (charging) {
            float front = u0 + (u1 - u0) * p;
            int fill = argb((0.22F + 0.2F * p) * life, rgb);
            int rest = argb((0.10F + 0.05F * pulse) * life, rgb);
            band(vc, cam, u0, front, v0, v1, (u, v) -> fill);
            band(vc, cam, front, u1, v0, v1, (u, v) -> rest);
            // Frente brilhante que anda do centro até a borda: "quando chegar na borda, acerta"
            float fw = Math.max(0.12F, bw);
            if (front > u0 + 0.01F) {
                int frontRgb = mixWhite(rgb, 0.55F);
                int frontCol = argb(0.9F * life, frontRgb);
                float fa = Math.max(u0, front - fw * 3);
                band(vc, cam, fa, front - fw, v0, v1, rampU(fa, front - fw, 0, 0.45F * life, frontRgb));
                band(vc, cam, Math.max(u0, front - fw), front, v0, v1, (u, v) -> frontCol);
            }
        } else {
            float fast = 0.5F + 0.5F * Mth.sin((float) (age * 0.6));
            int full = argb((0.38F + 0.1F * fast) * life, rgb);
            band(vc, cam, u0, u1, v0, v1, (u, v) -> full);
        }
        drawEdges(vc, cam, life, pulse, bw, light);
        drawCircleLine(vc, cam, life * 0.8F, light);
    }

    private void drawSafe(VertexConsumer vc, Vec3 cam, float life, float p, float pulse, float bw, int light) {
        int area = argb((0.09F + 0.04F * pulse) * life, rgb);
        band(vc, cam, u0, u1, v0, v1, (u, v) -> area);
        // Relógio: setor que gira mostrando quanto tempo de zona segura já passou
        if (p > 0 && endedAt < 0) {
            int sweep = argb(0.09F * life, light);
            band(vc, cam, u0, u1 * 0.9F, v0, v0 + (v1 - v0) * p, (u, v) -> sweep);
        }
        // Borda suave: gradiente de dentro para fora + halo
        float soft = Math.min(0.9F, u1 * 0.35F);
        float edgeA = (0.45F + 0.2F * pulse) * life;
        band(vc, cam, u1 - soft, u1, v0, v1, rampU(u1 - soft, u1, 0, edgeA, light));
        band(vc, cam, u1, u1 + 0.3F, v0, v1, rampU(u1, u1 + 0.3F, edgeA, 0, light));
        drawCircleLine(vc, cam, life * 0.7F, light);
    }

    /** Bordas nítidas + brilho interno/externo em degradê. */
    private void drawEdges(VertexConsumer vc, Vec3 cam, float life, float pulse, float bw, int light) {
        int edge = argb((0.65F + 0.3F * pulse) * life, light);
        float glowA = 0.32F * life;
        float g = bw * 1.8F;
        if (kind == Telegraph.Kind.LINE) {
            band(vc, cam, u0, u1, v0, v0 + bw, (u, v) -> edge);
            band(vc, cam, u0, u1, v1 - bw, v1, (u, v) -> edge);
            band(vc, cam, u0, u0 + bw, v0, v1, (u, v) -> edge);
            band(vc, cam, u1 - bw, u1, v0, v1, (u, v) -> edge);
            band(vc, cam, u0, u1, v0 - g, v0, rampV(v0 - g, v0, 0, glowA, rgb));
            band(vc, cam, u0, u1, v1, v1 + g, rampV(v1, v1 + g, glowA, 0, rgb));
            band(vc, cam, u1, u1 + g, v0, v1, rampU(u1, u1 + g, glowA, 0, rgb));
            return;
        }
        band(vc, cam, u1 - bw, u1, v0, v1, (u, v) -> edge);
        band(vc, cam, u1, u1 + g, v0, v1, rampU(u1, u1 + g, glowA, 0, rgb));
        float inGlow = Math.max(u0, u1 - bw * 4);
        band(vc, cam, inGlow, u1 - bw, v0, v1, rampU(inGlow, u1 - bw, 0, glowA, rgb));
        if (kind == Telegraph.Kind.RING && u0 > 0.05F) {
            band(vc, cam, u0, u0 + bw, v0, v1, (u, v) -> edge);
            float in = Math.max(0, u0 - g);
            band(vc, cam, in, u0, v0, v1, rampU(in, u0, 0, glowA, rgb));
        }
        if (kind == Telegraph.Kind.CONE && v1 - v0 < TWO_PI - 0.01F) {
            radialEdge(vc, cam, v0, 1, bw, edge);
            radialEdge(vc, cam, v1, -1, bw, edge);
        }
    }

    /** Linha fina de "círculo mágico" dentro das formas redondas grandes. */
    private void drawCircleLine(VertexConsumer vc, Vec3 cam, float life, int light) {
        if (kind == Telegraph.Kind.LINE || u1 < 2.5F || has(Telegraph.FLAG_NO_RUNES)) return;
        float r = kind == Telegraph.Kind.RING ? (u0 + u1) * 0.5F : u1 * 0.62F;
        if (r - 0.05F < u0) return;
        int col = argb(0.35F * life, light);
        band(vc, cam, r - 0.04F, r + 0.04F, v0, v1, (u, v) -> col);
    }

    /** Flash no momento do golpe (ou do cancelamento com flash): área clara que some + anel que expande. */
    private void drawFlash(VertexConsumer vc, Vec3 cam, double now, double age, float bw) {
        double t;
        if (endedAt >= 0) {
            if (!endFlash) return;
            t = (now - endedAt) / FLASH_TICKS;
        } else {
            if (has(Telegraph.FLAG_NO_FLASH) || age < warmup) return;
            t = (age - warmup) / FLASH_TICKS;
        }
        if (t < 0 || t >= 1) return;
        float k = (float) (1 - t);
        int white = mixWhite(rgb, 0.7F);
        int area = argb(0.55F * k * k, white);
        band(vc, cam, u0, u1, v0, v1, (u, v) -> area);
        if (kind == Telegraph.Kind.LINE) return;
        float r = u1 + (float) t * 0.9F;
        int ring = argb(0.8F * k, white);
        band(vc, cam, Math.max(u0, r - bw * 1.5F), r, v0, v1, (u, v) -> ring);
    }

    /** Anel de runas girando (formas redondas). */
    void drawRunes(VertexConsumer vc, Vec3 cam, double now) {
        if (kind == Telegraph.Kind.LINE || has(Telegraph.FLAG_NO_RUNES) || u1 < 1.5F) return;
        float life = life(now);
        if (life <= 0.004F) return;
        double age = age(now);
        float p = (float) Mth.clamp(age / warmup, 0, 1);
        boolean safe = has(Telegraph.FLAG_SAFE);
        float uc, h;
        if (kind == Telegraph.Kind.RING) {
            if (u1 - u0 < 1.2F) return;
            uc = (u0 + u1) * 0.5F;
            h = Math.min((u1 - u0) * 0.45F, 1.2F);
        } else {
            uc = u1 * (kind == Telegraph.Kind.CONE ? 0.78F : 0.80F);
            h = Mth.clamp(u1 * 0.14F, 0.35F, 1.3F);
        }
        float ua = uc - h * 0.5F, ub = uc + h * 0.5F;
        // 8 runas por repetição da textura; número inteiro de repetições no círculo inteiro (sem emenda)
        boolean full = v1 - v0 >= TWO_PI - 0.01F;
        float reps = full ? Math.max(1, Math.round(TWO_PI * uc / (8 * h))) : (v1 - v0) * uc / (8 * h);
        float span = v1 - v0;
        float rot = (float) (age * (safe ? -0.004 : 0.008 + 0.02 * p));
        float alpha = (safe ? 0.5F : 0.45F + 0.45F * p) * life;
        int col = argb(alpha, mixWhite(rgb, 0.35F));
        bandTex(vc, cam, ua, ub, v0, v1, new Tex() {
            @Override public float s(float u, float v) { return (v - v0) / span * reps + rot; }
            @Override public float t(float u, float v) { return 1 - (u - ua) / (ub - ua); }
        }, col);
    }

    /** Setas correndo ao longo da linha, na direção do golpe. */
    void drawChevrons(VertexConsumer vc, Vec3 cam, double now) {
        if (kind != Telegraph.Kind.LINE || has(Telegraph.FLAG_NO_RUNES) || u1 < 1.5F) return;
        float life = life(now);
        if (life <= 0.004F) return;
        double age = age(now);
        float p = (float) Mth.clamp(age / warmup, 0, 1);
        float cw = Math.min(width * 0.32F, 0.7F);
        float tile = cw * 2;
        float scroll = (float) (age * (0.05 + 0.15 * p));
        int col = argb((0.4F + 0.4F * p) * life, mixWhite(rgb, 0.4F));
        bandTex(vc, cam, u0 + 0.2F, u1 - 0.2F, -cw, cw, new Tex() {
            @Override public float s(float u, float v) { return (u - scroll) / tile; }
            @Override public float t(float u, float v) { return (v + cw) / tile; }
        }, col);
    }
}
