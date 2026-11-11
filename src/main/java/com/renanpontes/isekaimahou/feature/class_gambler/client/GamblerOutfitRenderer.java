package com.renanpontes.isekaimahou.feature.class_gambler.client;

import org.jetbrains.annotations.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_gambler.outfit.GamblerOutfitItem;
import com.renanpontes.isekaimahou.feature.class_gambler.outfit.GamblerOutfitTier;

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
 * Traje 3D do Apostador (modelos em {@code geo/item/armor/outfit_gambler[_<grau>].geo.json}). Além dos ossos padrão:
 * <ul>
 *   <li>{@code outfitCape} (filho do tronco): as abas do fraque (II/III) ou a capa do sobretudo (IV) sobem com a corrida
 *   e balançam devagar;</li>
 *   <li>{@code waistChest} (IV): a saia do sobretudo segue o tronco e os painéis abrem com o passo;</li>
 *   <li>{@code hatCards}: as cartas presas na fita do chapéu/viseira tremulam.</li>
 * </ul>
 * Graus II–IV brilham (naipes, gemas, fichas).
 */
public class GamblerOutfitRenderer extends GeoArmorRenderer<GamblerOutfitItem> {
    private GeoBone tails, hatCards, waistChest;
    private final GeoBone[] panels = new GeoBone[4];

    public GamblerOutfitRenderer(GamblerOutfitTier tier) {
        super(new DefaultedItemGeoModel<>(IsekaiMahou.id("armor/" + tier.materialId())));
        if (tier != GamblerOutfitTier.DEALER) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void grabRelevantBones(BakedGeoModel bakedModel) {
        boolean fresh = bakedModel != this.lastModel;
        super.grabRelevantBones(bakedModel);
        if (!fresh) return;
        tails = bakedModel.getBone("outfitCape").orElse(null);
        hatCards = bakedModel.getBone("hatCards").orElse(null);
        waistChest = bakedModel.getBone("waistChest").orElse(null);
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
            float forward = Math.max(0F, -Math.min(base.rightLeg.xRot, base.leftLeg.xRot));
            float backward = Math.max(0F, Math.max(base.rightLeg.xRot, base.leftLeg.xRot));
            float side = swing * 0.12F;
            rotate(panels[0], -forward * 0.85F, 0F, 0F);
            rotate(panels[1], backward * 0.85F, 0F, 0F);
            rotate(panels[2], 0F, 0F, side);
            rotate(panels[3], 0F, 0F, -side);
        }
        if (tails != null) {
            float lift = 0.05F + swing * 0.75F + Mth.sin(age * 0.12F) * 0.03F * (0.4F + swing);
            if (base.crouching) lift += 0.25F;
            rotate(tails, Math.min(lift, 1.1F), 0F, Mth.sin(age * 0.08F) * 0.03F);
        }
        if (hatCards != null) rotate(hatCards, 0F, 0F, Mth.sin(age * 0.2F) * 0.06F * (0.3F + swing));
    }

    /** Soma uma rotação (convenção do ModelPart) à rotação original do osso. */
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
        // a saia do sobretudo não é filha dos ossos padrão: aparece só com o peitoral
        setBoneVisible(waistChest, slot == EquipmentSlot.CHEST && this.baseModel.body.visible);
    }

    /** Entrega o renderer ao GeckoLib (um por peça, criado na primeira vez que ela aparece). */
    public static class Provider implements GeoRenderProvider {
        private final GamblerOutfitItem item;
        private GamblerOutfitRenderer renderer;

        public Provider(GamblerOutfitItem item) {
            this.item = item;
        }

        @Override
        public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack,
                @Nullable EquipmentSlot slot, @Nullable HumanoidModel<T> original) {
            if (renderer == null) renderer = new GamblerOutfitRenderer(item.tier());
            return renderer;
        }
    }
}
