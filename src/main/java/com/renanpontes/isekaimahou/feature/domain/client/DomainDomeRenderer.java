package com.renanpontes.isekaimahou.feature.domain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.domain.entity.DomainDomeEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Desenha o domo: uma esfera de quads translúcida (textura de "barreira" hexagonal tingida com a cor da magia,
 * mais opaca nas bordas — efeito fresnel), com uma faixa de runas na base. Animações:
 * surge crescendo, desmorona encolhendo e, ao perder um Choque de Domínios, se despedaça em cacos que caem.
 * Barreira Aberta: só a faixa de runas no chão (sem paredes). Domínio Simples: esfera pequena e clara que segue o dono.
 * Magias multi-elemento: a faixa de runas ondula entre a cor da magia e a do 2º elemento (cor de destaque).
 * v0.8 — barreira: painéis rachados (brilhantes, tremulando) aparecem conforme a integridade cai, a parede pisca
 * a cada golpe, e a barreira INVERTIDA tem as cores invertidas (negativo), uma casca interna escura e as runas
 * de cabeça para baixo girando ao contrário.
 */
public class DomainDomeRenderer extends EntityRenderer<DomainDomeEntity> {
    private static final ResourceLocation DOME = IsekaiMahou.id("textures/entity/domain_dome.png");
    private static final ResourceLocation RING = IsekaiMahou.id("textures/entity/domain_ring.png");
    private static final int LON = 40;
    private static final int LAT = 24;
    private static final float[] SIN_T = new float[LAT + 1], COS_T = new float[LAT + 1];
    private static final float[] SIN_P = new float[LON + 1], COS_P = new float[LON + 1];

    static {
        for (int i = 0; i <= LAT; i++) {
            double t = Math.PI * i / LAT;
            SIN_T[i] = (float) Math.sin(t);
            COS_T[i] = (float) Math.cos(t);
        }
        for (int j = 0; j <= LON; j++) {
            double p = Math.PI * 2 * j / LON;
            SIN_P[j] = (float) Math.sin(p);
            COS_P[j] = (float) Math.cos(p);
        }
    }

    public DomainDomeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(DomainDomeEntity entity) {
        return DOME;
    }

    @Override
    public boolean shouldRender(DomainDomeEntity e, Frustum frustum, double camX, double camY, double camZ) {
        if (!e.shouldRender(camX, camY, camZ)) return false;
        Entity owner = e.isSimple() ? e.ownerEntity() : null;
        AABB box = (owner != null ? owner.getBoundingBox() : e.getBoundingBox()).inflate(e.radius() + 3);
        return frustum.isVisible(box);
    }

    @Override
    public void render(DomainDomeEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        float radius = e.visualRadius(pt);
        if (radius < 0.05F) return;
        boolean simple = e.isSimple();
        Vec3 center = e.getPosition(pt);

        pose.pushPose();
        if (simple) {
            // O Domínio Simples acompanha o dono: desenha na posição interpolada dele (sem "atraso")
            Entity owner = e.ownerEntity();
            if (owner != null) {
                Vec3 op = owner.getPosition(pt);
                pose.translate(op.x - center.x, op.y - center.y, op.z - center.z);
                center = op;
            }
        }

        int rgb = e.color();
        int accent = e.accentColor();
        boolean inverted = e.isInverted();
        if (inverted) {
            rgb = 0xFFFFFF ^ rgb;
            accent = 0xFFFFFF ^ accent;
        }
        if (simple) {
            rgb = mix(rgb, 0xFFFFFF, 0.55F);
            accent = mix(accent, 0xFFFFFF, 0.4F);
        }
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        float age = e.visualAge(pt);
        float presence = e.state() == DomainDomeEntity.COLLAPSING ? e.presence(pt) : 1.0F;
        float glow = Mth.clamp(1.0F - age / DomainDomeEntity.EXPAND_TICKS, 0, 1);
        float shatter = e.shatterProgress(pt);
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(center);
        PoseStack.Pose last = pose.last();

        if (!e.isOpen()) {
            float crack = simple ? 0F : 1F - e.integrityFraction();
            float flash = simple ? 0F : Mth.clamp(1F - e.sinceHit(pt) / 8F, 0F, 1F);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(DOME));
            if (inverted && shatter < 0) {
                // Casca interna escura: o "negativo" da barreira invertida
                sphere(vc, last, radius * 0.985F, 0.05F, 0.02F, 0.08F, 0.55F * presence, 0, -age, cam, -1, 0, 0);
            }
            sphere(vc, last, radius, r, g, b, (simple ? 0.5F : 0.8F) * presence, glow, inverted ? -age : age, cam, shatter, crack, flash);
        }
        if (shatter < 0) {
            VertexConsumer rc = buffers.getBuffer(RenderType.entityTranslucentEmissive(RING));
            float h = e.isOpen() ? 3.2F : (simple ? 0.9F : 1.9F);
            // Invertida: runas de cabeça para baixo, girando ao contrário
            ring(rc, last, radius, h, rgb, accent, presence, (inverted ? -age : age) * 0.01F, inverted);
            if (e.isOpen()) ring(rc, last, radius * 0.985F, h * 0.55F, accent, rgb, 0.75F * presence, -age * 0.016F, false);
        }
        pose.popPose();
    }

    // =====================================================================================
    //  Esfera
    // =====================================================================================
    private static void sphere(VertexConsumer vc, PoseStack.Pose pose, float R, float r, float g, float b, float alpha,
                               float glow, float age, Vec3 cam, float shatter, float crack, float flash) {
        // Mantém o tamanho dos hexágonos parecido em domos grandes e pequenos
        float uRep = Math.max(6, Math.round(R * 0.9F));
        float vRep = uRep * 0.5F;
        float scroll = age * 0.004F;
        float lit = Math.max(glow * 0.6F, flash * 0.55F);
        float lr = Mth.lerp(lit, r, 1.0F), lg = Mth.lerp(lit, g, 1.0F), lb = Mth.lerp(lit, b, 1.0F);
        float a = alpha * (1.0F + glow * 1.5F + flash * 0.8F);
        // Painéis rachados: até 70% da casca, conforme a integridade cai
        float crackShare = crack * 0.7F;
        float[] xs = new float[4], ys = new float[4], zs = new float[4], us = new float[4], vs = new float[4];
        int[] ii = new int[4], jj = new int[4];

        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                ii[0] = i; jj[0] = j;
                ii[1] = i; jj[1] = j + 1;
                ii[2] = i + 1; jj[2] = j + 1;
                ii[3] = i + 1; jj[3] = j;
                for (int k = 0; k < 4; k++) {
                    xs[k] = SIN_T[ii[k]] * COS_P[jj[k]];
                    ys[k] = COS_T[ii[k]];
                    zs[k] = SIN_T[ii[k]] * SIN_P[jj[k]];
                    us[k] = jj[k] / (float) LON * uRep;
                    vs[k] = ii[k] / (float) LAT * vRep + scroll;
                }
                float qa = a;
                float qr = lr, qg = lg, qb = lb;
                if (crackShare > 0) {
                    float hc = hash(i * 7 + 3, j * 13 + 1);
                    if (hc < crackShare) {
                        // Rachado: mais claro, mais opaco e tremulando
                        float flick = 0.5F + 0.5F * Mth.sin(age * 0.6F + hc * 40F);
                        float w = 0.35F + 0.4F * flick;
                        qr = Mth.lerp(w, lr, 1F);
                        qg = Mth.lerp(w, lg, 1F);
                        qb = Mth.lerp(w, lb, 1F);
                        qa *= 1.3F + 0.5F * flick;
                    }
                }
                float ox = 0, oy = 0, oz = 0, shrink = 1;
                float cx = 0, cy = 0, cz = 0;
                if (shatter >= 0) {
                    // Caco: afasta do centro, cai com "gravidade", encolhe e some
                    for (int k = 0; k < 4; k++) { cx += xs[k] * 0.25F; cy += ys[k] * 0.25F; cz += zs[k] * 0.25F; }
                    float rnd = 0.6F + 0.8F * hash(i, j);
                    float s = shatter;
                    ox = cx * R * 0.35F * s * rnd;
                    oy = cy * R * 0.35F * s * rnd - s * s * R * 0.6F * rnd;
                    oz = cz * R * 0.35F * s * rnd;
                    shrink = 1 - 0.65F * s;
                    qa *= (1 - s) * (1 - s);
                    cx *= R; cy *= R; cz *= R;
                }
                for (int k = 0; k < 4; k++) {
                    float nx = xs[k], ny = ys[k], nz = zs[k];
                    float px = nx * R, py = ny * R, pz = nz * R;
                    if (shatter >= 0) {
                        px = cx + (px - cx) * shrink + ox;
                        py = cy + (py - cy) * shrink + oy;
                        pz = cz + (pz - cz) * shrink + oz;
                    }
                    // Fresnel: mais opaco onde a superfície fica "de lado" para a câmera
                    double vx = px - cam.x, vy = py - cam.y, vz = pz - cam.z;
                    double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
                    float facing = len < 1.0E-4 ? 1 : (float) Math.abs((nx * vx + ny * vy + nz * vz) / len);
                    float edge = 1 - facing;
                    float va = Math.min(1.0F, qa * (0.3F + 0.7F * edge * edge));
                    vertex(vc, pose, px, py, pz, qr, qg, qb, va, us[k], vs[k], nx, ny, nz);
                }
            }
        }
    }

    // =====================================================================================
    //  Faixa de runas na base
    // =====================================================================================
    /** Faixa de runas; a cor ondula de {@code rgbA} para {@code rgbB} em volta do círculo (iguais = cor única). */
    private static void ring(VertexConsumer vc, PoseStack.Pose pose, float R, float h, int rgbA, int rgbB,
                             float alpha, float scroll, boolean flipped) {
        int seg = Math.max(32, (int) (R * 6));
        float uRep = Math.max(4, Math.round((float) (Math.PI * 2 * R) / 3.0F));
        for (int j = 0; j < seg; j++) {
            double a0 = Math.PI * 2 * j / seg, a1 = Math.PI * 2 * (j + 1) / seg;
            // 3 ondas de cor girando junto com as runas
            int col = mix(rgbA, rgbB, 0.5F + 0.5F * Mth.sin((float) a0 * 3 + scroll * 40));
            float r = ((col >> 16) & 255) / 255F, g = ((col >> 8) & 255) / 255F, b = (col & 255) / 255F;
            float x0 = (float) Math.cos(a0) * R, z0 = (float) Math.sin(a0) * R;
            float x1 = (float) Math.cos(a1) * R, z1 = (float) Math.sin(a1) * R;
            float u0 = j / (float) seg * uRep + scroll, u1 = (j + 1) / (float) seg * uRep + scroll;
            float nx0 = (float) Math.cos(a0), nz0 = (float) Math.sin(a0);
            float nx1 = (float) Math.cos(a1), nz1 = (float) Math.sin(a1);
            float vb = flipped ? 0 : 1, vt = flipped ? 1 : 0;
            vertex(vc, pose, x0, -0.15F, z0, r, g, b, alpha, u0, vb, nx0, 0, nz0);
            vertex(vc, pose, x1, -0.15F, z1, r, g, b, alpha, u1, vb, nx1, 0, nz1);
            vertex(vc, pose, x1, h, z1, r, g, b, 0, u1, vt, nx1, 0, nz1);
            vertex(vc, pose, x0, h, z0, r, g, b, 0, u0, vt, nx0, 0, nz0);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float r, float g, float b,
                               float a, float u, float v, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }

    /** Pseudoaleatório estável por caco (0..1). */
    private static float hash(int i, int j) {
        int h = i * 73856093 ^ j * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535F;
    }

    private static int mix(int c1, int c2, float t) {
        int r = (int) Mth.lerp(t, (c1 >> 16) & 255, (c2 >> 16) & 255);
        int g = (int) Mth.lerp(t, (c1 >> 8) & 255, (c2 >> 8) & 255);
        int b = (int) Mth.lerp(t, c1 & 255, c2 & 255);
        return (r << 16) | (g << 8) | b;
    }
}
