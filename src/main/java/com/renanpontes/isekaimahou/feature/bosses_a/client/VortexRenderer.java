package com.renanpontes.isekaimahou.feature.bosses_a.client;

import java.util.function.BiFunction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Funis translúcidos GeckoLib (tornado do Zéfiro, redemoinho da Maré). A escala acompanha o abrir/fechar
 * do efeito; o tornado brilha (vento claro), o redemoinho usa a luz do lugar.
 */
public class VortexRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    private final BiFunction<T, Float, Float> scale;
    private final boolean fullBright;

    public VortexRenderer(EntityRendererProvider.Context ctx, String id, BiFunction<T, Float, Float> scale, boolean fullBright) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id(id)));
        this.scale = scale;
        this.fullBright = fullBright;
        this.shadowRadius = 0F;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float s = Math.max(0.01F, scale.apply(entity, partialTick));
        this.scaleWidth = s;
        this.scaleHeight = s;
        super.render(entity, yaw, partialTick, pose, buffers, fullBright ? LightTexture.FULL_BRIGHT : light);
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }
}
