package com.renanpontes.isekaimahou.feature.class_knight.outfit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Armadura do Cavaleiro: o grau I vai no kit; II/III/IV chegam sozinhos nos Círculos 3/5/8 (uma vez cada).
 * Ouvintes com prioridade ALTA gravam também a flag comum {@code gear.armor.<grau>}, para o módulo de trajes não
 * entregar o traje antigo da classe no mesmo Círculo.
 */
public final class KnightOutfitRewards {
    private static final Map<UUID, Integer> LOGIN_DELAY = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, KnightOutfitRewards::onCircle);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, KnightOutfitRewards::onPath);
        NeoForge.EVENT_BUS.addListener(KnightOutfitRewards::onLogin);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, KnightOutfitRewards::onRespawn);
        NeoForge.EVENT_BUS.addListener(KnightOutfitRewards::onLogout);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, KnightOutfitRewards::onTick);
    }

    /** Kit: armadura grau I (chamado no setup comum, DEPOIS do módulo de trajes, para substituir a reserva antiga). */
    public static void registerKitArmor() {
        for (ArmorItem.Type type : KnightRegistries.TYPES) {
            ClassKits.setArmor(MagicPath.KNIGHT, type.getSlot(), () -> new ItemStack(KnightRegistries.armor(KnightOutfitTier.SQUIRE, type).get()));
        }
    }

    private static void onCircle(MagicProgressEvent.CircleChanged e) {
        if (e.newCircle > e.oldCircle) grantPending(e.getPlayer());
    }

    private static void onPath(MagicProgressEvent.PathChosen e) {
        grantPending(e.getPlayer());
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) LOGIN_DELAY.put(sp.getUUID(), 40);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && !e.isEndConquered()) LOGIN_DELAY.put(sp.getUUID(), 20);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LOGIN_DELAY.remove(e.getEntity().getUUID());
    }

    private static void onTick(PlayerTickEvent.Post e) {
        if (LOGIN_DELAY.isEmpty() || !(e.getEntity() instanceof ServerPlayer sp)) return;
        Integer left = LOGIN_DELAY.get(sp.getUUID());
        if (left == null) return;
        if (left > 0) {
            LOGIN_DELAY.put(sp.getUUID(), left - 1);
            return;
        }
        if (!sp.isAlive()) return;
        LOGIN_DELAY.remove(sp.getUUID());
        grantPending(sp);
    }

    /** Entrega o maior grau alcançado e ainda não recebido (só Cavaleiros). */
    public static void grantPending(ServerPlayer player) {
        if (!player.isAlive()) return;
        MagicData d = MagicApi.get(player);
        if (d.path() != MagicPath.KNIGHT) return;
        KnightOutfitTier best = null;
        for (KnightOutfitTier t : KnightOutfitTier.REWARDS) {
            if (d.circle() < t.circle) continue;
            // a flag comum impede o traje antigo da classe; a nossa garante uma entrega só
            if (t.sharedFlag != null) d.setFlag(t.sharedFlag, true);
            if (d.hasFlag(t.flag())) continue;
            d.setFlag(t.flag(), true);
            best = t;
        }
        d.markDirty();
        if (best != null) deliver(player, best);
    }

    public static void deliver(ServerPlayer player, KnightOutfitTier tier) {
        int equipped = 0;
        for (ArmorItem.Type type : KnightRegistries.TYPES) {
            ItemStack fresh = new ItemStack(KnightRegistries.armor(tier, type).get());
            EquipmentSlot slot = type.getSlot();
            ItemStack worn = player.getItemBySlot(slot);
            boolean replace = worn.isEmpty() || worn.getItem() instanceof KnightArmorItem old && old.tier().ordinal() < tier.ordinal()
                    && !worn.isEnchanted();
            if (replace) {
                if (!worn.isEmpty()) Rewards.give(player, worn.copy());
                player.setItemSlot(slot, fresh);
                equipped++;
            } else {
                Rewards.give(player, fresh);
            }
        }
        ItemStack icon = new ItemStack(KnightRegistries.armor(tier, ArmorItem.Type.CHESTPLATE).get());
        Component title = Component.translatable("toast.isekai_mahou.class_knight.outfit", tier.displayName());
        Rewards.toast(player, title, Component.translatable("toast.isekai_mahou.class_knight.outfit.sub", tier.circle), icon, tier.color);
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.class_knight.outfit_granted", tier.circle,
                tier.displayName().withStyle(tier.chatColor())).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable(equipped == 4 ? "message.isekai_mahou.class_knight.outfit_equipped"
                : "message.isekai_mahou.class_knight.outfit_inventory").withStyle(ChatFormatting.GRAY));
        ServerLevel level = player.serverLevel();
        KnightFx.sound(level, player.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, 1.0F);
        KnightFx.sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F + 0.1F * tier.number);
        for (int i = 0; i < 40; i++) {
            float a = i * 0.5F;
            level.sendParticles(KnightFx.dust(tier.color, 1.1F), player.getX() + Mth.cos(a) * 0.9, player.getY() + i * 0.05,
                    player.getZ() + Mth.sin(a) * 0.9, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 50, 0.4, 0.7, 0.4, 0.8);
    }

    public static void clear() {
        LOGIN_DELAY.clear();
    }

    private KnightOutfitRewards() {}
}
