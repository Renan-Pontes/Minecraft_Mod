package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import org.jetbrains.annotations.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiArmor;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.ShinigamiArmorItem;

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
 * Renderer do traje Shinigami (mesmas convenções de ossos dos trajes do mod): a hakama de 4 painéis presa à cintura
 * abre com o passo, as mangas largas do quimono balançam com o braço, a ponta da faixa ondula nas costas e o haori
 * (graus III e IV) sobe ao correr. Glowmask em todos os graus (o Reiatsu do emblema e das bordas).
 */
public class ShinigamiArmorRenderer extends GeoArmorRenderer<ShinigamiArmorItem> {
    private GeoBone waistLegs, cape, sashTail, sleeveR, sleeveL;
    private final GeoBone[] panels = new GeoBone[4];

    public ShinigamiArmorRenderer(ShinigamiArmor.Tier tier) {
        super(new DefaultedItemGeoModel<>(IsekaiMahou.id("armor/" + tier.modelName())));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void grabRelevantBones(BakedGeoModel bakedModel) {
        boolean fresh = bakedModel != this.lastModel;
        super.grabRelevantBones(bakedModel);
        if (!fresh) return;
        waistLegs = bone(bakedModel, "waistLegs");
        cape = bone(bakedModel, "outfitCape");
        sashTail = bone(bakedModel, "sashTail");
        sleeveR = bone(bakedModel, "sleeveRight");
        sleeveL = bone(bakedModel, "sleeveLeft");
        String[] sides = {"Front", "Back", "Right", "Left"};
        for (int i = 0; i < 4; i++) panels[i] = bone(bakedModel, "legsSkirt" + sides[i]);
    }

    private static GeoBone bone(BakedGeoModel model, String name) {
        return model.getBone(name).orElse(null);
    }

    @Override
    protected void applyBaseTransformations(HumanoidModel<?> base) {
        super.applyBaseTransformations(base);
        float age = this.currentEntity != null ? this.currentEntity.tickCount + this.partialTick : 0F;
        float swing = Mth.clamp(this.limbSwingAmount, 0F, 1F);
        if (waistLegs != null) {
            RenderUtilHelper.follow(base, waistLegs);
        }
        float forward = Math.max(0F, -Math.min(base.rightLeg.xRot, base.leftLeg.xRot));
        float backward = Math.max(0F, Math.max(base.rightLeg.xRot, base.leftLeg.xRot));
        rotate(panels[0], -forward * 0.9F, 0F, 0F);
        rotate(panels[1], backward * 0.9F, 0F, 0F);
        rotate(panels[2], 0F, 0F, swing * 0.15F);
        rotate(panels[3], 0F, 0F, -swing * 0.15F);
        if (cape != null) {
            float lift = 0.05F + swing * 0.85F + Mth.sin(age * 0.1F) * 0.03F * (0.4F + swing);
            if (base.crouching) lift += 0.25F;
            rotate(cape, Math.min(lift, 1.2F), 0F, Mth.sin(age * 0.07F) * 0.025F);
        }
        if (sashTail != null) rotate(sashTail, 0.15F + swing * 0.5F, 0F, Mth.sin(age * 0.12F) * 0.12F);
        // mangas largas: ficam um pouco "para trás" do movimento do braço
        if (sleeveR != null) rotate(sleeveR, -base.rightArm.xRot * 0.25F + swing * 0.1F, 0F, Mth.sin(age * 0.09F) * 0.03F);
        if (sleeveL != null) rotate(sleeveL, -base.leftArm.xRot * 0.25F + swing * 0.1F, 0F, -Mth.sin(age * 0.09F) * 0.03F);
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
        setBoneVisible(waistLegs, visible);
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
        super.applyBoneVisibilityBySlot(slot);
        // a cintura (hakama) não é filha dos ossos padrão: aparece com as calças
        setBoneVisible(waistLegs, slot == EquipmentSlot.LEGS && this.baseModel.body.visible);
    }

    /** Copia a pose do tronco para a cintura (a hakama acompanha o corpo). */
    private static final class RenderUtilHelper {
        static void follow(HumanoidModel<?> base, GeoBone waist) {
            software.bernie.geckolib.util.RenderUtil.matchModelPartRot(base.body, waist);
            waist.updatePosition(base.body.x, -base.body.y, base.body.z);
        }
    }

    /** Entrega o renderer 3D de uma peça ao GeckoLib (um por grau, criado na primeira vez). */
    public static final class Provider implements GeoRenderProvider {
        private final ShinigamiArmorItem item;
        private ShinigamiArmorRenderer renderer;

        public Provider(ShinigamiArmorItem item) {
            this.item = item;
        }

        @Override
        public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack, @Nullable EquipmentSlot slot,
                                                                             @Nullable HumanoidModel<T> original) {
            if (renderer == null) renderer = new ShinigamiArmorRenderer(item.tier());
            return renderer;
        }
    }
}
