package com.renanpontes.isekaimahou.feature.class_steelball.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SteelBallEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * Bola de Aço (arma do kit do Steel Ball, {@code steel_ball}; graus {@code steel_ball_t1..t4}).
 * Segurar o botão direito gira a bola (quanto mais tempo, mais forte — até 1 s); soltar arremessa com rotação.
 * A bola acerta, aplica Girado e VOLTA para a mão. Também serve de arma de mão (pesada e lenta).
 */
public class SteelBallItem extends Item {
    public static final int FULL_CHARGE = 20;
    private final int tier;

    public SteelBallItem(int tier, Properties props) {
        super(props.stacksTo(1).rarity(rarityOf(tier)).attributes(attributes(tier)));
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    private static Rarity rarityOf(int tier) {
        return switch (tier) {
            case 0, 1 -> Rarity.COMMON;
            case 2 -> Rarity.UNCOMMON;
            case 3 -> Rarity.RARE;
            default -> Rarity.EPIC;
        };
    }

    private static ItemAttributeModifiers attributes(int tier) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 2.0 + tier, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.6, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** Dano físico do arremesso com carga máxima (antes dos bônus de classe/partes). */
    public static float throwDamage(int tier) {
        // técnica de classe: segue a inflação de dano da v1.0 (×1,5)
        return (5.0F + 1.75F * tier) * com.renanpontes.isekaimahou.core.Progression.DAMAGE_SCALE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        if (!level.isClientSide) SbFx.sound((ServerLevel) level, player.position(), SoundEvents.GRINDSTONE_USE, 0.25F, 1.9F);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        int used = getUseDuration(stack, user) - remaining;
        if (level instanceof ServerLevel sl && used % 4 == 0 && used <= FULL_CHARGE) {
            // a bola gira mais rápido na mão: estalo de aço subindo de tom
            SbFx.sound(sl, user.position(), SoundEvents.CHAIN_STEP, 0.35F, 1.0F + used * 0.04F);
            if (used == FULL_CHARGE) SbFx.spinRing(sl, user.position().add(0, 1.2, 0), tier >= 3 ? SbFx.GOLD : SbFx.STEEL, 0.6, 10);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (!(user instanceof Player player) || !(level instanceof ServerLevel sl)) return;
        int used = getUseDuration(stack, user) - timeLeft;
        float charge = Mth.clamp(0.45F + 0.55F * used / (float) FULL_CHARGE, 0.45F, 1.0F);
        SteelBallEntity ball = SteelBallEntity.thrown(sl, player, tier, charge);
        sl.addFreshEntity(ball);
        player.getCooldowns().addCooldown(this, SteelBallEntity.ITEM_COOLDOWN);
        player.awardStat(Stats.ITEM_USED.get(this));
        SbFx.sound(sl, player.position(), SoundEvents.TRIDENT_THROW.value(), 0.8F, 1.4F + charge * 0.3F);
        SbFx.sound(sl, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.4F, 1.7F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.ball.flavor" + (tier == 0 ? "" : "_t" + tier))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.ball.damage", String.format(java.util.Locale.ROOT, "%.1f", throwDamage(tier)))
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.ball.use").withStyle(ChatFormatting.DARK_GRAY));
        if (tier > 0) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.ball.spell", 6 * tier).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
