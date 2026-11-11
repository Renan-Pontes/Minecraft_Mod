package com.renanpontes.isekaimahou.feature.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.equipment.EquipmentItems;
import com.renanpontes.isekaimahou.feature.equipment.FocusOrb;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Como os acessórios aparecem no corpo (respeita o botão "mostrar" do espaço no Curios):
 * o Foco flutua girando ao lado do ombro direito, o Amuleto fica no peito e o Talismã pendurado no quadril.
 * Os anéis são pequenos demais para aparecer.
 *
 * Espaço do modelo (onde os renderers desenham): x negativo = direita, y negativo = para cima, z positivo = trás,
 * y = 1.501 na altura dos pés; o modelo do jogador ainda é reduzido para 0.9375.
 */
public final class CurioRenderers {
    private static final float PLAYER_MODEL_SCALE = 0.9375F;
    private static final float FEET_Y = 1.501F;

    public static void register() {
        for (Element e : Element.BASE) {
            CuriosRendererRegistry.register(EquipmentItems.FOCI.get(e).get(), FocusOrbRenderer::new);
            CuriosRendererRegistry.register(EquipmentItems.GREATER_FOCI.get(e).get(), FocusOrbRenderer::new);
        }
        CuriosRendererRegistry.register(EquipmentItems.ARCANE_FOCUS.get(), FocusOrbRenderer::new);
        CuriosRendererRegistry.register(EquipmentItems.ARCANE_AMULET.get(), PendantRenderer::new);
        CuriosRendererRegistry.register(EquipmentItems.MEDITATION_CHARM.get(), CharmRenderer::new);
    }

    private static void drawItem(ItemStack stack, LivingEntity entity, PoseStack pose, MultiBufferSource buffer, int light) {
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, pose, buffer, entity.level(), entity.getId());
    }

    /** Foco flutuante: mesma posição usada pelas partículas (FocusOrb), girando devagar e brilhando no escuro. */
    static final class FocusOrbRenderer implements ICurioRenderer {
        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
                PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffer, int light, float limbSwing,
                float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            LivingEntity e = slotContext.entity();
            if (e.isInvisible()) return;
            float ms = e instanceof Player ? PLAYER_MODEL_SCALE : 1.0F;
            pose.pushPose();
            pose.translate(-FocusOrb.SIDE / ms, FEET_Y - (FocusOrb.HEIGHT + FocusOrb.bob(ageInTicks)) / ms, FocusOrb.BACK / ms);
            pose.mulPose(Axis.ZP.rotationDegrees(180F)); // o eixo Y do modelo é invertido
            pose.mulPose(Axis.YP.rotationDegrees(ageInTicks * 3.0F));
            pose.scale(0.4F, 0.4F, 0.4F);
            drawItem(stack, e, pose, buffer, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }

    /** Amuleto no peito, acompanhando o tronco (agachar, animações). */
    static final class PendantRenderer implements ICurioRenderer {
        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
                PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffer, int light, float limbSwing,
                float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!(parent.getModel() instanceof HumanoidModel<?> model) || slotContext.entity().isInvisible()) return;
            pose.pushPose();
            model.body.translateAndRotate(pose);
            pose.translate(0.0F, 0.25F, -0.16F);
            pose.mulPose(Axis.ZP.rotationDegrees(180F));
            pose.scale(0.3F, 0.3F, 0.3F);
            drawItem(stack, slotContext.entity(), pose, buffer, light);
            pose.popPose();
        }
    }

    /** Talismã pendurado no lado esquerdo do quadril, balançando com a caminhada. */
    static final class CharmRenderer implements ICurioRenderer {
        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
                PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffer, int light, float limbSwing,
                float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!(parent.getModel() instanceof HumanoidModel<?> model) || slotContext.entity().isInvisible()) return;
            pose.pushPose();
            model.body.translateAndRotate(pose);
            pose.translate(0.22F, 0.62F, -0.15F);
            float sway = Mth.cos(limbSwing * 0.6662F) * limbSwingAmount * 18F;
            pose.mulPose(Axis.XP.rotationDegrees(sway));
            pose.translate(0.0F, 0.1F, 0.0F);
            pose.mulPose(Axis.ZP.rotationDegrees(180F));
            pose.scale(0.24F, 0.24F, 0.24F);
            drawItem(stack, slotContext.entity(), pose, buffer, light);
            pose.popPose();
        }
    }

    private CurioRenderers() {}
}
