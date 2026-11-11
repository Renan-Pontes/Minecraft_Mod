package com.renanpontes.isekaimahou.feature.bosses_g.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

/**
 * Ferrão da Manticora: adaga-espada rápida (diamante) que envenena (Veneno II por 4 s) e faz sangrar a cada golpe.
 */
public class ManticoreStingerItem extends SwordItem {

    public ManticoreStingerItem(Properties props) {
        super(Tiers.DIAMOND, props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 2, -2.0F)));
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1), attacker);
            Statuses.apply(target, Statuses.Kind.BLEEDING, 1, 80, attacker, 1F);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.isekai_mahou.manticore_stinger.desc").withStyle(ChatFormatting.DARK_GREEN));
    }
}
