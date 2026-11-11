package com.renanpontes.isekaimahou.feature.class_steelball.client;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_steelball.item.SbOutfitItem;
import com.renanpontes.isekaimahou.feature.class_steelball.item.SbOutfitTier;

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
 * Roupa do Steel Ball (GeckoLib): além dos 8 ossos de armadura, o modelo tem {@code outfitCape} (abas do casaco / cachecol
 * / capa, filho do tronco) que levanta com a corrida e balança, e {@code hatBand} (fitas do chapéu) que tremulam.
 * Graus III–IV têm glowmask (espirais e o Retângulo Dourado brilham).
 */
public class SbOutfitRenderer extends GeoArmorRenderer<SbOutfitItem> {
    private GeoBone cape, ribbon;

    public SbOutfitRenderer(SbOutfitTier tier) {
        super(new DefaultedItemGeoModel<>(tier.modelId()));
        if (tier.glows) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void grabRelevantBones(BakedGeoModel model) {
        boolean fresh = model != this.lastModel;
        super.grabRelevantBones(model);
        if (!fresh) return;
        cape = model.getBone("outfitCape").orElse(null);
        ribbon = model.getBone("hatBand").orElse(null);
    }

    @Override
    protected void applyBaseTransformations(HumanoidModel<?> base) {
        super.applyBaseTransformations(base);
        float age = this.currentEntity != null ? this.currentEntity.tickCount + this.partialTick : 0F;
        float swing = Mth.clamp(this.limbSwingAmount, 0F, 1F);
        boolean riding = this.currentEntity != null && this.currentEntity.isPassenger();
        if (cape != null) {
            // a cavalo o casaco voa mais
            float lift = 0.05F + swing * 0.8F + (riding ? 0.55F : 0F) + Mth.sin(age * 0.12F) * 0.04F * (0.4F + swing);
            if (base.crouching) lift += 0.25F;
            rotate(cape, Math.min(lift, 1.25F), 0F, Mth.sin(age * 0.08F) * 0.03F);
        }
        if (ribbon != null) {
            rotate(ribbon, -0.1F - swing * 0.3F - (riding ? 0.3F : 0F), 0F, Mth.sin(age * 0.2F) * 0.08F);
        }
    }

    private static void rotate(GeoBone bone, float x, float y, float z) {
        BoneSnapshot init = bone.getInitialSnapshot();
        float ix = init != null ? init.getRotX() : 0F;
        float iy = init != null ? init.getRotY() : 0F;
        float iz = init != null ? init.getRotZ() : 0F;
        bone.updateRotation(ix - x, iy - y, iz + z);
    }

    /** Entrega o renderer ao GeckoLib (um por grau, criado na primeira vez que a peça aparece). */
    public static class Provider implements GeoRenderProvider {
        private final SbOutfitItem item;
        private SbOutfitRenderer renderer;

        public Provider(SbOutfitItem item) {
            this.item = item;
        }

        @Override
        public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack,
                @Nullable EquipmentSlot slot, @Nullable HumanoidModel<T> original) {
            if (renderer == null) renderer = new SbOutfitRenderer(item.tier());
            return renderer;
        }
    }
}
