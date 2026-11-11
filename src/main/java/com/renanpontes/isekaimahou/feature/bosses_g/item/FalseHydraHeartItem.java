package com.renanpontes.isekaimahou.feature.bosses_g.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Coração da Falsa Hidra: apertá-lo canta um eco do Canto do Esquecimento — por 45 s você fica invisível, mais
 * rápido, e todo monstro a 32 blocos que estava atrás de você ESQUECE de você. Consumido ao usar (recarga 60 s).
 */
public class FalseHydraHeartItem extends Item {
    private static final int DURATION = 900;

    public FalseHydraHeartItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 0));
            int forgot = 0;
            for (Mob m : sl.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), m -> m.getTarget() == player)) {
                m.setTarget(null);
                m.getNavigation().stop();
                forgot++;
            }
            sl.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 2.0, player.getZ(), 12, 0.8, 0.4, 0.8, 1.0);
            sl.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.6, 0.8, 0.6, 0.02);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.PLAYERS, 1.2F, 0.5F);
            player.displayClientMessage(Component.translatable("item.isekai_mahou.false_hydra_heart.used", forgot)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            player.getCooldowns().addCooldown(this, 1200);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.isekai_mahou.false_hydra_heart.desc").withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.add(Component.translatable("item.isekai_mahou.false_hydra_heart.use").withStyle(ChatFormatting.GRAY));
    }
}
