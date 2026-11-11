package com.renanpontes.isekaimahou.feature.class_ranger.client;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_ranger.item.RangerArmorItem;
import com.renanpontes.isekaimahou.feature.class_ranger.item.RangerOutfitTier;

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

/**
 * Renderer do Traje Selvagem: os 8 ossos padrão de armadura do GeckoLib + ossos com movimento procedural:
 * {@code garbCape} (capa/manto de pele que levanta ao correr), {@code hoodTail} (ponta do capuz) e {@code plume}
 * (penas de águia no ombro, balançam com o vento). Graus II–IV têm runas que brilham (_glowmask).
 */
public class RangerArmorRenderer extends GeoArmorRenderer<RangerArmorItem> {
    private GeoBone cape, hoodTail, plume;

    public RangerArmorRenderer(RangerOutfitTier tier) {
        super(new DefaultedItemGeoModel<>(IsekaiMahou.id("armor/" + tier.modelId())));
        if (tier != RangerOutfitTier.SCOUT) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void grabRelevantBones(BakedGeoModel bakedModel) {
        boolean fresh = bakedModel != this.lastModel;
        super.grabRelevantBones(bakedModel);
        if (!fresh) return;
        cape = bakedModel.getBone("garbCape").orElse(null);
        hoodTail = bakedModel.getBone("hoodTail").orElse(null);
        plume = bakedModel.getBone("plume").orElse(null);
    }

    @Override
    protected void applyBaseTransformations(HumanoidModel<?> base) {
        super.applyBaseTransformations(base);
        float age = this.currentEntity != null ? this.currentEntity.tickCount + this.partialTick : 0F;
        float swing = Mth.clamp(this.limbSwingAmount, 0F, 1F);
        if (cape != null) {
            float lift = 0.05F + swing * 0.85F + Mth.sin(age * 0.11F) * 0.035F * (0.4F + swing);
            if (base.crouching) lift += 0.25F;
            rotate(cape, Math.min(lift, 1.25F), 0F, Mth.sin(age * 0.07F) * 0.025F);
        }
        if (hoodTail != null) rotate(hoodTail, -swing * 0.3F, 0F, Mth.sin(age * 0.065F) * 0.07F);
        if (plume != null) rotate(plume, Mth.sin(age * 0.09F) * 0.06F - swing * 0.15F, 0F, Mth.sin(age * 0.13F) * 0.08F);
    }

    /** Soma uma rotação (convenção do ModelPart) à rotação original do osso. */
    private static void rotate(GeoBone bone, float x, float y, float z) {
        BoneSnapshot init = bone.getInitialSnapshot();
        float ix = init != null ? init.getRotX() : 0F;
        float iy = init != null ? init.getRotY() : 0F;
        float iz = init != null ? init.getRotZ() : 0F;
        bone.updateRotation(ix - x, iy - y, iz + z);
    }

    /** Entrega o renderer ao GeckoLib (um por grau, criado na primeira vez que aparece). */
    public static class Provider implements GeoRenderProvider {
        private final RangerArmorItem item;
        private RangerArmorRenderer renderer;

        public Provider(RangerArmorItem item) {
            this.item = item;
        }

        @Override
        public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack,
                @Nullable EquipmentSlot slot, @Nullable HumanoidModel<T> original) {
            if (renderer == null) renderer = new RangerArmorRenderer(item.tier());
            return renderer;
        }
    }
}
