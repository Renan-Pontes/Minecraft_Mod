package com.renanpontes.isekaimahou.feature.espada.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.espada.aging.Aging;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaFx;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Restauração de Reishi: frasco de partículas espirituais que refaz o corpo. Beber tira TODO o Envelhecido na hora
 * (a única cura instantânea; leite não funciona) e cura um pouco.
 */
public class ReishiRestorationItem extends Item {
    public ReishiRestorationItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 24;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel sl) {
            boolean was = Aging.stacks(entity) > 0;
            Aging.cure(entity);
            entity.heal(6F);
            EspadaFx.regen(sl, entity);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
            if (entity instanceof Player p) {
                p.displayClientMessage(Component.translatable(was ? "message.isekai_mahou.espada.restored" : "message.isekai_mahou.espada.restored_none")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
        if (!(entity instanceof Player p) || !p.getAbilities().instabuild) stack.shrink(1);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.isekai_mahou.reishi_restoration.desc").withStyle(ChatFormatting.GRAY));
    }
}
