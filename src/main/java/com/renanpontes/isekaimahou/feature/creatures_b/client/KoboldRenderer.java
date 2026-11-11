package com.renanpontes.isekaimahou.feature.creatures_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.KoboldEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/** Kobold: modelo GeckoLib + a picareta (item da mão) desenhada na mão direita; vela do capacete brilha. */
public class KoboldRenderer extends CbRenderer<KoboldEntity> {
    public KoboldRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, CbRenderer.model("kobold", "kobold"), true, 0.4F, null);
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, KoboldEntity kobold) {
                if (!"right_hand".equals(bone.getName())) return null;
                ItemStack s = kobold.getMainHandItem();
                return s.isEmpty() ? null : s;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, KoboldEntity kobold) {
                return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack pose, GeoBone bone, ItemStack stack, KoboldEntity kobold,
                                              MultiBufferSource buffers, float partialTick, int light, int overlay) {
                // O item sai "deitado" do osso: gira para apontar para frente, e é menor (kobold é pequeno).
                pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
                pose.scale(0.7F, 0.7F, 0.7F);
                super.renderStackForBone(pose, bone, stack, kobold, buffers, partialTick, light, overlay);
            }
        });
    }
}
