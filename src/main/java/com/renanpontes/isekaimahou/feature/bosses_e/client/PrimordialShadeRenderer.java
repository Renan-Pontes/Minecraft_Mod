package com.renanpontes.isekaimahou.feature.bosses_e.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialDragon;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialShade;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

/** Clone de Sombra: o mesmo modelo do Dragão, menor (~9 blocos de envergadura), escuro e translúcido, olhos e veios violeta. */
public class PrimordialShadeRenderer extends GeoEntityRenderer<PrimordialShade> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/primordial_shade.png");

    public PrimordialShadeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<PrimordialShade>(IsekaiMahou.id(PrimordialDragon.ID)) {
            @Override
            public ResourceLocation getTextureResource(PrimordialShade animatable) {
                return TEXTURE;
            }
        });
        withScale(1.0F);
        addRenderLayer(new Glow(this));
        this.shadowRadius = 0F;
    }

    @Override
    public RenderType getRenderType(PrimordialShade animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(PrimordialShade animatable, float partialTick, int packedLight) {
        return Color.ofRGBA(255, 255, 255, 184);
    }

    static final class Glow extends GeoRenderLayer<PrimordialShade> {
        Glow(GeoRenderer<PrimordialShade> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, PrimordialShade s, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            RenderType glow = AutoGlowingTexture.getRenderType(TEXTURE);
            float pulse = 0.7F + 0.3F * Mth.sin((s.tickCount + partialTick) * 0.15F);
            int r = (int) (0xB0 * pulse);
            int g = (int) (0x70 * pulse);
            int b = (int) (0xE0 * pulse);
            getRenderer().reRender(model, poseStack, buffers, s, glow, buffers.getBuffer(glow), partialTick, LightTexture.FULL_BRIGHT,
                    packedOverlay, 0xFF000000 | (r << 16) | (g << 8) | b);
        }
    }
}
