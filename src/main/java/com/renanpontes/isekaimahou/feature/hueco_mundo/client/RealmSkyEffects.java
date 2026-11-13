package com.renanpontes.isekaimahou.feature.hueco_mundo.client;

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
 * Céus dos reinos.
 * <ul>
 *   <li><b>Mundo Oco</b>: céu negro, poucas estrelas frias e a LUA CRESCENTE ETERNA — grande, parada sempre no mesmo
 *       lugar sobre o palácio, com um halo pálido.</li>
 *   <li><b>Reino das Fadas</b>: crepúsculo violeta, estrelas coloridas que cintilam, um planeta rosado enorme no
 *       horizonte e uma lua pequena que gira devagar.</li>
 * </ul>
 */
public class RealmSkyEffects extends DimensionSpecialEffects {
    public enum Style { HOLLOW, FEY }

    private static final ResourceLocation CRESCENT = IsekaiMahou.id("textures/environment/hm_crescent_moon.png");
    private static final ResourceLocation HALO = IsekaiMahou.id("textures/environment/hm_halo.png");
    private static final int[] FEY_STARS = {0xFFD6F5, 0xD8B8FF, 0xB8FFF0, 0xFFF4B8};
    private final Style style;
    private final int stars;
    private final float[][] starQuads;
    private final float[] starPhase;
    private final int[] starColor;

    public RealmSkyEffects(Style style) {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
        this.style = style;
        this.stars = style == Style.HOLLOW ? 500 : 1100;
        this.starQuads = new float[stars][];
        this.starPhase = new float[stars];
        this.starColor = new int[stars];
        buildStars();
    }

    private void buildStars() {
        RandomSource r = RandomSource.create(4243L * (style.ordinal() + 3));
        int n = 0;
        while (n < stars) {
            float x = r.nextFloat() * 2 - 1, y = r.nextFloat() * 2 - 1, z = r.nextFloat() * 2 - 1;
            float len2 = x * x + y * y + z * z;
            if (len2 <= 0.01F || len2 >= 1.0F) continue;
            Vector3f center = new Vector3f(x, y, z).normalize(100.0F);
            float size = 0.1F + r.nextFloat() * (style == Style.FEY ? 0.22F : 0.14F);
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
            starColor[n] = style == Style.HOLLOW ? 0xDDE8FF : FEY_STARS[r.nextInt(FEY_STARS.length)];
            n++;
        }
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
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
        if (camera.getEntity() instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS))) return true;

        PoseStack ps = new PoseStack();
        ps.mulPose(modelView);
        Tesselator tess = Tesselator.getInstance();
        float time = ticks + partialTick;

        // 1) Cúpula: a cor pura do céu do bioma (o tempo é fixo nos reinos).
        int c = level.getBiome(camera.getBlockPosition()).value().getSkyColor();
        float sr = ((c >> 16) & 255) / 255F, sg = ((c >> 8) & 255) / 255F, sb = (c & 255) / 255F;
        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(sr, sg, sb, 1.0F);
        Matrix4f m = ps.last().pose();
        BufferBuilder dome = tess.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        dome.addVertex(m, 0.0F, 16.0F, 0.0F);
        for (int i = -180; i <= 180; i += 45) dome.addVertex(m, 512.0F * Mth.cos(i * Mth.DEG_TO_RAD), 16.0F, 512.0F * Mth.sin(i * Mth.DEG_TO_RAD));
        BufferUploader.drawWithShader(dome.buildOrThrow());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // 2) Estrelas, halo e astros com mistura aditiva e sem névoa.
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        FogRenderer.setupNoFog();
        drawStars(tess, ps, time);
        drawBodies(tess, ps, time);

        setupFog.run();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        return true;
    }

    private void drawStars(Tesselator tess, PoseStack ps, float time) {
        float base = style == Style.HOLLOW ? 0.55F : 0.85F;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(style == Style.FEY ? time * 0.006F : 0F));
        Matrix4f m = ps.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < stars; i++) {
            float a = base * (0.55F + 0.45F * Mth.sin(time * 0.05F + starPhase[i]));
            int col = starColor[i];
            float r = ((col >> 16) & 255) / 255F, g = ((col >> 8) & 255) / 255F, bl = (col & 255) / 255F;
            float[] q = starQuads[i];
            for (int k = 0; k < 4; k++) b.addVertex(m, q[k * 3], q[k * 3 + 1], q[k * 3 + 2]).setColor(r, g, bl, a);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        ps.popPose();
    }

    private void drawBodies(Tesselator tess, PoseStack ps, float time) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        if (style == Style.HOLLOW) {
            // A lua fica ao NORTE (sobre o palácio, para quem chega pelo sul), alta e imóvel.
            // v1.3: halo em três camadas que "respiram" devagar (a lua parece fria e enorme sobre Las Noches).
            RenderSystem.setShaderTexture(0, HALO);
            BufferBuilder h = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float breath = Mth.sin(time * 0.02F);
            body(h, ps, 180F, 48F, 110F, 0x6E7FA8, 0.10F + 0.03F * breath);
            body(h, ps, 180F, 48F, 58F, 0xB8C8E8, 0.35F + 0.05F * breath);
            body(h, ps, 180F, 48F, 34F, 0xE8F0FF, 0.22F + 0.04F * Mth.sin(time * 0.033F + 1.3F));
            BufferUploader.drawWithShader(h.buildOrThrow());
            RenderSystem.setShaderTexture(0, CRESCENT);
            BufferBuilder mo = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            body(mo, ps, 180F, 48F, 27F, 0xF4F6FF, 1.0F);
            // Brilho aditivo por cima da própria lua (fica mais "luminosa" que um adesivo no céu).
            body(mo, ps, 180F, 48F, 27.6F, 0xCFE0FF, 0.25F + 0.05F * breath);
            BufferUploader.drawWithShader(mo.buildOrThrow());
        } else {
            RenderSystem.setShaderTexture(0, HALO);
            BufferBuilder b = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            body(b, ps, 30F, 14F, 90F, 0xF29AD8, 0.45F);
            body(b, ps, 30F, 14F, 46F, 0xFFD6F0, 0.6F);
            float yaw = time * 0.02F + 200F;
            body(b, ps, yaw, 56F, 12F, 0xCFE8FF, 0.5F);
            body(b, ps, yaw, 56F, 5F, 0xFFFFFF, 0.95F);
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
    }

    /** Quadrado com a textura atual a 100 blocos, na direção (yaw, elevação) em graus. */
    private static void body(BufferBuilder b, PoseStack ps, float yaw, float elevation, float size, int rgb, float alpha) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.mulPose(Axis.XP.rotationDegrees(-(90.0F - elevation)));
        Matrix4f m = ps.last().pose();
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, bl = (rgb & 255) / 255F;
        b.addVertex(m, -size, 100.0F, -size).setUv(0.0F, 0.0F).setColor(r, g, bl, alpha);
        b.addVertex(m, size, 100.0F, -size).setUv(1.0F, 0.0F).setColor(r, g, bl, alpha);
        b.addVertex(m, size, 100.0F, size).setUv(1.0F, 1.0F).setColor(r, g, bl, alpha);
        b.addVertex(m, -size, 100.0F, size).setUv(0.0F, 1.0F).setColor(r, g, bl, alpha);
        ps.popPose();
    }
}
