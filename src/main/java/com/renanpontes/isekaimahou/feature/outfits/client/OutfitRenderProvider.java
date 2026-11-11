package com.renanpontes.isekaimahou.feature.outfits.client;

import org.jetbrains.annotations.Nullable;

import com.renanpontes.isekaimahou.feature.outfits.OutfitArmorItem;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

/** Entrega ao GeckoLib o renderer 3D de uma peça (criado na primeira vez que ela aparece; um por traje e grau). */
public class OutfitRenderProvider implements GeoRenderProvider {
    private final OutfitArmorItem item;
    private OutfitArmorRenderer renderer;

    public OutfitRenderProvider(OutfitArmorItem item) {
        this.item = item;
    }

    @Override
    public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack,
            @Nullable EquipmentSlot slot, @Nullable HumanoidModel<T> original) {
        if (renderer == null) renderer = new OutfitArmorRenderer(item.set(), item.tier());
        return renderer;
    }
}
