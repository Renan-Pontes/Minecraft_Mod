package com.renanpontes.isekaimahou.feature.class_quincy.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

/**
 * Lâmina Espiritual (homenagem ao Seele Schneider): espada de Reishi, mais RÁPIDA e mais FRACA que uma de ferro
 * (o Quincy não é um espadachim). Cada golpe de um Quincy devolve um pouco de Foco e, a cada 3, 1 de Reishi.
 */
public class SeeleSchneiderItem extends SwordItem {
    public SeeleSchneiderItem(Properties props) {
        super(Tiers.IRON, props.attributes(SwordItem.createAttributes(Tiers.IRON, 1, -2.0F)));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof ServerPlayer sp && Quincy.isQuincy(sp)) {
            Quincy.gainFocus(sp, 0.01F);
            if (sp.tickCount % 3 == 0 || sp.getRandom().nextInt(3) == 0) Quincy.addReishi(sp, 1);
            if (sp.level() instanceof ServerLevel sl) {
                sl.sendParticles(QuincyFx.REISHI, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_seele_schneider").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_seele_schneider.effect").withColor(Quincy.RGB));
    }
}
