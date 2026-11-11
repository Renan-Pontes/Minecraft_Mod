package com.renanpontes.isekaimahou.feature.class_smith;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithOutfitItem;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithOutfitTier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Entrega da roupa de grau do Ferreiro (igual às outras classes): Círculo 3 → Artesão Rúnico (II), 5 → Mestre da
 * Bigorna (III), 8 → Forjador de Lendas (IV). Uma vez por grau (flag); quem pula graus recebe só o maior. As peças vão
 * direto para o corpo se o espaço estiver vazio ou com uma peça de grau menor do Ferreiro sem encantamento.
 */
public final class SmithOutfits {

    public static void grantPending(ServerPlayer sp) {
        if (!sp.isAlive()) return;
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.SMITH) return;
        SmithOutfitTier best = null;
        for (SmithOutfitTier tier : SmithOutfitTier.VALUES) {
            if (tier == SmithOutfitTier.APPRENTICE || d.circle() < tier.circle || d.hasFlag(tier.flag())) continue;
            d.setFlag(tier.flag(), true);
            best = tier;
        }
        if (best == null) return;
        d.markDirty();
        deliver(sp, best);
    }

    public static void deliver(ServerPlayer sp, SmithOutfitTier tier) {
        int equipped = 0;
        for (ArmorItem.Type type : SmithOutfitTier.TYPES) {
            ItemStack fresh = new ItemStack(SmithRegistries.piece(tier, type).get());
            EquipmentSlot slot = type.getSlot();
            ItemStack worn = sp.getItemBySlot(slot);
            boolean replace = worn.isEmpty() || (worn.getItem() instanceof SmithOutfitItem old
                    && old.tier().ordinal() < tier.ordinal() && !worn.isEnchanted());
            if (replace) {
                if (!worn.isEmpty()) Rewards.give(sp, worn.copy());
                sp.setItemSlot(slot, fresh);
                equipped++;
            } else {
                Rewards.give(sp, fresh);
            }
        }
        ItemStack icon = new ItemStack(SmithRegistries.piece(tier, ArmorItem.Type.CHESTPLATE).get());
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.class_smith.outfit", tier.displayName()),
                Component.translatable("toast.isekai_mahou.class_smith.outfit_sub", tier.circle), icon, tier.color);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.class_smith.outfit_granted", tier.circle,
                tier.displayName().withStyle(tier.chatColor())).withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(Component.translatable(equipped == SmithOutfitTier.TYPES.length
                ? "message.isekai_mahou.class_smith.outfit_equipped" : "message.isekai_mahou.class_smith.outfit_inventory")
                .withStyle(ChatFormatting.GRAY));
        ServerLevel sl = sp.serverLevel();
        Vec3 p = sp.position();
        sl.sendParticles(SmithFx.dust(tier.color, 1.4F), p.x, p.y + 1, p.z, 40, 0.5, 0.9, 0.5, 0);
        sl.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.2, p.z, 20, 0.6, 0.1, 0.6, 0.02);
        SmithFx.sound(sl, p, SoundEvents.ANVIL_USE, 0.8F, 0.9F);
        SmithFx.sound(sl, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F + 0.1F * tier.number);
    }

    private SmithOutfits() {}
}
