package com.renanpontes.isekaimahou.feature.class_steelball;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_steelball.item.SbOutfitItem;
import com.renanpontes.isekaimahou.feature.class_steelball.item.SbOutfitTier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Entrega da roupa do Steel Ball nos Círculos 3 / 5 / 8 (graus II–IV), como as roupas das outras classes: uma vez por
 * grau (flag {@code steelball.armor.<grau>}); a peça vai direto para o corpo se o espaço está vazio ou tem um grau menor
 * sem encantamentos. Pulou graus (comando)? Recebe só o maior.
 */
public final class SbOutfits {
    public static void grantPending(ServerPlayer sp) {
        if (!sp.isAlive()) return;
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.STEELBALL) return;
        SbOutfitTier best = null;
        for (SbOutfitTier t : SbOutfitTier.REWARDS) {
            if (d.circle() < t.circle || d.hasFlag(t.flag())) continue;
            d.setFlag(t.flag(), true);
            best = t;
        }
        if (best != null) deliver(sp, best);
    }

    public static void deliver(ServerPlayer sp, SbOutfitTier tier) {
        int equipped = 0;
        for (ArmorItem.Type type : SbOutfitTier.TYPES) {
            ItemStack fresh = new ItemStack(SbRegistry.outfit(tier, type).get());
            EquipmentSlot slot = type.getSlot();
            ItemStack worn = sp.getItemBySlot(slot);
            boolean replace = worn.isEmpty() || (worn.getItem() instanceof SbOutfitItem old && old.tier().ordinal() < tier.ordinal()
                    && !worn.isEnchanted());
            if (replace) {
                if (!worn.isEmpty()) Rewards.give(sp, worn.copy());
                sp.setItemSlot(slot, fresh);
                equipped++;
            } else {
                Rewards.give(sp, fresh);
            }
        }
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.steelball.outfit", tier.displayName()),
                Component.translatable("toast.isekai_mahou.steelball.outfit.sub", tier.circle),
                new ItemStack(SbRegistry.outfit(tier, ArmorItem.Type.CHESTPLATE).get()), tier.color);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.steelball.outfit", tier.circle,
                tier.displayName().withStyle(tier.chatColor())).withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(stats(tier));
        sp.sendSystemMessage(Component.translatable(equipped == SbOutfitTier.TYPES.length ? "message.isekai_mahou.steelball.outfit.equipped"
                : "message.isekai_mahou.steelball.outfit.inventory").withStyle(ChatFormatting.GRAY));
        ServerLevel sl = sp.serverLevel();
        SbFx.sound(sl, sp.position(), SoundEvents.ARMOR_EQUIP_GOLD.value(), 1.0F, 1.0F);
        SbFx.sound(sl, sp.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F + 0.1F * tier.number);
        SbFx.spiral(sl, sp.position(), tier.color, 0.9, 2.2, 40, 0F);
        sl.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1, sp.getZ(), 12 + 6 * tier.number, 0.35, 0.6, 0.35, 0.04);
    }

    public static MutableComponent stats(SbOutfitTier tier) {
        MutableComponent line = Component.translatable("message.isekai_mahou.steelball.outfit.stats", tier.totalDefense());
        for (SbOutfitTier.Bonus b : tier.totals()) line.append(Component.literal(" · ")).append(b.stat().describe(b.amount()));
        return line.withStyle(ChatFormatting.GRAY);
    }

    private SbOutfits() {}
}
