package com.renanpontes.isekaimahou.feature.class_rogue.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.class_rogue.entity.RogueKnifeEntity;

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
 * Faca voadora: o modelo da Faca de Arremesso girando de ponta no sentido do voo; as facas do Leque de Facas brilham
 * (luz cheia) e são um pouco menores.
 */
public class RogueKnifeRenderer extends EntityRenderer<RogueKnifeEntity> {
    private final ItemRenderer items;

    public RogueKnifeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.shadowRadius = 0.1F;
    }

    @Override
    public void render(RogueKnifeEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        if (e.tickCount < 2 && entityRenderDispatcher.camera.getEntity().distanceToSqr(e) < 3.0) return;
        ps.pushPose();
        float t = e.tickCount + partialTick;
        float ry = Mth.lerp(partialTick, e.yRotO, e.getYRot());
        boolean spell = e.mode() == RogueKnifeEntity.MODE_SPELL;
        ps.mulPose(Axis.YP.rotationDegrees(ry - 90F));
        ps.mulPose(Axis.ZP.rotationDegrees(-t * (spell ? 55F : 40F)));
        float s = spell ? 0.75F : 0.85F;
        ps.scale(s, s, s);
        items.renderStatic(e.getItem(), ItemDisplayContext.GROUND, spell ? 0xF000F0 : light, OverlayTexture.NO_OVERLAY, ps, buffers,
                e.level(), e.getId());
        ps.popPose();
        super.render(e, yaw, partialTick, ps, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(RogueKnifeEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
