package com.renanpontes.isekaimahou.feature.familiars;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Incenso do Vínculo — o "ritual simples" do Familiar: segure para queimar (4 s) perto de uma fogueira acesa.
 * Sem familiar, desperta um (classe escolhida + nível {@link FamiliarService#AWAKEN_LEVEL}); com familiar, renova o
 * vínculo e a forma se ajusta na hora a quem o jogador é agora. Uso único.
 */
public class BondIncenseItem extends Item {
    private static final int USE_TICKS = 80;

    public BondIncenseItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            Component problem = FamiliarService.ritualProblem(sp);
            if (problem != null) {
                sp.displayClientMessage(problem.copy().withColor(0xE0A0A0), true);
                return InteractionResultHolder.fail(stack);
            }
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.7F, 0.8F);
        } else if (!FamiliarService.nearCampfire(player)) {
            // O cliente só sabe da fogueira; classe e nível quem confere é o servidor
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel sl) || remaining % 4 != 0) return;
        // Fumaça subindo em espiral: fecha conforme o incenso queima
        double t = (USE_TICKS - remaining) / (double) USE_TICKS, a = remaining * 0.35, r = 1.2 - 0.8 * t;
        sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, living.getX() + Math.cos(a) * r, living.getY() + 0.2 + 1.6 * t,
                living.getZ() + Math.sin(a) * r, 1, 0, 0.02, 0, 0.005);
        sl.sendParticles(ParticleTypes.ENCHANT, living.getX(), living.getY() + 1.4, living.getZ(), 3, 0.5, 0.4, 0.5, 0.3);
        if (remaining % 20 == 0) {
            sl.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.6F, 0.8F + (float) t * 0.6F);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (!(living instanceof ServerPlayer sp)) return stack;
        Component problem = FamiliarService.ritualProblem(sp);
        if (problem != null) {
            sp.displayClientMessage(problem.copy().withColor(0xE0A0A0), true);
            return stack;
        }
        FamiliarService.completeRitual(sp);
        stack.consume(1, sp);
        sp.getCooldowns().addCooldown(this, 100);
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.bond_incense.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.isekai_mahou.bond_incense.desc2", FamiliarService.AWAKEN_LEVEL)
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
