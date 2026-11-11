package com.renanpontes.isekaimahou.feature.trials.client;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

/**
 * Céu do Reino das Provações: crepúsculo estrelado (brilho quente num lado do horizonte, violeta profundo no alto),
 * auroras ondulando na cor do elemento da provação (fusões: as cores dos dois pais) e um selo mágico girando
 * devagar no zênite. Sem sol, lua ou nuvens.
 */
public class TrialSkyEffects extends DimensionSpecialEffects {
    private static final ResourceLocation SIGIL = IsekaiMahou.id("textures/environment/trial_sigil.png");
    private static final int STARS = 1100;
    private static final int ZENITH = 0x05030D, MIDDLE = 0x150D2B, HORIZON = 0x2C1942, DUSK = 0xFF7A4A;

    private final float[][] starQuads = new float[STARS][];
    private final float[] starPhase = new float[STARS];
    private final int[] starColor = new int[STARS];

    public TrialSkyEffects() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
        buildStars();
    }

    private void buildStars() {
        RandomSource r = RandomSource.create(4242L);
        int[] palette = {0xFFFFFF, 0xE6DAFF, 0xFFE6F2, 0xCFE8FF};
        int n = 0;
        while (n < STARS) {
            float x = r.nextFloat() * 2 - 1, y = r.nextFloat() * 2 - 1, z = r.nextFloat() * 2 - 1;
            float len2 = x * x + y * y + z * z;
            if (len2 <= 0.01F || len2 >= 1.0F || y < -0.15F) continue;
            Vector3f center = new Vector3f(x, y, z).normalize(100.0F);
            float size = 0.10F + r.nextFloat() * 0.22F;
            Quaternionf q = new Quaternionf().rotateTo(new Vector3f(0, 0, -1), center).rotateZ((float) (r.nextDouble() * Math.PI * 2));
            float[] quad = new float[12];
            float[][] corners = {{size, -size}, {size, size}, {-size, size}, {-size, -size}};
            for (int k = 0; k < 4; k++) {
                Vector3f v = new Vector3f(corners[k][0], corners[k][1], 0).rotate(q).add(center);
                quad[k * 3] = v.x;
                quad[k * 3 + 1] = v.y;
                quad[k * 3 + 2] = v.z;
            }
            starQuads[n] = quad;
            starPhase[n] = r.nextFloat() * Mth.TWO_PI;
            starColor[n] = palette[r.nextInt(palette.length)];
            n++;
        }
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor.multiply(brightness * 0.9F + 0.1F, brightness * 0.9F + 0.1F, brightness * 0.88F + 0.12F);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Nullable
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera,
                             Matrix4f projection, boolean isFoggy, Runnable setupFog) {
        setupFog.run();
        if (isFoggy) return true;
        FogType fog = camera.getFluidInCamera();
        if (fog == FogType.POWDER_SNOW || fog == FogType.LAVA) return true;
        if (camera.getEntity() instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS))) {
            return true;
        }
        PoseStack ps = new PoseStack();
        ps.mulPose(modelView);
        Tesselator tess = Tesselator.getInstance();
        float time = ticks + partialTick;
        Element el = TrialClientState.element();
        int[] colors = el.isFusion() ? new int[] {el.parentA.color, el.color, el.parentB.color} : new int[] {el.color, lighten(el.color, 0.35F), el.color};

        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        FogRenderer.setupNoFog();
        drawDome(tess, ps, el);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        drawStars(tess, ps, time);
        drawAuroras(tess, ps, time, colors);
        drawSigil(tess, ps, time, el.color);

        setupFog.run();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        return true;
    }

    /** Cúpula em faixas: violeta profundo no zênite, tom do elemento no horizonte e um brilho de crepúsculo a oeste. */
    private void drawDome(Tesselator tess, PoseStack ps, Element el) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = ps.last().pose();
        int horizon = mix(HORIZON, el.color, 0.22F);
        BufferBuilder b = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int lat = -30; lat < 90; lat += 10) {
            for (int lon = 0; lon < 360; lon += 15) {
                vertex(b, m, lat, lon, domeColor(lat, lon, horizon), 1F);
                vertex(b, m, lat, lon + 15, domeColor(lat, lon + 15, horizon), 1F);
                vertex(b, m, lat + 10, lon + 15, domeColor(lat + 10, lon + 15, horizon), 1F);
                vertex(b, m, lat + 10, lon, domeColor(lat + 10, lon, horizon), 1F);
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static int domeColor(float lat, float lon, int horizon) {
        float t = Mth.clamp((lat + 30F) / 120F, 0F, 1F);
        int c = t < 0.3F ? mix(horizon, MIDDLE, t / 0.3F) : mix(MIDDLE, ZENITH, (t - 0.3F) / 0.7F);
        float dusk = Math.max(0F, Mth.cos((lon - 270F) * Mth.DEG_TO_RAD)) * Math.max(0F, 1F - (lat + 30F) / 50F);
        return mix(c, DUSK, dusk * 0.42F);
    }

    private void drawStars(Tesselator tess, PoseStack ps, float time) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(time * 0.003F));
        Matrix4f m = ps.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < STARS; i++) {
            float[] q = starQuads[i];
            float height = Mth.clamp(q[1] / 100F + 0.15F, 0F, 1F);           // estrelas somem perto do horizonte quente
            float a = 0.9F * height * (0.55F + 0.45F * Mth.sin(time * 0.06F + starPhase[i]));
            int c = starColor[i];
            float r = ((c >> 16) & 255) / 255F, g = ((c >> 8) & 255) / 255F, bl = (c & 255) / 255F;
            for (int k = 0; k < 4; k++) b.addVertex(m, q[k * 3], q[k * 3 + 1], q[k * 3 + 2]).setColor(r, g, bl, a);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        ps.popPose();
    }

    /** Três cortinas de aurora: base ondulando, topo apagando, brilho correndo ao longo delas. */
    private void drawAuroras(Tesselator tess, PoseStack ps, float time, int[] colors) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = ps.last().pose();
        BufferBuilder b = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 48;
        for (int k = 0; k < 3; k++) {
            int color = colors[k % colors.length];
            float start = k * 120F + time * 0.008F;
            float span = 150F;
            for (int i = 0; i < segments; i++) {
                float[] p0 = curtain(k, i, segments, start, span, time);
                float[] p1 = curtain(k, i + 1, segments, start, span, time);
                // corpo: base clara → topo transparente
                vertex(b, m, p0[1], p0[0], color, p0[3]);
                vertex(b, m, p1[1], p1[0], color, p1[3]);
                vertex(b, m, p1[1] + p1[2], p1[0], color, 0F);
                vertex(b, m, p0[1] + p0[2], p0[0], color, 0F);
                // miolo mais claro e baixo
                int core = lighten(color, 0.5F);
                vertex(b, m, p0[1], p0[0], core, p0[3] * 0.8F);
                vertex(b, m, p1[1], p1[0], core, p1[3] * 0.8F);
                vertex(b, m, p1[1] + p1[2] * 0.3F, p1[0], core, 0F);
                vertex(b, m, p0[1] + p0[2] * 0.3F, p0[0], core, 0F);
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** {lon, latBase, altura, alpha} do ponto i da cortina k. */
    private static float[] curtain(int k, int i, int segments, float start, float span, float time) {
        float f = i / (float) segments;
        float lon = start + span * f;
        float wave = Mth.sin(i * 0.33F + time * 0.018F + k * 1.7F);
        float lat = 22F + k * 5F + 7F * wave;
        float height = 15F + 7F * Mth.sin(i * 0.21F + time * 0.013F + k * 2.3F);
        float shimmer = 0.6F + 0.4F * Mth.sin(i * 0.55F - time * 0.07F + k);
        float edge = Mth.sin(f * Mth.PI);
        return new float[] {lon, lat, height, 0.42F * shimmer * edge};
    }

    private void drawSigil(Tesselator tess, PoseStack ps, float time, int rgb) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SIGIL);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(time * 0.05F));
        Matrix4f m = ps.last().pose();
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, bl = (rgb & 255) / 255F;
        float s = 34F, a = 0.38F;
        BufferBuilder b = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(m, -s, 100F, -s).setUv(0F, 0F).setColor(r, g, bl, a);
        b.addVertex(m, s, 100F, -s).setUv(1F, 0F).setColor(r, g, bl, a);
        b.addVertex(m, s, 100F, s).setUv(1F, 1F).setColor(r, g, bl, a);
        b.addVertex(m, -s, 100F, s).setUv(0F, 1F).setColor(r, g, bl, a);
        BufferUploader.drawWithShader(b.buildOrThrow());
        ps.popPose();
    }

    private static void vertex(BufferBuilder b, Matrix4f m, float latDeg, float lonDeg, int rgb, float alpha) {
        float lat = latDeg * Mth.DEG_TO_RAD, lon = lonDeg * Mth.DEG_TO_RAD;
        float cl = Mth.cos(lat);
        float x = cl * Mth.sin(lon) * 100F, y = Mth.sin(lat) * 100F, z = cl * Mth.cos(lon) * 100F;
        b.addVertex(m, x, y, z).setColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, alpha);
    }

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0F, 1F);
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int lighten(int rgb, float f) {
        return mix(rgb, 0xFFFFFF, f);
    }
}
