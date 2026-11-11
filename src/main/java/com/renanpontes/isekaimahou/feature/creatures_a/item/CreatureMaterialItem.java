package com.renanpontes.isekaimahou.feature.creatures_a.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Material de criatura (géis de slime, fetiche goblin): mostra uma descrição no tooltip
 * ({@code tooltip.isekai_mahou.<id>}), pode servir de combustível e, se for comestível, pode apagar o fogo de quem come.
 */
public class CreatureMaterialItem extends Item {
    private final int burnTime;
    private final boolean extinguishOnEat;

    public CreatureMaterialItem(Properties props, int burnTime, boolean extinguishOnEat) {
        super(props);
        this.burnTime = burnTime;
        this.extinguishOnEat = extinguishOnEat;
    }

    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        return burnTime > 0 ? burnTime : super.getBurnTime(stack, recipeType);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (extinguishOnEat && entity.isOnFire()) {
            entity.clearFire();
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = "tooltip.isekai_mahou." + BuiltInRegistries.ITEM.getKey(this).getPath();
        tooltip.add(Component.translatable(key)
                .withStyle(ChatFormatting.GRAY));
    }
}
