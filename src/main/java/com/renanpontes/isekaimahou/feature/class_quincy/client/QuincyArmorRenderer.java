package com.renanpontes.isekaimahou.feature.class_quincy.client;

import org.jetbrains.annotations.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyArmor;
import com.renanpontes.isekaimahou.feature.class_quincy.item.QuincyArmorItem;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Renderer do traje Quincy (mesmas convenções de ossos dos trajes do mod): abas do casaco presas à cintura que abrem com
 * o passo, capa que sobe ao correr, ponta do capuz que balança e — no grau IV — a auréola que gira e as asas de luz que
 * batem devagar. Todas as peças têm glowmask (o pingente da cruz brilha sempre).
 */
public class QuincyArmorRenderer extends GeoArmorRenderer<QuincyArmorItem> {
    private GeoBone waistChest, cape, hatMid, hatTip, halo, wings;
    private final GeoBone[] panels = new GeoBone[4];

    public QuincyArmorRenderer(QuincyArmor.Tier tier) {
        super(new DefaultedItemGeoModel<>(IsekaiMahou.id("armor/" + tier.modelName())));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void grabRelevantBones(BakedGeoModel bakedModel) {
        boolean fresh = bakedModel != this.lastModel;
        super.grabRelevantBones(bakedModel);
        if (!fresh) return;
        waistChest = bone(bakedModel, "waistChest");
        cape = bone(bakedModel, "outfitCape");
        hatMid = bone(bakedModel, "hatMid");
        hatTip = bone(bakedModel, "hatTip");
        halo = bone(bakedModel, "halo");
        wings = bone(bakedModel, "wings");
        String[] sides = {"Front", "Back", "Right", "Left"};
        for (int i = 0; i < 4; i++) panels[i] = bone(bakedModel, "chestSkirt" + sides[i]);
    }

    private static GeoBone bone(BakedGeoModel model, String name) {
        return model.getBone(name).orElse(null);
    }

    @Override
    protected void applyBaseTransformations(HumanoidModel<?> base) {
        super.applyBaseTransformations(base);
        float age = this.currentEntity != null ? this.currentEntity.tickCount + this.partialTick : 0F;
        float swing = Mth.clamp(this.limbSwingAmount, 0F, 1F);
        if (waistChest != null) {
            RenderUtil.matchModelPartRot(base.body, waistChest);
            waistChest.updatePosition(base.body.x, -base.body.y, base.body.z);
        }
        float forward = Math.max(0F, -Math.min(base.rightLeg.xRot, base.leftLeg.xRot));
        float backward = Math.max(0F, Math.max(base.rightLeg.xRot, base.leftLeg.xRot));
        rotate(panels[0], -forward * 0.85F, 0F, 0F);
        rotate(panels[1], backward * 0.85F, 0F, 0F);
        rotate(panels[2], 0F, 0F, swing * 0.12F);
        rotate(panels[3], 0F, 0F, -swing * 0.12F);
        if (cape != null) {
            float lift = 0.06F + swing * 0.9F + Mth.sin(age * 0.11F) * 0.035F * (0.4F + swing);
            if (base.crouching) lift += 0.25F;
            rotate(cape, Math.min(lift, 1.3F), 0F, Mth.sin(age * 0.07F) * 0.025F);
        }
        if (hatMid != null) rotate(hatMid, -swing * 0.10F, 0F, Mth.sin(age * 0.065F) * 0.035F);
        if (hatTip != null) rotate(hatTip, -swing * 0.25F, 0F, Mth.sin(age * 0.065F - 0.8F) * 0.08F);
        if (halo != null) {
            rotate(halo, 0F, age * 0.05F, 0F);
            halo.updatePosition(0F, Mth.sin(age * 0.08F) * 0.4F, 0F);
        }
        if (wings != null) rotate(wings, -0.08F - swing * 0.2F, 0F, 0F);
    }

    /** Soma uma rotação (convenção do ModelPart) à rotação original do osso. */
    private static void rotate(GeoBone bone, float x, float y, float z) {
        if (bone == null) return;
        BoneSnapshot init = bone.getInitialSnapshot();
        float ix = init != null ? init.getRotX() : 0F;
        float iy = init != null ? init.getRotY() : 0F;
        float iz = init != null ? init.getRotZ() : 0F;
        bone.updateRotation(ix - x, iy - y, iz + z);
    }

    @Override
    protected void setAllBonesVisible(boolean visible) {
        super.setAllBonesVisible(visible);
        setBoneVisible(waistChest, visible);
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
        super.applyBoneVisibilityBySlot(slot);
        // a cintura não é filha dos ossos padrão: aparece só junto do peitoral
        setBoneVisible(waistChest, slot == EquipmentSlot.CHEST && this.baseModel.body.visible);
    }

    /** Entrega o renderer 3D de uma peça ao GeckoLib (um por grau, criado na primeira vez). */
    public static final class Provider implements GeoRenderProvider {
        private final QuincyArmorItem item;
        private QuincyArmorRenderer renderer;

        public Provider(QuincyArmorItem item) {
            this.item = item;
        }

        @Override
        public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack, @Nullable EquipmentSlot slot,
                                                                             @Nullable HumanoidModel<T> original) {
            if (renderer == null) renderer = new QuincyArmorRenderer(item.tier());
            return renderer;
        }
    }
}
