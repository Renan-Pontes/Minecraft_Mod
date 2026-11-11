package com.renanpontes.isekaimahou.feature.origins_a;

import com.renanpontes.isekaimahou.feature.origins_a.curse.Frogify;
import com.renanpontes.isekaimahou.feature.origins_a.curse.VoodooLinks;
import com.renanpontes.isekaimahou.feature.origins_a.rune.CircleTravel;
import com.renanpontes.isekaimahou.feature.origins_a.rune.TempEnchant;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Eventos de jogo do módulo: maldições, runas (retorno, encantamento temporário) e limpeza. */
public final class OaEvents {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(OaEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(OaEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, OaEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(OaEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(OaEvents::onBlockDrops);
        NeoForge.EVENT_BUS.addListener(OaEvents::onJoin);
        NeoForge.EVENT_BUS.addListener(OaEvents::onLeave);
        NeoForge.EVENT_BUS.addListener(OaEvents::onAnvil);
        NeoForge.EVENT_BUS.addListener(OaEvents::onGrindstone);
        NeoForge.EVENT_BUS.addListener(OaEvents::onContainerOpen);
        // Entrar no mundo em cima de um Círculo de Teleporte não dispara a viagem
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> CircleTravel.grantGrace(e.getEntity(), 40));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> CircleTravel.grantGrace(e.getEntity(), 40));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> CircleTravel.grantGrace(e.getEntity(), 40));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            VoodooLinks.clear();
            Frogify.clear();
            CircleTravel.clear();
        });
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        VoodooLinks.tick(event.getServer());
        Frogify.tick(event.getServer());
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        CircleTravel.tickRecall(sp);
        if (sp.tickCount % 20 == 7) TempEnchant.sweepPlayer(sp);
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        // Mau-Olhado: quem está amaldiçoado erra 30% dos golpes
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim
                && attacker.hasEffect(OaRegistries.EVIL_EYE) && attacker.getRandom().nextFloat() < 0.3F) {
            event.setCanceled(true);
            ServerLevel level = (ServerLevel) victim.level();
            level.sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                    8, 0.25, 0.25, 0.25, 0.02);
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
                    SoundSource.HOSTILE, 0.8F, 0.8F);
            return;
        }
        VoodooLinks.onIncomingDamage(event);
        // Concentração da Runa de Retorno quebra ao levar dano de verdade
        if (!event.isCanceled() && victim instanceof ServerPlayer sp && event.getAmount() > 0 && CircleTravel.recalling(sp)) {
            CircleTravel.cancelRecall(sp, "recall_hurt");
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Frog frog && Frogify.isFrogified(frog) && frog.level() instanceof ServerLevel level) {
            Frogify.revert(level, frog, event.getSource());
        }
    }

    /** Mau-Olhado num jogador: parte do que ele colhe se perde. */
    private static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player p) || !p.hasEffect(OaRegistries.EVIL_EYE)) return;
        int before = event.getDrops().size();
        event.getDrops().removeIf(it -> p.getRandom().nextFloat() < 0.4F);
        if (event.getDrops().size() < before) {
            event.getLevel().sendParticles(ParticleTypes.SMOKE, event.getPos().getX() + 0.5, event.getPos().getY() + 0.5,
                    event.getPos().getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.01);
        }
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Frogify.onJoin(event.getEntity(), level);
        // Item solto com encantamento temporário vencido volta ao normal
        if (event.getEntity() instanceof ItemEntity it && TempEnchant.expired(it.getItem(), level.getGameTime())) {
            TempEnchant.revert(it.getItem(), level.registryAccess());
        }
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) Frogify.onLeave(event.getEntity());
    }

    /** O encantamento temporário não pode ser passado adiante na bigorna. */
    private static void onAnvil(AnvilUpdateEvent event) {
        if (event.getLeft().has(OaRegistries.TEMP_ENCHANT.get()) || event.getRight().has(OaRegistries.TEMP_ENCHANT.get())) {
            event.setCanceled(true);
        }
    }

    private static void onGrindstone(GrindstoneEvent.OnPlaceItem event) {
        if (event.getTopItem().has(OaRegistries.TEMP_ENCHANT.get()) || event.getBottomItem().has(OaRegistries.TEMP_ENCHANT.get())) {
            event.setCanceled(true);
        }
    }

    /** Ao abrir um baú/container, desfaz encantamentos temporários vencidos guardados nele. */
    private static void onContainerOpen(PlayerContainerEvent.Open event) {
        Player p = event.getEntity();
        if (p.level().isClientSide) return;
        long now = p.level().getGameTime();
        for (Slot s : event.getContainer().slots) {
            if (TempEnchant.expired(s.getItem(), now)) {
                TempEnchant.revert(s.getItem(), p.level().registryAccess());
                s.setChanged();
            }
        }
    }

    private OaEvents() {}
}
