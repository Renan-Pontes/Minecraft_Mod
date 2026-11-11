package com.renanpontes.isekaimahou.feature.class_steelball.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SteelballMentorEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/** Mestre Rotari: modelo GeckoLib (chapéu de aba, sobretudo, cachecol) com a bola de aço na mão direita. */
public class MentorRenderer extends GeoEntityRenderer<SteelballMentorEntity> {
    public MentorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("steelball_mentor"), true));
        this.shadowRadius = 0.5F;
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, SteelballMentorEntity mentor) {
                return bone.getName().equals("right_item") ? mentor.getMainHandItem() : null;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, SteelballMentorEntity mentor) {
                return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack ps, GeoBone bone, ItemStack stack, SteelballMentorEntity mentor,
                    MultiBufferSource buffers, float partialTick, int light, int overlay) {
                if (stack.isEmpty()) return;
                ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
                super.renderStackForBone(ps, bone, stack, mentor, buffers, partialTick, light, overlay);
            }
        });
    }
}
