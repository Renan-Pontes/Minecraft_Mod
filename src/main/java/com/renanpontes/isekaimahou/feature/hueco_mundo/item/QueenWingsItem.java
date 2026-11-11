package com.renanpontes.isekaimahou.feature.hueco_mundo.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Asas da Rainha (Rainha dos Espinhos): abrir as asas de vidro dá 30 s de queda lenta, salto alto e velocidade — para
 * planar sobre os vales e fugir de quedas. Recarga de 1 min; gasta durabilidade (conserta com Pó de Fada na bigorna).
 */
public class QueenWingsItem extends Item {
    private static final int DURATION = 600, COOLDOWN = 1200;

    public QueenWingsItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, DURATION, 0));
        sp.addEffect(new MobEffectInstance(MobEffects.JUMP, DURATION, 1));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 0));
        ServerLevel sl = sp.serverLevel();
        sl.sendParticles(ParticleTypes.CHERRY_LEAVES, sp.getX(), sp.getY() + 1.2, sp.getZ(), 30, 0.8, 0.6, 0.8, 0.05);
        sl.sendParticles(HmFx.dust(HmFx.FEY_PINK, 1.0F), sp.getX(), sp.getY() + 1.2, sp.getZ(), 20, 0.7, 0.5, 0.7, 0);
        sl.playSound(null, sp.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.6F, 1.6F);
        stack.hurtAndBreak(1, sp, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        sp.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries.FEY_DUST.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.isekai_mahou.queen_wings.desc").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
