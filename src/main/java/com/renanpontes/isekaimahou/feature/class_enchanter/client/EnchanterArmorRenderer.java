package com.renanpontes.isekaimahou.feature.class_enchanter.client;

import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterArmorItem;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterOutfit;

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
 * Renderer do traje do Encantador (modelo por grau, gerado por {@code tools/gen/class_enchanter.py}). Além dos 8 ossos de
 * armadura do GeckoLib: a cintura {@code waistChest} (segue o TRONCO) com as 4 abas do casaco que abrem com o passo,
 * a capa/manto {@code outfitCape} que levanta ao correr e o {@code runeHalo} (graus II–IV) que gira devagar atrás da
 * cabeça. Runas e gemas brilham no escuro (glowmask).
 */
public class EnchanterArmorRenderer extends GeoArmorRenderer<EnchanterArmorItem> {
    private GeoBone waistChest, cape, halo;
    private final GeoBone[] panels = new GeoBone[4];

    public EnchanterArmorRenderer(EnchanterOutfit tier) {
        super(new DefaultedItemGeoModel<>(tier.modelId()));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void grabRelevantBones(BakedGeoModel model) {
        boolean fresh = model != this.lastModel;
        super.grabRelevantBones(model);
        if (!fresh) return;
        waistChest = model.getBone("waistChest").orElse(null);
        cape = model.getBone("outfitCape").orElse(null);
        halo = model.getBone("runeHalo").orElse(null);
        String[] sides = {"Front", "Back", "Right", "Left"};
        for (int i = 0; i < 4; i++) panels[i] = model.getBone("chestSkirt" + sides[i]).orElse(null);
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
            float lift = 0.06F + swing * 0.9F + Mth.sin(age * 0.11F) * 0.035F * (0.4F + swing);
            if (base.crouching) lift += 0.25F;
            rotate(cape, Math.min(lift, 1.3F), 0F, Mth.sin(age * 0.07F) * 0.025F);
        }
        if (halo != null) rotate(halo, 0F, 0F, age * 0.02F);
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
        setBoneVisible(waistChest, slot == EquipmentSlot.CHEST && this.baseModel.body.visible);
    }
}
