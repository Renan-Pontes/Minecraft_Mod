package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.BlackCoffinEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Hadō nº 90 — Kurohitsugi: o caixão negro se MONTA em volta do alvo — placas negras de veios violeta voam de fora e
 * se encaixam uma a uma (parede por parede, e a tampa por último), com uma casca violeta pulsando por fora. A cada rodada
 * de lanças, espinhos violeta atravessam as paredes de fora para dentro. No esmagamento as paredes se fecham e somem.
 */
public class BlackCoffinRenderer extends EntityRenderer<BlackCoffinEntity> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/shinigami_black_coffin.png");
    /** Placas por parede: 2 colunas × 3 fileiras; a tampa tem 2 × 2. */
    private static final int COLS = 2, ROWS = 3;
    private static final int PANELS = 4 * COLS * ROWS + 4;

    public BlackCoffinRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(BlackCoffinEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double camX, double camY, double camZ) {
        double h = e.width() / 2 + 3.0;
        return frustum.isVisible(new net.minecraft.world.phys.AABB(e.getX() - h, e.getY(), e.getZ() - h, e.getX() + h, e.getY() + e.height() + 3.0,
                e.getZ() + h));
    }

    @Override
    public void render(BlackCoffinEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        float fade = t > BlackCoffinEntity.CRUSH ? Mth.clamp(1F - (t - BlackCoffinEntity.CRUSH) / 8F, 0F, 1F) : 1F;
        if (fade <= 0.01F) return;
        float squeeze = t > BlackCoffinEntity.CRUSH - 6 ? 1F - 0.25F * Mth.clamp((t - (BlackCoffinEntity.CRUSH - 6)) / 6F, 0F, 1F) : 1F;
        float w = e.width() * squeeze, h = e.height();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        PoseStack.Pose p = pose.last();
        int idx = 0;
        for (int face = 0; face < 4; face++) {
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    panel(vc, p, face, col, row, w, h, t, idx++, fade);
                }
            }
        }
        for (int k = 0; k < 4; k++) lid(vc, p, k, w, h, t, idx++, fade);
        // casca violeta pulsando por fora (só depois de montado)
        float built = Mth.clamp((t - BlackCoffinEntity.RISE) / 4F, 0F, 1F);
        if (built > 0) {
            float pulse = 1.06F + 0.03F * Mth.sin(t * 0.7F);
            box(vc, p, w * pulse, h * Math.min(1.04F, pulse), 0.55F, 0.2F, 0.85F, 0.25F * fade * built);
        }
        // espinhos violeta atravessando as paredes a cada rodada de lanças
        VertexConsumer add = buffers.getBuffer(ShinigamiRenderTypes.additive(ShinigamiRenderTypes.SPIKE));
        Matrix4f m = pose.last().pose();
        for (int s = 0; s < BlackCoffinEntity.SPEARS.length; s++) spikes(add, m, BlackCoffinEntity.SPEARS[s], s, w, h, t);
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    /** Momento (0..1) em que a placa {@code idx} termina de se encaixar, e o quanto já chegou. */
    private static float arrive(int idx, float t) {
        float start = idx * (BlackCoffinEntity.RISE - 5F) / PANELS;
        return Mth.clamp((t - start) / 5F, 0F, 1F);
    }

    private static void panel(VertexConsumer vc, PoseStack.Pose p, int face, int col, int row, float w, float h, float t, int idx, float fade) {
        float k = arrive(idx, t);
        if (k <= 0) return;
        float ease = 1F - (1F - k) * (1F - k) * (1F - k);
        float half = w / 2;
        float u0 = -half + w * col / COLS, u1 = -half + w * (col + 1) / COLS;
        float y0 = h * row / ROWS, y1 = h * (row + 1) / ROWS;
        // normal para fora da parede; a placa vem de 2,5 blocos para fora e um pouco de cima, girando
        float nx = face == 0 ? 0 : face == 1 ? 0 : face == 2 ? -1 : 1;
        float nz = face == 0 ? -1 : face == 1 ? 1 : 0;
        float off = (1F - ease) * 2.6F, lift = (1F - ease) * 1.2F;
        float ox = nx * off, oz = nz * off;
        float ax, az, bx, bz;
        if (face < 2) {
            float z = face == 0 ? -half : half;
            ax = u0; az = z; bx = u1; bz = z;
        } else {
            float x = face == 2 ? -half : half;
            ax = x; az = u0; bx = x; bz = u1;
        }
        float a = 0.94F * fade * k;
        float uw = (u1 - u0), vh = (y1 - y0);
        quad(vc, p, ax + ox, y0 + lift, az + oz, bx + ox, y0 + lift, bz + oz, bx + ox, y1 + lift, bz + oz, ax + ox, y1 + lift, az + oz, uw, vh,
                1F, 1F, 1F, a, nx, 0, nz);
    }

    private static void lid(VertexConsumer vc, PoseStack.Pose p, int k, float w, float h, float t, int idx, float fade) {
        float a0 = arrive(idx, t);
        if (a0 <= 0) return;
        float ease = 1F - (1F - a0) * (1F - a0) * (1F - a0);
        float half = w / 2;
        float x0 = (k & 1) == 0 ? -half : 0, x1 = x0 + half;
        float z0 = (k & 2) == 0 ? -half : 0, z1 = z0 + half;
        float y = h + (1F - ease) * 3F;
        quad(vc, p, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, half, half, 1F, 1F, 1F, 0.94F * fade * a0, 0, 1, 0);
    }

    private static void spikes(VertexConsumer vc, Matrix4f m, int at, int round, float w, float h, float t) {
        float local = t - at + 2;
        if (local < 0 || local > 10) return;
        float in = Mth.clamp(local / 3F, 0F, 1F);
        float a = local < 6 ? 1F : 1F - (local - 6) / 4F;
        int col = ShinigamiRenderTypes.argb(0xB060FF, 0.95F * a);
        float half = w / 2;
        for (int i = 0; i < 8; i++) {
            float s1 = hash(i, round * 7 + 1), s2 = hash(i, round * 7 + 2), s3 = hash(i, round * 7 + 3);
            float ang = s1 * Mth.TWO_PI;
            float dx = Mth.cos(ang), dz = Mth.sin(ang);
            float y = h * (0.2F + 0.6F * s2);
            float len = 1.6F + 1.2F * s3;
            // a ponta parte de fora e atravessa até perto do centro
            float tipDist = Mth.lerp(in, half + 2.4F, 0.15F);
            float tx = dx * tipDist, tz = dz * tipDist;
            float bx = dx * (tipDist + len), bz = dz * (tipDist + len);
            float wdt = 0.16F;
            float px = -dz * wdt, pz = dx * wdt;
            // dois planos cruzados ao longo do eixo do espinho (ponta = v 0)
            vc.addVertex(m, tx - px, y, tz - pz).setUv(0, 0).setColor(col);
            vc.addVertex(m, tx + px, y, tz + pz).setUv(1, 0).setColor(col);
            vc.addVertex(m, bx + px, y, bz + pz).setUv(1, 1).setColor(col);
            vc.addVertex(m, bx - px, y, bz - pz).setUv(0, 1).setColor(col);
            vc.addVertex(m, tx, y - wdt, tz).setUv(0, 0).setColor(col);
            vc.addVertex(m, tx, y + wdt, tz).setUv(1, 0).setColor(col);
            vc.addVertex(m, bx, y + wdt, bz).setUv(1, 1).setColor(col);
            vc.addVertex(m, bx, y - wdt, bz).setUv(0, 1).setColor(col);
        }
    }

    private static float hash(int i, int salt) {
        int x = i * 374761393 + salt * 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        return ((x ^ (x >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Caixa centrada no chão (largura {@code w}, altura {@code h}); a textura se repete a cada bloco. */
    private static void box(VertexConsumer vc, PoseStack.Pose p, float w, float h, float r, float g, float b, float a) {
        float x0 = -w / 2, x1 = w / 2, z0 = -w / 2, z1 = w / 2;
        quad(vc, p, x0, 0, z0, x1, 0, z0, x1, h, z0, x0, h, z0, w, h, r, g, b, a, 0, 0, -1);
        quad(vc, p, x1, 0, z1, x0, 0, z1, x0, h, z1, x1, h, z1, w, h, r, g, b, a, 0, 0, 1);
        quad(vc, p, x0, 0, z1, x0, 0, z0, x0, h, z0, x0, h, z1, w, h, r, g, b, a, -1, 0, 0);
        quad(vc, p, x1, 0, z0, x1, 0, z1, x1, h, z1, x1, h, z0, w, h, r, g, b, a, 1, 0, 0);
        quad(vc, p, x0, h, z0, x1, h, z0, x1, h, z1, x0, h, z1, w, w, r, g, b, a, 0, 1, 0);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
                             float cz, float dx, float dy, float dz, float uMax, float vMax, float r, float g, float b, float a,
                             float nx, float ny, float nz) {
        vertex(vc, p, ax, ay, az, 0, vMax, r, g, b, a, nx, ny, nz);
        vertex(vc, p, bx, by, bz, uMax, vMax, r, g, b, a, nx, ny, nz);
        vertex(vc, p, cx, cy, cz, uMax, 0, r, g, b, a, nx, ny, nz);
        vertex(vc, p, dx, dy, dz, 0, 0, r, g, b, a, nx, ny, nz);
        // verso
        vertex(vc, p, dx, dy, dz, 0, 0, r, g, b, a, -nx, -ny, -nz);
        vertex(vc, p, cx, cy, cz, uMax, 0, r, g, b, a, -nx, -ny, -nz);
        vertex(vc, p, bx, by, bz, uMax, vMax, r, g, b, a, -nx, -ny, -nz);
        vertex(vc, p, ax, ay, az, 0, vMax, r, g, b, a, -nx, -ny, -nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, float r, float g, float b,
                               float a, float nx, float ny, float nz) {
        vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(BlackCoffinEntity e) {
        return TEXTURE;
    }
}
