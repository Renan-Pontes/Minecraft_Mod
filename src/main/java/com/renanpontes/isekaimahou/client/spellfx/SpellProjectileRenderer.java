package com.renanpontes.isekaimahou.client.spellfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.entity.SpellProjectileEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Projétil de magia: núcleo brilhante (camada translúcida colorida + halo aditivo + ponto quente) e uma FITA de rastro
 * que segue as últimas posições, afinando até a cauda, com a textura "correndo". Tudo escala com o tamanho e o tier
 * visual: tier 3+ ganha raios girando no halo; tier 4+ um círculo rúnico girando atrás do núcleo, de frente para a
 * direção do voo. Sempre aceso.
 */
public class SpellProjectileRenderer extends EntityRenderer<SpellProjectileEntity> {
    private static final ResourceLocation CORE = IsekaiMahou.id("textures/entity/fx_orb_core.png");
    private static final ResourceLocation GLOW = IsekaiMahou.id("textures/entity/fx_orb_glow.png");
    private static final ResourceLocation TRAIL = IsekaiMahou.id("textures/entity/fx_trail.png");
    private static final ResourceLocation RUNE_RING = IsekaiMahou.id("textures/particle/fx_circle_0.png");

    public SpellProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    protected int getBlockLightLevel(SpellProjectileEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public boolean shouldRender(SpellProjectileEntity e, Frustum frustum, double camX, double camY, double camZ) {
        if (!e.shouldRender(camX, camY, camZ)) return false;
        return frustum.isVisible(e.getBoundingBoxForCulling().inflate(3.0));
    }

    @Override
    public void render(SpellProjectileEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        int rgb = e.color() & 0xFFFFFF;
        int tier = Mth.clamp(e.visualTier(), 0, 5);
        float size = Mth.clamp(e.size(), 0.5F, 4.0F);
        Element el = e.element();
        float age = e.tickCount + pt;
        boolean dark = el == Element.SHADOW || el == Element.VOID || el == Element.ABYSS || el == Element.GRAVITY;
        float flicker = switch (el) {
            case FIRE, SUN, MAGMA, HELLFIRE -> 1F + 0.12F * Mth.sin(age * 2.1F) + 0.06F * Mth.sin(age * 5.3F);
            case LIGHTNING -> 0.85F + 0.3F * ((e.getId() * 31 + (int) (age * 3)) % 7) / 7F;
            default -> 1F + 0.07F * Mth.sin(age * 0.9F);
        };
        Vec3 feet = e.getPosition(pt);
        Vec3 head = feet.add(0, e.getBbHeight() / 2, 0);

        // ---------------- Rastro ----------------
        renderTrail(e, pose, buffers, feet, head, rgb, tier, size, age, dark);

        // ---------------- Núcleo ----------------
        pose.pushPose();
        pose.translate(0, e.getBbHeight() / 2, 0);

        // Círculo rúnico atrás do núcleo (tier 4+), virado para a direção do voo
        Vec3 v = e.getDeltaMovement();
        if (tier >= 4 && v.lengthSqr() > 1.0E-4) {
            pose.pushPose();
            Vec3 d = v.normalize();
            pose.translate(-d.x * 0.35 * size, -d.y * 0.35 * size, -d.z * 0.35 * size);
            float yRot = (float) Mth.atan2(d.x, d.z);
            float xRot = (float) -Math.asin(Mth.clamp(d.y, -1, 1));
            pose.mulPose(Axis.YP.rotation(yRot));
            pose.mulPose(Axis.XP.rotation(xRot));
            pose.mulPose(Axis.ZP.rotationDegrees(age * 9F));
            float rs = (0.7F + 0.12F * tier) * size;
            quadBoth(buffers.getBuffer(RenderType.eyes(RUNE_RING)), pose.last(), rs, 0xFF000000 | scale(rgb, 0.85F));
            pose.popPose();
        }

        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        float halo = (0.55F + 0.18F * tier) * size * flicker;
        // Halo aditivo (brilha) — gira devagar; tier 3+ mostra os raios girando mais rápido
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(age * (tier >= 3 ? 6F : 2F)));
        quad(buffers.getBuffer(RenderType.eyes(GLOW)), pose.last(), halo, 0xFF000000 | scale(rgb, dark ? 0.55F : 0.85F));
        if (tier >= 3) {
            pose.mulPose(Axis.ZP.rotationDegrees(-age * 11F));
            quad(buffers.getBuffer(RenderType.eyes(GLOW)), pose.last(), halo * 1.35F, 0xFF000000 | scale(rgb, 0.35F));
        }
        pose.popPose();
        // Núcleo translúcido colorido (aparece de dia) + ponto quente
        float core = (0.28F + 0.06F * tier) * size * (0.95F + 0.05F * flicker);
        int coreRgb = dark ? darken(rgb, 0.35F) : lighten(rgb, 0.35F);
        quad(buffers.getBuffer(RenderType.entityTranslucentEmissive(CORE)), pose.last(), core, (235 << 24) | coreRgb);
        int hot = dark ? lighten(rgb, 0.2F) : lighten(rgb, 0.8F);
        quad(buffers.getBuffer(RenderType.eyes(CORE)), pose.last(), core * 0.6F, 0xFF000000 | scale(hot, dark ? 0.5F : 0.9F));
        pose.popPose();

        super.render(e, yaw, pt, pose, buffers, light);
    }

    /** Fita do rastro: pontos da cabeça (posição interpolada) até a cauda, larguras e brilho caindo. */
    private void renderTrail(SpellProjectileEntity e, PoseStack pose, MultiBufferSource buffers, Vec3 feet, Vec3 head,
                             int rgb, int tier, float size, float age, boolean dark) {
        int count = e.trailCount();
        // trailAt(0) é a posição do tick atual (à frente da cabeça interpolada): o rastro começa em trailAt(1)
        int n = Math.min(count - 1, 5 + 2 * tier);
        if (n < 1) return;
        Vec3[] pts = new Vec3[n + 1];
        pts[0] = head;
        for (int i = 1; i <= n; i++) pts[i] = e.trailAt(i);
        // descarta pontos colados uns nos outros (projétil parado / recém-nascido)
        int m = 0;
        Vec3[] clean = new Vec3[pts.length];
        clean[m++] = pts[0];
        for (int i = 1; i < pts.length; i++) {
            if (pts[i].distanceToSqr(clean[m - 1]) > 0.0025) clean[m++] = pts[i];
        }
        if (m < 2) return;
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        float width = (0.32F + 0.07F * tier) * size;
        PoseStack.Pose p = pose.last();
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(TRAIL));
        ribbon(glow, p, clean, m, feet, cam, width * 1.6F, rgb, dark ? 0.5F : 0.8F, age, true);
        VertexConsumer inner = buffers.getBuffer(RenderType.entityTranslucentEmissive(TRAIL));
        ribbon(inner, p, clean, m, feet, cam, width * 0.7F, dark ? darken(rgb, 0.4F) : lighten(rgb, 0.4F), 0.75F, age * 1.3F, false);
    }

    private static void ribbon(VertexConsumer vc, PoseStack.Pose p, Vec3[] pts, int m, Vec3 origin, Vec3 cam,
                               float width, int rgb, float bright, float age, boolean additive) {
        Vec3[] side = new Vec3[m];
        for (int i = 0; i < m; i++) {
            Vec3 tan = i == 0 ? pts[0].subtract(pts[1]) : i == m - 1 ? pts[i - 1].subtract(pts[i]) : pts[i - 1].subtract(pts[i + 1]);
            Vec3 s = tan.cross(pts[i].subtract(cam));
            if (s.lengthSqr() < 1.0E-8) s = tan.cross(new Vec3(0, 1, 0));
            if (s.lengthSqr() < 1.0E-8) s = new Vec3(1, 0, 0);
            float f = 1F - i / (float) (m - 1);
            side[i] = s.normalize().scale(width * 0.5F * (0.15F + 0.85F * f));
        }
        float flow = -age * 0.15F;
        for (int i = 0; i < m - 1; i++) {
            float f0 = 1F - i / (float) (m - 1), f1 = 1F - (i + 1) / (float) (m - 1);
            int c0 = color(rgb, bright * f0, additive), c1 = color(rgb, bright * f1, additive);
            Vec3 a = pts[i].subtract(origin), b = pts[i + 1].subtract(origin);
            float u0 = flow + i / (float) (m - 1), u1 = flow + (i + 1) / (float) (m - 1);
            Vec3 s0 = side[i], s1 = side[i + 1];
            // frente
            vtx(vc, p, a.subtract(s0), c0, u0, 1);
            vtx(vc, p, a.add(s0), c0, u0, 0);
            vtx(vc, p, b.add(s1), c1, u1, 0);
            vtx(vc, p, b.subtract(s1), c1, u1, 1);
            // verso
            vtx(vc, p, b.subtract(s1), c1, u1, 1);
            vtx(vc, p, b.add(s1), c1, u1, 0);
            vtx(vc, p, a.add(s0), c0, u0, 0);
            vtx(vc, p, a.subtract(s0), c0, u0, 1);
        }
    }

    /** Aditivo: escurecer a cor = mais transparente. Translúcido: usa o alfa. */
    private static int color(int rgb, float f, boolean additive) {
        f = Mth.clamp(f, 0F, 1F);
        if (additive) return 0xFF000000 | scale(rgb, f);
        return ((int) (255 * f) << 24) | (rgb & 0xFFFFFF);
    }

    private static void vtx(VertexConsumer vc, PoseStack.Pose p, Vec3 pos, int argb, float u, float v) {
        vc.addVertex(p, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(p, 0, 1, 0);
    }

    /** Quadrado no plano XY (já virado para a câmera pela pose), de lado {@code size}. */
    private static void quad(VertexConsumer vc, PoseStack.Pose p, float size, int argb) {
        float h = size * 0.5F;
        vtx(vc, p, new Vec3(-h, -h, 0), argb, 0, 1);
        vtx(vc, p, new Vec3(h, -h, 0), argb, 1, 1);
        vtx(vc, p, new Vec3(h, h, 0), argb, 1, 0);
        vtx(vc, p, new Vec3(-h, h, 0), argb, 0, 0);
    }

    /** Quadrado visível dos dois lados (para o círculo rúnico, que não encara a câmera). */
    private static void quadBoth(VertexConsumer vc, PoseStack.Pose p, float size, int argb) {
        quad(vc, p, size, argb);
        float h = size * 0.5F;
        vtx(vc, p, new Vec3(-h, h, 0), argb, 0, 0);
        vtx(vc, p, new Vec3(h, h, 0), argb, 1, 0);
        vtx(vc, p, new Vec3(h, -h, 0), argb, 1, 1);
        vtx(vc, p, new Vec3(-h, -h, 0), argb, 0, 1);
    }

    private static int scale(int rgb, float f) {
        f = Mth.clamp(f, 0F, 1F);
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int lighten(int rgb, float f) {
        return FxDraw.lighten(rgb, f);
    }

    private static int darken(int rgb, float f) {
        return FxDraw.darken(rgb, f);
    }

    @Override
    public ResourceLocation getTextureLocation(SpellProjectileEntity entity) {
        return CORE;
    }
}
