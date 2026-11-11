package com.renanpontes.isekaimahou.feature.armory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.armory.WeaponKind;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryWeapon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Armas em PAR: com Adagas Gêmeas ou Manoplas de Ki na mão principal e a outra mão vazia, a segunda adaga/manopla
 * aparece na outra mão — em 3ª pessoa (camada no jogador, para todos verem) e em 1ª pessoa (mão secundária, que
 * também anima o corte do golpe duplo).
 */
public final class TwinWeaponRender {

    static boolean isTwin(ItemStack stack) {
        ArmoryWeapon w = ArmoryWeapon.of(stack);
        return w != null && (w.kind() == WeaponKind.TWIN_DAGGERS || w.kind() == WeaponKind.KI_GAUNTLETS);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void addLayers(EntityRenderersEvent.AddLayers event) {
        ItemInHandRenderer items = event.getContext().getItemInHandRenderer();
        for (PlayerSkin.Model skin : event.getSkins()) {
            EntityRenderer<? extends Player> r = event.getSkin(skin);
            if (r instanceof LivingEntityRenderer lr && lr.getModel() instanceof PlayerModel) {
                lr.addLayer(new Layer(lr, items));
            }
        }
    }

    /** 3ª pessoa: a cópia da arma na mão secundária vazia (mesma conta do ItemInHandLayer do vanilla). */
    static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final ItemInHandRenderer items;

        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer items) {
            super(parent);
            this.items = items;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack main = player.getMainHandItem();
            if (player.isInvisible() || !isTwin(main) || !player.getOffhandItem().isEmpty()) return;
            HumanoidArm arm = player.getMainArm().getOpposite();
            boolean left = arm == HumanoidArm.LEFT;
            pose.pushPose();
            getParentModel().translateToHand(arm, pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
            items.renderItem(player, main, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    left, pose, buffer, light);
            pose.popPose();
        }
    }

    /** 1ª pessoa: desenha a cópia na mão secundária (o vanilla não desenha nada numa mão secundária vazia). */
    static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.OFF_HAND || !event.getItemStack().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isUsingItem() || player.isScoping()) return;
        ItemStack main = player.getMainHandItem();
        if (!isTwin(main)) return;
        HumanoidArm arm = player.getMainArm().getOpposite();
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        float swing = event.getSwingProgress();
        float equip = event.getEquipProgress();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // Mesmas contas do ItemInHandRenderer para um item comum (balanço do golpe + posição da mão)
        float sx = -0.4F * Mth.sin(Mth.sqrt(swing) * (float) Math.PI);
        float sy = 0.2F * Mth.sin(Mth.sqrt(swing) * (float) Math.PI * 2F);
        float sz = -0.2F * Mth.sin(swing * (float) Math.PI);
        pose.translate(i * sx, sy, sz);
        pose.translate(i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        float f = Mth.sin(swing * swing * (float) Math.PI);
        pose.mulPose(Axis.YP.rotationDegrees(i * (45.0F + f * -20.0F)));
        float f1 = Mth.sin(Mth.sqrt(swing) * (float) Math.PI);
        pose.mulPose(Axis.ZP.rotationDegrees(i * f1 * -20.0F));
        pose.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
        pose.mulPose(Axis.YP.rotationDegrees(i * -45.0F));
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, main,
                arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                arm == HumanoidArm.LEFT, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
        event.setCanceled(true);
    }

    private TwinWeaponRender() {}
}
