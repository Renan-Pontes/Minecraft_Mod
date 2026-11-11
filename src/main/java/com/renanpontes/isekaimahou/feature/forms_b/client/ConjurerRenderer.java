package com.renanpontes.isekaimahou.feature.forms_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellConjurerEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/** Espírito da Invocação: modelo GeckoLib etéreo (translúcido, brilhante), tingido pela cor da magia. */
public class ConjurerRenderer extends GeoEntityRenderer<SpellConjurerEntity> {

    public ConjurerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("spell_conjurer")));
        this.shadowRadius = 0;
    }

    @Override
    public void render(SpellConjurerEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        super.render(e, yaw, pt, ps, buffers, LightTexture.FULL_BRIGHT);
    }

    @Override
    protected void applyRotations(SpellConjurerEntity e, PoseStack ps, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        super.applyRotations(e, ps, ageInTicks, Mth.rotLerp(partialTick, e.yRotO, e.getYRot()), partialTick, nativeScale);
    }

    @Override
    public RenderType getRenderType(SpellConjurerEntity e, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(SpellConjurerEntity e, float partialTick, int packedLight) {
        float flicker = 0.9F + 0.1F * Mth.sin((e.tickCount + partialTick) * 0.5F);
        int a = Mth.clamp((int) (200 * flicker * e.fade(partialTick)), 0, 255);
        return new Color((a << 24) | QuadFx.lighten(e.color(), 0.35F));
    }
}
