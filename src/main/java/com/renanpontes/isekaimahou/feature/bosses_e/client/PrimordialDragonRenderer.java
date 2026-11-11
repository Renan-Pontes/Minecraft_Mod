package com.renanpontes.isekaimahou.feature.bosses_e.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.DragonPhase;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialDragon;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Renderer do Dragão Primordial: modelo desenhado a 1/2,5 e aumentado 2,5× (~23 blocos de envergadura), textura das
 * escamas da FASE atual, transição suave entre as escamas da fase anterior e da nova (2,5 s) e o brilho (_glowmask)
 * tingido pela cor do elemento — no Crepúsculo alterna dourado e violeta; carregando a Aniquilação, pulsa rápido.
 */
public class PrimordialDragonRenderer extends GeoEntityRenderer<PrimordialDragon> {
    public static final float SCALE = 2.5F;
    private static final int FADE_TICKS = 50;

    public PrimordialDragonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        withScale(SCALE);
        addRenderLayer(new PhaseFadeLayer(this));
        addRenderLayer(new PhaseGlowLayer(this));
        this.shadowRadius = 4.0F;
    }

    @Override
    protected float getDeathMaxRotation(PrimordialDragon animatable) {
        return 0F;
    }

    @Override
    public int getPackedOverlay(PrimordialDragon animatable, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(animatable.hurtTime > 0 && !animatable.isDeadOrDying()));
    }

    @Override
    public boolean shouldShowName(PrimordialDragon animatable) {
        return false;
    }

    static final class Model extends DefaultedEntityGeoModel<PrimordialDragon> {
        Model() {
            super(IsekaiMahou.id(PrimordialDragon.ID));
        }

        @Override
        public ResourceLocation getTextureResource(PrimordialDragon animatable) {
            return animatable.phase().texture;
        }
    }

    /** [fase anterior, tick em que mudou, fase vista por último] — guardado na própria entidade (cliente). */
    private static int[] fade(PrimordialDragon d) {
        int[] f = d.clientFade;
        int now = d.phase().number();
        if (f[2] == 0) {
            f[0] = now;
            f[2] = now;
        } else if (now != f[2]) {
            f[0] = f[2];
            f[1] = d.tickCount;
            f[2] = now;
        }
        return f;
    }

    /** Camada que desenha as escamas da fase ANTERIOR por cima, sumindo aos poucos. */
    static final class PhaseFadeLayer extends GeoRenderLayer<PrimordialDragon> {
        PhaseFadeLayer(GeoRenderer<PrimordialDragon> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, PrimordialDragon d, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (d.isInvisible()) return;
            int[] f = fade(d);
            float age = d.tickCount + partialTick - f[1];
            if (age < 0 || age >= FADE_TICKS || f[0] == f[2]) return;
            float alpha = 1F - age / FADE_TICKS;
            ResourceLocation prev = DragonPhase.of(f[0]).texture;
            RenderType rt = RenderType.entityTranslucent(prev);
            int argb = ((int) (alpha * 255) << 24) | 0xFFFFFF;
            getRenderer().reRender(model, poseStack, buffers, d, rt, buffers.getBuffer(rt), partialTick, packedLight, packedOverlay, argb);
        }
    }

    /** Brilho do _glowmask tingido pela cor da fase (pulsa; rápido durante a carga da ultimate). */
    static final class PhaseGlowLayer extends GeoRenderLayer<PrimordialDragon> {
        PhaseGlowLayer(GeoRenderer<PrimordialDragon> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, PrimordialDragon d, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (d.isInvisible()) return;
            DragonPhase ph = d.phase();
            RenderType glow = AutoGlowingTexture.getRenderType(ph.texture);
            float t = d.tickCount + partialTick;
            int rgb = ph.rgb;
            if (ph == DragonPhase.TWILIGHT) {
                float k = 0.5F + 0.5F * Mth.sin(t * 0.08F);
                rgb = lerp(0xFFE6A0, 0xB070E0, k);
            }
            float pulse = 0.75F + 0.25F * Mth.sin(t * (d.isCharging() ? 0.6F : 0.1F));
            if (d.isExhausted()) pulse *= 0.55F;
            if (d.isDeadOrDying()) pulse *= Math.max(0.1F, 1F - d.deathTime / 200F);
            int r = (int) (((rgb >> 16) & 255) * pulse);
            int g = (int) (((rgb >> 8) & 255) * pulse);
            int b = (int) ((rgb & 255) * pulse);
            int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
            getRenderer().reRender(model, poseStack, buffers, d, glow, buffers.getBuffer(glow), partialTick, LightTexture.FULL_BRIGHT,
                    packedOverlay, argb);
        }

        private static int lerp(int a, int b, float k) {
            int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255);
            int g = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255);
            int bl = (int) Mth.lerp(k, a & 255, b & 255);
            return (r << 16) | (g << 8) | bl;
        }
    }
}
