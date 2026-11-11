package com.renanpontes.isekaimahou.feature.dimensions.client;

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
 * Céu mágico das dimensões (sem sol nem lua comuns):
 * <ul>
 *   <li>Plano Elemental: quatro orbes elementais girando devagar, estrelas coloridas e um mar de nuvens abaixo das ilhas.</li>
 *   <li>Santuário Celestial: um sol radiante parado no zênite e duas luas de cristal.</li>
 *   <li>Fenda do Crepúsculo: céu violeta cheio de estrelas e um eclipse eterno.</li>
 * </ul>
 * A cor do céu vem do bioma (sky_color), então cada região tem o seu tom.
 */
public class MagicSkyEffects extends DimensionSpecialEffects {
    public enum Style { ELEMENTAL, CELESTIAL, TWILIGHT }

    private static final ResourceLocation ORB = IsekaiMahou.id("textures/environment/sky_orb.png");
    private static final ResourceLocation DISC = IsekaiMahou.id("textures/environment/eclipse_disc.png");
    private static final int STARS = 900;
    private static final int[] ELEMENT_ORBS = {0xFF7A3A, 0x4FB4FF, 0xC8955A, 0xA8FFE0};
    private static final int[] TWILIGHT_STARS = {0xFFD6F5, 0xD8B8FF, 0xFFFFFF, 0xFFB8D8};

    private final Style style;
    private final float[][] starQuads = new float[STARS][];
    private final float[] starPhase = new float[STARS];
    private final int[] starColor = new int[STARS];

    public MagicSkyEffects(Style style) {
        super(switch (style) {
            case ELEMENTAL -> 40.0F;
            case CELESTIAL -> 100.0F;
            case TWILIGHT -> Float.NaN;
        }, true, SkyType.NORMAL, false, false);
        this.style = style;
        buildStars();
    }

    /** Estrelas fixas (direção, tamanho, fase da cintilação e cor) geradas uma vez. */
    private void buildStars() {
        RandomSource r = RandomSource.create(7919L * (style.ordinal() + 1));
        int n = 0;
        while (n < STARS) {
            float x = r.nextFloat() * 2 - 1, y = r.nextFloat() * 2 - 1, z = r.nextFloat() * 2 - 1;
            float len2 = x * x + y * y + z * z;
            if (len2 <= 0.01F || len2 >= 1.0F) continue;
            Vector3f center = new Vector3f(x, y, z).normalize(100.0F);
            float size = 0.12F + r.nextFloat() * (style == Style.TWILIGHT ? 0.24F : 0.16F);
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
            starColor[n] = switch (style) {
                case ELEMENTAL -> lighten(ELEMENT_ORBS[r.nextInt(ELEMENT_ORBS.length)], 0.6F);
                case CELESTIAL -> 0xFFF6DC;
                case TWILIGHT -> TWILIGHT_STARS[r.nextInt(TWILIGHT_STARS.length)];
            };
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
        if (camera.getEntity() instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS))) {
            return true;
        }

        PoseStack ps = new PoseStack();
        ps.mulPose(modelView);
        Tesselator tess = Tesselator.getInstance();
        float time = ticks + partialTick;

        // 1) Cúpula com a cor do céu do bioma.
        Vec3 sky = skyColor(level, camera, partialTick);
        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor((float) sky.x, (float) sky.y, (float) sky.z, 1.0F);
        Matrix4f m = ps.last().pose();
        BufferBuilder dome = tess.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        dome.addVertex(m, 0.0F, 16.0F, 0.0F);
        for (int i = -180; i <= 180; i += 45) {
            dome.addVertex(m, 512.0F * Mth.cos(i * Mth.DEG_TO_RAD), 16.0F, 512.0F * Mth.sin(i * Mth.DEG_TO_RAD));
        }
        BufferUploader.drawWithShader(dome.buildOrThrow());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // 2) Estrelas e orbes com mistura aditiva, sem névoa.
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        FogRenderer.setupNoFog();
        drawStars(tess, ps, time);
        drawOrbs(tess, ps, time);

        setupFog.run();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        return true;
    }

    private Vec3 skyColor(ClientLevel level, Camera camera, float partialTick) {
        if (style != Style.TWILIGHT) return level.getSkyColor(camera.getPosition(), partialTick);
        // No Crepúsculo o tempo é fixo no fim da tarde: usa a cor pura do bioma, levemente escurecida.
        int c = level.getBiome(camera.getBlockPosition()).value().getSkyColor();
        return new Vec3(((c >> 16) & 255) / 255.0, ((c >> 8) & 255) / 255.0, (c & 255) / 255.0).scale(0.8);
    }

    private void drawStars(Tesselator tess, PoseStack ps, float time) {
        float base = switch (style) {
            case ELEMENTAL -> 0.45F;
            case CELESTIAL -> 0.3F;
            case TWILIGHT -> 0.95F;
        };
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(time * 0.004F));
        Matrix4f m = ps.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < STARS; i++) {
            float a = base * (0.55F + 0.45F * Mth.sin(time * 0.05F + starPhase[i]));
            int c = starColor[i];
            float r = ((c >> 16) & 255) / 255F, g = ((c >> 8) & 255) / 255F, bl = (c & 255) / 255F;
            float[] q = starQuads[i];
            for (int k = 0; k < 4; k++) b.addVertex(m, q[k * 3], q[k * 3 + 1], q[k * 3 + 2]).setColor(r, g, bl, a);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        ps.popPose();
    }

    private void drawOrbs(Tesselator tess, PoseStack ps, float time) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, ORB);
        BufferBuilder b = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        switch (style) {
            case ELEMENTAL -> {
                for (int i = 0; i < ELEMENT_ORBS.length; i++) {
                    float yaw = time * 0.02F + i * 90.0F;
                    float elevation = 35.0F + 15.0F * Mth.sin(time * 0.003F + i * 1.7F);
                    orb(b, ps, yaw, elevation, 26.0F, ELEMENT_ORBS[i], 0.35F);
                    orb(b, ps, yaw, elevation, 8.0F, lighten(ELEMENT_ORBS[i], 0.35F), 0.95F);
                }
            }
            case CELESTIAL -> {
                orb(b, ps, 20.0F, 72.0F, 62.0F, 0xFFE9A8, 0.45F);
                orb(b, ps, 20.0F, 72.0F, 18.0F, 0xFFFDF0, 1.0F);
                for (int k = 0; k < 2; k++) {
                    float yaw = time * 0.03F + k * 180.0F;
                    int c = k == 0 ? 0xC9B8FF : 0xA8F0FF;
                    orb(b, ps, yaw, 24.0F, 14.0F, c, 0.3F);
                    orb(b, ps, yaw, 24.0F, 5.0F, lighten(c, 0.5F), 0.9F);
                }
            }
            case TWILIGHT -> {
                orb(b, ps, 0.0F, 30.0F, 60.0F, 0xFF7AC8, 0.5F);
                orb(b, ps, 0.0F, 30.0F, 36.0F, 0xFFB347, 0.55F);
                orb(b, ps, 0.0F, 30.0F, 20.0F, 0xFFF0F8, 0.9F);
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());

        if (style == Style.TWILIGHT) {
            // O disco escuro da lua por cima do brilho (mistura normal, não aditiva).
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, DISC);
            BufferBuilder d = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            orb(d, ps, 0.0F, 30.0F, 17.0F, 0x0A0612, 1.0F);
            BufferUploader.drawWithShader(d.buildOrThrow());
        }
    }

    /** Quadrado com a textura atual, a 100 blocos, na direção (yaw, elevação) em graus. */
    private static void orb(BufferBuilder b, PoseStack ps, float yaw, float elevation, float size, int rgb, float alpha) {
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

    private static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r + (255 - r) * f);
        g = (int) (g + (255 - g) * f);
        b = (int) (b + (255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }
}
