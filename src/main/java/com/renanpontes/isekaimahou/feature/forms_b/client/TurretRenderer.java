package com.renanpontes.isekaimahou.feature.forms_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellTurretEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/**
 * Torre: cristal GeckoLib translúcido, brilhante e tingido pela cor da magia, com um selo no chão
 * na cor de destaque (2º elemento em magias multi-elemento).
 */
public class TurretRenderer extends GeoEntityRenderer<SpellTurretEntity> {
    private static final ResourceLocation BASE = QuadFx.tex("spell_zone_inner");

    public TurretRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("spell_turret")));
        this.shadowRadius = 0.3F;
    }

    @Override
    public void render(SpellTurretEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float fade = e.fade(pt);
        ps.pushPose();
        ps.translate(0, -0.45, 0);
        ps.mulPose(Axis.YP.rotationDegrees((e.tickCount + pt) * 1.5F));
        QuadFx.flat(ps, QuadFx.glow(buffers, BASE), 0.9F, e.accentColor(), 0.6F * fade);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, LightTexture.FULL_BRIGHT);
    }

    @Override
    protected void applyRotations(SpellTurretEntity e, PoseStack ps, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        super.applyRotations(e, ps, ageInTicks, Mth.rotLerp(partialTick, e.yRotO, e.getYRot()), partialTick, nativeScale);
    }

    @Override
    public RenderType getRenderType(SpellTurretEntity e, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(SpellTurretEntity e, float partialTick, int packedLight) {
        int a = Mth.clamp((int) (220 * e.fade(partialTick)), 0, 255);
        return new Color((a << 24) | QuadFx.lighten(e.color(), 0.25F));
    }
}
