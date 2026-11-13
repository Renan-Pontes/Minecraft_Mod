package com.renanpontes.isekaimahou.feature.mounts.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.mounts.entity.RideMount;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Apito de Montaria: assobio agudo que chama TODAS as montarias do jogador (voadoras e de chão) num raio de
 * 128 blocos (as que estão carregadas). Elas levantam voo e pousam ao lado de quem chamou.
 */
public class MountWhistleItem extends Item {
    private static final double RANGE = 128.0;

    public MountWhistleItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(),
                SoundSource.PLAYERS, 1.2F, 2.0F);
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(),
                    SoundSource.PLAYERS, 1.4F, 1.6F);
            List<Mob> mounts = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE, 64.0, RANGE),
                    m -> m instanceof RideMount fm && fm.isMountOwnedBy(player) && !m.isVehicle() && !m.isLeashed());
            for (Mob m : mounts) ((RideMount) m).answerCall(player);
            if (mounts.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.whistle_none"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.whistle_called", mounts.size())
                        .withStyle(ChatFormatting.AQUA), true);
            }
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.NOTE, player.getX(), player.getEyeY() + 0.4, player.getZ(), 4, 0.3, 0.2, 0.3, 1.0);
            }
        }
        player.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.mount_whistle.desc").withStyle(ChatFormatting.GRAY));
    }
}
