package com.renanpontes.isekaimahou.feature.bosses_g.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.BgBones;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.GBossEntity;
import com.renanpontes.isekaimahou.feature.bosses_g.polish.BossShow;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * Renderer GeckoLib dos chefes e invocações do módulo: modelo/animações/textura pelo id, camada brilhante (_glowmask),
 * ossos que somem/aparecem conforme {@link BgBones} e sem "tombar" ao morrer (a animação de morte faz isso).
 */
public class BgGeoRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {

    public BgGeoRenderer(EntityRendererProvider.Context ctx, String modelId, boolean turnsHead, float scale, float shadow) {
        super(ctx, new ToggleModel<>(IsekaiMahou.id(modelId), turnsHead));
        withScale(scale);
        this.shadowRadius = shadow;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(T animatable) {
        return 0.0F;
    }

    /** A morte tem animação própria: o tom vermelho só aparece no golpe, não na queda inteira. */
    @Override
    public int getPackedOverlay(T animatable, float u, float partialTick) {
        if (animatable instanceof LivingEntity living && living.deathTime > 0 && living.hurtTime <= 0) {
            return OverlayTexture.NO_OVERLAY;
        }
        return super.getPackedOverlay(animatable, u, partialTick);
    }

    /** Modelo padrão + esconde os ossos que a entidade pedir. */
    /** v1.0: nos últimos ticks da morte o corpo do chefe se desfaz (1 = inteiro, 0 = sumiu). */
    private float deathAlpha(T animatable, float partialTick) {
        if (!(animatable instanceof GBossEntity boss) || boss.deathTime <= 0) return 1F;
        float left = boss.deathSpan() - (boss.deathTime + partialTick);
        return Mth.clamp(left / BossShow.DISSOLVE_TICKS, 0F, 1F);
    }

    @Override
    public Color getRenderColor(T animatable, float partialTick, int packedLight) {
        float a = deathAlpha(animatable, partialTick);
        if (a >= 1F) return super.getRenderColor(animatable, partialTick, packedLight);
        return Color.ofRGBA(1F, 1F, 1F, a);
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        if (deathAlpha(animatable, partialTick) < 1F) return RenderType.entityTranslucent(texture);
        return super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    static final class ToggleModel<T extends GeoAnimatable> extends DefaultedEntityGeoModel<T> {
        ToggleModel(ResourceLocation id, boolean turnsHead) {
            super(id, turnsHead);
        }

        @Override
        public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> state) {
            super.setCustomAnimations(animatable, instanceId, state);
            if (animatable instanceof BgBones toggles) {
                for (String name : toggles.toggledBones()) {
                    getBone(name).ifPresent(bone -> bone.setHidden(!toggles.isBoneVisible(name)));
                }
            }
        }
    }
}
