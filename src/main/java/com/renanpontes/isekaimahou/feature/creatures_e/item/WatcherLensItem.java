package com.renanpontes.isekaimahou.feature.creatures_e.item;

import com.renanpontes.isekaimahou.feature.creatures_e.CeFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Lente do Vigia: reutilizavel (com recarga). Da Visao Noturna e revela (brilho) as criaturas
 * num raio de 32 blocos, inclusive atraves das paredes.
 */
public class WatcherLensItem extends CeMaterialItem {
    public WatcherLensItem(Properties props) {
        super(props, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0));
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32),
                    e -> e != player && e.isAlive())) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), player);
            }
            CeFx.burst(sl, player.getEyePosition(), ParticleTypes.REVERSE_PORTAL, 30, 0.3, 0.1);
            sl.playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, player.getSoundSource(), 0.8F, 0.6F);
            sl.playSound(null, player.blockPosition(), SoundEvents.SPYGLASS_USE, player.getSoundSource(), 1.0F, 0.8F);
            player.getCooldowns().addCooldown(this, 1200);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
