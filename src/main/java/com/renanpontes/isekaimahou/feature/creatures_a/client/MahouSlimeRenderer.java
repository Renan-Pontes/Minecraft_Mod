package com.renanpontes.isekaimahou.feature.creatures_a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.slime.MahouSlime;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.slime.SlimeKind;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Slimes do módulo: gelatina translúcida (GeckoLib), escala pelo tamanho e "amassa/estica" com os pulos igual ao
 * slime vanilla. Slime de fogo e Rei Slime têm partes que brilham no escuro (textura _glowmask).
 */
public class MahouSlimeRenderer extends GeoEntityRenderer<MahouSlime> {
    public MahouSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SlimeModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(PoseStack poseStack, MahouSlime slime, BakedGeoModel model, RenderType renderType,
                               MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int light, int overlay) {
                if (slime.kind() == SlimeKind.FIRE || slime.kind() == SlimeKind.KING) {
                    super.render(poseStack, slime, model, renderType, buffers, buffer, partialTick, light, overlay);
                }
            }
        });
        this.shadowRadius = 0.25F;
    }

    @Override
    public RenderType getRenderType(MahouSlime slime, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void render(MahouSlime slime, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        this.shadowRadius = 0.25F * slime.getSize();
        super.render(slime, yaw, partialTick, poseStack, buffers, light);
    }

    /** O Rei Slime derrete com animação própria: não tomba de lado como os mobs vanilla. */
    @Override
    protected float getDeathMaxRotation(MahouSlime slime) {
        return slime.kind() == SlimeKind.KING ? 0.0F : super.getDeathMaxRotation(slime);
    }

    /** Rei derretendo: vermelho só no golpe final (a animação de morte dura 2 s). */
    @Override
    public int getPackedOverlay(MahouSlime slime, float u, float partialTick) {
        if (slime.kind() != SlimeKind.KING || slime.deathTime <= 0) return super.getPackedOverlay(slime, u, partialTick);
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(slime.deathTime < 8));
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, MahouSlime slime, BakedGeoModel model,
                                    boolean isReRender, float partialTick, int light, int overlay) {
        // Mesma conta do SlimeRenderer vanilla: tamanho * "squish" (achata ao pousar, estica no pulo).
        float size = slime.getSize();
        float squish = slime.squishAmount(partialTick) / (size * 0.5F + 1.0F);
        float f = 1.0F / (squish + 1.0F);
        super.scaleModelForRender(widthScale * f * size * 0.999F, heightScale / f * size * 0.999F, poseStack, slime, model,
                isReRender, partialTick, light, overlay);
    }

    /** Um modelo/textura por variedade; animações compartilhadas (o Rei Slime tem arquivo próprio). */
    static final class SlimeModel extends GeoModel<MahouSlime> {
        private static final ResourceLocation ANIMATIONS = IsekaiMahou.id("animations/entity/mahou_slime.animation.json");
        private static final ResourceLocation KING_ANIMATIONS = IsekaiMahou.id("animations/entity/king_slime.animation.json");

        @Override
        public ResourceLocation getModelResource(MahouSlime slime) {
            return IsekaiMahou.id("geo/entity/" + slime.kind().id + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(MahouSlime slime) {
            return IsekaiMahou.id("textures/entity/" + slime.kind().id + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(MahouSlime slime) {
            return slime.kind() == SlimeKind.KING ? KING_ANIMATIONS : ANIMATIONS;
        }
    }
}
