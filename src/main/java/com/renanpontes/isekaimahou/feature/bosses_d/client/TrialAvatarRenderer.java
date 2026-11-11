package com.renanpontes.isekaimahou.feature.bosses_d.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialAvatar;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialElement;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renderer do Avatar: textura do elemento do corpo (fusão = pai A) + brilho tingido (fusão = cor do pai B).
 * Sem o tombo/vermelho de morte do vanilla: a morte é a animação "death".
 */
public class TrialAvatarRenderer extends GeoEntityRenderer<TrialAvatar> {
    public TrialAvatarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        addRenderLayer(new TintedGlowLayer<>(this, TrialAvatar::glowRgb, TrialAvatar::isChanneling));
        this.shadowRadius = 0.9F;
    }

    @Override
    protected float getDeathMaxRotation(TrialAvatar animatable) {
        return 0F;
    }

    @Override
    public int getPackedOverlay(TrialAvatar animatable, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(animatable.hurtTime > 0 && !animatable.isDeadOrDying()));
    }

    /** Modelo/animações fixos; textura por elemento (a cabeça segue as animações, que olham para cima, para baixo...). */
    static final class Model extends DefaultedEntityGeoModel<TrialAvatar> {
        private static final ResourceLocation[] TEXTURES = new ResourceLocation[Element.values().length];

        Model() {
            super(IsekaiMahou.id("trial_avatar"));
        }

        @Override
        public ResourceLocation getTextureResource(TrialAvatar animatable) {
            Element body = TrialElement.body(animatable.element());
            int i = body.ordinal();
            if (TEXTURES[i] == null) TEXTURES[i] = IsekaiMahou.id("textures/entity/trial_avatar_" + body.id + ".png");
            return TEXTURES[i];
        }
    }
}
