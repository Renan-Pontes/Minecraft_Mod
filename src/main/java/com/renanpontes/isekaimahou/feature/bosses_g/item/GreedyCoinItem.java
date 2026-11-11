package com.renanpontes.isekaimahou.feature.bosses_g.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Moeda Gananciosa (do Rei Mímico): jogue-a para o alto — 10 min de Sorte II (baús e pescaria melhores) e Herói da
 * Vila (aldeões fazem preço de amigo). Consumida ao usar.
 */
public class GreedyCoinItem extends Item {
    private static final int DURATION = 12000;

    public GreedyCoinItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, DURATION, 1));
            player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, DURATION, 0));
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)), player.getX(), player.getY() + 2.2,
                    player.getZ(), 20, 0.3, 0.3, 0.3, 0.12);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 1.0F, 1.6F);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
            player.displayClientMessage(Component.translatable("item.isekai_mahou.greedy_coin.used").withStyle(ChatFormatting.GOLD), true);
            player.getCooldowns().addCooldown(this, 40);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.isekai_mahou.greedy_coin.desc").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("item.isekai_mahou.greedy_coin.use").withStyle(ChatFormatting.GRAY));
    }
}
