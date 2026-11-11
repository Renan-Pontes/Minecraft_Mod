package com.renanpontes.isekaimahou.feature.outfits.client;

import com.renanpontes.isekaimahou.feature.outfits.OutfitArmorItem;
import com.renanpontes.isekaimahou.feature.outfits.OutfitSet;
import com.renanpontes.isekaimahou.feature.outfits.OutfitTier;

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
 * Renderer das roupas de classe (todos os graus usam os mesmos nomes de ossos; ornamentos novos dos graus II–IV
 * são cubos dentro desses ossos). Além dos 8 ossos padrão de armadura do GeckoLib, o modelo tem:
 * <ul>
 *   <li>{@code waistChest}/{@code waistLegs}: cinturas que seguem o TRONCO (não as pernas), com 4 painéis de saia
 *   cada ({@code chestSkirtFront/Back/Right/Left}, {@code legsSkirt...}). Saia longa presa nas pernas se partiria ao
 *   andar; aqui ela fica inteira e os painéis se abrem conforme o passo (e quase na horizontal quando sentado).</li>
 *   <li>{@code outfitCape}: capa filha do tronco, levanta com a corrida e balança devagar.</li>
 *   <li>{@code hatMid}/{@code hatTip}: ponta do chapéu que balança e tomba para trás ao correr.</li>
 * </ul>
 * Ângulos nos helpers seguem a convenção do ModelPart: x &gt; 0 joga a parte de baixo para trás,
 * z &gt; 0 joga a parte de baixo para a direita da entidade.
 */
public class OutfitArmorRenderer extends GeoArmorRenderer<OutfitArmorItem> {
    private GeoBone waistChest, waistLegs, cape, hatMid, hatTip;
    private final GeoBone[] chestPanels = new GeoBone[4];
    private final GeoBone[] legsPanels = new GeoBone[4];

    public OutfitArmorRenderer(OutfitSet set, OutfitTier tier) {
        super(new DefaultedItemGeoModel<>(set.modelId(tier)));
        if (set.glows(tier)) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void grabRelevantBones(BakedGeoModel bakedModel) {
        boolean fresh = bakedModel != this.lastModel;
        super.grabRelevantBones(bakedModel);
        if (!fresh) return;
        waistChest = bone(bakedModel, "waistChest");
        waistLegs = bone(bakedModel, "waistLegs");
        cape = bone(bakedModel, "outfitCape");
        hatMid = bone(bakedModel, "hatMid");
        hatTip = bone(bakedModel, "hatTip");
        String[] sides = {"Front", "Back", "Right", "Left"};
        for (int i = 0; i < 4; i++) {
            chestPanels[i] = bone(bakedModel, "chestSkirt" + sides[i]);
            legsPanels[i] = bone(bakedModel, "legsSkirt" + sides[i]);
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
        // a perna mais à frente empurra o painel da frente; a mais atrás, o de trás
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
        if (hatMid != null) {
            rotate(hatMid, -swing * 0.10F, 0F, Mth.sin(age * 0.065F) * 0.035F);
        }
        if (hatTip != null) {
            rotate(hatTip, -swing * 0.25F, 0F, Mth.sin(age * 0.065F - 0.8F) * 0.08F);
        }
    }

    private static void openPanels(GeoBone[] panels, float forward, float backward, float side) {
        rotate(panels[0], -forward * 0.85F, 0F, 0F);
        rotate(panels[1], backward * 0.85F, 0F, 0F);
        rotate(panels[2], 0F, 0F, side);
        rotate(panels[3], 0F, 0F, -side);
    }

    /** Mesmo transform que o GeckoLib aplica no osso do tronco. */
    private static void followBody(GeoBone bone, HumanoidModel<?> base) {
        if (bone == null) return;
        RenderUtil.matchModelPartRot(base.body, bone);
        bone.updatePosition(base.body.x, -base.body.y, base.body.z);
    }

    /** Soma uma rotação (convenção do ModelPart) à rotação original do osso no modelo. */
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
        // as cinturas não são filhas dos ossos padrão: cada uma aparece só junto da peça dona dela
        setBoneVisible(waistChest, slot == EquipmentSlot.CHEST && this.baseModel.body.visible);
        setBoneVisible(waistLegs, slot == EquipmentSlot.LEGS
                && (this.baseModel.rightLeg.visible || this.baseModel.leftLeg.visible));
    }
}
