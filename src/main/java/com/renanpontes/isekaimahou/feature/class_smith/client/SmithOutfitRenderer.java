package com.renanpontes.isekaimahou.feature.class_smith.client;

import org.jetbrains.annotations.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithOutfitItem;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithOutfitTier;

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
 * Renderer da roupa do Ferreiro (um modelo por grau, mesmos nomes de ossos). Além dos ossos padrão do GeckoLib:
 * {@code waistChest} (segue o tronco) com os 4 painéis do AVENTAL de couro ({@code chestSkirtFront/Back/Right/Left}),
 * que se abrem com o passo, e a capa {@code outfitCape} (graus III–IV) que levanta ao correr. Ângulos na convenção do
 * ModelPart (x &gt; 0 joga a parte de baixo para trás).
 */
public class SmithOutfitRenderer extends GeoArmorRenderer<SmithOutfitItem> {
    private GeoBone waistChest, cape;
    private final GeoBone[] panels = new GeoBone[4];

    public SmithOutfitRenderer(SmithOutfitTier tier) {
        super(new DefaultedItemGeoModel<>(IsekaiMahou.id("armor/" + tier.modelName())));
        if (tier != SmithOutfitTier.APPRENTICE) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void grabRelevantBones(BakedGeoModel bakedModel) {
        boolean fresh = bakedModel != this.lastModel;
        super.grabRelevantBones(bakedModel);
        if (!fresh) return;
        waistChest = bakedModel.getBone("waistChest").orElse(null);
        cape = bakedModel.getBone("outfitCape").orElse(null);
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
        // avental de couro grosso: abre menos que uma saia de pano
        rotate(panels[0], -forward * 0.7F, 0F, 0F);
        rotate(panels[1], backward * 0.6F, 0F, 0F);
        rotate(panels[2], 0F, 0F, swing * 0.08F);
        rotate(panels[3], 0F, 0F, -swing * 0.08F);
        if (cape != null) {
            float lift = 0.06F + swing * 0.85F + Mth.sin(age * 0.11F) * 0.035F * (0.4F + swing);
            if (base.crouching) lift += 0.25F;
            rotate(cape, Math.min(lift, 1.25F), 0F, Mth.sin(age * 0.07F) * 0.025F);
        }
    }

    private static void rotate(@Nullable GeoBone bone, float x, float y, float z) {
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
        // o avental é do peitoral (não é filho de um osso padrão)
        setBoneVisible(waistChest, slot == EquipmentSlot.CHEST && this.baseModel.body.visible);
    }

    /** Entrega ao GeckoLib o renderer de um grau (criado na primeira vez). */
    public static class Provider implements GeoRenderProvider {
        private final SmithOutfitItem item;
        private SmithOutfitRenderer renderer;

        public Provider(SmithOutfitItem item) {
            this.item = item;
        }

        @Override
        public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack,
                @Nullable EquipmentSlot slot, @Nullable HumanoidModel<T> original) {
            if (renderer == null) renderer = new SmithOutfitRenderer(item.tier());
            return renderer;
        }
    }
}
