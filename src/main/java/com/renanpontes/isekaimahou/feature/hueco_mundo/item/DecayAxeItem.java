package com.renanpontes.isekaimahou.feature.hueco_mundo.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;

/** Machado da Decadência (Rei Vazio): todo golpe envelhece o alvo — definhar e lentidão por 3 s. */
public class DecayAxeItem extends AxeItem {

    public DecayAxeItem(Tier tier, Properties props) {
        super(tier, props);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), attacker);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), attacker);
        if (target.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ASH, target.getX(), target.getY() + 1, target.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
            sl.sendParticles(HmFx.dust(0xC9A23A, 1.0F), target.getX(), target.getY() + 1, target.getZ(), 6, 0.3, 0.4, 0.3, 0);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.isekai_mahou.decay_axe.desc").withStyle(ChatFormatting.GOLD));
    }
}
