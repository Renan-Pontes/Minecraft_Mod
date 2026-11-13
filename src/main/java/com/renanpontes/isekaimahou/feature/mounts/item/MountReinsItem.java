package com.renanpontes.isekaimahou.feature.mounts.item;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.mounts.entity.GroundMount;
import com.renanpontes.isekaimahou.feature.mounts.entity.MountFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Rédeas de uma montaria de chão: usar chama a montaria JÁ DOMADA E SELADA ao seu lado (gasta as Rédeas).
 * Se a montaria morrer, as Rédeas voltam para o dono ({@link GroundMount#die}). Fontes: loja da Guilda
 * (Dragão Terrestre), chefes (Pesadelo) e reputação com o Reino (Cervo da Aurora).
 */
public class MountReinsItem extends Item {
    private final Supplier<? extends EntityType<? extends GroundMount>> type;

    public MountReinsItem(Supplier<? extends EntityType<? extends GroundMount>> type, Properties props) {
        super(props);
        this.type = type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isPassenger()) return InteractionResultHolder.pass(stack);
        if (level instanceof ServerLevel sl) {
            GroundMount mount = type.get().create(sl);
            if (mount == null) return InteractionResultHolder.fail(stack);
            // Um passo à frente do jogador; sem espaço, nasce no lugar dele
            Vec3 front = player.position().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(1.6));
            mount.moveTo(front.x, player.getY(), front.z, player.getYRot(), 0.0F);
            if (!sl.noCollision(mount)) mount.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
            mount.tame(player);
            mount.setSaddled(true);
            mount.setOrderedToSit(false);
            mount.setPersistenceRequired();
            sl.addFreshEntity(mount);
            MountFx.burst(mount, ParticleTypes.POOF, 14);
            sl.playSound(null, mount.getX(), mount.getY(), mount.getZ(), SoundEvents.HORSE_SADDLE, SoundSource.NEUTRAL, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.isekai_mahou.mount_reins_used", mount.getDisplayName())
                    .withStyle(ChatFormatting.GOLD), true);
            if (player instanceof ServerPlayer sp) {
                String key = "feat.mount_own." + BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()).getPath();
                if (MagicApi.get(sp).counter(key) == 0) MagicApi.addCounter(sp, key, 1);
            }
            stack.consume(1, player);
        }
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.isekai_mahou.mount_reins.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
