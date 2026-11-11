package com.renanpontes.isekaimahou.feature.arcane_duel.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.arcane_duel.entity.DuelFxEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Desenha os visuais do duelo arcano: vidro mágico que estilhaça, casca de hexágonos, égide dourada, fenda do exílio,
 * sigilo do céu e pilares da Ira Divina, relógio do Passo no Tempo, cartas de magia e o espelho do Redirecionar.
 */
public class DuelFxRenderer extends EntityRenderer<DuelFxEntity> {
    private static final ResourceLocation RUNE = DuelRenderTypes.tex("rune_circle");
    private static final ResourceLocation HEX = DuelRenderTypes.tex("hex_cell");
    private static final ResourceLocation CARD = DuelRenderTypes.tex("spell_card");
    private static final ResourceLocation CLOCK = DuelRenderTypes.tex("clock_face");
    private static final ResourceLocation RIFT = DuelRenderTypes.tex("exile_rift");

    public DuelFxRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(DuelFxEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + pt;
        int life = e.life();
        if (age > life + 1) return;
        pose.pushPose();
        // Preso a alguém: desenha na posição suavizada da entidade (sem atraso de rede)
        Entity a = e.attached();
        if (a != null) {
            Vec3 own = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
            Vec3 at = new Vec3(Mth.lerp(pt, a.xo, a.getX()), Mth.lerp(pt, a.yo, a.getY()), Mth.lerp(pt, a.zo, a.getZ()));
            pose.translate(at.x - own.x, at.y - own.y, at.z - own.z);
        }
        switch (e.kind()) {
            case DuelFxEntity.SHATTER -> shatter(e, age, life, pose, buffers);
            case DuelFxEntity.HEX_WARD -> hexWard(e, age, life, pose, buffers);
            case DuelFxEntity.AEGIS -> aegis(e, age, life, pose, buffers);
            case DuelFxEntity.RIFT -> rift(e, age, life, pose, buffers);
            case DuelFxEntity.SKY_SIGIL -> skySigil(e, age, life, pose, buffers);
            case DuelFxEntity.PILLAR -> pillar(e, age, life, pose, buffers);
            case DuelFxEntity.CLOCK -> clock(e, age, life, pose, buffers);
            case DuelFxEntity.CARD -> card(e, age, life, pose, buffers);
            case DuelFxEntity.MIRROR -> mirror(e, age, life, pose, buffers);
            default -> { }
        }
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    private void faceCamera(PoseStack pose) {
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
    }

    // =====================================================================================
    //  Vidro mágico: o círculo rúnico surge, trinca e estilhaça em cacos que voam e somem
    // =====================================================================================
    private void shatter(DuelFxEntity e, float age, int life, PoseStack pose, MultiBufferSource buffers) {
        float r = e.radius();
        int rgb = e.color();
        int bright = DuelDraw.lighten(rgb, 0.55F);
        final float breakAt = 6F;
        faceCamera(pose);
        Matrix4f m = pose.last().pose();
        if (age < breakAt + 4) {
            float grow = DuelDraw.smooth(age / breakAt);
            float a = age < breakAt ? 1F : 1F - (age - breakAt) / 4F;
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(age * 9F));
            VertexConsumer t = buffers.getBuffer(DuelRenderTypes.additive(RUNE));
            DuelDraw.texQuadXY(t, pose.last().pose(), r * (0.35F + 0.65F * grow), r * (0.35F + 0.65F * grow), DuelDraw.argb(bright, a));
            pose.popPose();
            VertexConsumer g = buffers.getBuffer(DuelRenderTypes.GLOW);
            DuelDraw.halo(g, m, 0F, r * 0.5F, r * 1.15F, rgb, 0.35F * a, 24);
            // Rachaduras correndo do centro um instante antes de quebrar
            if (age > breakAt - 2.5F) {
                RandomSource rnd = RandomSource.create(e.getId() * 31L);
                float crack = Mth.clamp((age - (breakAt - 2.5F)) / 2.5F, 0F, 1F);
                for (int k = 0; k < 7; k++) {
                    float ang = rnd.nextFloat() * Mth.TWO_PI;
                    float len = r * (0.6F + 0.4F * rnd.nextFloat()) * crack;
                    float midA = ang + (rnd.nextFloat() - 0.5F) * 0.5F;
                    float mx = Mth.cos(midA) * len * 0.5F, my = Mth.sin(midA) * len * 0.5F;
                    int cc = DuelDraw.argb(0xFFFFFF, 0.9F * a);
                    DuelDraw.strip(g, m, 0, 0, mx, my, 0.05F * r, cc, cc);
                    DuelDraw.strip(g, m, mx, my, Mth.cos(ang) * len, Mth.sin(ang) * len, 0.04F * r, cc, DuelDraw.argb(bright, 0.4F * a));
                }
            }
        }
        if (age >= breakAt) {
            float k = (age - breakAt) / Math.max(1F, life - breakAt);
            VertexConsumer g = buffers.getBuffer(DuelRenderTypes.GLOW);
            // Clarão branco na quebra
            if (age < breakAt + 5) {
                float f = 1F - (age - breakAt) / 5F;
                DuelDraw.halo(g, m, 0F, r * 0.3F, r * (1.2F + (age - breakAt) * 0.25F), 0xFFFFFF, 0.7F * f, 24);
            }
            RandomSource rnd = RandomSource.create(e.getId() * 977L);
            float fade = 1F - DuelDraw.smooth(k);
            for (int i = 0; i < 18; i++) {
                float ang = Mth.TWO_PI * i / 18F + (rnd.nextFloat() - 0.5F) * 0.3F;
                float speed = 0.6F + rnd.nextFloat() * 0.9F;
                float d = r * (0.25F + 0.75F * rnd.nextFloat()) + r * speed * k * 1.8F;
                float drop = k * k * r * 0.8F * rnd.nextFloat();
                float size = r * (0.12F + 0.14F * rnd.nextFloat()) * (1F - 0.4F * k);
                float spin = (rnd.nextFloat() - 0.5F) * 12F * (age - breakAt) * 0.1F;
                float cx = Mth.cos(ang) * d, cy = Mth.sin(ang) * d - drop;
                float tipA = ang + spin;
                DuelDraw.shard(g, m, cx + Mth.cos(tipA) * size, cy + Mth.sin(tipA) * size, cx - Mth.cos(tipA) * size * 0.4F,
                        cy - Mth.sin(tipA) * size * 0.4F, size * 0.45F, DuelDraw.argb(0xFFFFFF, 0.95F * fade), DuelDraw.argb(rgb, 0.7F * fade));
            }
        }
    }

    // =====================================================================================
    //  Resguardo: casca de painéis hexagonais girando, anéis em cima e embaixo
    // =====================================================================================
    private void hexWard(DuelFxEntity e, float age, int life, PoseStack pose, MultiBufferSource buffers) {
        float r = e.radius();
        float h = Math.max(1F, e.param()) + 0.4F;
        int rgb = e.color();
        float env = DuelDraw.envelope(age, life, 6F, 10F);
        if (env <= 0.01F) return;
        float shimmer = 0.32F + 0.12F * Mth.sin(age * 0.35F);
        int seg = 10;
        float panelW = Mth.TWO_PI * r / seg * 0.5F;
        int rows = Math.max(2, Math.round(h / (panelW * 2F)));
        float panelH = h / rows * 0.5F;
        VertexConsumer t = buffers.getBuffer(DuelRenderTypes.additive(HEX));
        pose.pushPose();
        pose.translate(0, -0.15F, 0);
        pose.mulPose(Axis.YP.rotationDegrees(age * 2.2F));
        for (int row = 0; row < rows; row++) {
            for (int k = 0; k < seg; k++) {
                pose.pushPose();
                float ang = 360F * (k + (row % 2) * 0.5F) / seg;
                pose.mulPose(Axis.YP.rotationDegrees(ang));
                pose.translate(0, panelH * (2 * row + 1), r);
                float wave = 0.65F + 0.35F * Mth.sin(age * 0.25F + k * 0.9F + row * 1.7F);
                DuelDraw.texQuadXY(t, pose.last().pose(), panelW * 1.05F, panelH * 1.05F, DuelDraw.argb(rgb, env * shimmer * wave * 1.6F));
                pose.popPose();
            }
        }
        pose.popPose();
        VertexConsumer g = buffers.getBuffer(DuelRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        DuelDraw.ringFlat(g, m, r * 1.02F, 0.12F, 0.05F, rgb, 0.6F * env, 32);
        DuelDraw.ringFlat(g, m, r * 0.9F, 0.1F, h - 0.15F, rgb, 0.5F * env, 32);
        DuelDraw.column(g, m, r, 0F, h * 0.5F, rgb, 0.18F * env, 0F, 24);
    }

    // =====================================================================================
    //  Indestrutível: coluna de luz dourada, três anéis inclinados e auréola
    // =====================================================================================
    private void aegis(DuelFxEntity e, float age, int life, PoseStack pose, MultiBufferSource buffers) {
        float r = e.radius();
        float h = Math.max(1F, e.param());
        int rgb = e.color();
        float env = DuelDraw.envelope(age, life, 5F, 10F);
        if (env <= 0.01F) return;
        VertexConsumer g = buffers.getBuffer(DuelRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        float pulse = 0.8F + 0.2F * Mth.sin(age * 0.5F);
        DuelDraw.column(g, m, r * 0.85F, 0F, h + 1.2F, rgb, 0.22F * env * pulse, 0F, 20);
        DuelDraw.column(g, m, r * 0.55F, 0F, h + 2.5F, 0xFFFFFF, 0.12F * env, 0F, 16);
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            float y = h * (0.2F + 0.32F * k) + 0.12F * Mth.sin(age * 0.2F + k);
            pose.translate(0, y, 0);
            pose.mulPose(Axis.YP.rotationDegrees(age * (k % 2 == 0 ? 6F : -7F) + k * 40F));
            pose.mulPose(Axis.XP.rotationDegrees(14F + 8F * k));
            DuelDraw.ringFlat(g, pose.last().pose(), r, 0.09F, 0F, DuelDraw.lighten(rgb, 0.3F), 0.75F * env, 36);
            pose.popPose();
        }
        // Auréola sobre a cabeça
        DuelDraw.ringFlat(g, m, 0.32F, 0.08F, h + 0.3F, DuelDraw.lighten(rgb, 0.5F), 0.95F * env, 24);
        pose.pushPose();
        pose.translate(0, h * 0.55F, 0);
        faceCamera(pose);
        DuelDraw.halo(buffers.getBuffer(DuelRenderTypes.GLOW), pose.last().pose(), 0F, r * 0.6F, r * 1.7F, rgb, 0.18F * env, 24);
        pose.popPose();
    }

    // =====================================================================================
    //  Exilar: fenda oval para o nada — núcleo escuro girando, borda violeta e runas
    // =====================================================================================
    private void rift(DuelFxEntity e, float age, int life, PoseStack pose, MultiBufferSource buffers) {
        float r = e.radius();
        int rgb = e.color();
        float open = DuelDraw.smooth(age / 6F) * DuelDraw.smooth((life - age) / 8F);
        if (open <= 0.01F) return;
        faceCamera(pose);
        float w = r * 0.62F * open, h = r * 1.1F * (0.4F + 0.6F * open);
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-age * 14F));
        VertexConsumer core = buffers.getBuffer(RenderType.entityTranslucent(RIFT));
        float s = Math.max(w, h);
        pose.pushPose();
        pose.scale(w / s, h / s, 1F);
        DuelDraw.entityQuadXY(core, pose.last(), s, s, DuelDraw.argb(0xFFFFFF, 0.95F));
        pose.popPose();
        pose.popPose();
        // Borda: anel rúnico achatado girando ao contrário
        pose.pushPose();
        pose.scale(w / s, h / s, 1F);
        pose.mulPose(Axis.ZP.rotationDegrees(age * 9F));
        DuelDraw.texQuadXY(buffers.getBuffer(DuelRenderTypes.additive(RUNE)), pose.last().pose(), s * 1.25F, s * 1.25F,
                DuelDraw.argb(DuelDraw.lighten(rgb, 0.25F), 0.9F * open));
        pose.popPose();
        Matrix4f m = pose.last().pose();
        VertexConsumer g = buffers.getBuffer(DuelRenderTypes.GLOW);
        DuelDraw.halo(g, m, s * 0.3F, s * 0.9F, s * 1.9F, rgb, 0.4F * open, 28);
        // Fiapos sendo sugados para o centro
        RandomSource rnd = RandomSource.create(e.getId() * 13L);
        for (int i = 0; i < 10; i++) {
            float ang = rnd.nextFloat() * Mth.TWO_PI + age * 0.05F;
            float ph = (age * 0.06F + rnd.nextFloat()) % 1F;
            float d0 = s * (1.9F - 1.6F * ph), d1 = d0 + s * 0.35F;
            DuelDraw.strip(g, m, Mth.cos(ang) * d0, Mth.sin(ang) * d0, Mth.cos(ang) * d1, Mth.sin(ang) * d1, 0.05F * s,
                    DuelDraw.argb(0xFFFFFF, 0.6F * open * ph), DuelDraw.argb(rgb, 0F));
        }
    }

    // =====================================================================================
    //  Ira Divina: sigilo colossal no céu (dois círculos em sentidos opostos) e cortina de luz até o chão
    // =====================================================================================
    private void skySigil(DuelFxEntity e, float age, int life, PoseStack pose, MultiBufferSource buffers) {
        float r = e.radius();
        float sky = e.param();
        int rgb = e.color();
        float form = DuelDraw.smooth(age / 22F);
        float fade = DuelDraw.smooth((life - age) / 15F);
        float a = form * fade;
        if (a <= 0.01F) return;
        // Na hora do julgamento (~30 ticks) o sigilo pulsa forte
        float strike = age > 28 && age < 40 ? 1F - Math.abs(age - 32F) / 8F : 0F;
        VertexConsumer t = buffers.getBuffer(DuelRenderTypes.additive(RUNE));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(age * 1.6F));
        DuelDraw.texQuadXZ(t, pose.last().pose(), r * (0.6F + 0.4F * form), 0F, DuelDraw.argb(rgb, 0.85F * a + 0.15F * strike));
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-age * 2.8F));
        DuelDraw.texQuadXZ(t, pose.last().pose(), r * 0.55F * form, -0.05F, DuelDraw.argb(DuelDraw.lighten(rgb, 0.5F), 0.9F * a));
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(age * 4.5F));
        DuelDraw.texQuadXZ(t, pose.last().pose(), r * 0.28F * form, -0.1F, DuelDraw.argb(0xFFFFFF, a));
        pose.popPose();
        VertexConsumer g = buffers.getBuffer(DuelRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        DuelDraw.ringFlat(g, m, r * 1.05F * form, 0.5F, -0.02F, rgb, 0.7F * a, 64);
        // Cortina de luz caindo do sigilo
        DuelDraw.column(g, m, r * form, -sky, 0F, rgb, 0F, 0.16F * a + 0.25F * strike, 48);
        DuelDraw.column(g, m, r * 0.18F * form, -sky, 0F, 0xFFFFFF, 0.2F * strike, 0.45F * a, 16);
    }

    /** Pilar do julgamento sobre cada criatura: núcleo branco, casca da cor e onda no chão. */
    private void pillar(DuelFxEntity e, float age, int life, PoseStack pose, MultiBufferSource buffers) {
        float r = e.radius();
        float h = Math.max(8F, e.param());
        int rgb = e.color();
        float k = age / life;
        float a = 1F - DuelDraw.smooth(k);
        float width = 1F - 0.7F * k;
        VertexConsumer g = buffers.getBuffer(DuelRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        DuelDraw.column(g, m, r * 0.35F * width, 0F, h, 0xFFFFFF, 0.95F * a, 0.5F * a, 16);
        DuelDraw.column(g, m, r * width, 0F, h, rgb, 0.45F * a, 0.1F * a, 24);
        DuelDraw.column(g, m, r * 1.6F * width, 0F, h * 0.5F, rgb, 0.15F * a, 0F, 24);
        DuelDraw.ringFlat(g, m, r * (1F + 4F * DuelDraw.smooth(k)), 0.35F, 0.06F, DuelDraw.lighten(rgb, 0.4F), 0.8F * a, 40);
        if (age < 5) {
            pose.pushPose();
            pose.translate(0, 0.5F, 0);
            faceCamera(pose);
            DuelDraw.halo(buffers.getBuffer(DuelRenderTypes.GLOW), pose.last().pose(), 0F, r, r * 4F, 0xFFFFFF, 0.6F * (1F - age / 5F), 24);
            pose.popPose();
        }
    }

    // =====================================================================================
    //  Passo no Tempo: relógio no chão com ponteiros girando para trás e marcas subindo em espiral
    // =====================================================================================
    private void clock(DuelFxEntity e, float age, int life, PoseStack pose, MultiBufferSource buffers) {
        float r = e.radius();
        float h = Math.max(1F, e.param());
        int rgb = e.color();
        float env = DuelDraw.envelope(age, life, 6F, 12F);
        if (env <= 0.01F) return;
        float grow = 0.5F + 0.5F * DuelDraw.smooth(age / 8F);
        pose.pushPose();
        pose.translate(0, 0.06F, 0);
        pose.mulPose(Axis.YP.rotationDegrees(age * 3F));
        DuelDraw.texQuadXZ(buffers.getBuffer(DuelRenderTypes.additive(CLOCK)), pose.last().pose(), r * grow, 0F, DuelDraw.argb(rgb, 0.9F * env));
        pose.popPose();
        // Ponteiros (ao contrário: o tempo volta)
        VertexConsumer g = buffers.getBuffer(DuelRenderTypes.GLOW);
        pose.pushPose();
        pose.translate(0, 0.09F, 0);
        pose.mulPose(Axis.XP.rotationDegrees(90F));
        Matrix4f hm = pose.last().pose();
        float minute = -age * 0.42F, hour = -age * 0.07F;
        int white = DuelDraw.argb(0xFFFFFF, 0.95F * env), tip = DuelDraw.argb(rgb, 0.4F * env);
        DuelDraw.strip(g, hm, 0, 0, Mth.cos(minute) * r * 0.82F * grow, Mth.sin(minute) * r * 0.82F * grow, 0.09F, white, tip);
        DuelDraw.strip(g, hm, 0, 0, Mth.cos(hour) * r * 0.5F * grow, Mth.sin(hour) * r * 0.5F * grow, 0.15F, white, tip);
        pose.popPose();
        Matrix4f m = pose.last().pose();
        DuelDraw.ringFlat(g, m, r * grow, 0.12F, 0.08F, rgb, 0.8F * env, 48);
        // Doze marcas subindo em espiral em volta do corpo
        for (int i = 0; i < 12; i++) {
            float ph = ((age * 0.025F) + i / 12F) % 1F;
            float ang = -age * 0.12F + i * Mth.TWO_PI / 12F;
            float rr = r * 0.55F;
            pose.pushPose();
            pose.translate(Mth.cos(ang) * rr, ph * (h + 1F), Mth.sin(ang) * rr);
            faceCamera(pose);
            float aa = env * Mth.sin(ph * Mth.PI);
            DuelDraw.halo(buffers.getBuffer(DuelRenderTypes.GLOW), pose.last().pose(), 0F, 0.06F, 0.22F, DuelDraw.lighten(rgb, 0.4F), 0.9F * aa, 10);
            pose.popPose();
        }
    }

    // =====================================================================================
    //  Carta de magia: surge girando, sobe e some (param 1 = espelhada/cópia, 2 = se desfaz, 3 = resposta)
    // =====================================================================================
    private void card(DuelFxEntity e, float age, int life, PoseStack pose, MultiBufferSource buffers) {
        float r = e.radius();
        int rgb = e.color();
        int mode = Math.round(e.param());
        float k = age / life;
        float a = mode == 2 ? 1F - DuelDraw.smooth(k) : DuelDraw.envelope(age, life, 4F, 9F);
        if (a <= 0.01F) return;
        pose.translate(0, k * (mode == 2 ? 0.2F : 0.7F), 0);
        // Vira de frente para o jogador e gira no eixo vertical ("a carta é virada")
        float yawCam = -entityRenderDispatcher.camera.getYRot();
        pose.mulPose(Axis.YP.rotationDegrees(yawCam));
        float flip = mode == 3 ? Math.max(0F, 1F - age / 8F) * 540F : Math.max(0F, 1F - age / 12F) * 360F;
        if (mode == 1) flip = -flip;
        pose.mulPose(Axis.YP.rotationDegrees(flip));
        float scale = mode == 2 ? 1F - 0.5F * k : 0.7F + 0.3F * DuelDraw.smooth(age / 5F);
        float hw = r * 0.62F * scale, hh = r * scale;
        DuelDraw.texQuadXY(buffers.getBuffer(DuelRenderTypes.additive(CARD)), pose.last().pose(), hw, hh, DuelDraw.argb(rgb, a));
        VertexConsumer g = buffers.getBuffer(DuelRenderTypes.GLOW);
        DuelDraw.halo(g, pose.last().pose(), 0F, hh * 0.7F, hh * 1.7F, rgb, 0.3F * a, 20);
        if (mode == 2) {
            // Desfazendo: cacos saindo das bordas
            RandomSource rnd = RandomSource.create(e.getId() * 7L);
            for (int i = 0; i < 8; i++) {
                float x = (rnd.nextFloat() * 2 - 1) * hw, y = (rnd.nextFloat() * 2 - 1) * hh;
                float d = k * 0.8F;
                DuelDraw.shard(g, pose.last().pose(), x * (1 + d) + 0.08F, y * (1 + d), x * (1 + d), y * (1 + d) - 0.06F, 0.04F,
                        DuelDraw.argb(0xFFFFFF, a), DuelDraw.argb(rgb, a * 0.5F));
            }
        }
    }

    /** Redirecionar: espelho rúnico que aparece girando rápido onde o projétil foi devolvido. */
    private void mirror(DuelFxEntity e, float age, int life, PoseStack pose, MultiBufferSource buffers) {
        float r = e.radius();
        int rgb = e.color();
        float pop = DuelDraw.smooth(age / 3F);
        float a = pop * (1F - DuelDraw.smooth(age / life));
        if (a <= 0.01F) return;
        faceCamera(pose);
        pose.mulPose(Axis.ZP.rotationDegrees(age * 32F));
        DuelDraw.texQuadXY(buffers.getBuffer(DuelRenderTypes.additive(RUNE)), pose.last().pose(), r * pop, r * pop,
                DuelDraw.argb(DuelDraw.lighten(rgb, 0.3F), a));
        DuelDraw.halo(buffers.getBuffer(DuelRenderTypes.GLOW), pose.last().pose(), r * 0.2F, r * 0.8F, r * 1.4F, rgb, 0.35F * a, 20);
    }

    @Override
    public ResourceLocation getTextureLocation(DuelFxEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
