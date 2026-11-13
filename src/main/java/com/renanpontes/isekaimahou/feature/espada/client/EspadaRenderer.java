package com.renanpontes.isekaimahou.feature.espada.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaBoss;
import com.renanpontes.isekaimahou.feature.espada.entity.Ulquiorra;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * Renderer GeckoLib dos Espada: UM modelo por chefe com todas as formas; a forma sincronizada escolhe a TEXTURA
 * ({@link EspadaBoss#textureName()}, com glowmask própria) e liga/desliga ossos (asas, cauda, chifres, manto...).
 * Sem tombar ao morrer (a animação de morte faz isso); Ulquiorra fica translúcido e some como cinza.
 */
public class EspadaRenderer extends GeoEntityRenderer<EspadaBoss> {
    public EspadaRenderer(EntityRendererProvider.Context ctx, String modelId, float shadow) {
        super(ctx, new FormModel(IsekaiMahou.id(modelId)));
        this.shadowRadius = shadow;
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            // Virando cinza: o brilho (lágrimas, olhos) apaga junto com o corpo.
            @Override
            public void render(PoseStack pose, EspadaBoss boss, BakedGeoModel model, RenderType type, MultiBufferSource buffers,
                               VertexConsumer buffer, float partialTick, int light, int overlay) {
                if (alpha(boss, partialTick) < 0.6F) return;
                super.render(pose, boss, model, type, buffers, buffer, partialTick, light, overlay);
            }
        });
    }

    @Override
    protected float getDeathMaxRotation(EspadaBoss animatable) {
        return 0.0F;
    }

    @Override
    public int getPackedOverlay(EspadaBoss animatable, float u, float partialTick) {
        if (animatable.deathTime > 0 && animatable.hurtTime <= 0) return OverlayTexture.NO_OVERLAY;
        return super.getPackedOverlay(animatable, u, partialTick);
    }

    /** Opacidade na morte de Ulquiorra: some devagar (cinzas). */
    private static float alpha(EspadaBoss boss, float partialTick) {
        if (!(boss instanceof Ulquiorra) || boss.deathTime <= 0) return 1F;
        float t = boss.deathTime + partialTick;
        return Mth.clamp(1F - (t - 30F) / 100F, 0F, 1F);
    }

    @Override
    public Color getRenderColor(EspadaBoss animatable, float partialTick, int packedLight) {
        float a = alpha(animatable, partialTick);
        if (a >= 1F) return super.getRenderColor(animatable, partialTick, packedLight);
        // Vira cinza enquanto some.
        float g = 0.55F + 0.45F * a;
        return Color.ofRGBA(g, g, g, a);
    }

    @Override
    public RenderType getRenderType(EspadaBoss animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        if (alpha(animatable, partialTick) < 1F) return RenderType.entityTranslucent(texture);
        return super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    /**
     * Modelo padrão + textura pela forma + ossos por forma. Uma forma também pode trocar o MODELO e o arquivo de
     * animações ({@link EspadaBoss#modelName()} / {@link EspadaBoss#animationName()}): Barragan em Arrogante vira o
     * rei de ossos (modelo do Rei Vazio, animações copiadas com os nomes {@code animation.barragan.*}).
     */
    static final class FormModel extends DefaultedEntityGeoModel<EspadaBoss> {
        private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
        private static final Map<String, ResourceLocation> MODELS = new ConcurrentHashMap<>();
        private static final Map<String, ResourceLocation> ANIMATIONS = new ConcurrentHashMap<>();

        FormModel(ResourceLocation id) {
            super(id, true);
        }

        @Override
        public ResourceLocation getModelResource(EspadaBoss animatable) {
            return MODELS.computeIfAbsent(animatable.modelName(), n -> IsekaiMahou.id("geo/entity/" + n + ".geo.json"));
        }

        @Override
        public ResourceLocation getAnimationResource(EspadaBoss animatable) {
            return ANIMATIONS.computeIfAbsent(animatable.animationName(), n -> IsekaiMahou.id("animations/entity/" + n + ".animation.json"));
        }

        @Override
        public ResourceLocation getTextureResource(EspadaBoss animatable) {
            return TEXTURES.computeIfAbsent(animatable.textureName(), n -> IsekaiMahou.id("textures/entity/" + n + ".png"));
        }

        @Override
        public void setCustomAnimations(EspadaBoss animatable, long instanceId, AnimationState<EspadaBoss> state) {
            super.setCustomAnimations(animatable, instanceId, state);
            for (String name : animatable.toggledBones()) {
                getBone(name).ifPresent(bone -> bone.setHidden(!animatable.isBoneVisible(name)));
            }
        }
    }
}
