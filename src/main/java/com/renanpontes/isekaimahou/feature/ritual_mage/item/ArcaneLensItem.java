package com.renanpontes.isekaimahou.feature.ritual_mage.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.Observation;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.Vec3;

/**
 * Lente Arcana: segure o botão direito (1,5 s) mirando num bloco, criatura, no céu ou só observando o lugar.
 * Ao terminar, registra o fenômeno no Atlas e entrega Notas de Pesquisa.
 */
public class ArcaneLensItem extends Item {
    public static final int USE_TICKS = 30;

    public ArcaneLensItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.playSound(SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    /** Durante a observação: anel de runas no ponto focado e mana fluindo para a lente. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)) return;
        int elapsed = USE_TICKS - remaining;
        if (elapsed % 4 != 0) return;
        Vec3 focus = Observation.focusPoint(player);
        double radius = 0.3 + 0.9 * (1.0 - remaining / (double) USE_TICKS);
        double a0 = elapsed * 0.4;
        for (int i = 0; i < 6; i++) {
            double a = a0 + i * Math.PI / 3;
            server.sendParticles(ElementFx.dust(Element.ARCANE, 0.9F), focus.x + Math.cos(a) * radius, focus.y + 0.1,
                    focus.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
        server.sendParticles(Vfx.enchant(), focus.x, focus.y + 0.5, focus.z, 4, 0.3, 0.3, 0.3, 0.4);
        if (elapsed % 12 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.35F, 1.4F + elapsed / 60F);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            Observation.observe(player);
            player.getCooldowns().addCooldown(this, 20);
        }
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        entity.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.arcane_lens.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.isekai_mahou.arcane_lens.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
