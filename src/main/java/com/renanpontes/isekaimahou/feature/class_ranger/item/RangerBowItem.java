package com.renanpontes.isekaimahou.feature.class_ranger.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Arco do Ranger (arma do kit, {@code ranger_bow}) e os graus {@code ranger_bow_t1..t4} (Guilda, Provações e a Trilha
 * do Ranger). Puxa mais rápido que o arco comum, as flechas saem mais fortes e, nos graus III/IV, o espírito da mata
 * devolve parte das flechas. Funciona com a forma Flecha do Grimório (a magia viaja nas flechas deste arco).
 */
public class RangerBowItem extends BowItem {
    /** Grau 0 = o arco do kit; 1..4 = os graus. */
    private final int tier;
    private final int drawTicks;
    private final float damageMult;
    private final float saveChance;

    public RangerBowItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.drawTicks = new int[] {18, 17, 15, 13, 11}[tier];
        this.damageMult = new float[] {1.05F, 1.15F, 1.3F, 1.45F, 1.65F}[tier];
        this.saveChance = new float[] {0F, 0F, 0F, 0.2F, 0.35F}[tier];
    }

    public int tier() {
        return tier;
    }

    /** Ticks para puxar o arco por completo (o vanilla usa 20). */
    public int drawTicks() {
        return drawTicks;
    }

    /** Mesma lógica do arco vanilla, com a puxada encurtada para {@link #drawTicks}. */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof Player player)) return;
        ItemStack ammo = player.getProjectile(stack);
        if (ammo.isEmpty()) return;
        int used = getUseDuration(stack, living) - timeLeft;
        used = net.neoforged.neoforge.event.EventHooks.onArrowLoose(stack, level, player, used, true);
        if (used < 0) return;
        float power = getPowerForTime(Math.round(used * 20F / drawTicks));
        if (power < 0.1F) return;
        List<ItemStack> drawn = draw(stack, ammo, player);
        if (level instanceof ServerLevel sl && !drawn.isEmpty()) {
            shoot(sl, player, player.getUsedItemHand(), stack, drawn, power * 3.0F, 1.0F, power == 1.0F, null);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /** Flecha mais forte; nos graus III/IV às vezes a flecha volta para a aljava (a disparada não pode ser recolhida). */
    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        arrow.setBaseDamage(arrow.getBaseDamage() * damageMult);
        if (saveChance > 0 && arrow.getOwner() instanceof Player p && !p.getAbilities().instabuild
                && arrow.pickup == AbstractArrow.Pickup.ALLOWED && p.getRandom().nextFloat() < saveChance) {
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            ItemStack back = projectileStack.copyWithCount(1);
            if (!p.getInventory().add(back)) p.drop(back, false);
        }
        return arrow;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return tier >= 2 ? repair.is(CoreRegistries.MANA_CRYSTAL.get()) : repair.is(Items.STRING) || repair.is(Items.LEATHER);
    }

    @Override
    public int getEnchantmentValue() {
        return 14 + tier * 3;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        String flavor = "tooltip.isekai_mahou.ranger_bow.flavor." + tier;
        tooltip.add(Component.translatable(flavor).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ranger_bow.stats",
                String.format(java.util.Locale.ROOT, "%.2f", drawTicks / 20.0), Math.round((damageMult - 1) * 100))
                .withStyle(ChatFormatting.DARK_GREEN));
        if (saveChance > 0) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.ranger_bow.save", Math.round(saveChance * 100))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ranger_bow.spell").withStyle(ChatFormatting.DARK_GRAY));
    }
}
