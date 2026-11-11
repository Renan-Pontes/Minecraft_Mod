package com.renanpontes.isekaimahou.feature.class_knight.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_knight.outfit.KnightArmorItem;
import com.renanpontes.isekaimahou.feature.class_knight.outfit.KnightOutfitTier;

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
 * Renderer da armadura do Cavaleiro Rúnico ({@code geo/item/armor/knight_<grau>.geo.json}, gerado por
 * tools/gen/class_knight.py). Além dos 8 ossos de armadura do GeckoLib:
 * <ul>
 *   <li>{@code waistChest}/{@code waistLegs}: cinturas que seguem o tronco, com 4 painéis (tabardo/faldas) que se
 *   abrem com o passo;</li>
 *   <li>{@code outfitCape}: capa que levanta ao correr;</li>
 *   <li>{@code hatMid}/{@code hatTip}: crista/flâmula do elmo que balança.</li>
 * </ul>
 * Graus II–IV têm glowmask (runas e gemas).
 */
public class KnightArmorRenderer extends GeoArmorRenderer<KnightArmorItem> {
    private GeoBone waistChest, waistLegs, cape, crestMid, crestTip;
    private final GeoBone[] chestPanels = new GeoBone[4];
    private final GeoBone[] legsPanels = new GeoBone[4];

    public KnightArmorRenderer(KnightOutfitTier tier) {
        super(new DefaultedItemGeoModel<>(IsekaiMahou.id("armor/" + tier.modelName())));
        if (tier.glows()) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void grabRelevantBones(BakedGeoModel model) {
        boolean fresh = model != this.lastModel;
        super.grabRelevantBones(model);
        if (!fresh) return;
        waistChest = bone(model, "waistChest");
        waistLegs = bone(model, "waistLegs");
        cape = bone(model, "outfitCape");
        crestMid = bone(model, "hatMid");
        crestTip = bone(model, "hatTip");
        String[] sides = {"Front", "Back", "Right", "Left"};
        for (int i = 0; i < 4; i++) {
            chestPanels[i] = bone(model, "chestSkirt" + sides[i]);
            legsPanels[i] = bone(model, "legsSkirt" + sides[i]);
        }
    }

    private static GeoBone bone(BakedGeoModel model, String name) {
        return model.getBone(name).orElse(null);
    }

    @Override
    protected void applyBaseTransformations(HumanoidModel<?> base) {
        super.applyBaseTransformations(base);
        float age = this.currentEntity != null ? this.currentEntity.tickCount + this.partialTick : 0F;
        float swing = Mth.clamp(this.limbSwingAmount, 0F, 1F);
        followBody(waistChest, base);
        followBody(waistLegs, base);
        float forward = Math.max(0F, -Math.min(base.rightLeg.xRot, base.leftLeg.xRot));
        float backward = Math.max(0F, Math.max(base.rightLeg.xRot, base.leftLeg.xRot));
        float side = swing * 0.12F;
        openPanels(chestPanels, forward, backward, side);
        openPanels(legsPanels, forward, backward, side);
        if (cape != null) {
            float lift = 0.06F + swing * 0.9F + Mth.sin(age * 0.11F) * 0.035F * (0.4F + swing);
            if (base.crouching) lift += 0.25F;
            rotate(cape, Math.min(lift, 1.3F), 0F, Mth.sin(age * 0.07F) * 0.025F);
        }
        if (crestMid != null) rotate(crestMid, -swing * 0.10F, 0F, Mth.sin(age * 0.065F) * 0.035F);
        if (crestTip != null) rotate(crestTip, -swing * 0.25F, 0F, Mth.sin(age * 0.065F - 0.8F) * 0.08F);
    }

    private static void openPanels(GeoBone[] panels, float forward, float backward, float side) {
        rotate(panels[0], -forward * 0.85F, 0F, 0F);
        rotate(panels[1], backward * 0.85F, 0F, 0F);
        rotate(panels[2], 0F, 0F, side);
        rotate(panels[3], 0F, 0F, -side);
    }

    private static void followBody(GeoBone bone, HumanoidModel<?> base) {
        if (bone == null) return;
        RenderUtil.matchModelPartRot(base.body, bone);
        bone.updatePosition(base.body.x, -base.body.y, base.body.z);
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
        setBoneVisible(waistLegs, visible);
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
        super.applyBoneVisibilityBySlot(slot);
        setBoneVisible(waistChest, slot == EquipmentSlot.CHEST && this.baseModel.body.visible);
        setBoneVisible(waistLegs, slot == EquipmentSlot.LEGS && (this.baseModel.rightLeg.visible || this.baseModel.leftLeg.visible));
    }
}
