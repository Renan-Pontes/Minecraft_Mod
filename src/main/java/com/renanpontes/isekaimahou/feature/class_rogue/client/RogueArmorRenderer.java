package com.renanpontes.isekaimahou.feature.class_rogue.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueGear;
import com.renanpontes.isekaimahou.feature.class_rogue.item.RogueArmorItem;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Renderer do Traje das Sombras (GeckoLib; modelos em {@code geo/item/armor/rogue_shade_<grau>.geo.json}). Além dos
 * ossos padrão de armadura: {@code waistChest} (abas do gibão que seguem o tronco e se abrem com o passo),
 * {@code outfitCape} (capa/cachecol que sobe ao correr e ondula), {@code hatMid} (ponta do capuz que balança).
 */
public class RogueArmorRenderer extends GeoArmorRenderer<RogueArmorItem> {
    private GeoBone waistChest, cape, hatMid;
    private final GeoBone[] panels = new GeoBone[4];

    public RogueArmorRenderer(RogueGear gear) {
        super(new DefaultedItemGeoModel<>(IsekaiMahou.id("armor/" + gear.materialId())));
        if (gear.glows) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void grabRelevantBones(BakedGeoModel bakedModel) {
        boolean fresh = bakedModel != this.lastModel;
        super.grabRelevantBones(bakedModel);
        if (!fresh) return;
        waistChest = bakedModel.getBone("waistChest").orElse(null);
        cape = bakedModel.getBone("outfitCape").orElse(null);
        hatMid = bakedModel.getBone("hatMid").orElse(null);
        String[] sides = {"Front", "Back", "Right", "Left"};
        for (int i = 0; i < 4; i++) panels[i] = bakedModel.getBone("chestSkirt" + sides[i]).orElse(null);
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
        float side = swing * 0.12F;
        rotate(panels[0], -forward * 0.85F, 0F, 0F);
        rotate(panels[1], backward * 0.85F, 0F, 0F);
        rotate(panels[2], 0F, 0F, side);
        rotate(panels[3], 0F, 0F, -side);
        if (cape != null) {
            float lift = 0.08F + swing * 1.0F + Mth.sin(age * 0.13F) * 0.04F * (0.4F + swing);
            if (base.crouching) lift += 0.3F;
            rotate(cape, Math.min(lift, 1.35F), 0F, Mth.sin(age * 0.09F) * 0.03F);
        }
        if (hatMid != null) rotate(hatMid, -swing * 0.14F, 0F, Mth.sin(age * 0.07F) * 0.04F);
    }

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
        // as abas do gibão não são filhas dos ossos padrão: aparecem só com o peitoral
        setBoneVisible(waistChest, slot == EquipmentSlot.CHEST && this.baseModel.body.visible);
    }
}
