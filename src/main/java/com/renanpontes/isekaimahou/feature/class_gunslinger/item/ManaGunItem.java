package com.renanpontes.isekaimahou.feature.class_gunslinger.item;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_gunslinger.GsRegistries;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunCombat;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
 * Arma de mana do Pistoleiro (revólver, pistola, escopeta, pistolas duplas, fuzil). Clique direito atira (pistola e
 * pistolas duplas são automáticas: segure); o tambor ({@link GsRegistries#GUN_ROUNDS}) esvazia a cada disparo e a
 * recarga — tecla de trocar de mão (F) ou automática ao puxar o gatilho vazio — gasta Foco. Com a arma na mão, as
 * formas de glifo do Pistoleiro (Bala, Rajada...) gastam um cartucho e saem mais fortes.
 * Modelo 3D (JSON) na mão, ícone 2D no inventário; animação de tiro/recarga pelo playerAnimator.
 */
public class ManaGunItem extends Item {
    private final GunKind kind;
    /** Tier do revólver (0 = kit, 1–4 = Guilda/Provações). As outras armas crescem com o rank. */
    private final int tier;

    public ManaGunItem(GunKind kind, int tier, Properties properties) {
        super(properties);
        this.kind = kind;
        this.tier = tier;
    }

    public GunKind kind() {
        return kind;
    }

    public int tier() {
        return tier;
    }

    /** Cartuchos no tambor (componente ausente = cheio). */
    public static int rounds(ItemStack stack) {
        if (!(stack.getItem() instanceof ManaGunItem gun)) return 0;
        Integer r = stack.get(GsRegistries.GUN_ROUNDS.get());
        return r == null ? gun.kind.rounds : Mth.clamp(r, 0, gun.kind.rounds);
    }

    public static void setRounds(ItemStack stack, int rounds) {
        if (!(stack.getItem() instanceof ManaGunItem gun)) return;
        stack.set(GsRegistries.GUN_ROUNDS.get(), Mth.clamp(rounds, 0, gun.kind.rounds));
    }

    @Nullable
    public static ManaGunItem of(ItemStack stack) {
        return stack.getItem() instanceof ManaGunItem g ? g : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (kind.automatic) player.startUsingItem(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) GunCombat.trigger(sp, stack, hand);
        // consume: sem balançar o braço (a animação de tiro é do playerAnimator)
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !kind.automatic || !(entity instanceof ServerPlayer sp)) return;
        int used = getUseDuration(stack, entity) - remaining;
        if (used > 0 && used % kind.interval == 0) GunCombat.trigger(sp, stack, sp.getUsedItemHand());
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return kind.automatic ? 72000 : 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return rounds(stack) < kind.rounds;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * rounds(stack) / kind.rounds);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return rounds(stack) == 0 ? 0xE0503A : 0xF2C14E;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return tier >= 4 || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(kind.description().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (tier > 0) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.gun.tier", roman(tier)).withStyle(ChatFormatting.GOLD));
        } else if (kind != GunKind.REVOLVER) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.gun.rank_scaling").withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.gun.drum", rounds(stack), kind.rounds)
                .withStyle(ChatFormatting.YELLOW));
        if (!flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.more").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        String dmg = kind.pellets > 1 ? kind.pellets + "×" + fmt(kind.damage * GunCombat.tierMultiplier(this))
                : fmt(kind.damage * GunCombat.tierMultiplier(this));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.gun.stats", dmg, fmt(20.0F / kind.interval),
                fmt(kind.reloadTicks / 20.0F)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(kind.automatic ? "tooltip.isekai_mahou.gunslinger.gun.auto"
                : "tooltip.isekai_mahou.gunslinger.gun.semi").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.gun.reload_cost",
                fmt(kind.reloadCostPerRound * kind.rounds * 100)).withStyle(ChatFormatting.DARK_AQUA));
        if (kind == GunKind.RIFLE) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.gun.scope").withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gunslinger.gun.spells").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }

    private static String fmt(float v) {
        float r = Math.round(v * 10F) / 10F;
        return r == Math.rint(r) ? String.valueOf((int) r) : String.format(Locale.ROOT, "%.1f", r);
    }
}
