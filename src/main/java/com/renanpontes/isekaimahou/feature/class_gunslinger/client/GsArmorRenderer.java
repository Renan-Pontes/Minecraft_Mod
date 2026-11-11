package com.renanpontes.isekaimahou.feature.class_gunslinger.client;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.GsOutfitTier;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.GunslingerArmorItem;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.RenderUtil;
import software.bernie.geckolib.animation.state.BoneSnapshot;

/**
 * Renderer do traje do Pistoleiro (padrão dos trajes do módulo outfits): além dos 8 ossos de armadura do GeckoLib, o
 * modelo tem {@code waistChest} (abas do guarda-pó: {@code chestSkirtFront/Back/Right/Left}, que se abrem com o passo),
 * {@code outfitCape} (poncho/manta nas costas, levanta ao correr) e {@code hatMid} (cordão do chapéu, balança).
 * Graus II–IV têm glowmask (runas e balas que brilham).
 */
public class GsArmorRenderer extends GeoArmorRenderer<GunslingerArmorItem> {
    private GeoBone waistChest, cape, hatMid;
    private final GeoBone[] panels = new GeoBone[4];

    public GsArmorRenderer(GsOutfitTier tier) {
        super(new DefaultedItemGeoModel<>(IsekaiMahou.id("armor/" + tier.materialId())));
        if (tier.glows()) addRenderLayer(new AutoGlowingGeoLayer<>(this));
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
        rotate(panels[1], backward * 0.85F + swing * 0.25F, 0F, 0F);
        rotate(panels[2], 0F, 0F, side);
        rotate(panels[3], 0F, 0F, -side);
        if (cape != null) {
            float lift = 0.05F + swing * 0.7F + Mth.sin(age * 0.11F) * 0.03F * (0.4F + swing);
            if (base.crouching) lift += 0.25F;
            rotate(cape, Math.min(lift, 1.1F), 0F, Mth.sin(age * 0.07F) * 0.02F);
        }
        if (hatMid != null) rotate(hatMid, -swing * 0.2F, 0F, Mth.sin(age * 0.09F) * 0.06F);
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
        // a cintura não é filha dos ossos padrão: aparece só com o peitoral
        setBoneVisible(waistChest, slot == EquipmentSlot.CHEST && this.baseModel.body.visible);
    }

    /** Entrega ao GeckoLib o renderer de uma peça (um por grau, criado na primeira vez). */
    public static final class Provider implements GeoRenderProvider {
        private final GunslingerArmorItem item;
        private GsArmorRenderer renderer;

        public Provider(GunslingerArmorItem item) {
            this.item = item;
        }

        @Override
        public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack,
                @Nullable EquipmentSlot slot, @Nullable HumanoidModel<T> original) {
            if (renderer == null) renderer = new GsArmorRenderer(item.tier());
            return renderer;
        }
    }
}
