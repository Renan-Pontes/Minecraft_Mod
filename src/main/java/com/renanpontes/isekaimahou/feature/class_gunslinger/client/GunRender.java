package com.renanpontes.isekaimahou.feature.class_gunslinger.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.ManaGunItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Como as armas aparecem:
 * <ul>
 *   <li>{@link Extensions}: em 3ª pessoa a arma fica erguida (dois braços) enquanto atira/mira; em 1ª pessoa, coice a
 *       cada tiro, mergulho da recarga com o giro do revólver no fim, e o fuzil vai para o meio da tela na luneta.</li>
 *   <li>Pistolas Duplas: a segunda pistola aparece na mão vazia (3ª e 1ª pessoa).</li>
 *   <li>Luneta do fuzil: agachado com o fuzil na mão, a visão aproxima (FOV ×0,35).</li>
 * </ul>
 */
public final class GunRender {

    static boolean dual(ItemStack s) {
        ManaGunItem g = ManaGunItem.of(s);
        return g != null && g.kind() == GunKind.DUAL_PISTOLS;
    }

    static boolean scoped(Player p) {
        ManaGunItem g = ManaGunItem.of(p.getMainHandItem());
        return g != null && g.kind() == GunKind.RIFLE && p.isCrouching();
    }

    /** Pose de braço e transformação em 1ª pessoa das armas de mana. */
    public static final class Extensions implements IClientItemExtensions {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            boolean raised = GunAnimations.aiming(entity) || (entity.isUsingItem() && entity.getUseItem() == stack)
                    || (entity instanceof Player p && scoped(p));
            if (!raised) return null;
            return dual(stack) ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                               float partialTick, float equip, float swing) {
            int i = arm == HumanoidArm.RIGHT ? 1 : -1;
            pose.translate(i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
            if (scoped(player) && player.getMainHandItem() == stack) {
                pose.translate(-i * 0.5F, 0.12F, 0.05F);
            }
            boolean main = player.getMainHandItem() == stack;
            float rec = main ? GunAnimations.recoil(partialTick) : 0F;
            if (rec > 0) {
                pose.translate(0, rec * 0.04F, rec * 0.14F);
                pose.mulPose(Axis.XP.rotationDegrees(rec * 20F));
            }
            float rel = main ? GunAnimations.reloadProgress(partialTick) : -1F;
            if (rel >= 0) {
                float dip = Mth.sin(rel * Mth.PI);
                pose.translate(-i * 0.12F * dip, -0.22F * dip, 0.05F * dip);
                pose.mulPose(Axis.ZP.rotationDegrees(i * 35F * dip));
                if (rel > 0.55F) {
                    // giro do revólver no fim da recarga
                    float t = (rel - 0.55F) / 0.45F;
                    float ease = t * t * (3F - 2F * t);
                    pose.mulPose(Axis.XP.rotationDegrees(-360F * ease));
                }
            }
            return true;
        }
    }

    // =====================================================================================
    //  Pistolas Duplas
    // =====================================================================================
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        ItemInHandRenderer items = event.getContext().getItemInHandRenderer();
        for (PlayerSkin.Model skin : event.getSkins()) {
            EntityRenderer<? extends Player> r = event.getSkin(skin);
            if (r instanceof LivingEntityRenderer lr && lr.getModel() instanceof PlayerModel) lr.addLayer(new DualLayer(lr, items));
        }
    }

    /** 3ª pessoa: a segunda pistola na mão secundária vazia. */
    static final class DualLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final ItemInHandRenderer items;

        DualLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer items) {
            super(parent);
            this.items = items;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack main = player.getMainHandItem();
            if (player.isInvisible() || !dual(main) || !player.getOffhandItem().isEmpty()) return;
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

    /** 1ª pessoa: desenha a segunda pistola na mão secundária vazia. */
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.OFF_HAND || !event.getItemStack().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack main = player.getMainHandItem();
        if (!dual(main)) return;
        HumanoidArm arm = player.getMainArm().getOpposite();
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(i * 0.56F, -0.52F + event.getEquipProgress() * -0.6F, -0.72F);
        float rec = GunAnimations.recoil(event.getPartialTick()) * 0.7F;
        if (rec > 0) {
            pose.translate(0, rec * 0.04F, rec * 0.12F);
            pose.mulPose(Axis.XP.rotationDegrees(rec * 18F));
        }
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, main,
                arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                arm == HumanoidArm.LEFT, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
        event.setCanceled(true);
    }

    /** Luneta do fuzil. */
    public static void onFov(ComputeFovModifierEvent event) {
        if (scoped(event.getPlayer())) event.setNewFovModifier(event.getFovModifier() * 0.35F);
    }

    private GunRender() {}
}
