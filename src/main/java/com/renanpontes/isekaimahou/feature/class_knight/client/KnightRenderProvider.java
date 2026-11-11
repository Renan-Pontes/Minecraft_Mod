package com.renanpontes.isekaimahou.feature.class_knight.client;

import org.jetbrains.annotations.Nullable;

import com.renanpontes.isekaimahou.feature.class_knight.outfit.KnightArmorItem;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

/** Entrega ao GeckoLib o renderer 3D de uma peça da armadura do Cavaleiro (um por grau, criado na 1ª vez). */
public class KnightRenderProvider implements GeoRenderProvider {
    private final KnightArmorItem item;
    private KnightArmorRenderer renderer;

    public KnightRenderProvider(KnightArmorItem item) {
        this.item = item;
    }

    @Override
    public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack,
            @Nullable EquipmentSlot slot, @Nullable HumanoidModel<T> original) {
        if (renderer == null) renderer = new KnightArmorRenderer(item.tier());
        return renderer;
    }
}
