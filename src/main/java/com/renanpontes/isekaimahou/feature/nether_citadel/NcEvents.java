package com.renanpontes.isekaimahou.feature.nether_citadel;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.nether_citadel.item.AntimagicBladeItem;
import com.renanpontes.isekaimahou.feature.nether_citadel.item.SunKatanaItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Eventos de jogo do módulo: infusões da Forja, tesouros, dívida do pacto, bônus da armadura e progresso do diário. */
public final class NcEvents {
    public static final ResourceKey<Structure> CITADEL = ResourceKey.create(Registries.STRUCTURE, IsekaiMahou.id("nether_citadel"));

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NcEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(NcEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(NcEvents::onTooltip);
        NeoForge.EVENT_BUS.addListener((MobEffectEvent.Expired e) -> {
            if (e.getEffectInstance() != null && e.getEffectInstance().is(NcRegistries.DEMON_PACT)) NcPact.onPactEnded(e.getEntity());
        });
        NeoForge.EVENT_BUS.addListener((MobEffectEvent.Remove e) -> {
            if (e.getEffect().is(NcRegistries.DEMON_PACT) && e.getEntity().hasEffect(NcRegistries.DEMON_PACT)) NcPact.onPactEnded(e.getEntity());
        });
    }

    // ------------------------------------------------------------------ Dano

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource source = event.getSource();
        // Lâmina Antimagia na mão: magia inimiga dói 30% menos.
        if (source.is(CoreRegistries.SPELL_DAMAGE) && victim.getMainHandItem().is(NcRegistries.ANTIMAGIC_BLADE.get())) {
            event.setAmount(event.getAmount() * 0.7F);
        }
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) return;
        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) return;
        ServerLevel sl = (ServerLevel) victim.level();
        double y = victim.getY() + victim.getBbHeight() * 0.6;
        String infusion = weapon.get(NcRegistries.INFUSION.get());
        if ("fire".equals(infusion)) {
            event.setAmount(event.getAmount() + 2.0F);
            victim.igniteForSeconds(4.0F);
            sl.sendParticles(ParticleTypes.FLAME, victim.getX(), y, victim.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        } else if ("shadow".equals(infusion)) {
            event.setAmount(event.getAmount() + 2.0F);
            victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), attacker);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, victim.getX(), y, victim.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }
        if (weapon.getItem() instanceof SunKatanaItem && NcDemons.isUnholy(victim)) {
            event.setAmount(event.getAmount() + SunKatanaItem.UNHOLY_BONUS);
            sl.sendParticles(ParticleTypes.FLAME, victim.getX(), y, victim.getZ(), 10, 0.3, 0.4, 0.3, 0.04);
            sl.playSound(null, victim.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.5F);
        } else if (weapon.getItem() instanceof AntimagicBladeItem) {
            AntimagicBladeItem.dispel(victim, 3);
        }
    }

    // ------------------------------------------------------------------ Tick do jogador (a cada 2 s)

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 40 != 0 || !sp.isAlive()) return;
        if (fullAdamantite(sp)) {
            sp.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false, true));
        }
        var data = MagicApi.get(sp);
        if (!data.hasFlag(NcProgress.FLAG_ADAMANTITE)
                && sp.getInventory().hasAnyMatching(s -> s.is(NcRegistries.RAW_ADAMANTITE.get()) || s.is(NcRegistries.ADAMANTITE_INGOT.get()))) {
            NcProgress.flag(sp, NcProgress.FLAG_ADAMANTITE);
        }
        if (sp.level().dimension() == Level.NETHER && !data.hasFlag(NcProgress.FLAG_ENTERED) && insideCitadel(sp)) {
            NcProgress.flag(sp, NcProgress.FLAG_ENTERED);
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.nether_citadel.entered").withColor(0xD4242C));
            sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value(), SoundSource.AMBIENT, 1.0F, 0.6F);
        }
    }

    public static boolean insideCitadel(ServerPlayer sp) {
        ServerLevel sl = sp.serverLevel();
        Structure s = sl.registryAccess().registryOrThrow(Registries.STRUCTURE).get(CITADEL);
        return s != null && sl.structureManager().getStructureWithPieceAt(sp.blockPosition(), s).isValid();
    }

    private static boolean fullAdamantite(ServerPlayer sp) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = sp.getItemBySlot(slot);
            if (!(s.getItem() instanceof ArmorItem a) || !a.getMaterial().is(NcRegistries.ADAMANTITE_MATERIAL.getKey())) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ Tooltip

    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String infusion = stack.get(NcRegistries.INFUSION.get());
        if (infusion != null) {
            event.getToolTip().add(1, Component.translatable("tooltip.isekai_mahou.citadel_infusion." + infusion)
                    .withStyle("fire".equals(infusion) ? ChatFormatting.GOLD : ChatFormatting.DARK_PURPLE));
        }
        if (stack.getItem() instanceof ArmorItem a && a.getMaterial().is(NcRegistries.ADAMANTITE_MATERIAL.getKey())) {
            event.getToolTip().add(Component.translatable("tooltip.isekai_mahou.adamantite_set").withStyle(ChatFormatting.DARK_RED));
        }
    }

    private NcEvents() {}
}
