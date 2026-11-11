package com.renanpontes.isekaimahou.feature.class_gambler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.ThrownCardEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Carta arremessada: deitada e girando como um disco (o giro clássico das cartas cinéticas); a ficha do Caça-Níquel
 * gira de pé como moeda. Brilho cheio nas cartas de magia (estão carregadas de energia).
 */
public class ThrownCardRenderer extends EntityRenderer<ThrownCardEntity> {
    private final ItemRenderer items;

    public ThrownCardRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.shadowRadius = 0.1F;
    }

    @Override
    public void render(ThrownCardEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        if (e.tickCount < 2 && entityRenderDispatcher.camera.getEntity().distanceToSqr(e) < 3.0) return;
        float t = e.tickCount + partialTick;
        ps.pushPose();
        float ry = Mth.lerp(partialTick, e.yRotO, e.getYRot());
        if (e.mode() == ThrownCardEntity.Mode.COIN) {
            ps.mulPose(Axis.YP.rotationDegrees(t * 30F));
            ps.scale(0.55F, 0.55F, 0.55F);
        } else {
            ps.mulPose(Axis.YP.rotationDegrees(ry));
            ps.mulPose(Axis.XP.rotationDegrees(90F));
            ps.mulPose(Axis.ZP.rotationDegrees(t * 45F));
            ps.scale(0.7F, 0.7F, 0.7F);
        }
        int l = e.mode() == ThrownCardEntity.Mode.PHYSICAL ? light : LightTexture.FULL_BRIGHT;
        items.renderStatic(e.getItem(), ItemDisplayContext.GROUND, l, OverlayTexture.NO_OVERLAY, ps, buffers, e.level(), e.getId());
        ps.popPose();
        super.render(e, yaw, partialTick, ps, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(ThrownCardEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
