package com.renanpontes.isekaimahou.feature.class_gambler.item;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.ThrownCardEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * Baralho Arcano — a arma do Apostador (graus I a IV). Na mão principal dá Poder Mágico e um pouco de dano.
 * Clique direito: arremessa uma carta afiada (dano físico, chance de "Ás" com dano dobrado; enche o Foco ao acertar).
 * Agachado + clique direito: abre a Mesa da Casa (aba do menu com ranks, Sorte e a Rodada da Casa).
 */
public class GamblerDeckItem extends Item {
    /** Gancho de cliente: abre a aba da Casa (preenchido pelo ClassGamblerClient; nulo no servidor). */
    public static volatile Runnable openTable;

    private static final float[] CARD_DAMAGE = {4F, 5.5F, 7F, 9F};
    private static final double[] POWER = {0.05, 0.10, 0.15, 0.22};
    private static final double[] MELEE = {1.0, 2.0, 3.0, 4.0};
    private static final int[] COLORS = {0xFFD86B, 0x7FC8FF, 0xFFC94A, 0xFF4AD2};

    private final int tier;

    public GamblerDeckItem(int tier, Properties props) {
        super(props.attributes(modifiers(tier)));
        this.tier = Math.max(1, Math.min(4, tier));
    }

    public int tier() {
        return tier;
    }

    private static ItemAttributeModifiers modifiers(int tier) {
        int i = Math.max(1, Math.min(4, tier)) - 1;
        return ItemAttributeModifiers.builder()
                .add(CoreRegistries.SPELL_POWER, new AttributeModifier(IsekaiMahou.id("gambler_deck_power"), POWER[i],
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, MELEE[i],
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                Runnable r = openTable;
                if (r != null) r.run();
            }
            return InteractionResultHolder.success(stack);
        }
        if (level instanceof ServerLevel sl) {
            boolean ace = sl.random.nextFloat() < 0.12F;
            float dmg = CARD_DAMAGE[tier - 1] * (ace ? 2F : 1F);
            ThrownCardEntity card = ThrownCardEntity.physical(level, player, dmg, ace ? 0xFF4A5A : COLORS[tier - 1]);
            card.shootFromRotation(player, player.getXRot(), player.getYRot(), 0F, 2.0F, 0.5F);
            sl.addFreshEntity(card);
            if (ace) sl.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getEyeY(), player.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
            GbFx.sound(sl, player.getEyePosition(), SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.8F);
            GbFx.sound(sl, player.getEyePosition(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.4F, 2.0F);
        }
        player.getCooldowns().addCooldown(this, 9 - tier);
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return tier >= 4 || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gambler_deck.flavor").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gambler_deck.tier", tier).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gambler_deck.throw",
                String.format(java.util.Locale.ROOT, "%.1f", CARD_DAMAGE[tier - 1] * com.renanpontes.isekaimahou.core.Progression.DAMAGE_SCALE))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gambler_deck.table").withStyle(ChatFormatting.DARK_GRAY));
    }
}
